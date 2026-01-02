(ns repository.backend.memory
  "In-memory repository backend for testing and development.

  Stores entities in an atom, supporting the full Repository protocol with
  scopes implemented as predicate functions.

  ## Example Usage

      (require '[repository.backend.memory :as mem]
               '[repository.protocol :as repo])

      ;; Create repository
      (def users (mem/create {:id-field :id}))

      ;; Save entities
      (repo/save! users {:name \"Alice\"})
      ;; => {:id #uuid \"...\" :name \"Alice\"}

      ;; Query
      (repo/find-one users {:where {:name \"Alice\"}})

      ;; With scopes
      (def games (mem/create {:scopes {:active (fn [_] #(= (:status %) :active))}}))
      (repo/find-many games {:scope :active})"
  (:require [repository.cursor :as cursor]
            [repository.protocol :as proto]))

(defn- matches-where?
  "Tests if an entity matches a where clause (key/value map).

  All key/value pairs must match (AND semantics)."
  [entity where]
  (if (or (nil? where) (empty? where))
    true
    (every? (fn [[k v]]
              (= (get entity k) v))
            where)))

(defn- apply-scope
  "Applies a named scope to filter entities.

  Looks up the scope function in the scopes map, calls it with scope-params,
  and returns the resulting predicate function."
  [scopes scope-name scope-params]
  (if-let [scope-fn (get scopes scope-name)]
    (scope-fn scope-params)
    (throw (ex-info (str "Unknown scope: " scope-name)
                    {:scope scope-name
                     :available-scopes (keys scopes)}))))

(defn- compare-for-sort
  "Compares two values for sorting, handling nil and different types."
  [a b]
  (cond
    (and (nil? a) (nil? b)) 0
    (nil? a) -1
    (nil? b) 1
    (and (number? a) (number? b)) (compare a b)
    (and (inst? a) (inst? b)) (compare a b)
    :else (compare (str a) (str b))))

(defn- apply-order-by
  "Sorts entities according to order-by specification.

  Order-by is a vector of `[field direction]` pairs where direction
  is `:asc` or `:desc`. Arguments ordered for `->>` threading."
  [order-by entities]
  (if (or (nil? order-by) (empty? order-by))
    entities
    (sort (fn [a b]
            (reduce (fn [_ [field direction]]
                      (let [va     (get a field)
                            vb     (get b field)
                            cmp    (compare-for-sort va vb)
                            result (if (= direction :desc) (- cmp) cmp)]
                        (if (zero? result)
                          0
                          (reduced result))))
                    0
                    order-by))
          entities)))

(defn- apply-cursor
  "Filters entities to those after the cursor position.

  Uses keyset pagination based on the order-by fields."
  [entities order-by cursor-data]
  (if (or (nil? cursor-data) (empty? cursor-data))
    entities
    (drop-while
     (fn [entity]
       (let [cmp (reduce (fn [_ [field direction]]
                           (let [cursor-val (get cursor-data field)
                                 entity-val (get entity field)
                                 cmp        (compare-for-sort entity-val cursor-val)]
                             (cond
                               (zero? cmp) 0
                               (= direction :desc) (if (neg? cmp) (reduced true) (reduced false))
                               :else (if (pos? cmp) (reduced true) (reduced false)))))
                         0
                         order-by)]
         (not (true? cmp))))
     entities)))

(defrecord MemoryRepository [store scopes id-field id-generator]
  proto/Repository
  (find-one [_this query]
    (let [{:keys [where scope scope-params]} query
          pred                               (when scope (apply-scope scopes scope scope-params))]
      (->> (vals @store)
           (filter #(matches-where? % where))
           (filter (or pred (constantly true)))
           first)))

  (find-many [_this query]
    (let [{:keys [where scope scope-params order-by limit cursor]} query
          pred                                                     (when scope (apply-scope scopes scope scope-params))
          cursor-data                                              (when (:after cursor) (cursor/decode (:after cursor)))
          order-by'                                                (or order-by [[id-field :asc]])
          ordered                                                  (->> (vals @store)
                                                                        (filter #(matches-where? % where))
                                                                        (filter (or pred (constantly true)))
                                                                        (apply-order-by order-by')
                                                                        vec)
          after-cursor                                             (apply-cursor ordered order-by' cursor-data)
          entities                                                 (cond->> after-cursor limit (take limit) true vec)]
      {:data entities
       :page-info (cursor/page-info entities order-by' limit)}))

  (save! [_this entity]
    (let [id      (or (get entity id-field) (id-generator))
          entity' (assoc entity id-field id)]
      (swap! store assoc id entity')
      entity'))

  (delete! [_this query]
    (let [{:keys [where scope scope-params]} query
          pred                               (when scope (apply-scope scopes scope scope-params))
          to-delete                          (->> (vals @store)
                                                  (filter #(matches-where? % where))
                                                  (filter (or pred (constantly true)))
                                                  (map #(get % id-field))
                                                  set)]
      (swap! store (fn [s] (apply dissoc s to-delete)))
      (count to-delete)))

  proto/Countable
  (count-matching [_this query]
    (let [{:keys [where scope scope-params]} query
          pred                               (when scope (apply-scope scopes scope scope-params))]
      (->> (vals @store)
           (filter #(matches-where? % where))
           (filter (or pred (constantly true)))
           count))))

(defn create
  "Creates an in-memory repository.

  Options:
  - `:id-field` - Field to use as primary key (default `:id`)
  - `:id-generator` - Function to generate new IDs (default `random-uuid`)
  - `:scopes` - Map of scope name to scope function

  Scope functions take a params map and return a predicate function:

      {:active (fn [_params] #(= (:status %) :active))
       :by-user (fn [{:keys [user-id]}] #(= (:user-id %) user-id))}"
  ([]
   (create {}))
  ([{:keys [id-field id-generator scopes]
     :or {id-field :id
          id-generator random-uuid
          scopes {}}}]
   (->MemoryRepository (atom {}) scopes id-field id-generator)))

(defn create-with-data
  "Creates an in-memory repository pre-populated with data.

  Convenience function for testing. Each entity is saved, generating
  IDs for entities that don't have them."
  ([initial-data]
   (create-with-data initial-data {}))
  ([initial-data opts]
   (let [repo (create opts)]
     (doseq [entity initial-data]
       (proto/save! repo entity))
     repo)))
