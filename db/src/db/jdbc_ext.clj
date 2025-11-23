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
  "Transforms Clojure data to a PGobject containing the data as JSON.

   Takes a Clojure data structure and converts it to a PostgreSQL PGobject. When
   called with just a value, creates a `jsonb` PGobject. When called with both a
   value and a pgtype string, uses the specified PostgreSQL type. String values
   are stored directly; other values are serialized to JSON. Returns the configured
   PGobject."
  ([x]
   (->pgobject x "jsonb"))
  ([x pgtype]
   (doto (PGobject.)
     (.setType pgtype)
     (.setValue (if (string? x) x (->json x))))))

(defn- <-pgobject
  "Transforms a PGobject containing JSON or JSONB data to Clojure data.

   Takes a PGobject and checks its type. When the type is `json` or `jsonb`,
   parses the value as JSON and attaches type metadata. For other types, returns
   the value unchanged. Returns the parsed Clojure data structure or the original
   value."
  [^PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (some-> value <-json (with-meta {:pgtype type}))
      value)))

(defn- fetch-pg-enums
  "Queries the PostgreSQL database to retrieve all enum type names.

   Takes a JDBC connection or datasource and executes a query against `pg_type`
   to find all types where `typtype = 'e'` (enum types). Returns a set of enum
   type names as strings."
  [connection]
  (let [query   "SELECT typname FROM pg_type WHERE typtype = 'e'"
        results (jdbc/execute! connection [query])]
    (into #{} (map :pg_type/typname results))))

(def ^:private enum-cache (atom nil))

(defn refresh-enum-cache!
  "Refreshes the cache of PostgreSQL enum types.

   Takes a JDBC connection or datasource, fetches all enum type names from the
   database, and updates the internal cache. Returns the set of enum type names."
  [connection]
  (reset! enum-cache (fetch-pg-enums connection)))

(defn- enum-type?
  "Checks if a column type is a PostgreSQL enum.

   Takes a SQL type name (from ResultSetMetaData) and checks if it exists in the
   cached set of enum types. Returns true if the type name is a known enum type,
   false otherwise. Returns nil if the cache has not been initialized."
  [type-name]
  (when-let [enums @enum-cache]
    (contains? enums type-name)))

(defn- keyword->enum-type
  "Extracts the PostgreSQL enum type name from a namespaced keyword.

   Takes a namespaced keyword (like `:status-enum/active`) and converts the
   namespace to snake_case (like `\"status_enum\"`). Checks if this snake_case
   name is a known enum type. Returns the string enum type name if found, or nil
   if the keyword is not namespaced or doesn't correspond to a known enum type."
  [kw]
  (when (and (keyword? kw) (namespace kw))
    (let [ns-part    (namespace kw)
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
