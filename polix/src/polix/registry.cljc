(ns polix.registry
  "Unified registry for namespace resolution in policies.

  The registry maps namespace prefixes to their handlers:
  - `:doc` — document accessor (built-in)
  - `:fn` — builtin functions (built-in)
  - `:self` — self-references in let bindings
  - `:param` — policy parameters
  - `:event` — event data accessor
  - User-defined modules containing named policies

  ## Example

      (require '[polix.registry :as reg])

      ;; Create and populate a registry
      (def my-registry
        (-> (reg/create-registry)
            (reg/register-module :auth
              {:policies {:admin [:= :doc/role \"admin\"]
                          :has-role [:= :doc/role :param/role]}})
            (reg/register-alias :a :auth)))

      ;; Resolve references
      (reg/resolve-namespace my-registry :auth)
      ;; => {:type :module, :version 1, :policies {...}}

      (reg/resolve-policy my-registry :auth :admin)
      ;; => [:= :doc/role \"admin\"]"
  (:require
   [malli.core :as m]
   [malli.error :as me]))

;;; ---------------------------------------------------------------------------
;;; Schemas
;;; ---------------------------------------------------------------------------

(def RegistryEntryType
  "Valid entry types in the registry."
  [:enum :document-accessor :self-accessor :param-accessor
   :event-accessor :builtins :module :alias])

(def ModuleEntry
  "Schema for a user-defined module entry."
  [:map
   [:type [:= :module]]
   [:version :int]
   [:policies [:map-of :keyword :any]]
   [:imports {:optional true} [:vector :keyword]]])

(def AliasEntry
  "Schema for an alias entry."
  [:map
   [:type [:= :alias]]
   [:target :keyword]])

(def AccessorEntry
  "Schema for built-in accessor entries."
  [:map
   [:type [:enum :document-accessor :self-accessor :param-accessor :event-accessor]]])

(def BuiltinsEntry
  "Schema for the :fn builtins entry."
  [:map
   [:type [:= :builtins]]
   [:entries {:optional true} [:map-of :keyword :any]]])

(def RegistryEntry
  "Schema for any registry entry."
  [:or ModuleEntry AliasEntry AccessorEntry BuiltinsEntry])

(def Registry
  "Schema for the complete registry."
  [:map
   [:entries [:map-of :keyword RegistryEntry]]
   [:version :int]])

;;; ---------------------------------------------------------------------------
;;; Registry Protocol
;;; ---------------------------------------------------------------------------

(defprotocol IRegistry
  "Protocol for registry operations."

  (resolve-namespace [this ns-key]
    "Returns the entry for `ns-key`, following aliases.
     Returns nil if not found.")

  (resolve-policy [this ns-key policy-key]
    "Returns the policy expression for `ns-key/policy-key`.
     Returns nil if the namespace is not a module or policy not found.")

  (registry-version [this]
    "Returns the current version number for cache invalidation."))

;;; ---------------------------------------------------------------------------
;;; Registry Record
;;; ---------------------------------------------------------------------------

(defrecord RegistryRecord [entries version]
  IRegistry

  (resolve-namespace [_ ns-key]
    (let [entry (get entries ns-key)]
      (if (= :alias (:type entry))
        (get entries (:target entry))
        entry)))

  (resolve-policy [this ns-key policy-key]
    (when-let [module (resolve-namespace this ns-key)]
      (when (= :module (:type module))
        (get-in module [:policies policy-key]))))

  (registry-version [_] version))

;;; ---------------------------------------------------------------------------
;;; Reserved Namespaces
;;; ---------------------------------------------------------------------------

(def reserved-namespaces
  "Set of namespace keywords reserved for built-in accessors."
  #{:doc :fn :self :param :event})

(defn reserved-namespace?
  "Returns true if `ns-key` is a reserved namespace."
  [ns-key]
  (contains? reserved-namespaces ns-key))

;;; ---------------------------------------------------------------------------
;;; Registry Construction
;;; ---------------------------------------------------------------------------

(defn create-registry
  "Creates a new registry with built-in entries.

  Built-ins include:
  - `:doc` — document accessor
  - `:fn` — builtin functions
  - `:self` — self-references
  - `:param` — parameter accessor
  - `:event` — event accessor"
  []
  (->RegistryRecord
   {:doc   {:type :document-accessor}
    :fn    {:type :builtins :entries {}}
    :self  {:type :self-accessor}
    :param {:type :param-accessor}
    :event {:type :event-accessor}}
   1))

;;; ---------------------------------------------------------------------------
;;; Registry Modification
;;; ---------------------------------------------------------------------------

(defn register-module
  "Adds a module to the registry. Returns a new registry.

  `ns-key` is the namespace keyword (e.g., `:auth`).
  `module-def` is a map with:
  - `:policies` — map of policy-key to policy expression
  - `:imports` — (optional) vector of imported namespace keys

  Throws if `ns-key` is a reserved namespace."
  [registry ns-key module-def]
  (when (reserved-namespace? ns-key)
    (throw (ex-info "Cannot register module with reserved namespace"
                    {:namespace ns-key
                     :reserved reserved-namespaces})))
  (let [entry (merge {:type :module
                      :version 1
                      :policies {}}
                     module-def
                     {:type :module})]
    (-> registry
        (update :entries assoc ns-key entry)
        (update :version inc))))

(defn register-alias
  "Adds an alias to the registry. Returns a new registry.

  `alias-key` is the alias keyword (e.g., `:a`).
  `target-key` is the namespace it points to (e.g., `:auth`).

  Throws if `alias-key` is a reserved namespace or if `target-key`
  does not exist in the registry."
  [registry alias-key target-key]
  (when (reserved-namespace? alias-key)
    (throw (ex-info "Cannot register alias with reserved namespace"
                    {:alias alias-key
                     :reserved reserved-namespaces})))
  (when-not (get-in registry [:entries target-key])
    (throw (ex-info "Alias target does not exist"
                    {:alias alias-key
                     :target target-key})))
  (-> registry
      (update :entries assoc alias-key {:type :alias :target target-key})
      (update :version inc)))

(defn unregister-module
  "Removes a module from the registry. Returns a new registry.

  Does not remove aliases pointing to this module."
  [registry ns-key]
  (-> registry
      (update :entries dissoc ns-key)
      (update :version inc)))

;;; ---------------------------------------------------------------------------
;;; Reference Resolution
;;; ---------------------------------------------------------------------------

(defn resolve-reference
  "Resolves a namespaced keyword to its handler information.

  Returns a map with:
  - `:namespace` — the namespace keyword
  - `:name` — the local name keyword
  - `:entry` — the resolved registry entry

  Returns nil if the namespace is not found.

  Resolution precedence:
  1. Built-in namespaces (`:doc`, `:fn`, `:self`, `:param`, `:event`)
  2. Aliases (followed to target)
  3. User modules"
  [registry kw]
  (when-let [ns-str (namespace kw)]
    (let [ns-key (keyword ns-str)
          entry  (resolve-namespace registry ns-key)]
      (when entry
        {:namespace ns-key
         :name      (keyword (name kw))
         :entry     entry}))))

;;; ---------------------------------------------------------------------------
;;; Global Registry
;;; ---------------------------------------------------------------------------

(defonce ^:private global-registry (atom nil))

(defn init-global-registry!
  "Initializes the global registry. Idempotent."
  []
  (when (nil? @global-registry)
    (reset! global-registry (create-registry)))
  @global-registry)

(defn get-global-registry
  "Returns the global registry, initializing if needed."
  []
  (init-global-registry!))

(defn reset-global-registry!
  "Resets the global registry to a fresh state. Useful for testing."
  []
  (reset! global-registry (create-registry)))

(defn register-module!
  "Registers a module in the global registry."
  [ns-key module-def]
  (swap! global-registry register-module ns-key module-def))

(defn register-alias!
  "Registers an alias in the global registry."
  [alias-key target-key]
  (swap! global-registry register-alias alias-key target-key))

;;; ---------------------------------------------------------------------------
;;; Validation
;;; ---------------------------------------------------------------------------

(defn validate-module
  "Validates a module definition against the schema.

  Returns `{:ok module-def}` if valid, `{:error error-map}` if invalid."
  [module-def]
  (let [entry (merge {:type :module :version 1} module-def {:type :module})]
    (if (m/validate ModuleEntry entry)
      {:ok entry}
      {:error {:message "Invalid module definition"
               :explanation (me/humanize (m/explain ModuleEntry entry))}})))

(defn validate-registry
  "Validates the complete registry against the schema.

  Returns `{:ok registry}` if valid, `{:error error-map}` if invalid."
  [registry]
  (if (m/validate Registry registry)
    {:ok registry}
    {:error {:message "Invalid registry"
             :explanation (me/humanize (m/explain Registry registry))}}))

;;; ---------------------------------------------------------------------------
;;; Query Helpers
;;; ---------------------------------------------------------------------------

(defn module-namespaces
  "Returns a set of all module namespace keys in the registry."
  [registry]
  (->> (:entries registry)
       (filter (fn [[_ v]] (= :module (:type v))))
       (map first)
       (set)))

(defn alias-namespaces
  "Returns a set of all alias keys in the registry."
  [registry]
  (->> (:entries registry)
       (filter (fn [[_ v]] (= :alias (:type v))))
       (map first)
       (set)))

(defn all-policies
  "Returns a map of qualified policy keys to their expressions.

  Example: `{:auth/admin [:= :doc/role \"admin\"], ...}`"
  [registry]
  (->> (:entries registry)
       (filter (fn [[_ v]] (= :module (:type v))))
       (mapcat (fn [[ns-key module]]
                 (map (fn [[policy-key expr]]
                        [(keyword (name ns-key) (name policy-key)) expr])
                      (:policies module))))
       (into {})))
