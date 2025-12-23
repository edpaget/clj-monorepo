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
   [clojure.string :as str]
   [polix.ast :as ast]
   [polix.collection-ops :as coll-ops]
   [polix.operators :as op]
   [polix.parser :as parser]
   [polix.residual :as res]
   [polix.result :as r]))

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
  - `nil` if any result is `nil` (contradiction)
  - `{}` if all results are `{}` (satisfied)
  - Merged residual otherwise

  Uses [[polix.residual/merge-residuals]] for combining constraint maps."
  [results]
  (cond
    (some nil? results) nil
    (every? res/satisfied? results) (res/satisfied)
    :else (reduce res/merge-residuals (res/satisfied) results)))

(defn unify-or
  "Combines results with OR semantics.

  Returns:
  - `{}` if any result is `{}` (short-circuit satisfied)
  - `nil` if all results are `nil` (contradiction)
  - Complex residual otherwise (disjunctions cannot be simplified)

  Uses [[polix.residual/combine-residuals]] for OR combination."
  [results]
  (cond
    (some res/satisfied? results) (res/satisfied)
    (every? nil? results) nil
    :else (reduce res/combine-residuals nil results)))

(defn unify-not
  "Negates a unification result.

  Returns:
  - `nil` if result is `{}` (NOT satisfied = contradiction)
  - `{}` if result is `nil` (NOT contradiction = satisfied)
  - Complex marker if result is residual (cannot negate unknown)"
  [result]
  (cond
    (res/satisfied? result) nil
    (nil? result) (res/satisfied)
    :else {::res/complex {:type :not :child result}}))

;;; ---------------------------------------------------------------------------
;;; Collection Operator Helpers
;;; ---------------------------------------------------------------------------

(declare unify-ast)

(defn- traverse-fns
  "Returns the function map needed by traverse-collection.

  Adapts [[unify-ast]] results to the format expected by collection ops.
  The collection ops module uses `{:residual ...}` format for residuals
  and expects residual keys to be vector paths."
  []
  {:eval-ast-fn (fn [ast document ctx]
                  (let [result (unify-ast ast document ctx)]
                    (cond
                      (res/satisfied? result) true
                      (nil? result) false
                      (res/has-complex? result) {:complex result}
                      :else {:residual result})))
   :with-binding-fn with-binding
   :get-binding-fn get-binding
   :path-exists-fn path-exists?})

(defn- adapt-collection-result
  "Converts collection op result back to unify format."
  [result]
  (cond
    (true? result) (res/satisfied)
    (false? result) nil
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
  and the actual `value` from the document.

  Returns `{}` if satisfied, `nil` if contradicted, or a complex marker if
  the operator is unknown."
  [ctx constraint value]
  (let [result (op/eval-in-context ctx constraint value)]
    (cond
      (true? result) (res/satisfied)
      (false? result) nil
      :else {::res/complex {:unknown-op (:op constraint)}})))

(defn unify-constraints-for-key
  "Unifies all constraints for a key against a value.

  Returns:
  - `{}` if all satisfied
  - `nil` if any contradicted
  - Residual if value missing or operator unknown"
  [ctx constraints value value-present? path]
  (if-not value-present?
    (res/residual path (mapv (fn [c] [(:op c) (:value c)]) constraints))
    (let [results (map #(unify-constraint ctx % value) constraints)]
      (cond
        (some nil? results) nil
        (every? res/satisfied? results) (res/satisfied)
        :else (res/residual path
                            (mapv (fn [[_ c]] [(:op c) (:value c)])
                                  (filter #(not (res/satisfied? (first %)))
                                          (map vector results constraints))))))))

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
      (let [binding-name (keyword binding-ns)
            bound-value  (get-binding ctx binding-name)]
        (if (nil? bound-value)
          (res/residual path [[:binding binding-name :any]])
          (if (path-exists? bound-value path)
            (get-in bound-value path)
            (res/residual path [[:any]]))))
      (if (path-exists? document path)
        (get-in document path)
        (res/residual path [[:any]])))))

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

(defn- unify-children
  "Unifies all child nodes of a function call."
  [children document ctx]
  (mapv #(unify-ast % document ctx) children))

(defn- resolve-accessor-value
  "Resolves the value for a doc-accessor node."
  [accessor-node document ctx]
  (let [path       (:value accessor-node)
        binding-ns (get-in accessor-node [:metadata :binding-ns])]
    (if binding-ns
      (let [binding-name (keyword binding-ns)
            bound-value  (get-binding ctx binding-name)]
        (if (and bound-value (path-exists? bound-value path))
          {:found true :value (get-in bound-value path)}
          {:found false}))
      (if (path-exists? document path)
        {:found true :value (get-in document path)}
        {:found false}))))

(defn- comparison-to-residual
  "Converts a comparison function call to a residual if possible.

  Returns `[true result]` when handled, `[false nil]` when not applicable.
  This tuple convention distinguishes between a nil contradiction result
  and the function not being able to handle the comparison."
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
                  result     (op/eval-in-context ctx constraint (:value resolved))]
              (cond
                (true? result) [true (res/satisfied)]
                (false? result) [true nil]
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
                  result     (op/eval-in-context ctx constraint (:value resolved))]
              (cond
                (true? result) [true (res/satisfied)]
                (false? result) [true nil]
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
                (false? result) [true nil]
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
                    (false? op-result) nil
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
  - `opts` - Optional map with `:operators`, `:fallback`, `:strict?`

  Returns:
  - `{}` if fully satisfied
  - `{:key [constraints]}` if partial (residual)
  - `nil` if contradiction

  ## Examples

      ;; Unify AST
      (unify ast {:role \"admin\"})
      ;; => {}

      ;; Partial evaluation
      (unify ast {:role \"admin\"})  ; missing :level
      ;; => {[:level] [[:> 5]]}

      ;; Contradiction
      (unify ast {:role \"guest\"})
      ;; => nil"
  ([policy document]
   (unify policy document {}))
  ([policy document opts]
   (let [ctx (op/make-context opts)]
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
  - `[:contradiction]` for `nil`
  - The simplified constraints for residual"
  [result]
  (cond
    (res/satisfied? result) nil
    (nil? result) [:contradiction]
    (res/residual? result)
    (let [constraints (residual->constraints result)]
      (if (= 1 (count constraints))
        (first constraints)
        (into [:and] constraints)))
    :else [:complex result]))
