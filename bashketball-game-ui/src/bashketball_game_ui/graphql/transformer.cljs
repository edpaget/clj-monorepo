(ns bashketball-game-ui.graphql.transformer
  "Malli transformers for GraphQL response decoding.

  Provides composable transformers for:
  - camelCase → kebab-case key conversion
  - GraphQL enum strings → namespaced keywords
  - HexPosition string parsing (\"0,1\" → [0 1])
  - Default value application

  Uses Malli's transformer compilation for efficient schema-driven decoding."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [malli.core :as m]
   [malli.transform :as mt]))

(defn- parse-hex-position
  "Parses comma-separated string to vector of integers.
  \"0,1\" → [0 1]"
  [s]
  (when (string? s)
    (mapv js/parseInt (str/split s #","))))

(def ^:private enum-transformer
  "Malli transformer for enum values.

  Decodes GraphQL enum strings to namespaced keywords by inferring the namespace
  from the first enum option. Only converts if the value matches a valid option."
  (mt/transformer
   {:decoders
    {:enum
     {:compile
      (fn [schema _opts]
        (let [enum-options   (m/children schema)
              enum-namespace (when-let [first-opt (first enum-options)]
                               (when (keyword? first-opt)
                                 (namespace first-opt)))]
          (fn [value]
            (cond
              (and enum-namespace (string? value))
              (let [kw-value (keyword enum-namespace value)]
                (if (some #(= kw-value %) enum-options)
                  kw-value
                  value))

              (string? value)
              (keyword value)

              :else
              value))))}}}))

(defn- uppercase-key?
  "Returns true if key is all uppercase (e.g., \"HOME\", \"AWAY\", :HOME, :AWAY).

  Used to preserve keys that have explicit :graphql/name overrides in the schema."
  [k]
  (let [key-str (if (keyword? k) (name k) (str k))]
    (and (not (str/blank? key-str))
         (= key-str (str/upper-case key-str)))))

(defn- preserve-typename-kebab
  "Converts key to kebab-case keyword, preserving __typename and uppercase keys.

  Uppercase keys (e.g., HOME, AWAY) are preserved as-is to match GraphQL schema
  fields with :graphql/name overrides."
  [k]
  (let [key-name (if (keyword? k) (name k) k)]
    (cond
      (= key-name "__typename") :__typename
      (uppercase-key? key-name) (keyword key-name)
      :else                     (csk/->kebab-case-keyword k))))

(def ^:private kebab-key-transformer
  "Malli transformer for map keys.

  Decodes keys from camelCase to kebab-case keywords, preserving __typename."
  (mt/key-transformer
   {:decode preserve-typename-kebab}))

(def ^:private tuple-transformer
  "Malli transformer for tuple types with :graphql/scalar property.

  Decodes HexPosition strings (\"0,1\") back to vectors ([0 1])."
  (mt/transformer
   {:decoders
    {:tuple
     {:compile
      (fn [schema _opts]
        (when (-> schema m/properties :graphql/scalar)
          (fn [value]
            (if (string? value)
              (parse-hex-position value)
              value))))}}}))

(def ^:private map-of-key-transformer
  "Malli transformer for map-of types with tuple keys.

  When a schema is [:map-of [:tuple :int :int] ValueSchema], the keys
  arrive as comma-separated strings and need to be parsed back to vectors."
  (mt/transformer
   {:decoders
    {:map-of
     {:compile
      (fn [schema _opts]
        (let [[key-schema _val-schema] (m/children schema)]
          (when (= :tuple (m/type key-schema))
            (fn [m]
              (if (map? m)
                (into {}
                      (map (fn [[k v]]
                             [(if (string? k)
                                (parse-hex-position k)
                                k)
                              v]))
                      m)
                m)))))}}}))

(def decoding-transformer
  "Composite transformer for decoding GraphQL responses.

  Order of operations:
  1. Transform map keys (camelCase → kebab-case)
  2. Transform enum values (string → namespaced keyword)
  3. Transform tuple values (string → vector) for :graphql/scalar tuples
  4. Transform map-of keys (string → vector) for tuple-keyed maps
  5. Apply default values for missing fields"
  (mt/transformer
   kebab-key-transformer
   enum-transformer
   tuple-transformer
   map-of-key-transformer
   mt/default-value-transformer))

(defn decode
  "Decodes a value according to the given Malli schema.

  Applies the [[decoding-transformer]] to transform:
  - camelCase keys to kebab-case keywords
  - Enum strings to namespaced keywords
  - HexPosition strings to vectors
  - Map-of string keys to vector keys"
  [value schema]
  (m/decode schema value decoding-transformer))
