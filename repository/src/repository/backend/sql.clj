(ns repository.backend.sql
  "SQL repository backend using HoneySQL and next.jdbc.

  Provides a PostgreSQL-backed repository implementation with:
  - Automatic field type coercion (UUID, enum, JSON)
  - Named scopes as HoneySQL-returning functions
  - Bidirectional transforms for complex data types
  - Cursor-based (keyset) pagination

  ## Example Usage

      (require '[repository.backend.sql :as sql]
               '[repository.protocol :as repo])

      ;; Create repository
      (def user-repo
        (sql/create {:table-name :users
                     :field-types {:id :uuid}
                     :scopes {:by-email (fn [{:keys [email]}]
                                          [:= :email email])}}))

      ;; Basic operations
      (repo/find-one user-repo {:where {:email \"test@example.com\"}})
      (repo/find-many user-repo {:scope :by-email
                                  :scope-params {:email \"alice@example.com\"}
                                  :limit 10})
      (repo/save! user-repo {:name \"Alice\" :email \"alice@example.com\"})

  ## Configuration Options

  - `:table-name` - Required keyword for the database table
  - `:id-field` - Primary key field (default `:id`)
  - `:scopes` - Map of scope name to HoneySQL-returning function
  - `:transforms` - Map with `:from-db` and `:to-db` functions
  - `:field-types` - Map of field to type for coercion (:uuid, :enum, :json)
  - `:column-mapping` - Map of entity field names to DB column names"
  (:require [db.core :as db]
            [repository.cursor :as cursor]
            [repository.protocol :as proto]))

(defn- coerce-value
  "Coerces a value based on field type for HoneySQL.

  Type coercions:
  - `:uuid` - Wraps in `[:cast value :uuid]`
  - `:enum` - Wraps in `[:lift value]` for PostgreSQL enums
  - `:json` - Wraps in `[:lift value]` for JSONB columns
  - `nil` or unknown - Returns value unchanged"
  [value field-type]
  (case field-type
    :uuid [:cast value :uuid]
    :enum [:lift value]
    :json [:lift value]
    value))

(defn- coerce-field
  "Coerces a field value based on field-types configuration."
  [field value field-types]
  (if-let [field-type (get field-types field)]
    (coerce-value value field-type)
    value))

(defn- map-column
  "Maps an entity field name to its DB column name."
  [field column-mapping]
  (get column-mapping field field))

(defn- build-where-clause
  "Builds a HoneySQL where clause from a key/value map.

  All conditions are combined with AND semantics. Field names are mapped
  through column-mapping and values are coerced based on field-types."
  [where column-mapping field-types]
  (when (seq where)
    (into [:and]
          (map (fn [[field value]]
                 (let [column  (map-column field column-mapping)
                       coerced (coerce-field field value field-types)]
                   [:= column coerced]))
               where))))

(defn- apply-scope
  "Applies a named scope to get a HoneySQL where clause.

  Looks up the scope function and calls it with scope-params.
  Throws if scope is not found."
  [scopes scope-name scope-params]
  (if-let [scope-fn (get scopes scope-name)]
    (scope-fn scope-params)
    (throw (ex-info (str "Unknown scope: " scope-name)
                    {:scope scope-name
                     :available-scopes (keys scopes)}))))

(defn- build-cursor-where
  "Builds a HoneySQL where clause for keyset pagination.

  Given cursor data and order-by spec, generates a compound comparison
  that excludes records at or before the cursor position."
  [cursor-data order-by column-mapping field-types]
  (when (and (seq cursor-data) (seq order-by))
    (let [conditions
          (loop [remaining   order-by
                 accumulated []]
            (if (empty? remaining)
              accumulated
              (let [[field direction] (first remaining)
                    column            (map-column field column-mapping)
                    cursor-val        (coerce-field field (get cursor-data field) field-types)
                    ;; For desc, we want values LESS than cursor
                    ;; For asc, we want values GREATER than cursor
                    op                (if (= direction :desc) :< :>)
                    ;; Build equality conditions for all previous fields
                    eq-conditions     (mapv (fn [[f _d]]
                                              (let [c  (map-column f column-mapping)
                                                    cv (coerce-field f (get cursor-data f) field-types)]
                                                [:= c cv]))
                                            (take (count accumulated) order-by))
                    ;; This level: all previous fields equal AND this field past cursor
                    this-condition    (if (seq eq-conditions)
                                        (into [:and] (conj eq-conditions [op column cursor-val]))
                                        [op column cursor-val])]
                (recur (rest remaining)
                       (conj accumulated this-condition)))))]
      (if (= 1 (count conditions))
        (first conditions)
        (into [:or] conditions)))))

(defn- build-order-by
  "Builds HoneySQL order-by clause, mapping field names to columns."
  [order-by column-mapping]
  (when (seq order-by)
    (mapv (fn [[field direction]]
            [(map-column field column-mapping) direction])
          order-by)))

(defn- build-set-clause
  "Builds the SET clause for UPDATE, applying coercion and column mapping."
  [entity id-field column-mapping field-types]
  (reduce-kv (fn [m field value]
               (if (= field id-field)
                 m ; Skip ID field in SET clause
                 (let [column  (map-column field column-mapping)
                       coerced (coerce-field field value field-types)]
                   (assoc m column coerced))))
             {}
             entity))

(defn- build-insert-values
  "Builds the values map for INSERT, applying coercion and column mapping."
  [entity column-mapping field-types]
  (reduce-kv (fn [m field value]
               (let [column  (map-column field column-mapping)
                     coerced (coerce-field field value field-types)]
                 (assoc m column coerced)))
             {}
             entity))

(defrecord SqlRepository [table-name id-field scopes transforms field-types column-mapping]
  proto/Repository
  (find-one [_this query]
    (let [{:keys [where scope scope-params]} query
          where-clause                       (cond
                                               scope (apply-scope scopes scope scope-params)
                                               (seq where) (build-where-clause where column-mapping field-types)
                                               :else nil)
          sql-map                            (cond-> {:select [:*]
                                                      :from [table-name]}
                                               where-clause (assoc :where where-clause)
                                               true (assoc :limit 1))
          from-db                            (:from-db transforms identity)]
      (some-> (db/execute-one! sql-map)
              from-db)))

  (find-many [_this query]
    (let [{:keys [where scope scope-params order-by limit cursor]} query
          cursor-data                                              (when (:after cursor) (cursor/decode (:after cursor)))
          order-by'                                                (or order-by [[id-field :asc]])
          scope-where                                              (when scope (apply-scope scopes scope scope-params))
          basic-where                                              (when (seq where) (build-where-clause where column-mapping field-types))
          cursor-where                                             (build-cursor-where cursor-data order-by' column-mapping field-types)
          combined-where                                           (cond
                                                                     (and scope-where cursor-where)
                                                                     [:and scope-where cursor-where]
                                                                     (and basic-where cursor-where)
                                                                     [:and basic-where cursor-where]
                                                                     scope-where scope-where
                                                                     basic-where basic-where
                                                                     cursor-where cursor-where
                                                                     :else nil)
          sql-map                                                  (cond-> {:select [:*]
                                                                            :from [table-name]
                                                                            :order-by (build-order-by order-by' column-mapping)}
                                                                     combined-where (assoc :where combined-where)
                                                                     limit (assoc :limit limit))
          from-db                                                  (:from-db transforms identity)
          results                                                  (mapv from-db (db/execute! sql-map))]
      {:data results
       :page-info (cursor/page-info results order-by' limit)}))

  (save! [_this entity]
    (let [to-db   (:to-db transforms identity)
          from-db (:from-db transforms identity)
          entity' (to-db entity)
          has-id? (contains? entity' id-field)
          id-val  (get entity' id-field)]
      (if has-id?
        ;; UPDATE
        (let [set-clause (build-set-clause entity' id-field column-mapping field-types)
              id-column  (map-column id-field column-mapping)
              id-coerced (coerce-field id-field id-val field-types)
              sql-map    {:update table-name
                          :set set-clause
                          :where [:= id-column id-coerced]
                          :returning [:*]}]
          (some-> (db/execute-one! sql-map)
                  from-db))
        ;; INSERT
        (let [values  (build-insert-values entity' column-mapping field-types)
              sql-map {:insert-into table-name
                       :values [values]
                       :returning [:*]}]
          (some-> (db/execute-one! sql-map)
                  from-db)))))

  (delete! [_this query]
    (let [{:keys [where scope scope-params]} query
          where-clause                       (cond
                                               scope (apply-scope scopes scope scope-params)
                                               (seq where) (build-where-clause where column-mapping field-types)
                                               :else (throw (ex-info "Delete requires a where clause or scope"
                                                                     {:query query})))
          sql-map                            {:delete-from table-name
                                              :where where-clause}
          result                             (db/execute-one! sql-map)]
      (or (:next.jdbc/update-count result) 0)))

  proto/Countable
  (count-matching [_this query]
    (let [{:keys [where scope scope-params]} query
          scope-where                        (when scope (apply-scope scopes scope scope-params))
          basic-where                        (when (seq where) (build-where-clause where column-mapping field-types))
          where-clause                       (or scope-where basic-where)
          sql-map                            (cond-> {:select [[[:count :*] :count]]
                                                      :from [table-name]}
                                               where-clause (assoc :where where-clause))]
      (:count (db/execute-one! sql-map))))

  proto/Transactional
  (with-transaction [_this f]
    (db/with-transaction [_tx]
      (f))))

(defn create
  "Creates a SQL-backed repository.

  Required:
  - `:table-name` - Keyword for the database table (e.g., `:users`, `:games`)

  Optional:
  - `:id-field` - Primary key field (default `:id`)
  - `:scopes` - Map of scope name to HoneySQL-returning function
  - `:transforms` - Map with `:from-db` and `:to-db` functions
  - `:field-types` - Map of field to type for coercion (`:uuid`, `:enum`, `:json`)
  - `:column-mapping` - Map of entity field names to DB column names

  Example:

      (create {:table-name :users
               :field-types {:id :uuid}
               :scopes {:active (fn [_] [:= :status [:lift :active]])}
               :transforms {:from-db identity
                            :to-db identity}})"
  [{:keys [table-name id-field scopes transforms field-types column-mapping]
    :or {id-field :id
         scopes {}
         transforms {}
         field-types {}
         column-mapping {}}}]
  (when-not table-name
    (throw (ex-info "table-name is required" {})))
  (->SqlRepository table-name id-field scopes transforms field-types column-mapping))
