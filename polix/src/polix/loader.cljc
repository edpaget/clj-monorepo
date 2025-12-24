(ns polix.loader
  "Module loading from EDN data with dependency resolution.

  Modules are collections of named policies that can import policies from
  other modules. The loader validates dependencies, detects circular imports,
  and loads modules in topological order (dependencies first).

  ## Module Definition Format

      {:namespace :auth
       :imports [:common :utils]
       :policies {:admin [:= :doc/role \"admin\"]
                  :has-role [:= :doc/role :param/role]}}

  ## Usage

      (require '[polix.loader :as loader]
               '[polix.registry :as reg])

      (def modules
        [{:namespace :common
          :policies {:active [:= :doc/status \"active\"]}}
         {:namespace :auth
          :imports [:common]
          :policies {:admin-active [:and [:common/active]
                                         [:= :doc/role \"admin\"]]}}])

      (let [{:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
        (if error
          (println \"Load failed:\" error)
          (println \"Loaded\" (count (reg/module-namespaces ok)) \"modules\")))"
  (:require
   [polix.registry :as reg]))

;;; ---------------------------------------------------------------------------
;;; Dependency Graph
;;; ---------------------------------------------------------------------------

(defn- build-dependency-graph
  "Builds a map of module namespace -> set of imported namespaces."
  [module-defs]
  (into {}
        (map (fn [m]
               [(:namespace m) (set (:imports m))]))
        module-defs))

;;; ---------------------------------------------------------------------------
;;; Cycle Detection
;;; ---------------------------------------------------------------------------

(defn detect-cycle
  "Detects if the dependency graph contains a cycle.

  Uses DFS with path tracking. Returns nil if no cycle found,
  or a vector representing the cycle path if found.

  Example: `[:a :b :c :a]` means a -> b -> c -> a."
  [graph]
  (let [visited   (volatile! #{})
        rec-stack (volatile! #{})]
    (letfn [(dfs [node path]
              (vswap! visited conj node)
              (vswap! rec-stack conj node)
              (let [neighbors (get graph node #{})]
                (loop [remaining neighbors]
                  (if (empty? remaining)
                    (do (vswap! rec-stack disj node)
                        nil)
                    (let [neighbor (first remaining)]
                      (cond
                        (@rec-stack neighbor)
                        (conj (vec (drop-while #(not= % neighbor) path)) neighbor)

                        (not (@visited neighbor))
                        (if-let [cycle (dfs neighbor (conj path neighbor))]
                          cycle
                          (recur (rest remaining)))

                        :else
                        (recur (rest remaining))))))))]
      (loop [nodes (keys graph)]
        (if (empty? nodes)
          nil
          (if (@visited (first nodes))
            (recur (rest nodes))
            (if-let [cycle (dfs (first nodes) [(first nodes)])]
              cycle
              (recur (rest nodes)))))))))

;;; ---------------------------------------------------------------------------
;;; Topological Sort
;;; ---------------------------------------------------------------------------

(defn topological-sort
  "Returns modules in dependency order (dependencies first).

  Uses DFS-based topological sort. Returns vector of nodes with dependencies
  before dependents. Returns nil if the graph contains a cycle."
  [graph]
  (let [visited (volatile! #{})
        result  (volatile! [])]
    (letfn [(visit [node]
              (when-not (@visited node)
                (vswap! visited conj node)
                (doseq [dep (get graph node #{})]
                  (visit dep))
                (vswap! result conj node)))]
      (doseq [node (keys graph)]
        (visit node))
      ;; DFS post-order naturally gives dependencies before dependents
      @result)))

;;; ---------------------------------------------------------------------------
;;; Validation
;;; ---------------------------------------------------------------------------

(defn- validate-module-def
  "Validates a module definition. Returns nil if valid, or error map if invalid."
  [module-def]
  (cond
    (not (map? module-def))
    {:error :invalid-module
     :message "Module definition must be a map"
     :value module-def}

    (not (keyword? (:namespace module-def)))
    {:error :invalid-namespace
     :message "Module :namespace must be a keyword"
     :value (:namespace module-def)}

    (and (:imports module-def)
         (not (vector? (:imports module-def))))
    {:error :invalid-imports
     :message "Module :imports must be a vector"
     :value (:imports module-def)}

    (and (:imports module-def)
         (not (every? keyword? (:imports module-def))))
    {:error :invalid-imports
     :message "Module :imports must contain only keywords"
     :value (:imports module-def)}

    (not (map? (:policies module-def)))
    {:error :invalid-policies
     :message "Module :policies must be a map"
     :value (:policies module-def)}

    :else nil))

(defn- find-missing-imports
  "Finds imports that don't exist in the module definitions.

  Returns a vector of `{:module ns :missing import-ns}` maps, or empty if all valid."
  [module-defs]
  (let [defined-ns (set (map :namespace module-defs))]
    (for [m     module-defs
          imp   (:imports m)
          :when (not (defined-ns imp))]
      {:module (:namespace m) :missing imp})))

;;; ---------------------------------------------------------------------------
;;; Loading
;;; ---------------------------------------------------------------------------

(defn load-module
  "Loads a single module definition into a registry.

  Returns the updated registry. Does not validate imports."
  [registry module-def]
  (reg/register-module registry
                       (:namespace module-def)
                       {:policies (:policies module-def)
                        :imports (:imports module-def [])}))

(defn load-modules
  "Loads multiple modules into a registry with dependency resolution.

  Validates all module definitions, checks for circular imports, verifies
  that all imports exist, and loads modules in topological order
  (dependencies first).

  Returns:
  - `{:ok registry}` on success
  - `{:error error-map}` on failure

  Error types:
  - `:invalid-module` - module definition is malformed
  - `:circular-import` - modules form an import cycle
  - `:missing-imports` - some imports reference undefined modules"
  [registry module-defs]
  (if-let [validation-error (some validate-module-def module-defs)]
    {:error validation-error}
    (let [graph  (build-dependency-graph module-defs)
          ns-set (set (map :namespace module-defs))
          dups   (when (not= (count ns-set) (count module-defs))
                   (->> module-defs
                        (map :namespace)
                        frequencies
                        (filter (fn [[_ c]] (> c 1)))
                        (map first)
                        vec))]
      (cond
        (seq dups)
        {:error {:type :duplicate-namespaces
                 :namespaces dups}}

        (some? (detect-cycle graph))
        {:error {:type :circular-import
                 :cycle (detect-cycle graph)}}

        (seq (find-missing-imports module-defs))
        {:error {:type :missing-imports
                 :details (vec (find-missing-imports module-defs))}}

        :else
        (let [order          (or (topological-sort graph)
                                 (keys graph))
              ns->def        (into {} (map (juxt :namespace identity) module-defs))
              final-registry (reduce (fn [reg ns-key]
                                       (load-module reg (ns->def ns-key)))
                                     registry
                                     order)]
          {:ok final-registry})))))
