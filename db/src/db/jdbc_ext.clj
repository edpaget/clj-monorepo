(ns db.jdbc-ext
  (:require
   [camel-snake-kebab.core :as csk]
   [jsonista.core :as json]
   [next.jdbc :as jdbc]
   [next.jdbc.prepare :as prepare]
   [next.jdbc.result-set :as rs])
  (:import
   [java.sql PreparedStatement]
   [org.postgresql.util PGobject]))

(set! *warn-on-reflection* true)

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))
(def ^:private ->json json/write-value-as-string)
(def ^:private <-json #(json/read-value % mapper))

(defn ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`.

  Args:
    x: Clojure data structure to convert to PostgreSQL JSON/JSONB
    pgtype: Optional PostgreSQL type (defaults to jsonb for non-string values)

  Returns:
    PGobject with JSON representation of x"
  ([x]
   (->pgobject x "jsonb"))
  ([x pgtype]
   (doto (PGobject.)
     (.setType pgtype)
     (.setValue (if (string? x) x (->json x))))))

(defn- <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure data.

  Args:
    v: PGobject containing JSON/JSONB data

  Returns:
    Clojure data structure parsed from JSON, or original value if not JSON type"
  [^PGobject v]
  (let [type (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (some-> value <-json (with-meta {:pgtype type}))
      value)))

(defn- fetch-pg-enums
  "Queries PostgreSQL database to retrieve all enum type names.

  Args:
    connection: JDBC connection or datasource

  Returns:
    Set of enum type names as strings"
  [connection]
  (let [query "SELECT typname FROM pg_type WHERE typtype = 'e'"
        results (jdbc/execute! connection [query])]
    (into #{} (map :pg_type/typname results))))

(def ^:private enum-cache (atom nil))

(defn refresh-enum-cache!
  "Refreshes the cache of PostgreSQL enum types.

  Args:
    connection: JDBC connection or datasource

  Returns:
    Set of enum type names"
  [connection]
  (reset! enum-cache (fetch-pg-enums connection)))

(defn- enum-type?
  "Checks if a column type is a PostgreSQL enum.

  Args:
    type-name: SQL type name from ResultSetMetaData
    connection: Optional JDBC connection for cache refresh

  Returns:
    True if type-name is a known enum type"
  [type-name]
  (when-let [enums @enum-cache]
    (contains? enums type-name)))

(defn- keyword->enum-type
  "Extracts the PostgreSQL enum type name from a namespaced keyword.

  Args:
    kw: Namespaced keyword (e.g., :status-enum/active)

  Returns:
    String enum type name (e.g., \"status_enum\") or nil if not an enum keyword"
  [kw]
  (when (and (keyword? kw) (namespace kw))
    (let [ns-part (namespace kw)
          snake-case (csk/->snake_case ns-part)]
      (when (enum-type? snake-case)
        snake-case))))

(extend-protocol rs/ReadableColumn
  java.lang.String
  (read-column-by-label [^java.lang.String v _]
    v)
  (read-column-by-index [^java.lang.String v ^java.sql.ResultSetMetaData rsmeta ^Long idx]
    (let [type (.getColumnTypeName rsmeta idx)]
      (if (enum-type? type)
        (keyword (csk/->kebab-case type) v)
        v))))

;; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v)))

  clojure.lang.Keyword
  (set-parameter [kw ^PreparedStatement s i]
    (if-let [enum-type (keyword->enum-type kw)]
      (.setObject s i (->pgobject (name kw) enum-type))
      (.setObject s i (name kw)))))

;; if a row contains a PGobject then we'll convert them to Clojure data
;; while reading (if column is either "json" or "jsonb" type):
(extend-protocol rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))
