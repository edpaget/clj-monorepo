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
      ;; => {:residual {:role [[:= \"admin\"]]}}

  Uses [[polix.operators]] registry as primary operator source with support
  for context overrides via the `:operators` option."
  (:require
   [polix.ast :as ast]
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

   A constraint set is a map of `{key -> [constraints], ::complex -> [nodes]}`.

   Returns:
   - true if all constraints satisfied
   - false if any constraint contradicted
   - `{:residual {...}}` if partial match"
  ([constraint-set document]
   (evaluate-constraint-set constraint-set document (op/make-context)))
  ([constraint-set document ctx]
   (let [doc-keys (set (keys document))]
     (loop [keys-to-check     (keys (dissoc constraint-set ::complex))
            residuals         {}
            any-contradicted? false]
       (if (or any-contradicted? (empty? keys-to-check))
         (cond
           any-contradicted? false
           (seq residuals) {:residual residuals}
           :else true)
         (let [k              (first keys-to-check)
               constraints    (get constraint-set k)
               value-present? (contains? doc-keys k)
               value          (get document k)
               result         (evaluate-constraints-for-key ctx constraints value value-present?)]
           (recur
            (rest keys-to-check)
            (if (residual? result)
              (assoc residuals k (:residual result))
              residuals)
            (false? result))))))))

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
  [node document _ctx]
  (let [key (:value node)]
    (if (contains? document key)
      (get document key)
      {:residual {key [[:any]]}})))

(defmethod eval-ast-3v ::ast/uri
  [_node _document ctx]
  (if-let [uri (:uri ctx)]
    uri
    {:residual {:uri [[:any]]}}))

(defn- evaluate-children
  "Evaluates all child nodes of a function call."
  [children document ctx]
  (mapv #(eval-ast-3v % document ctx) children))

(defn- comparison-to-constraint
  "Converts a comparison function call to a constraint if possible."
  [op-key children document ctx]
  (when (= 2 (count children))
    (let [[left right] children
          left-type    (:type left)
          right-type   (:type right)]
      (cond
        ;; [:= :doc/key value]
        (and (= ::ast/doc-accessor left-type)
             (= ::ast/literal right-type))
        (let [key      (:value left)
              expected (:value right)]
          (if (contains? document key)
            (let [constraint {:op op-key :value expected}
                  result     (evaluate-constraint ctx constraint (get document key))]
              (if (nil? result)
                {:complex {:op op-key :ast {:left left :right right}}}
                result))
            {:residual {key [[op-key expected]]}}))

        ;; [:= value :doc/key] - flipped
        (and (= ::ast/literal left-type)
             (= ::ast/doc-accessor right-type))
        (let [key        (:value right)
              expected   (:value left)
              flipped-op (case op-key
                           :< :>
                           :> :<
                           :<= :>=
                           :>= :<=
                           op-key)]
          (if (contains? document key)
            (let [constraint {:op flipped-op :value expected}
                  result     (evaluate-constraint ctx constraint (get document key))]
              (if (nil? result)
                {:complex {:op flipped-op :ast {:left left :right right}}}
                result))
            {:residual {key [[flipped-op expected]]}}))

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
      (or (comparison-to-constraint op-key children document ctx)
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
   - `opts` - Optional map with `:operators`, `:fallback`, `:strict?`, `:trace?`, `:uri`

   Returns:
   - `true` if fully satisfied
   - `false` if contradicted
   - `{:residual {...}}` if partial evaluation

   ## Examples

       ;; Evaluate AST
       (evaluate ast {:role \"admin\"})
       ;; => true

       ;; With options
       (evaluate ast document {:strict? true})

       ;; With URI context
       (evaluate ast document {:uri \"/api/users\"})"
  ([policy document]
   (evaluate policy document {}))
  ([policy document opts]
   (let [ctx (-> (op/make-context opts)
                 (assoc :uri (:uri opts)))]
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

   Takes `{:level [[:> 5]], :status [[:in #{\"a\" \"b\"}]]}`
   Returns `[[:> :doc/level 5] [:in :doc/status #{\"a\" \"b\"}]]`"
  [residual]
  (mapcat
   (fn [[key constraints]]
     (map (fn [[op value]]
            [op (keyword "doc" (name key)) value])
          constraints))
   residual))

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
