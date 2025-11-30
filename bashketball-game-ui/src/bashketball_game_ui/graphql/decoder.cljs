(ns bashketball-game-ui.graphql.decoder
  "Malli decoder for transforming GraphQL responses into Clojure data.

  Performs the inverse of the graphql-server encoder:
  - Converts camelCase keys to kebab-case keywords
  - Converts enum strings to namespaced keywords
  - Converts JS objects/arrays to Clojure maps/vectors"
  (:require
   [camel-snake-kebab.core :as csk]
   [malli.core :as m]
   [malli.transform :as mt]))

(def ^:private enum-transformer
  "Decodes string enum values to namespaced keywords.

  For `:enum` schemas with namespaced keyword options, converts string values
  like `\"PLAYER_CARD\"` to `:card-type/PLAYER_CARD`."
  (mt/transformer
   {:decoders
    {:enum
     {:compile
      (fn [schema _options]
        (let [enum-options   (m/children schema)
              enum-namespace (when-let [first-option (first enum-options)]
                               (when (keyword? first-option)
                                 (namespace first-option)))]
          (fn [value]
            (if (and enum-namespace (string? value))
              (let [kw-value (keyword enum-namespace value)]
                (if (some #(= kw-value %) enum-options)
                  kw-value
                  value))
              value))))}}}))

(def ^:private key-transformer
  "Decodes map keys from camelCase strings to kebab-case keywords."
  (mt/key-transformer
   {:decode (fn [k]
              (csk/->kebab-case-keyword (name k)))}))

(def ^:private js->clj-transformer
  "Converts JS objects and arrays to Clojure data structures.

  Handles the conversion of Apollo Client response objects to native
  Clojure maps and vectors before other transformations are applied."
  (mt/transformer
   {:decoders
    {:map
     {:enter (fn [value]
               (if (object? value)
                 (js->clj value)
                 value))}
     :vector
     {:enter (fn [value]
               (if (array? value)
                 (js->clj value)
                 value))}}}))

(def decoding-transformer
  "Composite transformer for decoding GraphQL responses.

  Transforms Apollo Client responses into idiomatic Clojure data:
  1. Convert JS objects/arrays to Clojure data structures
  2. Transform map keys from camelCase to kebab-case keywords
  3. Transform enum strings to namespaced keywords"
  (mt/transformer
   js->clj-transformer
   key-transformer
   enum-transformer))

(defn decode
  "Decodes a GraphQL response value using the given Malli schema.

  Transforms the JS object returned by Apollo Client into a Clojure map
  with kebab-case keys and properly namespaced enum keywords.

  Example:
    (decode card-schema/Card apollo-card)
    ;=> {:slug \"player-1\"
    ;    :name \"Star Player\"
    ;    :card-type :card-type/PLAYER_CARD
    ;    ...}"
  [schema value]
  (m/decode schema value decoding-transformer))

(defn decode-seq
  "Decodes a sequence of GraphQL response values using the given schema.

  Convenience function for decoding arrays of items like query results."
  [schema values]
  (mapv #(decode schema %) values))
