(ns bashketball-game-ui.graphql.transformer
  "Malli transformers for GraphQL response decoding.

  Extends the base [[graphql-client.transformer]] with app-specific transformers:
  - HexPosition EDN string parsing (\"[0 1]\" → [0 1])
  - Map-of tuple key parsing for position-keyed maps

  Uses Malli's transformer compilation for efficient schema-driven decoding."
  (:require
   [clojure.edn :as edn]
   [graphql-client.transformer :as gql-transformer]
   [malli.core :as m]
   [malli.transform :as mt]))

(defn- parse-hex-position
  "Parses EDN string to vector of integers.
  \"[0 1]\" → [0 1]"
  [s]
  (when (string? s)
    (edn/read-string s)))

;; -----------------------------------------------------------------------------
;; App-specific transformers

(def ^:private tuple-transformer
  "Malli transformer for tuple types with :graphql/scalar property.

  Decodes HexPosition EDN strings (\"[0 1]\") back to vectors ([0 1])."
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
  arrive as EDN strings (\"[0 1]\") and need to be parsed back to vectors."
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

(def ^:private or-scalar-transformer
  "Malli transformer for :or schemas with :graphql/scalar property.

  Decodes EDN-serialized scalars like PolicyExpr and SkillTestTarget.
  These arrive as EDN strings and need to be parsed back to Clojure data:
  - PolicyExpr: `\"[:= :doc/phase :phase/PLAY]\"` → `[:= :doc/phase :phase/PLAY]`
  - SkillTestTarget: `\"[0 1]\"` → `[0 1]` or `\"player-1\"` → `\"player-1\"`"
  (mt/transformer
   {:decoders
    {:or
     {:compile
      (fn [schema _opts]
        (when (-> schema m/properties :graphql/scalar)
          (fn [value]
            (if (string? value)
              (edn/read-string value)
              value))))}}}))

(def decoding-transformer
  "Composite transformer for decoding GraphQL responses.

  Extends [[graphql-client.transformer/decoding-transformer]] with app-specific
  transformers for tuple, map-of, and :or scalar types.

  Order of operations:
  1. Transform map keys (camelCase → kebab-case) - from library
  2. Transform enum values (string → namespaced keyword) - from library
  3. Transform tuple values (string → vector) for :graphql/scalar tuples
  4. Transform map-of keys (string → vector) for tuple-keyed maps
  5. Transform :or scalars (string → EDN) for PolicyExpr, SkillTestTarget
  6. Apply default values for missing fields - from library

  Note: :graphql/name reverse mapping (HOME → :team/HOME) is handled
  separately by [[bashketball-game-ui.graphql.decoder/reverse-graphql-names]]."
  (mt/transformer
   gql-transformer/kebab-key-transformer
   gql-transformer/enum-transformer
   tuple-transformer
   map-of-key-transformer
   or-scalar-transformer
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
