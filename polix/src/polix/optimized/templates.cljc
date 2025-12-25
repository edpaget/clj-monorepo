(ns polix.optimized.templates
  "Pre-computed residual structures for optimized evaluation.

  Extracts templates from constraint sets that can be used to construct
  residuals efficiently at runtime. Open residuals are fully pre-computed;
  conflict residuals use pre-computed templates with per-constraint makers.")

(def ^:private conflict-kw
  "Cached :conflict keyword to avoid repeated interning."
  :conflict)

(def ^:private satisfied-result
  "The satisfied result - an empty map. Reused to avoid allocation."
  {})

(defn satisfied
  "Returns the satisfied result (empty map)."
  []
  satisfied-result)

(defn constraint->open
  "Converts a constraint to its open residual form.

  Returns a vector like `[:= \"admin\"]` that represents the constraint
  waiting to be evaluated."
  [{:keys [op value]}]
  [op value])

(defn build-open-residual
  "Builds a pre-computed open residual map for a path.

  Returns a map like `{[:role] [[:= \"admin\"]]}` that can be returned
  directly when the document is missing the required path."
  [path constraints]
  (let [path-vec         (vec (map keyword path))
        constraint-forms (mapv constraint->open constraints)]
    {path-vec constraint-forms}))

(defn- create-constraint-evaluator
  "Creates an evaluator for a single constraint.

  Returns a map with:
  - `:check-fn` - function (fn [op-fn value] boolean)
  - `:make-conflict` - function (fn [witness] conflict-residual)
  - `:op` - the operator keyword
  - `:value` - the expected value

  The make-conflict function is pre-computed per constraint to avoid
  path-level lookups during conflict construction."
  [path-vec constraint]
  (let [op-kw           (:op constraint)
        expected        (:value constraint)
        constraint-form [op-kw expected]]
    {:op op-kw
     :value expected
     :constraint-form constraint-form
     :make-conflict (fn [witness]
                      {path-vec [[conflict-kw constraint-form witness]]})}))

(defn extract-path-templates
  "Extracts templates for a single path in the constraint set.

  Returns a map with:
  - `:path` - the document path as keyword vector
  - `:open` - pre-computed open residual map
  - `:constraint-evaluators` - vector of per-constraint evaluators

  Each constraint evaluator has its own pre-computed make-conflict function,
  eliminating the need for filter/find operations during conflict detection."
  [path constraints]
  (let [path-vec (vec (map keyword path))]
    {:path path-vec
     :open (build-open-residual path constraints)
     :constraint-evaluators (mapv #(create-constraint-evaluator path-vec %) constraints)}))

(defn extract-templates
  "Extracts all templates from a constraint set.

  Takes a constraint set (map of path -> constraints) and returns a map with:
  - `:satisfied` - empty map for satisfied result
  - `:path-templates` - vector of path templates for iteration

  The satisfied result is always the empty map which can be returned
  with zero allocation. Path templates are stored as a vector for
  efficient iteration during evaluation."
  [constraint-set]
  (let [path-templates (for [[path cs] constraint-set
                             :when     (vector? path)]
                         (extract-path-templates path cs))]
    {:satisfied satisfied-result
     :path-templates (vec path-templates)}))

(defn template-info
  "Returns information about templates for debugging/diagnostics.

  Returns a map with:
  - `:path-count` - number of paths with templates
  - `:paths` - list of paths
  - `:total-constraints` - total constraint count across all paths"
  [templates]
  (let [pts (:path-templates templates)]
    {:path-count (count pts)
     :paths (mapv :path pts)
     :total-constraints (reduce + 0 (map #(count (:constraint-evaluators %)) pts))}))
