(ns polix.residual
  "Residual data model for policy unification.

  Residuals represent the result of unifying a policy with a document:

  - `{}` (empty map) — satisfied, no constraints remain
  - `{:key [constraints]}` — partial, constraints remain on keys
  - `nil` — contradiction, no possible solution

  This three-valued logic enables bidirectional policy evaluation where
  partial documents produce residuals describing remaining requirements.

  ## Residual Structure

  A residual is a map from paths to constraint vectors:

      {[:role] [[:= \"admin\"]]
       [:level] [[:> 5] [:< 100]]}

  Special keys:
  - `::cross-key` — constraints comparing two document paths
  - `::complex` — non-simplifiable expressions (quantifiers, etc.)")

;;; ---------------------------------------------------------------------------
;;; Predicates
;;; ---------------------------------------------------------------------------

(defn satisfied?
  "Returns true if `x` represents a satisfied policy (empty residual).

  A policy is satisfied when no constraints remain after unification."
  [x]
  (and (map? x) (empty? x)))

(defn contradiction?
  "Returns true if `x` represents a contradiction.

  A contradiction occurs when constraints are incompatible and no
  document could satisfy the policy."
  [x]
  (nil? x))

(defn residual?
  "Returns true if `x` is a non-empty residual (partial satisfaction).

  A residual indicates some constraints remain unresolved, typically
  because the document was missing required keys."
  [x]
  (boolean (and (map? x) (seq x))))

(defn result-type
  "Returns the type of a unification result.

  - `:satisfied` — empty residual, policy fully satisfied
  - `:contradiction` — nil, policy cannot be satisfied
  - `:residual` — non-empty map, constraints remain"
  [x]
  (cond
    (nil? x) :contradiction
    (empty? x) :satisfied
    :else :residual))

;;; ---------------------------------------------------------------------------
;;; Constructors
;;; ---------------------------------------------------------------------------

(defn satisfied
  "Returns an empty residual indicating satisfaction."
  []
  {})

(defn contradiction
  "Returns nil indicating a contradiction."
  []
  nil)

(defn residual
  "Creates a residual with constraints on a single key.

  `path` is a vector of keys representing the document path.
  `constraints` is a vector of constraint tuples like `[[:= \"admin\"]]`."
  [path constraints]
  {path constraints})

;;; ---------------------------------------------------------------------------
;;; Constraint Operations
;;; ---------------------------------------------------------------------------

(defn merge-constraint-vectors
  "Merges two constraint vectors for the same key.

  Combines constraints with AND semantics. Both constraint sets must
  be satisfied for the merged result to be satisfied."
  [v1 v2]
  (into (vec v1) v2))

(defn merge-residuals
  "Merges two residuals with AND semantics.

  Returns:
  - `nil` if either residual is a contradiction
  - `{}` if both residuals are satisfied
  - Combined residual otherwise

  Constraints on the same key are merged into a single constraint vector."
  [r1 r2]
  (cond
    (nil? r1) nil
    (nil? r2) nil
    (empty? r1) r2
    (empty? r2) r1
    :else (merge-with merge-constraint-vectors r1 r2)))

(defn combine-residuals
  "Combines residuals with OR semantics.

  Returns:
  - `{}` if either residual is satisfied (short-circuit)
  - `nil` if both residuals are contradictions
  - `::complex` marker if residuals have different constraint structures

  OR combinations typically produce complex results because we cannot
  merge disjunctive constraints into a simple residual structure."
  [r1 r2]
  (cond
    ;; Either satisfied → result is satisfied
    (satisfied? r1) (satisfied)
    (satisfied? r2) (satisfied)
    ;; Both contradictions → contradiction
    (and (nil? r1) (nil? r2)) nil
    ;; One contradiction → other result (but may be complex)
    (nil? r1) r2
    (nil? r2) r1
    ;; Both are residuals with constraints → complex
    ;; We can't simply merge OR constraints into the residual model
    :else {::complex {:type :or :branches [r1 r2]}}))

;;; ---------------------------------------------------------------------------
;;; Accessors
;;; ---------------------------------------------------------------------------

(defn residual-keys
  "Returns the set of keys with constraints in the residual.

  Excludes special keys like `::cross-key` and `::complex`."
  [r]
  (when (map? r)
    (into #{}
          (remove #(or (= % ::cross-key) (= % ::complex)))
          (keys r))))

(defn constraints-for
  "Returns the constraints for a given path in the residual, or nil."
  [r path]
  (when (map? r)
    (get r path)))

(defn has-complex?
  "Returns true if the residual contains complex (non-simplifiable) constraints."
  [r]
  (and (map? r) (contains? r ::complex)))

(defn has-cross-key?
  "Returns true if the residual contains cross-key constraints."
  [r]
  (and (map? r) (contains? r ::cross-key)))

;;; ---------------------------------------------------------------------------
;;; Transformations
;;; ---------------------------------------------------------------------------

(defn add-constraint
  "Adds a constraint to a residual at the given path.

  If the path already has constraints, the new constraint is appended."
  [r path constraint]
  (cond
    (nil? r) nil
    :else (update r path (fnil conj []) constraint)))

(defn remove-path
  "Removes all constraints for a path from the residual."
  [r path]
  (when (map? r)
    (dissoc r path)))

(defn map-constraints
  "Applies f to each constraint vector in the residual.

  `f` receives `[path constraints]` and should return `[path new-constraints]`
  or `nil` to remove the path."
  [r f]
  (when (map? r)
    (into {}
          (keep (fn [[path constraints]]
                  (when-let [result (f path constraints)]
                    result)))
          r)))

;;; ---------------------------------------------------------------------------
;;; Conversion
;;; ---------------------------------------------------------------------------

(defn residual->constraints
  "Converts a residual to a flat sequence of constraint maps.

  Each constraint map has `:path`, `:op`, and `:value` keys."
  [r]
  (when (residual? r)
    (for [[path constraints] r
          :when              (vector? path)
          [op value]         constraints]
      {:path path :op op :value value})))

(defn constraints->residual
  "Converts a sequence of constraint maps back to a residual."
  [constraints]
  (reduce (fn [r {:keys [path op value]}]
            (add-constraint r path [op value]))
          (satisfied)
          constraints))
