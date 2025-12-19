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
   [cats.core :as m]
   [cats.monad.either :as either]
   [polix.ast :as ast]
   [polix.parser :as parser]))

;;; ---------------------------------------------------------------------------
;;; Constraint Representation
;;; ---------------------------------------------------------------------------

(defrecord Constraint [key op value])

(defn constraint
  "Creates a normalized constraint."
  [key op value]
  (->Constraint key op value))

(defn constraint?
  "Returns true if x is a Constraint."
  [x]
  (instance? Constraint x))

;;; ---------------------------------------------------------------------------
;;; AST to Constraint Normalization
;;; ---------------------------------------------------------------------------

(def comparison-ops
  "Set of comparison operators."
  #{:= :!= :< :> :<= :>= :in :not-in :matches})

(def boolean-ops
  "Set of boolean connective operators."
  #{:and :or :not})

(defn- extract-key
  "Extracts the document key from an AST node."
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
   - `:op` - `:and`, `:or`, or `:constraint`
   - `:constraints` - for `:and`/`:or`, vector of child structures
   - `:constraint` - for `:constraint`, the Constraint record
   - `:negated` - boolean for negated constraints"
  [ast]
  (if-not (= ::ast/function-call (:type ast))
    ;; Leaf node - shouldn't happen at top level, treat as literal true
    {:op :literal :value true}
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

        ;; Comparison operators
        (contains? comparison-ops op)
        (if-let [c (normalize-comparison op children)]
          {:op :constraint :constraint c}
          {:op :complex :ast ast})

        ;; Unknown operator - keep as complex
        :else
        {:op :complex :ast ast}))))

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
  "Evaluates a single constraint against a value.
   Returns true if satisfied, false if contradicted."
  [constraint value]
  (let [op       (:op constraint)
        expected (:value constraint)]
    (case op
      := (= value expected)
      :!= (not= value expected)
      :< (< value expected)
      :> (> value expected)
      :<= (<= value expected)
      :>= (>= value expected)
      :in (contains? expected value)
      :not-in (not (contains? expected value))
      :matches (boolean (re-matches (re-pattern expected) (str value)))
      ;; Unknown op - can't evaluate
      nil)))

(defn eval-constraints-for-key
  "Evaluates all constraints for a key against a value.
   Returns :satisfied, :contradicted, or the constraints if value is nil."
  [constraints value value-present?]
  (if-not value-present?
    ;; Value not present - return constraints as residual
    {:residual constraints}
    ;; Evaluate each constraint
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

   Returns:
   - true if all constraints are satisfied
   - false if any constraint is contradicted
   - {:residual {...}} if some constraints cannot be evaluated"
  [constraint-set document]
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
              result         (eval-constraints-for-key constraints value value-present?)]
          (recur
           (rest keys-to-check)
           (if (map? result)
             (assoc residuals k (mapv (fn [c] [(:op c) (:value c)]) (:residual result)))
             residuals)
           (= :contradicted result)))))))

;;; ---------------------------------------------------------------------------
;;; Policy Compilation
;;; ---------------------------------------------------------------------------

(defn normalize-policy-expr
  "Normalizes a policy expression (vector DSL) to constraint structure."
  [expr]
  (let [parse-result (parser/parse-policy expr)]
    (if (either/left? parse-result)
      (throw (ex-info "Failed to parse policy" (m/extract parse-result)))
      (normalize-ast (m/extract parse-result)))))

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

   Takes a sequence of policy expressions and returns a function that:
   - Takes a document (map)
   - Returns true, false, or {:residual {...}}

   Policies are merged with AND semantics - all must be satisfied.

   Example:

       (def check (compile-policies
                    [[:= :doc/role \"admin\"]
                     [:> :doc/level 5]]))

       (check {:role \"admin\" :level 10})  ;; => true
       (check {:role \"guest\" :level 10}) ;; => false
       (check {:role \"admin\"})           ;; => {:residual {:level [[:> 5]]}}"
  [policy-exprs]
  (let [merge-result (merge-policies policy-exprs)]
    (if (:contradicted merge-result)
      ;; Policies are inherently contradictory
      (constantly false)
      ;; Generate evaluation function
      (let [constraint-set (:simplified merge-result)]
        (fn evaluate [document]
          (evaluate-document constraint-set document))))))

;;; ---------------------------------------------------------------------------
;;; Residual Document Creation
;;; ---------------------------------------------------------------------------

(defn residual->constraints
  "Converts a residual map back to policy expressions.

   Takes {:level [[:> 5]], :status [[:in #{\"a\" \"b\"}]]}
   Returns [[:> :doc/level 5] [:in :doc/status #{\"a\" \"b\"}]]"
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
