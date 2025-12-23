(ns polix.residual
  "Residual data model for policy unification.

  Residuals represent the result of unifying a policy with a document:

  - `{}` (empty map) — satisfied, no constraints remain
  - `{:key [constraints]}` — partial, constraints remain on keys

  Constraints come in two forms:

  - **Open constraints** like `[:< 10]` — awaiting evaluation
  - **Conflict constraints** like `[:conflict [:< 10] 11]` — evaluated and failed

  A conflict `[:conflict C w]` records that constraint `C` was evaluated against
  witness value `w` and failed. This preserves diagnostic information about what
  was required and what was actually provided.

  ## Residual Structure

  A residual is a map from paths to constraint vectors:

      {[:role] [[:= \"admin\"]]
       [:level] [[:> 5] [:< 100]]}

  With conflicts:

      {[:mfa-age-minutes] [[:conflict [:< 10] 11]]}

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

;;; ---------------------------------------------------------------------------
;;; Conflict Operations
;;; ---------------------------------------------------------------------------

(defn conflict
  "Creates a conflict constraint tuple.

  A conflict records that `inner-constraint` was evaluated against `witness`
  and failed. The inner constraint is preserved for diagnostic and recovery
  purposes.

      (conflict [:< 10] 11)
      ;; => [:conflict [:< 10] 11]

  The conflict structure enables:
  - Diagnostic messages: 'required < 10, got 11'
  - Recovery guidance: the inner constraint tells you what would satisfy
  - Uniform handling: no special nil case"
  [inner-constraint witness]
  [:conflict inner-constraint witness])

(defn conflict?
  "Returns true if `x` is a conflict constraint tuple.

      (conflict? [:conflict [:< 10] 11])  ;=> true
      (conflict? [:< 10])                  ;=> false
      (conflict? nil)                      ;=> false"
  [x]
  (and (vector? x)
       (= :conflict (first x))
       (= 3 (count x))))

(defn conflict-constraint
  "Extracts the inner constraint from a conflict.

      (conflict-constraint [:conflict [:< 10] 11])
      ;; => [:< 10]

  Returns nil if not a valid conflict."
  [c]
  (when (conflict? c)
    (nth c 1)))

(defn conflict-witness
  "Extracts the witness value from a conflict.

      (conflict-witness [:conflict [:< 10] 11])
      ;; => 11

  Returns nil if not a valid conflict."
  [c]
  (when (conflict? c)
    (nth c 2)))

(defn- has-cross-key-conflict?
  "Returns true if cross-key entry contains a conflict."
  [cross-key-entries]
  (and (sequential? cross-key-entries)
       (some :conflict cross-key-entries)))

(defn- has-complex-conflict?
  "Returns true if complex marker represents a conflict.

  Collection operations that fail definitively (e.g., exists on empty)
  produce `:collection-conflict` type."
  [complex-entry]
  (and (map? complex-entry)
       (= :collection-conflict (:type complex-entry))))

(defn has-conflicts?
  "Returns true if the residual contains any conflict constraints.

  A residual with conflicts indicates the policy was evaluated against
  concrete data that violated constraints. This replaces checking for
  nil in the old contradiction model.

  Detects (in order for performance):
  1. `::conflict` marker (O(1) - set by [[conflict-residual]])
  2. Cross-key conflicts `{:conflict true ...}`
  3. Collection conflicts `{::complex {:type :collection-conflict}}`
  4. Tuple-form conflicts `[:conflict C w]` (O(n) fallback)

      (has-conflicts? {[:x] [[:conflict [:< 10] 15]]})  ;=> true
      (has-conflicts? {[:x] [[:< 10]]})                  ;=> false
      (has-conflicts? {})                                ;=> false"
  [r]
  (and (map? r)
       (boolean
        (or
         ;; O(1) check: explicit conflict marker
         (::conflict r)
         ;; O(1) check: cross-key conflicts
         (has-cross-key-conflict? (get r ::cross-key))
         ;; O(1) check: collection conflicts in complex marker
         (has-complex-conflict? (get r ::complex))
         ;; O(n) fallback: scan for tuple-form conflicts
         (some (fn [[path constraints]]
                 (and (vector? path)
                      (sequential? constraints)
                      (some conflict? constraints)))
               r)))))

(defn residual?
  "Returns true if `x` is a non-empty residual (partial satisfaction).

  A residual indicates some constraints remain unresolved, typically
  because the document was missing required keys."
  [x]
  (boolean (and (map? x) (seq x))))

(defn open-residual?
  "Returns true if `r` is a residual with only open (non-conflict) constraints.

  An open residual indicates the policy couldn't be fully evaluated due to
  missing data, but no contradictions were found in the data that was present.

      (open-residual? {[:x] [[:< 10]]})                  ;=> true
      (open-residual? {[:x] [[:conflict [:< 10] 15]]})  ;=> false
      (open-residual? {})                                ;=> false"
  [r]
  (and (residual? r)
       (not (has-conflicts? r))))

(defn- all-cross-key-conflicts?
  "Returns true if all cross-key entries are conflicts."
  [cross-key-entries]
  (or (nil? cross-key-entries)
      (empty? cross-key-entries)
      (every? :conflict cross-key-entries)))

(defn all-conflicts?
  "Returns true if every constraint in the residual is a conflict.

  Used to determine if NOT of a fully-conflicted residual should be satisfied.
  A residual where all paths have only conflict constraints represents a
  complete contradiction.

      (all-conflicts? {[:x] [[:conflict [:< 10] 15]]})           ;=> true
      (all-conflicts? {[:x] [[:conflict [:< 10] 15] [:> 5]]})   ;=> false
      (all-conflicts? {[:x] [[:< 10]]})                          ;=> false"
  [r]
  (and (residual? r)
       ;; Check that cross-key entries (if any) are all conflicts
       (all-cross-key-conflicts? (get r ::cross-key))
       ;; Check that all path constraints are conflicts
       (every? (fn [[path constraints]]
                 (or (#{::cross-key ::complex ::conflict} path)
                     (and (sequential? constraints)
                          (every? conflict? constraints))))
               r)))

(defn result-type
  "Returns the type of a unification result.

  - `:satisfied` — empty residual, policy fully satisfied
  - `:conflict` — residual contains conflicts, policy violated
  - `:open` — residual with only open constraints, needs more data
  - `:unknown` — unrecognized result type"
  [x]
  (cond
    (not (map? x)) :unknown
    (empty? x) :satisfied
    (has-conflicts? x) :conflict
    :else :open))

;;; ---------------------------------------------------------------------------
;;; Constructors
;;; ---------------------------------------------------------------------------

(defn satisfied
  "Returns an empty residual indicating satisfaction."
  []
  {})

(defn residual
  "Creates a residual with constraints on a single key.

  `path` is a vector of keys representing the document path.
  `constraints` is a vector of constraint tuples like `[[:= \"admin\"]]`.

  For conflicts, use [[conflict]] to create the constraint:

      (residual [:x] [(conflict [:< 10] 15)])"
  [path constraints]
  {path constraints})

(defn conflict-residual
  "Creates a residual with a single conflict constraint.

  Convenience function combining [[residual]] and [[conflict]]:

      (conflict-residual [:x] [:< 10] 15)
      ;; => {::conflict true, [:x] [[:conflict [:< 10] 15]]}

  The `::conflict` marker enables O(1) conflict detection."
  [path inner-constraint witness]
  {::conflict true
   path       [(conflict inner-constraint witness)]})

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
  - `{}` if both residuals are satisfied
  - Combined residual otherwise (may contain both open and conflict constraints)

  Constraints on the same key are merged into a single constraint vector.
  Conflicts are preserved and merged alongside open constraints.
  The `::conflict` marker is propagated if either residual has conflicts.

  Note: For backward compatibility, nil inputs are propagated as nil.
  New code should not produce nil residuals."
  [r1 r2]
  (cond
    (nil? r1) nil
    (nil? r2) nil
    (empty? r1) r2
    (empty? r2) r1
    :else (let [has-conflict (or (::conflict r1) (::conflict r2))
                merged       (merge-with merge-constraint-vectors
                                         (dissoc r1 ::conflict)
                                         (dissoc r2 ::conflict))]
            (if has-conflict
              (assoc merged ::conflict true)
              merged))))

(defn combine-residuals
  "Combines residuals with OR semantics.

  Returns:
  - `{}` if either residual is satisfied (short-circuit)
  - `::complex` marker if residuals have different constraint structures

  OR combinations typically produce complex results because we cannot
  merge disjunctive constraints into a simple residual structure.

  In the conflict model:
  - If both branches have conflicts, both are preserved in the complex marker
  - This enables showing 'fix either A or B' in UIs

  Note: For backward compatibility, nil inputs are handled but new code
  should not produce nil residuals."
  [r1 r2]
  (cond
    ;; Either satisfied → result is satisfied
    (satisfied? r1) (satisfied)
    (satisfied? r2) (satisfied)
    ;; Handle legacy nil (both nil → empty complex with nil branches)
    (and (nil? r1) (nil? r2)) {::complex {:type :or :branches []}}
    ;; One nil → other result
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

  Excludes special keys like `::cross-key`, `::complex`, and `::conflict`."
  [r]
  (when (map? r)
    (into #{}
          (remove #(or (= % ::cross-key) (= % ::complex) (= % ::conflict)))
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

  If the path already has constraints, the new constraint is appended.
  Sets `::conflict` marker if adding a conflict constraint."
  [r path constraint]
  (cond
    (nil? r) nil
    :else (let [updated (update r path (fnil conj []) constraint)]
            (if (conflict? constraint)
              (assoc updated ::conflict true)
              updated))))

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
