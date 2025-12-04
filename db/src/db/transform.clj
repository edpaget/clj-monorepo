(ns db.transform
  "Malli-based JSON transformation utilities for database columns.

  Provides bidirectional transforms between database JSON (string keys)
  and application data (keyword keys), driven by Malli schemas. Use these
  utilities in model repositories to explicitly transform JSON columns
  read from PostgreSQL.

  For simple flat maps without complex nesting, use [[keywordize-keys]].
  For schema-driven transforms that handle nested structures, enums, and
  special key types, use [[decode]] with a Malli schema.

  For multi schemas, use [[db-dispatch]] instead of a plain keyword for
  the dispatch function to handle both string and keyword keys/values."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [malli.core :as mc]
   [malli.transform :as mt]))

(defn- uppercase-string?
  "Returns true if the string is all uppercase."
  [s]
  (and (string? s)
       (not (str/blank? s))
       (= s (str/upper-case s))))

(defn- decode-key
  "Decodes a JSON string key to a keyword.

  - Uppercase keys (HOME, AWAY) -> uppercase keywords
  - Field name keys (snake_case) -> kebab-case keywords"
  [k]
  (cond
    (keyword? k) k
    (uppercase-string? k) (keyword k)
    (string? k) (csk/->kebab-case-keyword k)
    :else k))

(defn db-dispatch
  "Creates a dispatch function for multi schemas that handles DB JSON.

  Malli's multi dispatch happens before transformers run, so the dispatch
  function must handle both string and keyword keys/values from the database.

  Usage in schema:
      [:multi {:dispatch (db-dispatch :status)}
       [:POSSESSED BallPossessedSchema]
       [:LOOSE BallLooseSchema]]"
  [k]
  (fn [x]
    (let [v (or (get x k) (get x (name k)))]
      (if (string? v) (keyword v) v))))

(def ^:private enum-value-transformer
  "Transforms enum values from strings to keywords during decode.

  Handles [:enum ...] schemas and [:= :keyword] literal schemas."
  (mt/transformer
   {:decoders
    {:enum
     {:compile
      (fn [schema _options]
        (let [enum-values (set (mc/children schema))]
          (fn [value]
            (cond
              (keyword? value) value
              (string? value) (let [kw (keyword value)]
                                (if (contains? enum-values kw) kw value))
              :else value))))}
     :=
     {:compile
      (fn [schema _options]
        (let [expected (first (mc/children schema))]
          (fn [value]
            (cond
              (= value expected) value
              (and (keyword? expected)
                   (string? value)
                   (= (name expected) value))
              expected
              :else value))))}}}))

(def db-decoding-transformer
  "Composite transformer for decoding DB JSON to application data.

  Transforms:
  - String keys in :map schemas to kebab-case keywords (via key-transformer)
  - String enum values to keywords (via enum-value-transformer)

  Does NOT transform keys in :map-of schemas, preserving data value keys
  like player IDs and hex positions as their original type.

  For multi schemas, use [[db-dispatch]] for the dispatch function."
  (mt/transformer
   (mt/key-transformer {:decode decode-key :types #{:map}})
   enum-value-transformer))

(def ^:private db-encode-key-transformer
  "Transforms kebab-case keyword keys to snake_case strings when encoding for DB."
  (mt/key-transformer
   {:encode (fn [k]
              (if (keyword? k)
                (csk/->snake_case_string (name k))
                k))}))

(def db-encoding-transformer
  "Composite transformer for encoding application data to DB JSON.

  Transforms kebab-case keyword keys to snake_case strings."
  (mt/transformer
   db-encode-key-transformer))

(defn- with-db-dispatch
  "Transforms a schema to use db-dispatch for multi schema dispatch.

  Walks the schema and replaces any `:multi` schema with a keyword `:dispatch`
  property with one that uses [[db-dispatch]] instead. This allows shared CLJC
  schemas to work correctly with DB JSON decoding without modification."
  [schema]
  (mc/walk schema
           (fn [s _ children _]
             (if (and (= :multi (mc/type s))
                      (keyword? (-> s mc/properties :dispatch)))
               (let [dispatch-key (-> s mc/properties :dispatch)
                     props        (assoc (mc/properties s) :dispatch (db-dispatch dispatch-key))]
                 (mc/into-schema :multi props children (mc/options s)))
               (mc/into-schema (mc/type s) (mc/properties s) children (mc/options s))))
           {::mc/walk-schema-refs true
            ::mc/walk-refs true}))

(defn decode
  "Decodes database JSON to application data using a Malli schema.

  Takes raw JSON data (with string keys) from the database and transforms
  it according to the provided schema. String keys in :map schemas become
  kebab-case keywords, while keys in :map-of schemas are preserved as-is.

  Multi schemas with keyword dispatch are automatically wrapped with
  [[db-dispatch]] to handle both string and keyword keys/values from the
  database. This means shared CLJC schemas work without modification.

  Returns nil if data is nil."
  [data schema]
  (when data
    (mc/decode (with-db-dispatch schema) data db-decoding-transformer)))

(defn encode
  "Encodes application data to database JSON format using a Malli schema.

  Takes application data (with keyword keys) and transforms it for storage
  in PostgreSQL JSON columns. Kebab-case keyword keys become snake_case strings.

  Returns nil if data is nil."
  [data schema]
  (when data
    (mc/encode schema data db-encoding-transformer)))

(defn keywordize-keys
  "Recursively converts string keys to keywords in a data structure.

  Use for simple JSON columns that don't require schema-driven transforms.
  For complex nested structures with special key handling, use [[decode]]
  with a Malli schema instead."
  [x]
  (walk/keywordize-keys x))
