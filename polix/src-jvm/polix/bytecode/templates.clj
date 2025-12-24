(ns polix.bytecode.templates
  "Pre-computed residual structures for bytecode compilation.

  Extracts templates from constraint sets that can be used to construct
  residuals efficiently at runtime. Open residuals are fully pre-computed
  as static fields; conflict residuals are template functions that only
  need the witness value at runtime.")

(defn constraint->open
  "Converts a constraint to its open residual form.

  Returns a vector like `[:= \"admin\"]` that represents the constraint
  waiting to be evaluated."
  [{:keys [op value]}]
  [op value])

(defn constraint->conflict-form
  "Returns the inner constraint form for conflict construction.

  This is the constraint part of `[:conflict constraint witness]`."
  [{:keys [op value]}]
  [op value])

(defn build-open-template
  "Builds a pre-computed open residual map for a path.

  Returns a map like `{[:role] [[:= \"admin\"]]}` that can be returned
  directly when the document is missing the required path."
  [path constraints]
  {path (mapv constraint->open constraints)})

(defn build-conflict-template
  "Builds a conflict template specification for a path.

  Returns a map with:
  - `:path` - the document path
  - `:constraints` - the constraint forms for conflict construction
  - `:make-conflict` - function `(fn [witness] conflict-residual)`

  The make-conflict function constructs the full conflict residual at runtime,
  slotting in the witness value."
  [path constraints]
  (let [constraint-forms (mapv constraint->conflict-form constraints)]
    {:path path
     :constraints constraint-forms
     :make-conflict (fn [witness]
                      {path (mapv (fn [c] [:conflict c witness]) constraint-forms)})}))

(defn extract-path-templates
  "Extracts templates for a single path in the constraint set.

  Returns a map with:
  - `:path` - the document path vector
  - `:open` - pre-computed open residual map
  - `:conflict-fn` - function `(fn [witness] conflict-residual)`
  - `:constraints` - the original constraints"
  [path constraints]
  (let [constraint-forms (mapv constraint->open constraints)]
    {:path path
     :open {path constraint-forms}
     :conflict-fn (fn [witness]
                    {path (mapv (fn [c] [:conflict c witness]) constraint-forms)})
     :constraints constraints}))

(defn extract-templates
  "Extracts all templates from a constraint set.

  Takes a constraint set (map of path -> constraints) and returns a map with:
  - `:satisfied` - empty map `{}` for satisfied result
  - `:paths` - map of path -> path template
  - `:open-templates` - map of path -> pre-computed open residual
  - `:conflict-templates` - map of path -> conflict template spec

  The satisfied result is always the empty map `{}` which can be returned
  as a static field with zero allocation."
  [constraint-set]
  (let [paths (for [[path cs] constraint-set
                    :when (vector? path)]
                [path (extract-path-templates path cs)])
        path-map (into {} paths)]
    {:satisfied {}
     :paths path-map
     :open-templates (into {} (map (fn [[p t]] [p (:open t)]) paths))
     :conflict-templates (into {} (map (fn [[p t]] [p {:constraints (:constraints t)
                                                        :conflict-fn (:conflict-fn t)}])
                                       paths))}))

(defn merge-residuals
  "Merges multiple residual maps.

  Used when multiple constraints fail or are open. Simply merges the maps."
  [& residuals]
  (apply merge residuals))

(defn template-info
  "Returns information about templates for debugging/diagnostics.

  Returns a map with:
  - `:path-count` - number of paths with templates
  - `:paths` - list of paths
  - `:total-constraints` - total constraint count across all paths"
  [templates]
  (let [paths (keys (:paths templates))]
    {:path-count (count paths)
     :paths (vec paths)
     :total-constraints (reduce + 0 (map #(count (:constraints (get (:paths templates) %)))
                                          paths))}))
