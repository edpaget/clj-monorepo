(ns polix.compiler
  "Policy compilation with three-valued evaluation.

  Compiles multiple policies into a single optimized function that returns
  one of three values when applied to a document:

  - `true` - document fully satisfies all constraints
  - `false` - document contradicts at least one constraint
  - `{:residual {...}}` - partial match with remaining constraints

  ## Example

      (def checker (compile-policies
                     [[:and [:= :doc/role \"admin\"] [:> :doc/level 5]]
                      [:in :doc/status #{\"active\" \"pending\"}]]))

      (checker {:role \"admin\" :level 10 :status \"active\"})
      ;; => true

      (checker {:role \"guest\"})
      ;; => false

      (checker {:role \"admin\"})
      ;; => {:residual {:level [[:> 5]], :status [[:in #{\"active\" \"pending\"}]]}}"
  (:require
   [polix.ast :as ast]
   [polix.engine :as engine]
   [polix.operators :as op]
   [polix.parser :as parser]
   [polix.result :as r]))

;;; ---------------------------------------------------------------------------
;;; Constraint Representation
;;; ---------------------------------------------------------------------------

(defrecord Constraint [key op value]
  ;; `key` is a path vector (e.g., `[:user :name]`) representing nested access
  )

(defn constraint
  "Creates a normalized constraint.

  The `key` parameter is a path vector (e.g., `[:role]` or `[:user :name]`)
  representing the document path to constrain."
  [key op value]
  (->Constraint key op value))

(defn constraint?
  "Returns true if x is a Constraint."
  [x]
  (instance? Constraint x))

;;; ---------------------------------------------------------------------------
;;; AST to Constraint Normalization
;;; ---------------------------------------------------------------------------

(defn comparison-op?
  "Returns true if op is a registered comparison operator (not a boolean connective)."
  [op]
  (and (not (contains? #{:and :or :not} op))
       (op/get-operator op)))

(def boolean-ops
  "Set of boolean connective operators."
  #{:and :or :not})

(defn- extract-key
  "Extracts the document path from an AST node.

  Returns a path vector (e.g., `[:user :name]`) for doc-accessor nodes."
  [node]
  (when (= ::ast/doc-accessor (:type node))
    (:value node)))

(defn- extract-literal
  "Extracts a literal value from an AST node."
  [node]
  (when (= ::ast/literal (:type node))
    (:value node)))

(defn- flip-op
  "Flips a comparison operator for reversed operands."
  [op]
  (case op
    :< :>
    :> :<
    :<= :>=
    :>= :<=
    op))

(defn- normalize-comparison
  "Normalizes a comparison AST node to a Constraint.
   Returns nil if the node is not a simple comparison."
  [op children]
  (when (= 2 (count children))
    (let [[left right] children
          left-key     (extract-key left)
          right-key    (extract-key right)
          left-lit     (extract-literal left)
          right-lit    (extract-literal right)]
      (cond
        ;; [:= :doc/key value]
        (and left-key right-lit)
        (constraint left-key op right-lit)

        ;; [:= value :doc/key] - flip comparison
        (and left-lit right-key)
        (constraint right-key (flip-op op) left-lit)

        :else nil))))

;;; ---------------------------------------------------------------------------
;;; Constraint Normalization from AST
;;; ---------------------------------------------------------------------------

(defn normalize-ast
  "Converts a policy AST to a normalized constraint structure.

   Returns a map with:
   - `:op` - `:and`, `:or`, `:constraint`, `:quantifier`, or `:complex`
   - `:constraints` - for `:and`/`:or`, vector of child structures
   - `:constraint` - for `:constraint`, the Constraint record
   - `:negated` - boolean for negated constraints
   - `:ast` - for `:quantifier` and `:complex`, the original AST node"
  [ast]
  (case (:type ast)
    ::ast/quantifier
    {:op :quantifier :ast ast}

    ::ast/function-call
    (let [op       (:value ast)
          children (:children ast)]
      (cond
        ;; Boolean connectives
        (= :and op)
        {:op :and
         :children (mapv normalize-ast children)}

        (= :or op)
        {:op :or
         :children (mapv normalize-ast children)}

        (= :not op)
        {:op :not
         :child (normalize-ast (first children))}

        ;; Comparison operators (any registered non-boolean operator)
        (comparison-op? op)
        (if-let [c (normalize-comparison op children)]
          {:op :constraint :constraint c}
          {:op :complex :ast ast})

        ;; Unknown operator - keep as complex
        :else
        {:op :complex :ast ast}))

    ;; Default - leaf node, treat as literal true
    {:op :literal :value true}))

;;; ---------------------------------------------------------------------------
;;; Constraint Merging
;;; ---------------------------------------------------------------------------

(defn- collect-constraints
  "Collects all constraints from a normalized structure into a flat sequence.
   Handles AND at top level, returns constraints grouped by key."
  [normalized]
  (case (:op normalized)
    :and (mapcat collect-constraints (:children normalized))
    :constraint [(:constraint normalized)]
    :literal []
    :quantifier [{:complex normalized}]
    ;; For OR and complex, we can't easily flatten
    [{:complex normalized}]))

(defn merge-constraint-sets
  "Merges multiple constraint sets, grouping by key."
  [constraints]
  (reduce
   (fn [acc c]
     (if (constraint? c)
       (update acc (:key c) (fnil conj []) c)
       (update acc ::complex (fnil conj []) c)))
   {}
   constraints))

;;; ---------------------------------------------------------------------------
;;; Constraint Simplification
;;; ---------------------------------------------------------------------------

(defn- simplify-equality-constraints
  "Simplifies equality constraints on the same key.
   Returns {:satisfied c} if consistent, {:contradicted [c1 c2]} if not."
  [constraints]
  (let [eq-constraints    (filter #(= := (:op %)) constraints)
        other-constraints (remove #(= := (:op %)) constraints)]
    (if (empty? eq-constraints)
      {:simplified other-constraints}
      (let [values (set (map :value eq-constraints))]
        (if (= 1 (count values))
          ;; All equal to same value - keep one
          {:simplified (cons (first eq-constraints) other-constraints)}
          ;; Contradiction
          {:contradicted eq-constraints})))))

(defn- simplify-range-constraints
  "Simplifies range constraints (>, <, >=, <=) on the same key.
   Keeps the tightest bounds."
  [constraints]
  (let [lower-bounds   (filter #(contains? #{:> :>=} (:op %)) constraints)
        upper-bounds   (filter #(contains? #{:< :<=} (:op %)) constraints)
        other          (remove #(contains? #{:> :>= :< :<=} (:op %)) constraints)

        ;; Find tightest lower bound
        tightest-lower (when (seq lower-bounds)
                         (apply max-key :value lower-bounds))

        ;; Find tightest upper bound
        tightest-upper (when (seq upper-bounds)
                         (apply min-key :value upper-bounds))]

    ;; Check for contradiction
    (if (and tightest-lower tightest-upper
             (> (:value tightest-lower) (:value tightest-upper)))
      {:contradicted [tightest-lower tightest-upper]}
      {:simplified (concat other
                           (when tightest-lower [tightest-lower])
                           (when tightest-upper [tightest-upper]))})))

(defn simplify-constraints
  "Simplifies a list of constraints on a single key.
   Returns {:simplified [constraints]} or {:contradicted reason}."
  [constraints]
  (let [eq-result (simplify-equality-constraints constraints)]
    (if (:contradicted eq-result)
      eq-result
      (simplify-range-constraints (:simplified eq-result)))))

(defn simplify-constraint-set
  "Simplifies a constraint set (map of key -> constraints).
   Returns {:simplified {...}} or {:contradicted {:key ... :reason ...}}."
  [constraint-set]
  (reduce-kv
   (fn [acc key constraints]
     (if (= ::complex key)
       (assoc-in acc [:simplified key] constraints)
       (let [result (simplify-constraints constraints)]
         (if (:contradicted result)
           (reduced {:contradicted {:key key :constraints (:contradicted result)}})
           (assoc-in acc [:simplified key] (:simplified result))))))
   {:simplified {}}
   constraint-set))

;;; ---------------------------------------------------------------------------
;;; Constraint Evaluation
;;; ---------------------------------------------------------------------------

(defn eval-constraint
  "Evaluates a single constraint against a value using the operator registry.

   Returns true if satisfied, false if contradicted, nil if operator unknown."
  [constraint value]
  (op/eval-constraint constraint value))

(defn eval-constraints-for-key
  "Evaluates all constraints for a key against a value.
   Returns :satisfied, :contradicted, or the constraints if value is nil."
  [constraints value value-present?]
  (if-not value-present?
    {:residual constraints}
    (let [results (map #(eval-constraint % value) constraints)]
      (cond
        (some false? results) :contradicted
        (every? true? results) :satisfied
        :else {:residual (map second (filter #(nil? (first %))
                                             (map vector results constraints)))}))))

;;; ---------------------------------------------------------------------------
;;; Three-Valued Evaluation
;;; ---------------------------------------------------------------------------

(defn evaluate-document
  "Evaluates a simplified constraint set against a document.

   Delegates to [[polix.engine/evaluate-constraint-set]] for unified evaluation.

   Returns:
   - true if all constraints are satisfied
   - false if any constraint is contradicted
   - {:residual {...}} if some constraints cannot be evaluated"
  [constraint-set document]
  (engine/evaluate-constraint-set constraint-set document))

(defn- evaluate-complex-nodes
  "Evaluates complex AST nodes that couldn't be normalized to constraints.

  Complex nodes include quantifiers and other expressions that require
  runtime evaluation by the engine. Each complex-node is `{:complex normalized}`
  where normalized contains `:ast` key with the original AST."
  [complex-nodes document ctx]
  (let [results (map (fn [node]
                       (let [normalized (:complex node)
                             ast (:ast normalized)]
                         (engine/eval-ast-3v ast document ctx)))
                     complex-nodes)]
    (engine/eval-and results)))

(defn- evaluate-document-with-context
  "Evaluates a constraint set using operators from context.

   Evaluates both simple constraints (via constraint-set) and complex nodes
   (via engine evaluation), combining results with AND semantics.

   When `:trace?` is enabled in ctx, returns `{:result <value> :trace [...]}`
   where `<value>` is true, false, or `{:residual ...}`."
  [constraint-set document ctx]
  (let [complex-nodes (get constraint-set ::complex)
        constraint-result (engine/evaluate-constraint-set
                           (dissoc constraint-set ::complex) document ctx)
        final-result (if (or (false? constraint-result) (empty? complex-nodes))
                       constraint-result
                       (let [complex-result (evaluate-complex-nodes complex-nodes document ctx)]
                         (engine/eval-and [constraint-result complex-result])))]
    (if (and (:trace? ctx) (:trace ctx))
      {:result final-result :trace @(:trace ctx)}
      final-result)))

;;; ---------------------------------------------------------------------------
;;; Policy Compilation
;;; ---------------------------------------------------------------------------

(defn normalize-policy-expr
  "Normalizes a policy expression (vector DSL) to constraint structure."
  [expr]
  (let [parse-result (parser/parse-policy expr)]
    (if (r/error? parse-result)
      (throw (ex-info "Failed to parse policy" (r/unwrap parse-result)))
      (normalize-ast (r/unwrap parse-result)))))

(defn merge-policies
  "Merges multiple policy expressions into a single constraint set.
   All policies are ANDed together."
  [policy-exprs]
  (let [normalized      (map normalize-policy-expr policy-exprs)
        all-constraints (mapcat collect-constraints normalized)
        merged          (merge-constraint-sets all-constraints)]
    (simplify-constraint-set merged)))

(defn compile-policies
  "Compiles multiple policies into an optimized evaluation function.

   Takes a sequence of policy expressions and optional options map.
   Returns a function that takes a document and returns true, false,
   or `{:residual {...}}`.

   Options:
   - `:operators` - custom operators map (overrides registry)
   - `:fallback` - `(fn [op-key])` for unknown operators
   - `:strict?` - throw on unknown operators (default false)
   - `:trace?` - record evaluation trace (default false)

   Policies are merged with AND semantics - all must be satisfied.

   The returned function accepts either:
   - `(check document)` - use compile-time context
   - `(check document opts)` - override context per-evaluation

   When `:trace?` is enabled (at compile-time or per-evaluation), returns
   `{:result <value> :trace [...]}` where `:result` is the normal evaluation
   outcome and `:trace` is a vector of evaluation steps.

   Example:

       (def check (compile-policies
                    [[:= :doc/role \"admin\"]
                     [:> :doc/level 5]]))

       (check {:role \"admin\" :level 10})  ;; => true
       (check {:role \"guest\" :level 10}) ;; => false
       (check {:role \"admin\"})           ;; => {:residual {:level [[:> 5]]}}

       ;; With tracing
       (check {:role \"admin\"} {:trace? true})
       ;; => {:result true :trace [{:op := :value \"admin\" :expected \"admin\" :result true}]}"
  ([policy-exprs] (compile-policies policy-exprs {}))
  ([policy-exprs opts]
   (let [merge-result (merge-policies policy-exprs)]
     (if (:contradicted merge-result)
       (constantly false)
       (let [constraint-set (:simplified merge-result)
             compile-ctx    (op/make-context opts)]
         (fn evaluate
           ([document]
            (evaluate-document-with-context constraint-set document compile-ctx))
           ([document eval-opts]
            (let [eval-ctx (op/make-context (merge opts eval-opts))]
              (evaluate-document-with-context constraint-set document eval-ctx)))))))))

;;; ---------------------------------------------------------------------------
;;; Residual Document Creation
;;; ---------------------------------------------------------------------------

(defn residual->constraints
  "Converts a residual map back to policy expressions.

   Takes {[:level] [[:> 5]], [:user :status] [[:in #{\"a\" \"b\"}]]}
   Returns [[:> :doc/level 5] [:in :doc/user.status #{\"a\" \"b\"}]]"
  [residual]
  (mapcat
   (fn [[path constraints]]
     (map (fn [[op value]]
            [op (engine/path->doc-accessor path) value])
          constraints))
   residual))

(defn result->policy
  "Converts an evaluation result to a simplified policy expression.

   Returns:
   - nil for true (no constraints needed)
   - [:contradiction] for false
   - The simplified constraints for residual"
  [result]
  (cond
    (true? result) nil
    (false? result) [:contradiction]
    (map? result)
    (let [constraints (residual->constraints (:residual result))]
      (if (= 1 (count constraints))
        (first constraints)
        (into [:and] constraints)))))

;;; ---------------------------------------------------------------------------
;;; Tracing Helper
;;; ---------------------------------------------------------------------------

(defn with-trace
  "Evaluates a compiled policy with tracing enabled.

   Returns `{:result <value> :trace [...]}` where `:result` is the evaluation
   outcome (true, false, or `{:residual ...}`) and `:trace` is a vector of
   `{:op :value :expected :result}` maps for each constraint evaluated.

   Example:

       (def check (compile-policies [[:= :doc/role \"admin\"]]))
       (with-trace check {:role \"admin\"})
       ;; => {:result true :trace [{:op := :value \"admin\" :expected \"admin\" :result true}]}

       (def check2 (compile-policies [[:= :doc/role \"admin\"] [:> :doc/level 5]]))
       (with-trace check2 {:role \"admin\"})
       ;; => {:result {:residual {:level [[:> 5]]}}
       ;;     :trace [{:op := :value \"admin\" :expected \"admin\" :result true}]}"
  [compiled-fn document]
  (compiled-fn document {:trace? true}))
