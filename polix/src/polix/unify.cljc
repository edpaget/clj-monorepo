(ns polix.unify
  "Core unification engine for policy evaluation.

  Unifies policies with documents, returning residuals that describe the
  remaining constraints after partial evaluation.

  ## Result Types

  Unification returns one of three result types:

  - `{}` (empty map) — satisfied, all constraints met
  - `{:key [constraints]}` — residual, some constraints remain
  - `nil` — contradiction, no document can satisfy the policy

  ## Example

      (require '[polix.unify :as unify]
               '[polix.parser :as parser]
               '[polix.result :as r])

      ;; Fully satisfied
      (let [ast (r/unwrap (parser/parse-policy [:= :doc/role \"admin\"]))]
        (unify/unify ast {:role \"admin\"}))
      ;; => {}

      ;; Contradiction
      (unify/unify ast {:role \"guest\"})
      ;; => nil

      ;; Residual (missing data)
      (unify/unify ast {})
      ;; => {[:role] [[:= \"admin\"]]}

  Uses [[polix.residual]] for result construction and [[polix.operators]]
  for constraint evaluation."
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [polix.ast :as ast]
   [polix.collection-ops :as coll-ops]
   [polix.operators :as op]
   [polix.parser :as parser]
   [polix.registry :as registry]
   [polix.residual :as res]
   [polix.result :as r]))

;;; ---------------------------------------------------------------------------
;;; Value Classification
;;; ---------------------------------------------------------------------------

(defn- concrete-value?
  "Returns true if x is a concrete value (not a residual or complex marker).

  Used by let bindings to distinguish storable results from unresolved
  constraints. A concrete value can be stored in the self-context and used
  in subsequent expressions."
  [x]
  (and (not (res/residual? x))
       (not (res/has-complex? x))))

(defn- computed-field?
  "Returns true if a document value is a computed field (policy expression).

  Computed fields are vectors starting with a keyword operator. They are
  lazily evaluated when accessed via `:doc/` accessors."
  [value]
  (and (vector? value)
       (seq value)
       (keyword? (first value))))

;;; ---------------------------------------------------------------------------
;;; Path Utilities
;;; ---------------------------------------------------------------------------

(defn path-exists?
  "Returns true if the full path exists in the document.

  Unlike `get-in`, this distinguishes between a nil value at the path
  and a missing key. Returns true only if all keys in the path exist,
  even if the final value is nil.

      (path-exists? {:user {:name \"Alice\"}} [:user :name])  ;=> true
      (path-exists? {:user {:name nil}} [:user :name])       ;=> true
      (path-exists? {:user {}} [:user :name])                ;=> false
      (path-exists? {} [:user :name])                        ;=> false"
  [document path]
  (loop [current   document
         remaining path]
    (if (empty? remaining)
      true
      (let [k (first remaining)]
        (if (and (associative? current) (contains? current k))
          (recur (get current k) (rest remaining))
          false)))))

(defn path->doc-accessor
  "Converts a path vector back to a doc accessor keyword.

      (path->doc-accessor [:role])       ;=> :doc/role
      (path->doc-accessor [:user :name]) ;=> :doc/user.name"
  [path]
  (keyword "doc" (str/join "." (map name path))))

;;; ---------------------------------------------------------------------------
;;; Binding Context
;;; ---------------------------------------------------------------------------

(defn with-binding
  "Adds a binding to the evaluation context.

  Bindings are used by quantifiers to track the current element being iterated."
  [ctx binding-name value]
  (assoc-in ctx [:bindings binding-name] value))

(defn get-binding
  "Gets a binding value from context, or nil if not found."
  [ctx binding-name]
  (get-in ctx [:bindings binding-name]))

;;; ---------------------------------------------------------------------------
;;; Three-Valued Boolean Logic (Residual-Based)
;;; ---------------------------------------------------------------------------

(defn unify-and
  "Combines results with AND semantics.

  Returns:
  - `{}` if all results are satisfied
  - Merged residual otherwise (may contain open and/or conflict constraints)

  With the conflict model, all branches are evaluated to collect all
  constraints for diagnostic purposes. Uses [[polix.residual/merge-residuals]]
  for combining constraint maps."
  [results]
  (cond
    ;; Legacy nil handling during transition
    (some nil? results) nil
    (every? res/satisfied? results) (res/satisfied)
    :else (reduce res/merge-residuals (res/satisfied) results)))

(defn unify-or
  "Combines results with OR semantics.

  Returns:
  - `{}` if any result is satisfied (short-circuit)
  - Complex residual with all branches otherwise

  For OR, if any branch succeeds the whole succeeds. If all fail,
  we return a complex marker containing all the failure branches
  (which may include conflicts and/or open constraints).

  Uses [[polix.residual/combine-residuals]] for OR combination."
  [results]
  (cond
    (some res/satisfied? results) (res/satisfied)
    ;; Legacy nil handling during transition
    (every? nil? results) {::res/complex {:type :or :branches []}}
    ;; Filter out nils for transition, combine remaining results
    :else (let [non-nil (remove nil? results)]
            (if (empty? non-nil)
              {::res/complex {:type :or :branches []}}
              (reduce res/combine-residuals non-nil)))))

(defn unify-not
  "Negates a unification result.

  - NOT satisfied → complex (we don't know what to negate)
  - NOT all-conflicts → satisfied (if everything failed, NOT succeeds)
  - NOT open/partial → complex (cannot negate unknown)

  The key insight: if a residual contains only conflicts (all constraints
  were evaluated and all failed), then NOT of that is satisfied."
  [result]
  (cond
    (res/satisfied? result)
    ;; NOT true = need something that fails, but we don't know what
    {::res/complex {:type :not-satisfied}}

    ;; Legacy nil handling during transition
    (nil? result)
    (res/satisfied)

    (res/all-conflicts? result)
    ;; NOT (all failed) = satisfied
    (res/satisfied)

    :else
    ;; NOT (partially evaluated) = complex
    {::res/complex {:type :not :child result}}))

;;; ---------------------------------------------------------------------------
;;; Collection Operator Helpers
;;; ---------------------------------------------------------------------------

(declare unify-ast)

;;; ---------------------------------------------------------------------------
;;; Computed Field Evaluation
;;; ---------------------------------------------------------------------------

(defn- evaluate-computed-field
  "Lazily evaluates a computed field, tracking evaluation stack for cycle detection.

  Takes the field key, the document, and context with `:eval-stack` tracking.
  Returns the evaluated value or throws on cycle detection."
  [field-key document ctx]
  (let [eval-stack (or (:eval-stack ctx) #{})]
    (when (contains? eval-stack field-key)
      (throw (ex-info "Circular dependency in computed field"
                      {:field field-key :stack eval-stack})))
    (let [field-value (get document field-key)
          ast         (r/unwrap (parser/parse-policy field-value))
          new-ctx     (update ctx :eval-stack (fnil conj #{}) field-key)]
      (unify-ast ast document new-ctx))))

(defn- resolve-doc-value
  "Resolves a document value, evaluating computed fields lazily.

  For static values, returns them directly.
  For computed fields, evaluates with cycle detection."
  [path document ctx]
  (let [key   (first path)
        value (get document key)]
    (if (computed-field? value)
      (let [result (evaluate-computed-field key document ctx)]
        (if (= 1 (count path))
          result
          (get-in result (rest path))))
      (get-in document path))))

;;; ---------------------------------------------------------------------------
;;; Collection Traversal Helpers
;;; ---------------------------------------------------------------------------

(defn- traverse-fns
  "Returns the function map needed by traverse-collection.

  Adapts [[unify-ast]] results to the format expected by collection ops.
  The collection ops module uses `{:residual ...}` format for residuals
  and expects residual keys to be vector paths.

  Conflict residuals are treated as `false` (definite failure) because they
  indicate the constraint was evaluated against concrete data and failed."
  []
  {:eval-ast-fn (fn [ast document ctx]
                  (let [result (unify-ast ast document ctx)]
                    (cond
                      (res/satisfied? result) true
                      (nil? result) false
                      (res/has-conflicts? result) false
                      (res/has-complex? result) {:complex result}
                      :else {:residual result})))
   :with-binding-fn with-binding
   :get-binding-fn get-binding
   :path-exists-fn path-exists?})

(defn- adapt-collection-result
  "Converts collection op result back to unify format.

  Returns conflict marker for false results from collection ops (e.g., exists
  with no matching elements). The complex marker preserves that a definite
  failure occurred without path-specific constraint info."
  [result]
  (cond
    (true? result) (res/satisfied)
    (false? result) {::res/complex {:type :collection-conflict}}
    (and (map? result) (contains? result :complex)) (:complex result)
    (and (map? result) (contains? result :residual)) (:residual result)
    (and (map? result) (contains? result :partial-count)) result
    :else result))

(defn- unify-collection-op
  "Unifies a collection operation using the registered operator.

  Looks up the operator in the registry and calls traverse-collection.
  Falls back to a complex result if the operator is unknown."
  [op-key binding body document ctx]
  (if-let [coll-op (coll-ops/get-collection-op op-key)]
    (adapt-collection-result
     (coll-ops/traverse-collection coll-op binding body document ctx (traverse-fns)))
    {::res/complex {:unknown-collection-op op-key}}))

;;; ---------------------------------------------------------------------------
;;; Constraint Unification
;;; ---------------------------------------------------------------------------

(defn unify-constraint
  "Unifies a single constraint against a value.

  Takes an operator context `ctx`, a constraint map with `:op` and `:value` keys,
  the actual `value` from the document, and the `path` for residual construction.

  Returns `{}` if satisfied, or a conflict residual if violated."
  [ctx constraint value path]
  (let [result (op/eval-in-context ctx constraint value)]
    (cond
      (true? result) (res/satisfied)
      (false? result) (res/conflict-residual path [(:op constraint) (:value constraint)] value)
      :else {::res/complex {:unknown-op (:op constraint)}})))

(defn unify-constraints-for-key
  "Unifies all constraints for a key against a value.

  Returns:
  - `{}` if all satisfied
  - Residual with open constraints if value missing
  - Residual with conflicts if any constraint violated"
  [ctx constraints value value-present? path]
  (if-not value-present?
    ;; Value missing: return open constraints
    (res/residual path (mapv (fn [c] [(:op c) (:value c)]) constraints))
    ;; Value present: evaluate each constraint
    (let [results (map #(unify-constraint ctx % value path) constraints)]
      (if (every? res/satisfied? results)
        (res/satisfied)
        ;; Merge all results (conflicts and any complex markers)
        (reduce res/merge-residuals (res/satisfied) results)))))

;;; ---------------------------------------------------------------------------
;;; Constraint Set Unification
;;; ---------------------------------------------------------------------------

(defn unify-constraint-set
  "Unifies a constraint set against a document.

  A constraint set is a map of `{path -> [constraints], ::complex -> [nodes]}`.
  Paths are vectors of keywords representing nested document access.

  Returns:
  - `{}` if all constraints satisfied
  - `nil` if any constraint contradicted
  - Residual map if partial match"
  ([constraint-set document]
   (unify-constraint-set constraint-set document (op/make-context)))
  ([constraint-set document ctx]
   (loop [paths-to-check (keys (dissoc constraint-set ::res/complex))
          acc            (res/satisfied)]
     (cond
       (nil? acc) nil
       (empty? paths-to-check) acc
       :else
       (let [path           (first paths-to-check)
             constraints    (get constraint-set path)
             value-present? (path-exists? document path)
             value          (get-in document path)
             result         (unify-constraints-for-key ctx constraints value value-present? path)]
         (recur (rest paths-to-check)
                (res/merge-residuals acc result)))))))

;;; ---------------------------------------------------------------------------
;;; AST Unification
;;; ---------------------------------------------------------------------------

(defmulti unify-ast
  "Unifies an AST node with a document.

  Returns:
  - `{}` — satisfied
  - `{:key [constraints]}` — residual
  - `nil` — contradiction"
  (fn [node _document _ctx] (:type node)))

(defmethod unify-ast ::ast/literal
  [node _document _ctx]
  (:value node))

(defmethod unify-ast ::ast/doc-accessor
  [node document ctx]
  (let [path       (:value node)
        binding-ns (get-in node [:metadata :binding-ns])]
    (if binding-ns
      ;; Binding accessor (e.g., :u/role in quantifier context)
      (let [binding-name (keyword binding-ns)
            bound-value  (get-binding ctx binding-name)]
        (if (nil? bound-value)
          (res/residual path [[:binding binding-name :any]])
          (if (path-exists? bound-value path)
            (get-in bound-value path)
            (res/residual path [[:any]]))))
      ;; Document accessor - use lazy evaluation for computed fields
      (let [key (first path)]
        (if (and key (computed-field? (get document key)))
          (resolve-doc-value path document ctx)
          (if (path-exists? document path)
            (get-in document path)
            (res/residual path [[:any]])))))))

(defmethod unify-ast ::ast/quantifier
  [node document ctx]
  (let [quantifier-type (:value node)
        binding         (get-in node [:metadata :binding])
        body            (first (:children node))]
    (unify-collection-op quantifier-type binding body document ctx)))

(defmethod unify-ast ::ast/value-fn
  [node document ctx]
  (let [fn-type (:value node)
        binding (get-in node [:metadata :binding])]
    (unify-collection-op fn-type binding nil document ctx)))

(defmethod unify-ast ::ast/self-accessor
  [node _document ctx]
  (let [path (:value node)
        key  (first path)]
    (if-let [self-bindings (:self ctx)]
      (if (contains? self-bindings key)
        (get-in self-bindings path)
        (res/residual path [[:self :missing]]))
      (res/residual path [[:self :missing]]))))

(defmethod unify-ast ::ast/param-accessor
  [node _document ctx]
  (let [param-key (:value node)]
    (cond
      ;; No params context at all
      (nil? (:params ctx))
      (res/residual [param-key] [[:param :no-context]])

      ;; Param is bound
      (contains? (:params ctx) param-key)
      (get (:params ctx) param-key)

      ;; Param is explicitly marked unbound (partial application)
      (contains? (:unbound-params ctx) param-key)
      (res/residual [param-key] [[:param :unbound]])

      ;; Param is missing (error case)
      :else
      (res/residual [param-key] [[:param :missing]]))))

(defmethod unify-ast ::ast/event-accessor
  [node _document ctx]
  (let [path (:value node)]
    (if-let [event (:event ctx)]
      (if (path-exists? event path)
        (get-in event path)
        (res/residual path [[:event :any]]))
      (res/residual path [[:event :missing]]))))

(defmethod unify-ast ::ast/policy-reference
  [node document ctx]
  (let [{:keys [namespace name]} (:value node)
        provided-params          (get-in node [:metadata :params])
        registry                 (:registry ctx)]
    (if-not registry
      {::res/complex {:type :no-registry
                      :namespace namespace
                      :name name}}
      (if-let [info (registry/policy-info registry namespace name)]
        (let [{:keys [expr defaults]} info
              policy-ast      (r/unwrap (parser/parse-policy expr))
              required-params (parser/extract-param-keys policy-ast)
              ;; Merge precedence: defaults < context params < provided params
              merged-params   (merge defaults (:params ctx) provided-params)
              ;; Track which required params aren't bound after all merging
              missing-params  (set/difference required-params
                                               (set (keys merged-params)))
              ctx-with-params (-> ctx
                                  (assoc :params merged-params)
                                  (update :unbound-params
                                          (fnil into #{}) missing-params))]
          (unify-ast policy-ast document ctx-with-params))
        {::res/complex {:type :unknown-policy
                        :namespace namespace
                        :name name}}))))

(defmethod unify-ast ::ast/let-binding
  [node document ctx]
  (let [bindings (get-in node [:metadata :bindings])
        body     (first (:children node))]
    (loop [remaining bindings
           self-ctx  (or (:self ctx) {})]
      (if (empty? remaining)
        (unify-ast body document (assoc ctx :self self-ctx))
        (let [{:keys [name expr]} (first remaining)
              result              (unify-ast expr document (assoc ctx :self self-ctx))]
          (cond
            ;; Concrete value: store it and continue
            (concrete-value? result)
            (recur (rest remaining) (assoc self-ctx name result))

            ;; Conflict residual: binding failed
            (res/has-conflicts? result)
            {::res/complex {:type :let-binding-conflict
                            :binding name
                            :conflicts result}}

            ;; Open residual: can't resolve yet
            (res/residual? result)
            {::res/complex {:type :let-binding-residual
                            :binding name
                            :residual result}}

            ;; Complex marker: pass through
            (res/has-complex? result)
            {::res/complex {:type :let-binding-complex
                            :binding name
                            :complex result}}

            ;; Default: store the value
            :else
            (recur (rest remaining) (assoc self-ctx name result))))))))

(defn- unify-children
  "Unifies all child nodes of a function call."
  [children document ctx]
  (mapv #(unify-ast % document ctx) children))

(defn- resolve-accessor-value
  "Resolves the value for a doc-accessor node.

  For document accessors, also handles computed fields (policy expressions
  stored as document values) by evaluating them lazily with cycle detection."
  [accessor-node document ctx]
  (let [path       (:value accessor-node)
        binding-ns (get-in accessor-node [:metadata :binding-ns])]
    (if binding-ns
      (let [binding-name (keyword binding-ns)
            bound-value  (get-binding ctx binding-name)]
        (if (and bound-value (path-exists? bound-value path))
          {:found true :value (get-in bound-value path)}
          {:found false}))
      ;; Document accessor - check for computed fields
      (let [key (first path)]
        (if (and key (computed-field? (get document key)))
          {:found true :value (resolve-doc-value path document ctx)}
          (if (path-exists? document path)
            {:found true :value (get-in document path)}
            {:found false}))))))

(defn- comparison-to-residual
  "Converts a comparison function call to a residual if possible.

  Returns `[true result]` when handled, `[false nil]` when not applicable.
  This tuple convention distinguishes between not being able to handle
  the comparison and successfully handling it (even as a conflict)."
  [op-key children document ctx]
  (if-not (= 2 (count children))
    [false nil]
    (let [[left right] children
          left-type    (:type left)
          right-type   (:type right)]
      (cond
        ;; [:= :doc/key value]
        (and (= ::ast/doc-accessor left-type)
             (= ::ast/literal right-type))
        (let [path     (:value left)
              expected (:value right)
              resolved (resolve-accessor-value left document ctx)]
          (if (:found resolved)
            (let [constraint {:op op-key :value expected}
                  actual     (:value resolved)
                  result     (op/eval-in-context ctx constraint actual)]
              (cond
                (true? result) [true (res/satisfied)]
                (false? result) [true (res/conflict-residual path [op-key expected] actual)]
                :else [true {::res/complex {:op op-key :ast {:left left :right right}}}]))
            [true (res/residual path [[op-key expected]])]))

        ;; [:= value :doc/key] - flipped
        (and (= ::ast/literal left-type)
             (= ::ast/doc-accessor right-type))
        (let [path       (:value right)
              expected   (:value left)
              flipped-op (case op-key
                           :< :>
                           :> :<
                           :<= :>=
                           :>= :<=
                           op-key)
              resolved   (resolve-accessor-value right document ctx)]
          (if (:found resolved)
            (let [constraint {:op flipped-op :value expected}
                  actual     (:value resolved)
                  result     (op/eval-in-context ctx constraint actual)]
              (cond
                (true? result) [true (res/satisfied)]
                (false? result) [true (res/conflict-residual path [flipped-op expected] actual)]
                :else [true {::res/complex {:op flipped-op :ast {:left left :right right}}}]))
            [true (res/residual path [[flipped-op expected]])]))

        ;; [:= :doc/a :doc/b] - cross-key comparison
        (and (= ::ast/doc-accessor left-type)
             (= ::ast/doc-accessor right-type))
        (let [left-path      (:value left)
              right-path     (:value right)
              left-resolved  (resolve-accessor-value left document ctx)
              right-resolved (resolve-accessor-value right document ctx)
              left-found?    (:found left-resolved)
              right-found?   (:found right-resolved)]
          (cond
            (and left-found? right-found?)
            (let [left-val   (:value left-resolved)
                  right-val  (:value right-resolved)
                  constraint {:op op-key :value right-val}
                  result     (op/eval-in-context ctx constraint left-val)]
              (cond
                (true? result) [true (res/satisfied)]
                (false? result) [true {::res/cross-key [{:op op-key
                                                         :left-path left-path
                                                         :right-path right-path
                                                         :left-value left-val
                                                         :right-value right-val
                                                         :conflict true}]}]
                :else [true {::res/complex {:op op-key :cross-key true :left left-path :right right-path}}]))

            :else
            [true {::cross-key [{:op         op-key
                                 :left-path  left-path
                                 :right-path right-path
                                 :left-value (when left-found? (:value left-resolved))
                                 :right-value (when right-found? (:value right-resolved))}]}]))

        :else [false nil]))))

(defn- has-unresolved?
  "Returns true if x contains unresolved constraints (residual or complex).

  Used to detect when function call arguments have unresolved parts that
  prevent direct evaluation."
  [x]
  (or (res/residual? x)
      (res/has-complex? x)))

(defmethod unify-ast ::ast/function-call
  [node document ctx]
  (let [op-key   (:value node)
        children (:children node)]
    (case op-key
      :and (unify-and (unify-children children document ctx))
      :or (unify-or (unify-children children document ctx))
      :not (unify-not (unify-ast (first children) document ctx))

      (let [[handled? result] (comparison-to-residual op-key children document ctx)]
        (if handled?
          result
          (let [evaluated-args (unify-children children document ctx)]
            (if (some has-unresolved? evaluated-args)
              {::res/complex {:op op-key :children evaluated-args}}
              (if-let [operator (op/get-operator-in-context ctx op-key)]
                (let [op-result (apply op/eval operator evaluated-args)]
                  (cond
                    (true? op-result) (res/satisfied)
                    ;; Operator returned false but we don't have path info for a proper conflict
                    ;; Return a complex marker with the failure details
                    (false? op-result) {::res/complex {:type :op-failed
                                                       :op op-key
                                                       :args evaluated-args}}
                    :else op-result))
                {::res/complex {:op op-key :children evaluated-args}}))))))))

(defmethod unify-ast :default
  [node _document _ctx]
  {::res/complex {:unknown-node-type (:type node) :node node}})

;;; ---------------------------------------------------------------------------
;;; Entry Point
;;; ---------------------------------------------------------------------------

(defn unify
  "Unifies a policy with a document.

  Takes:
  - `policy` - AST node, constraint set, or policy expression vector
  - `document` - Associative data structure to evaluate against
  - `opts` - Optional map with:
    - `:operators` - operator overrides
    - `:fallback` - fallback operator lookup
    - `:strict?` - error on unknown operators
    - `:registry` - policy registry for resolving policy references
    - `:params` - parameter map for `:param/` accessors
    - `:self` - self-reference map for `:self/` accessors
    - `:event` - event data for `:event/` accessors

  Returns:
  - `{}` if fully satisfied
  - `{:key [constraints]}` if partial — open constraints or conflicts

  Residuals may contain:
  - **Open constraints** like `[:< 10]` — awaiting evaluation
  - **Conflict constraints** like `[:conflict [:< 10] 15]` — evaluated and failed

  Use [[polix.residual/has-conflicts?]] to check if the result contains conflicts.

  ## Examples

      ;; Unify AST
      (unify ast {:role \"admin\"})
      ;; => {}

      ;; Partial evaluation (missing data)
      (unify ast {:role \"admin\"})  ; missing :level
      ;; => {[:level] [[:> 5]]}

      ;; Conflict (data present but violates constraint)
      (unify ast {:role \"guest\"})
      ;; => {[:role] [[:conflict [:= \"admin\"] \"guest\"]]}

      ;; With registry for policy references
      (unify [:auth/admin] {:role \"admin\"} {:registry my-registry})"
  ([policy document]
   (unify policy document {}))
  ([policy document opts]
   (let [op-ctx (op/make-context opts)
         ctx    (merge op-ctx
                       (select-keys opts [:registry :params :self :event]))]
     (cond
       (and (map? policy) (:type policy))
       (unify-ast policy document ctx)

       (and (map? policy) (not (:type policy)))
       (unify-constraint-set policy document ctx)

       (vector? policy)
       (let [parse-result (r/unwrap (parser/parse-policy policy))]
         (unify-ast parse-result document ctx))

       :else
       (throw (ex-info "Unknown policy type" {:policy policy}))))))

;;; ---------------------------------------------------------------------------
;;; Residual Conversion
;;; ---------------------------------------------------------------------------

(defn residual->constraints
  "Converts a residual map back to policy expressions.

  Takes `{[:level] [[:> 5]], [:user :status] [[:in #{\"a\" \"b\"}]]}`
  Returns `[[:> :doc/level 5] [:in :doc/user.status #{\"a\" \"b\"}]]`

  Also handles cross-key constraints:
  `{::cross-key [{:op := :left-path [:a] :right-path [:b]}]}`
  Returns `[[:= :doc/a :doc/b]]`"
  [residual]
  (when (res/residual? residual)
    (let [standard-constraints
          (mapcat
           (fn [[path constraints]]
             (when (vector? path)
               (map (fn [[op value]]
                      [op (path->doc-accessor path) value])
                    constraints)))
           (dissoc residual ::cross-key ::res/complex))

          cross-key-constraints
          (map (fn [{:keys [op left-path right-path]}]
                 [op (path->doc-accessor left-path) (path->doc-accessor right-path)])
               (get residual ::cross-key []))]

      (concat standard-constraints cross-key-constraints))))

(defn result->policy
  "Converts a unification result to a simplified policy expression.

  Returns:
  - `nil` for `{}` (satisfied, no constraints needed)
  - `[:contradiction]` for legacy `nil` or conflict residuals
  - The simplified constraints for open residual"
  [result]
  (cond
    (res/satisfied? result) nil
    (nil? result) [:contradiction]
    (res/has-conflicts? result) [:contradiction]
    (res/residual? result)
    (let [constraints (residual->constraints result)]
      (if (= 1 (count constraints))
        (first constraints)
        (into [:and] constraints)))
    :else [:complex result]))
