(ns polix.engine
  "Unified policy evaluation engine.

  Combines constraint-based and AST-based evaluation into a single engine
  that returns three-valued results: `true` (satisfied), `false` (contradicted),
  or `{:residual ...}` (partial match with remaining constraints).

  Uses constraint sets as primary representation with AST fallback for complex
  expressions (OR, NOT, nested functions that cannot be normalized to constraints).

  ## Example

      (require '[polix.engine :as engine]
               '[polix.parser :as parser]
               '[polix.result :as r])

      ;; Evaluate parsed AST
      (let [ast (r/unwrap (parser/parse-policy [:= :doc/role \"admin\"]))]
        (engine/evaluate ast {:role \"admin\"}))
      ;; => true

      ;; Missing keys return residuals
      (engine/evaluate ast {})
      ;; => {:residual {[:role] [[:= \"admin\"]]}}

      ;; Nested paths
      (let [ast (r/unwrap (parser/parse-policy [:= :doc/user.name \"Alice\"]))]
        (engine/evaluate ast {:user {:name \"Alice\"}}))
      ;; => true

  Uses [[polix.operators]] registry as primary operator source with support
  for context overrides via the `:operators` option."
  (:require
   [clojure.string :as str]
   [polix.ast :as ast]
   [polix.collection-ops :as coll-ops]
   [polix.operators :as op]
   [polix.parser :as parser]
   [polix.result :as r]))

;;; ---------------------------------------------------------------------------
;;; Result Types
;;; ---------------------------------------------------------------------------

(defn residual?
  "Returns true if x is a residual result."
  [x]
  (and (map? x) (contains? x :residual)))

(defn complex?
  "Returns true if x is a complex (unevaluable) result."
  [x]
  (and (map? x) (contains? x :complex)))

(defn result-type
  "Returns the type of an evaluation result: :satisfied, :contradicted,
   :residual, or :complex."
  [x]
  (cond
    (true? x) :satisfied
    (false? x) :contradicted
    (residual? x) :residual
    (complex? x) :complex
    :else :value))

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
;;; Residual Merging
;;; ---------------------------------------------------------------------------

(defn merge-residuals
  "Merges multiple residual results into a single residual."
  [residuals]
  {:residual
   (reduce
    (fn [acc {:keys [residual]}]
      (merge-with into acc residual))
    {}
    residuals)})

;;; ---------------------------------------------------------------------------
;;; Three-Valued Boolean Logic
;;; ---------------------------------------------------------------------------

(defn eval-and
  "Evaluates AND with three-valued logic.

   - false AND x = false
   - true AND true = true
   - true AND residual = residual
   - residual AND residual = merged residual"
  [results]
  (cond
    (some false? results) false
    (some complex? results) {:complex {:op :and :children (filter complex? results)}}
    (every? true? results) true
    :else (merge-residuals (filter residual? results))))

(defn eval-or
  "Evaluates OR with three-valued logic.

   - true OR x = true
   - false OR false = false
   - false OR residual = residual (single)
   - residual OR residual = complex (cannot simplify)"
  [results]
  (cond
    (some true? results) true
    (every? false? results) false
    ;; Single residual with rest false
    (and (= 1 (count (filter residual? results)))
         (every? #(or (false? %) (residual? %)) results))
    (first (filter residual? results))
    ;; Multiple residuals or complex - cannot simplify
    :else {:complex {:op :or :children (remove false? results)}}))

(defn eval-not
  "Evaluates NOT with three-valued logic.

   - NOT true = false
   - NOT false = true
   - NOT residual = complex (cannot negate residual)"
  [result]
  (cond
    (true? result) false
    (false? result) true
    :else {:complex {:op :not :child result}}))

;;; ---------------------------------------------------------------------------
;;; Collection Operator Helpers
;;; ---------------------------------------------------------------------------

(declare eval-ast-3v)

(defn- traverse-fns
  "Returns the function map needed by traverse-collection."
  []
  {:eval-ast-fn eval-ast-3v
   :with-binding-fn with-binding
   :get-binding-fn get-binding
   :path-exists-fn path-exists?})

(defn- evaluate-collection-op
  "Evaluates a collection operation using the registered operator.

  Looks up the operator in the registry and calls traverse-collection.
  Falls back to a complex result if the operator is unknown."
  [op-key binding body document ctx]
  (if-let [coll-op (coll-ops/get-collection-op op-key)]
    (coll-ops/traverse-collection coll-op binding body document ctx (traverse-fns))
    {:complex {:unknown-collection-op op-key}}))

;;; ---------------------------------------------------------------------------
;;; Constraint Evaluation
;;; ---------------------------------------------------------------------------

(defn evaluate-constraint
  "Evaluates a single constraint against a value.

   Takes an operator context `ctx`, a constraint map with `:op` and `:value` keys,
   and the actual `value` from the document.

   Returns true if satisfied, false if contradicted, nil if operator unknown."
  [ctx constraint value]
  (op/eval-in-context ctx constraint value))

(defn evaluate-constraints-for-key
  "Evaluates all constraints for a key against a value.

   Returns:
   - true if all satisfied
   - false if any contradicted
   - `{:residual [...]}` if value missing or operator unknown"
  [ctx constraints value value-present?]
  (if-not value-present?
    {:residual (mapv (fn [c] [(:op c) (:value c)]) constraints)}
    (let [results (map #(evaluate-constraint ctx % value) constraints)]
      (cond
        (some false? results) false
        (every? true? results) true
        :else {:residual (mapv (fn [[_ c]] [(:op c) (:value c)])
                               (filter #(nil? (first %))
                                       (map vector results constraints)))}))))

;;; ---------------------------------------------------------------------------
;;; Constraint Set Evaluation
;;; ---------------------------------------------------------------------------

(defn evaluate-constraint-set
  "Evaluates a constraint set against a document.

   A constraint set is a map of `{path -> [constraints], ::complex -> [nodes]}`.
   Paths are vectors of keywords representing nested document access.

   Returns:
   - true if all constraints satisfied
   - false if any constraint contradicted
   - `{:residual {...}}` if partial match"
  ([constraint-set document]
   (evaluate-constraint-set constraint-set document (op/make-context)))
  ([constraint-set document ctx]
   (loop [paths-to-check    (keys (dissoc constraint-set ::complex))
          residuals         {}
          any-contradicted? false]
     (if (or any-contradicted? (empty? paths-to-check))
       (cond
         any-contradicted? false
         (seq residuals) {:residual residuals}
         :else true)
       (let [path           (first paths-to-check)
             constraints    (get constraint-set path)
             value-present? (path-exists? document path)
             value          (get-in document path)
             result         (evaluate-constraints-for-key ctx constraints value value-present?)]
         (recur
          (rest paths-to-check)
          (if (residual? result)
            (assoc residuals path (:residual result))
            residuals)
          (false? result)))))))

;;; ---------------------------------------------------------------------------
;;; Three-Valued AST Evaluation
;;; ---------------------------------------------------------------------------

(defmulti eval-ast-3v
  "Evaluates an AST node with three-valued semantics.

   Returns true, false, a value, `{:residual ...}`, or `{:complex ...}`."
  (fn [node _document _ctx] (:type node)))

(defmethod eval-ast-3v ::ast/literal
  [node _document _ctx]
  (:value node))

(defmethod eval-ast-3v ::ast/doc-accessor
  [node document ctx]
  (let [path       (:value node)
        binding-ns (get-in node [:metadata :binding-ns])]
    (if binding-ns
      ;; Binding accessor - look up in context
      (let [binding-name (keyword binding-ns)
            bound-value  (get-binding ctx binding-name)]
        (if (nil? bound-value)
          {:residual {path [[:binding binding-name :any]]}}
          (if (path-exists? bound-value path)
            (get-in bound-value path)
            {:residual {path [[:any]]}})))
      ;; Document accessor
      (if (path-exists? document path)
        (get-in document path)
        {:residual {path [[:any]]}}))))

(defmethod eval-ast-3v ::ast/quantifier
  [node document ctx]
  (let [quantifier-type (:value node)
        binding         (get-in node [:metadata :binding])
        body            (first (:children node))]
    (evaluate-collection-op quantifier-type binding body document ctx)))

(defmethod eval-ast-3v ::ast/value-fn
  [node document ctx]
  (let [fn-type (:value node)
        binding (get-in node [:metadata :binding])]
    (evaluate-collection-op fn-type binding nil document ctx)))

(defn- evaluate-children
  "Evaluates all child nodes of a function call."
  [children document ctx]
  (mapv #(eval-ast-3v % document ctx) children))

(defn- resolve-accessor-value
  "Resolves the value for a doc-accessor node, handling both document and binding accessors."
  [accessor-node document ctx]
  (let [path       (:value accessor-node)
        binding-ns (get-in accessor-node [:metadata :binding-ns])]
    (if binding-ns
      ;; Binding accessor
      (let [binding-name (keyword binding-ns)
            bound-value  (get-binding ctx binding-name)]
        (if (and bound-value (path-exists? bound-value path))
          {:found true :value (get-in bound-value path)}
          {:found false}))
      ;; Document accessor
      (if (path-exists? document path)
        {:found true :value (get-in document path)}
        {:found false}))))

(defn- comparison-to-constraint
  "Converts a comparison function call to a constraint if possible."
  [op-key children document ctx]
  (when (= 2 (count children))
    (let [[left right] children
          left-type    (:type left)
          right-type   (:type right)]
      (cond
        ;; [:= :doc/key value] or [:= :u/key value]
        (and (= ::ast/doc-accessor left-type)
             (= ::ast/literal right-type))
        (let [path     (:value left)
              expected (:value right)
              resolved (resolve-accessor-value left document ctx)]
          (if (:found resolved)
            (let [constraint {:op op-key :value expected}
                  result     (evaluate-constraint ctx constraint (:value resolved))]
              (if (nil? result)
                {:complex {:op op-key :ast {:left left :right right}}}
                result))
            {:residual {path [[op-key expected]]}}))

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
                  result     (evaluate-constraint ctx constraint (:value resolved))]
              (if (nil? result)
                {:complex {:op flipped-op :ast {:left left :right right}}}
                result))
            {:residual {path [[flipped-op expected]]}}))

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
            ;; Both values present - evaluate directly
            (and left-found? right-found?)
            (let [left-val   (:value left-resolved)
                  right-val  (:value right-resolved)
                  constraint {:op op-key :value right-val}
                  result     (evaluate-constraint ctx constraint left-val)]
              (if (nil? result)
                {:complex {:op op-key :cross-key true :left left-path :right right-path}}
                result))

            ;; One or both missing - return cross-key residual
            :else
            {:residual {::cross-key [{:op         op-key
                                      :left-path  left-path
                                      :right-path right-path
                                      :left-value (when left-found? (:value left-resolved))
                                      :right-value (when right-found? (:value right-resolved))}]}}))

        :else nil))))

(defmethod eval-ast-3v ::ast/function-call
  [node document ctx]
  (let [op-key   (:value node)
        children (:children node)]
    (case op-key
      ;; Boolean connectives
      :and (eval-and (evaluate-children children document ctx))
      :or (eval-or (evaluate-children children document ctx))
      :not (eval-not (eval-ast-3v (first children) document ctx))

      ;; Try constraint-based evaluation for comparisons
      ;; Use if-some to handle false results correctly (false is a valid result)
      (if-some [result (comparison-to-constraint op-key children document ctx)]
        result
        ;; Fall back to full evaluation for complex cases
        (let [evaluated-args (evaluate-children children document ctx)]
          (if (some #(or (residual? %) (complex? %)) evaluated-args)
            {:complex {:op op-key :children evaluated-args}}
            (if-let [operator (op/get-operator-in-context ctx op-key)]
              (apply op/eval operator evaluated-args)
              {:complex {:op op-key :children evaluated-args}})))))))

(defmethod eval-ast-3v :default
  [node _document _ctx]
  {:complex {:unknown-node-type (:type node) :node node}})

;;; ---------------------------------------------------------------------------
;;; Unified Entry Point
;;; ---------------------------------------------------------------------------

(defn evaluate
  "Evaluates a policy against a document with three-valued semantics.

   Takes:
   - `policy` - AST node, constraint set, or policy expression vector
   - `document` - Associative data structure to evaluate against
   - `opts` - Optional map with `:operators`, `:fallback`, `:strict?`, `:trace?`

   Returns:
   - `true` if fully satisfied
   - `false` if contradicted
   - `{:residual {...}}` if partial evaluation

   ## Examples

       ;; Evaluate AST
       (evaluate ast {:role \"admin\"})
       ;; => true

       ;; With options
       (evaluate ast document {:strict? true})"
  ([policy document]
   (evaluate policy document {}))
  ([policy document opts]
   (let [ctx (op/make-context opts)]
     (cond
       ;; AST node
       (and (map? policy) (:type policy))
       (eval-ast-3v policy document ctx)

       ;; Constraint set (map with keyword keys pointing to constraint vectors)
       (and (map? policy) (not (:type policy)))
       (evaluate-constraint-set policy document ctx)

       ;; Policy expression vector - parse first
       (vector? policy)
       (let [parse-result (r/unwrap (parser/parse-policy policy))]
         (eval-ast-3v parse-result document ctx))

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
  (let [standard-constraints
        (mapcat
         (fn [[path constraints]]
           (when (vector? path) ; Skip ::cross-key namespace key
             (map (fn [[op value]]
                    [op (path->doc-accessor path) value])
                  constraints)))
         (dissoc residual ::cross-key))

        cross-key-constraints
        (map (fn [{:keys [op left-path right-path]}]
               [op (path->doc-accessor left-path) (path->doc-accessor right-path)])
             (get residual ::cross-key []))]

    (concat standard-constraints cross-key-constraints)))

(defn result->policy
  "Converts an evaluation result to a simplified policy expression.

   Returns:
   - nil for true (no constraints needed)
   - `[:contradiction]` for false
   - The simplified constraints for residual"
  [result]
  (cond
    (true? result) nil
    (false? result) [:contradiction]
    (residual? result)
    (let [constraints (residual->constraints (:residual result))]
      (if (= 1 (count constraints))
        (first constraints)
        (into [:and] constraints)))
    (complex? result) [:complex (:complex result)]))

;;; ---------------------------------------------------------------------------
;;; Implied (Bidirectional) Evaluation
;;; ---------------------------------------------------------------------------

(defn- negate-residual
  "Negates all constraints in a residual map.

  Used by `implied` when the desired result is `false` - we need to
  return constraints that would contradict the policy."
  [residual]
  (let [negated-standard
        (reduce-kv
         (fn [acc path constraints]
           (if (vector? path)
             (let [negated (keep (fn [[op value]]
                                   (when-let [neg-op (op/negate-op op)]
                                     [neg-op value]))
                                 constraints)]
               (if (seq negated)
                 (assoc acc path (vec negated))
                 (assoc acc path [[:complex {:cannot-negate constraints}]])))
             acc))
         {}
         (dissoc residual ::cross-key))

        negated-cross-key
        (keep (fn [{:keys [op] :as ck}]
                (when-let [neg-op (op/negate-op op)]
                  (assoc ck :op neg-op)))
              (get residual ::cross-key []))]
    (cond-> negated-standard
      (seq negated-cross-key)
      (assoc ::cross-key negated-cross-key))))

(defn- handle-complex-for-implied
  "Special handling for complex results that can still be inverted.

  Some complex results from `evaluate` can be handled by `implied`:
  - NOT of residual: can negate inner or return as-is
  - OR with residual children: to make false, negate all children

  Returns the constraint map if handled, nil otherwise."
  [complex-result desired]
  (let [{:keys [op child children]} (:complex complex-result)]
    (case op
      ;; NOT: to make true, inner must be false (negate inner residual)
      ;;      to make false, inner must be true (inner residual as-is)
      :not
      (when (residual? child)
        (if desired
          (negate-residual (:residual child))   ; NOT true = inner false
          (:residual child)))                   ; NOT false = inner true

      ;; OR: to make true, any child can be true (complex - disjunction)
      ;;     to make false, ALL children must be false (merge negated)
      :or
      (when (and (not desired)
                 (every? #(or (residual? %) (false? %)) children))
        (let [residuals (filter residual? children)
              negated   (map #(negate-residual (:residual %)) residuals)]
          (if (some #(and (map? %) (contains? % :complex)) negated)
            nil  ; Can't fully negate some constraint
            (apply merge-with into negated))))

      ;; Default - can't handle
      nil)))

(defn- negate-complex
  "Attempts to negate a complex result.

  First tries `handle-complex-for-implied` for known patterns (NOT, OR false).
  Otherwise returns a complex result indicating the limitation."
  [complex-result]
  (or (handle-complex-for-implied complex-result false)
      {:complex {:negated-complex (:complex complex-result)}}))

(defn implied
  "Given a policy and desired result, returns constraints that would satisfy it.

  Leverages [[evaluate]] with an empty document to extract all constraints
  as residuals, then optionally negates them.

  `desired-result` can be:
  - `true` - return constraints that would satisfy the policy
  - `false` - return constraints that would contradict the policy
  - `{:residual ...}` - return constraints needed to complete a partial evaluation

  Returns:
  - `{[:path] [[:op value]], ...}` - constraint map
  - `{:complex ...}` - when inversion not possible (OR true, quantifiers, etc.)
  - `{}` - when already satisfied (no additional constraints needed)

  ## Examples

      ;; Basic inversion
      (implied [:= :doc/role \"admin\"] true)
      ;=> {[:role] [[:= \"admin\"]]}

      (implied [:= :doc/role \"admin\"] false)
      ;=> {[:role] [[:!= \"admin\"]]}

      ;; Compound policies
      (implied [:and [:= :doc/role \"admin\"] [:> :doc/level 5]] true)
      ;=> {[:role] [[:= \"admin\"]], [:level] [[:> 5]]}

      ;; Residual continuation - what else is needed?
      (let [result (evaluate policy {:role \"admin\"})]  ; partial doc
        (when (residual? result)
          (implied policy result)))
      ;=> {[:level] [[:> 5]]}  ; the remaining constraints"
  ([policy desired-result]
   (implied policy desired-result {}))
  ([policy desired-result opts]
   (cond
     ;; Residual input - extract and optionally negate
     (residual? desired-result)
     (if (:negate? opts)
       (negate-residual (:residual desired-result))
       (:residual desired-result))

     ;; true - evaluate with empty doc to get all constraints as residual
     (true? desired-result)
     (let [result (evaluate policy {} opts)]
       (cond
         (true? result)     {}  ; Tautology - always true, no constraints needed
         (false? result)    {:complex {:contradiction true}}  ; Impossible
         (residual? result) (:residual result)
         (complex? result)  (or (handle-complex-for-implied result true)
                                result)))

     ;; false - get constraints for true, then negate them
     (false? desired-result)
     (let [result (evaluate policy {} opts)]
       (cond
         (true? result)     {:complex {:contradiction true}}  ; Can't make tautology false
         (false? result)    {}  ; Already false, no constraints needed
         (residual? result) (negate-residual (:residual result))
         (complex? result)  (negate-complex result))))))
