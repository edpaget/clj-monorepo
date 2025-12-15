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

(defn- parse-tuple-key
  "Parses a stringified tuple key back to a vector.

  Handles keys like \"[2 3]\" or \"[2, 3]\" returning [2 3].
  Returns nil if the string doesn't match the expected format."
  [s]
  (when (string? s)
    (let [trimmed (str/trim s)]
      (when (and (str/starts-with? trimmed "[")
                 (str/ends-with? trimmed "]"))
        (let [inner (-> trimmed
                        (subs 1 (dec (count trimmed)))
                        str/trim)]
          (when-not (str/blank? inner)
            (let [parts (str/split inner #"[,\s]+")]
              (when (every? #(re-matches #"-?\d+" %) parts)
                (mapv #(Long/parseLong %) parts)))))))))

(defn- namespaced-enum-key?
  "Returns true if the string looks like a namespaced enum key.

  Matches patterns like \"team/HOME\", \"phase/ACTIONS\" where the namespace
  is lowercase and the value is uppercase."
  [s]
  (when (string? s)
    (when-let [[_ ns-part val-part] (re-matches #"([a-z][a-z0-9-]*)/([A-Z][A-Z0-9_]*)" s)]
      (and ns-part val-part))))

(defn- decode-key
  "Decodes a JSON string key to a keyword.

  - Namespaced enum keys (team/HOME) -> namespaced keywords :team/HOME
  - Uppercase keys (HOME, AWAY) -> uppercase keywords
  - Field name keys (snake_case) -> kebab-case keywords"
  [k]
  (cond
    (keyword? k) k
    (namespaced-enum-key? k) (keyword k)
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

(defn- keyword-matches-string?
  "Returns true if keyword matches string representation.

  Handles both namespaced and non-namespaced keywords:
  - :team/HOME matches \"team/HOME\" or \"HOME\"
  - :ACTIVE matches \"ACTIVE\""
  [kw s]
  (or (= (name kw) s)
      (and (namespace kw)
           (= (str (namespace kw) "/" (name kw)) s))))

(def ^:private enum-value-transformer
  "Transforms enum values from strings to keywords during decode.

  Handles [:enum ...] schemas and [:= :keyword] literal schemas.
  For namespaced keywords like :team/HOME, matches both \"team/HOME\" and \"HOME\"."
  (mt/transformer
   {:decoders
    {:enum
     {:compile
      (fn [schema _options]
        (let [enum-values (mc/children schema)]
          (fn [value]
            (cond
              (keyword? value) value
              (string? value)
              (or (some #(when (keyword-matches-string? % value) %) enum-values)
                  value)
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
                   (keyword-matches-string? expected value))
              expected
              :else value))))}}}))

(defn- int-tuple-schema?
  "Returns true if the schema is [:tuple :int :int]."
  [schema]
  (and (= :tuple (mc/type schema))
       (let [children (mc/children schema)]
         (and (= 2 (count children))
              (every? #(= :int (mc/type %)) children)))))

(defn- transform-tuple-keys
  "Transforms string keys that look like tuples into vectors."
  [m]
  (if (map? m)
    (into {}
          (map (fn [[k v]]
                 (if-let [parsed (parse-tuple-key k)]
                   [parsed v]
                   [k v]))
               m))
    m))

(def ^:private map-of-tuple-key-transformer
  "Transforms map-of keys from strings to vectors when key schema is [:tuple :int :int].

  JSON serialization converts vector keys like [2 3] to strings \"[2 3]\".
  This transformer parses them back to vectors during decode."
  (mt/transformer
   {:decoders
    {:map-of
     {:compile
      (fn [schema _options]
        (let [key-schema (first (mc/children schema))]
          (when (int-tuple-schema? key-schema)
            transform-tuple-keys)))}}}))

(def db-decoding-transformer
  "Composite transformer for decoding DB JSON to application data.

  Transforms:
  - String keys in :map schemas to kebab-case keywords (via key-transformer)
  - String enum values to keywords (via enum-value-transformer)
  - Stringified tuple keys in :map-of schemas back to vectors (via map-of-tuple-key-transformer)

  Preserves other :map-of keys (like player IDs) as their original type.

  For multi schemas, use [[db-dispatch]] for the dispatch function."
  (mt/transformer
   (mt/key-transformer {:decode decode-key :types #{:map}})
   enum-value-transformer
   map-of-tuple-key-transformer))

(defn- encode-key
  "Encodes a map key for database JSON storage.

  - Namespaced uppercase keywords (e.g., :team/HOME) → \"team/HOME\" (preserved)
  - Regular keywords (e.g., :player-id) → \"player_id\" (snake_case)"
  [k]
  (cond
    (not (keyword? k)) k
    (and (namespace k) (uppercase-string? (name k)))
    (str (namespace k) "/" (name k))
    :else
    (csk/->snake_case_string (name k))))

(def ^:private db-encode-key-transformer
  "Transforms keyword keys to strings when encoding for DB.

  Preserves namespaced uppercase keywords (like :team/HOME) as \"team/HOME\".
  Converts regular kebab-case keywords to snake_case strings."
  (mt/key-transformer {:encode encode-key}))

(def ^:private enum-value-encoder
  "Transforms keyword enum values to strings for DB storage.

  Preserves namespace information for namespaced keywords:
  - :team/HOME → \"team/HOME\"
  - :ACTIVE → \"ACTIVE\""
  (mt/transformer
   {:encoders
    {:enum
     (fn [value]
       (when (keyword? value)
         (if (namespace value)
           (str (namespace value) "/" (name value))
           (name value))))
     :=
     (fn [value]
       (cond
         (not (keyword? value)) value
         (namespace value) (str (namespace value) "/" (name value))
         :else (name value)))}}))

(def db-encoding-transformer
  "Composite transformer for encoding application data to DB JSON.

  Transforms:
  - Kebab-case keyword keys to snake_case strings
  - Namespaced keyword keys (like :team/HOME) preserved as \"team/HOME\"
  - Enum values to their string representations with namespace preserved"
  (mt/transformer
   enum-value-encoder
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
