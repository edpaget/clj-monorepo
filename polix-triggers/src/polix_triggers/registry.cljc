(ns polix-triggers.registry
  "Trigger registration and lookup.

  Provides functions for managing trigger lifecycle: registration, unregistration,
  and querying. The registry maintains an index by event type for efficient lookup
  during event processing."
  (:require [malli.core :as m]
            [polix-triggers.schema :as schema]))

(defn create-registry
  "Creates an empty trigger registry.

  The registry contains a map of triggers keyed by ID and an index mapping
  event types to sets of trigger IDs for O(1) lookup."
  []
  {:triggers {}
   :index-by-event {}})

(defn- generate-id
  "Generates a unique trigger ID."
  []
  (str #?(:clj (java.util.UUID/randomUUID)
          :cljs (random-uuid))))

(defn- add-to-index
  "Adds a trigger ID to the event-type index for all its event types."
  [index trigger-id event-types]
  (reduce (fn [idx event-type]
            (update idx event-type (fnil conj #{}) trigger-id))
          index
          event-types))

(defn- remove-from-index
  "Removes a trigger ID from the event-type index."
  [index trigger-id event-types]
  (reduce (fn [idx event-type]
            (let [updated (disj (get idx event-type #{}) trigger-id)]
              (if (empty? updated)
                (dissoc idx event-type)
                (assoc idx event-type updated))))
          index
          event-types))

(defn register-trigger
  "Registers a trigger definition in the registry.

  Takes a trigger definition map and binding context. The trigger receives a unique
  ID and is stored with its source, owner, and self bindings. Returns the updated
  registry. Optionally accepts a pre-compiled condition function via the `:condition-fn`
  key in the trigger definition."
  [registry trigger-def source-id owner self]
  (let [trigger-id (generate-id)
        trigger (-> trigger-def
                    (assoc :id trigger-id
                           :source source-id
                           :owner owner
                           :self self)
                    (update :priority #(or % 0))
                    (update :once? #(or % false)))]
    (-> registry
        (assoc-in [:triggers trigger-id] trigger)
        (update :index-by-event add-to-index trigger-id (:event-types trigger)))))

(defn unregister-trigger
  "Removes a trigger by ID from the registry.

  Returns the updated registry. If the trigger ID does not exist, returns
  the registry unchanged."
  [registry trigger-id]
  (if-let [trigger (get-in registry [:triggers trigger-id])]
    (-> registry
        (update :triggers dissoc trigger-id)
        (update :index-by-event remove-from-index trigger-id (:event-types trigger)))
    registry))

(defn unregister-triggers-by-source
  "Removes all triggers registered by a source.

  Useful when an ability or effect is removed and all its triggers should be
  cleaned up. Returns the updated registry."
  [registry source-id]
  (let [trigger-ids (->> (:triggers registry)
                         (filter (fn [[_ trigger]] (= source-id (:source trigger))))
                         (map first))]
    (reduce unregister-trigger registry trigger-ids)))

(defn get-triggers
  "Returns all registered triggers as a sequence."
  [registry]
  (vals (:triggers registry)))

(defn get-trigger
  "Returns a trigger by ID, or nil if not found."
  [registry trigger-id]
  (get-in registry [:triggers trigger-id]))

(defn get-triggers-for-event
  "Returns triggers that listen for the given event type.

  Triggers are sorted by priority (lower values fire first). Returns an empty
  sequence if no triggers match the event type."
  [registry event-type]
  (let [trigger-ids (get-in registry [:index-by-event event-type] #{})]
    (->> trigger-ids
         (map #(get-in registry [:triggers %]))
         (sort-by :priority))))
