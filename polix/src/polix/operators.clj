(ns polix.operators
  "Extensible operator system for policy constraints.

  Operators define how constraints are evaluated, simplified, and negated.
  Users can register custom operators to extend the policy DSL.

  ## Built-in Operators

  Comparison: `:=`, `:!=`, `:>`, `:<`, `:>=`, `:<=`
  Set membership: `:in`, `:not-in`
  Pattern matching: `:matches`

  ## Defining Custom Operators

  Use `defoperator` to define new operators:

      (defoperator :starts-with
        :eval (fn [value expected] (str/starts-with? value expected))
        :negate :not-starts-with)

      (defoperator :not-starts-with
        :eval (fn [value expected] (not (str/starts-with? value expected)))
        :negate :starts-with)

  Or use `register-operator!` for programmatic registration:

      (register-operator! :my-op
        {:eval (fn [value expected] ...)
         :simplify (fn [constraints] ...)
         :negate :my-op-negated})"
  (:require
   [clojure.string :as str]))

;;; ---------------------------------------------------------------------------
;;; Operator Protocol
;;; ---------------------------------------------------------------------------

(defprotocol IOperator
  "Protocol for constraint operators."

  (op-eval [this value expected]
    "Evaluates whether `value` satisfies the constraint with `expected`.
     Returns true, false, or nil if evaluation is not possible.")

  (op-negate [this constraint]
    "Returns the negated form of `constraint`.
     May return a new constraint or nil if negation is not supported.")

  (op-simplify [this constraints]
    "Simplifies a collection of constraints with this operator on the same key.
     Returns {:simplified [...]} or {:contradicted [...]}.")

  (op-subsumes? [this c1 c2]
    "Returns true if constraint c1 subsumes c2 (c1 implies c2).
     Used to eliminate redundant constraints."))

;;; ---------------------------------------------------------------------------
;;; Default Operator Implementation
;;; ---------------------------------------------------------------------------

(defrecord Operator [op-key eval-fn negate-key simplify-fn subsumes-fn]
  IOperator

  (op-eval [_ value expected]
    (when eval-fn
      (eval-fn value expected)))

  (op-negate [_ constraint]
    (when negate-key
      (assoc constraint :op negate-key)))

  (op-simplify [_ constraints]
    (if simplify-fn
      (simplify-fn constraints)
      {:simplified constraints}))

  (op-subsumes? [_ c1 c2]
    (if subsumes-fn
      (subsumes-fn c1 c2)
      false)))

;;; ---------------------------------------------------------------------------
;;; Operator Registry
;;; ---------------------------------------------------------------------------

(defonce ^:private registry (atom {}))

(defn register-operator!
  "Registers an operator in the global registry.

   `op-key` is the operator keyword (e.g., `:=`, `:my-app/custom-op`).

   `spec` is a map with optional keys:
   - `:eval` - `(fn [value expected] -> boolean?)` evaluation function
   - `:negate` - keyword of the negated operator
   - `:simplify` - `(fn [constraints] -> {:simplified [...]} | {:contradicted [...]})`
   - `:subsumes?` - `(fn [c1 c2] -> boolean?)` subsumption check"
  [op-key spec]
  (let [operator (->Operator
                  op-key
                  (:eval spec)
                  (:negate spec)
                  (:simplify spec)
                  (:subsumes? spec))]
    (swap! registry assoc op-key operator)
    operator))

(defn get-operator
  "Returns the operator for `op-key`, or nil if not found."
  [op-key]
  (get @registry op-key))

(defn operator-keys
  "Returns all registered operator keys."
  []
  (keys @registry))

(defn clear-registry!
  "Clears all registered operators. Useful for testing."
  []
  (reset! registry {}))

;;; ---------------------------------------------------------------------------
;;; Evaluation API
;;; ---------------------------------------------------------------------------

(defn eval-constraint
  "Evaluates a constraint against a value using the registered operator.

   Returns true if satisfied, false if contradicted, nil if operator unknown."
  [constraint value]
  (let [op-key (:op constraint)
        expected (:value constraint)]
    (if-let [operator (get-operator op-key)]
      (op-eval operator value expected)
      nil)))

(defn negate-constraint
  "Returns the negated form of a constraint, or nil if not supported."
  [constraint]
  (when-let [operator (get-operator (:op constraint))]
    (op-negate operator constraint)))

(defn simplify-constraints
  "Simplifies constraints with the same operator on the same key."
  [op-key constraints]
  (if-let [operator (get-operator op-key)]
    (op-simplify operator constraints)
    {:simplified constraints}))

;;; ---------------------------------------------------------------------------
;;; Simplification Helpers
;;; ---------------------------------------------------------------------------

(defn- simplify-equality
  "Simplifies equality constraints - all must have same value or contradiction."
  [constraints]
  (let [values (set (map :value constraints))]
    (if (= 1 (count values))
      {:simplified [(first constraints)]}
      {:contradicted constraints})))

(defn- simplify-lower-bounds
  "Simplifies lower bound constraints (:>, :>=) - keep tightest."
  [constraints]
  (if (empty? constraints)
    {:simplified []}
    (let [sorted (sort-by :value > constraints)
          tightest (first sorted)]
      {:simplified [tightest]})))

(defn- simplify-upper-bounds
  "Simplifies upper bound constraints (:<, :<=) - keep tightest."
  [constraints]
  (if (empty? constraints)
    {:simplified []}
    (let [sorted (sort-by :value < constraints)
          tightest (first sorted)]
      {:simplified [tightest]})))

(defn- simplify-in
  "Simplifies :in constraints - intersection of sets."
  [constraints]
  (let [sets (map :value constraints)
        intersection (apply clojure.set/intersection sets)]
    (if (empty? intersection)
      {:contradicted constraints}
      {:simplified [(assoc (first constraints) :value intersection)]})))

;;; ---------------------------------------------------------------------------
;;; Macro for Defining Operators
;;; ---------------------------------------------------------------------------

(defmacro defoperator
  "Defines and registers an operator.

   Example:

       (defoperator :starts-with
         :eval (fn [value expected] (str/starts-with? value expected))
         :negate :not-starts-with)

       (defoperator :between
         :eval (fn [value [low high]] (and (>= value low) (<= value high)))
         :simplify (fn [cs] {:simplified cs}))"
  [op-key & {:keys [eval negate simplify subsumes?]}]
  `(register-operator! ~op-key
                       {:eval ~eval
                        :negate ~negate
                        :simplify ~simplify
                        :subsumes? ~subsumes?}))

;;; ---------------------------------------------------------------------------
;;; Built-in Operators
;;; ---------------------------------------------------------------------------

(defn register-builtins!
  "Registers all built-in operators."
  []

  ;; Equality
  (register-operator! :=
    {:eval (fn [value expected] (= value expected))
     :negate :!=
     :simplify simplify-equality
     :subsumes? (fn [c1 c2] (= (:value c1) (:value c2)))})

  (register-operator! :!=
    {:eval (fn [value expected] (not= value expected))
     :negate :=})

  ;; Comparisons
  (register-operator! :>
    {:eval (fn [value expected] (> value expected))
     :negate :<=
     :simplify simplify-lower-bounds
     :subsumes? (fn [c1 c2] (>= (:value c1) (:value c2)))})

  (register-operator! :>=
    {:eval (fn [value expected] (>= value expected))
     :negate :<
     :simplify simplify-lower-bounds
     :subsumes? (fn [c1 c2] (>= (:value c1) (:value c2)))})

  (register-operator! :<
    {:eval (fn [value expected] (< value expected))
     :negate :>=
     :simplify simplify-upper-bounds
     :subsumes? (fn [c1 c2] (<= (:value c1) (:value c2)))})

  (register-operator! :<=
    {:eval (fn [value expected] (<= value expected))
     :negate :>
     :simplify simplify-upper-bounds
     :subsumes? (fn [c1 c2] (<= (:value c1) (:value c2)))})

  ;; Set membership
  (register-operator! :in
    {:eval (fn [value expected] (contains? expected value))
     :negate :not-in
     :simplify simplify-in})

  (register-operator! :not-in
    {:eval (fn [value expected] (not (contains? expected value)))
     :negate :in})

  ;; Pattern matching
  (register-operator! :matches
    {:eval (fn [value expected]
             (boolean (re-matches (if (string? expected)
                                    (re-pattern expected)
                                    expected)
                                  (str value))))
     :negate :not-matches})

  (register-operator! :not-matches
    {:eval (fn [value expected]
             (not (re-matches (if (string? expected)
                                (re-pattern expected)
                                expected)
                              (str value))))
     :negate :matches}))

;; Register builtins on namespace load
(register-builtins!)
