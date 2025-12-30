(ns bashketball-game-ui.graphql.decoder
  "Automatic GraphQL response decoder using __typename dispatch.

  Uses postwalk to traverse response data and apply Malli-based decoding
  to each object based on its GraphQL __typename field. This enables
  automatic decoding of polymorphic types and nested structures.

  Primary API:
  - [[decode-response]] - Decodes using __typename dispatch (recommended)
  - [[decode-js-response]] - Decodes JS responses with __typename dispatch

  Legacy API (for backward compatibility):
  - [[decode]] - Decodes with explicit schema
  - [[decode-seq]] - Decodes sequence with explicit schema"
  (:require
   [bashketball-game-ui.graphql.registry :as registry]
   [bashketball-game-ui.graphql.transformer :as transformer]
   [goog.object :as gobj]
   [graphql-client.core :as gql-core]
   [graphql-client.decoder :as gql-decoder]
   [malli.core :as m]))

;; -----------------------------------------------------------------------------
;; App-specific graphql/name mappings

(def ^:private graphql-name-mappings
  "Reverse mapping from GraphQL wire names to schema keys for types with :graphql/name.

  Maps from [typename graphql-name] -> schema-key for all types that use
  :graphql/name overrides."
  {["Players" "HOME"] :team/HOME
   ["Players" "AWAY"] :team/AWAY
   ["Score" "HOME"]   :team/HOME
   ["Score" "AWAY"]   :team/AWAY})

(defn- reverse-graphql-names
  "Converts GraphQL wire name keys back to schema keys based on __typename.

  For maps with a __typename that has :graphql/name overrides (like Players
  and Score), converts keys like :HOME/:AWAY to :team/HOME/:team/AWAY."
  [x]
  (cond
    (and (map? x) (gql-core/get-typename x))
    (let [typename (gql-core/get-typename x)]
      (into {}
            (map (fn [[k v]]
                   (let [k-str   (cond
                                   (keyword? k) (name k)
                                   (string? k)  k
                                   :else        nil)
                         new-key (if k-str
                                   (get graphql-name-mappings [typename k-str] k)
                                   k)]
                     [new-key (reverse-graphql-names v)])))
            x))

    (map? x)
    (into {} (map (fn [[k v]] [k (reverse-graphql-names v)])) x)

    (vector? x)
    (mapv reverse-graphql-names x)

    :else x))

(defn build-decode-fn
  "Returns a decoder function using the given typename->schema mappings.

  Uses schema-driven transformation:
  1. Postwalk applies Malli decoding to maps with __typename
  2. Malli's transformers handle :map keys and :map-of keys per schema
  3. Reverse :graphql/name overrides (HOME -> :team/HOME)
  4. Final pass converts remaining string keys (wrapper maps without __typename)"
  [type-mappings]
  (let [base-decode-fn (gql-decoder/build-decode-fn type-mappings transformer/decoding-transformer)]
    (fn [data]
      (->> data
           base-decode-fn
           reverse-graphql-names))))

(def decode-response
  "Decodes a GraphQL response using __typename dispatch.

  Uses the global typename registry to look up schemas and apply appropriate
  transformations. This is the recommended way to decode GraphQL responses.

  Handles:
  - camelCase → kebab-case key conversion (for :map schemas)
  - Enum strings → namespaced keywords
  - HexPosition strings → vectors
  - Map-of string keys → vector keys (for :map-of schemas with tuple keys)
  - Nested objects with different __typename values

  The input should be a Clojure map with string keys (from [[js->clj-preserve-keys]])
  so that Malli can apply schema-appropriate transformations."
  (build-decode-fn registry/typename->schema))

(defn decode-js-response
  "Decodes a JS GraphQL response using __typename dispatch.

  Converts JS objects/arrays to Clojure maps/vectors while preserving string
  keys, then applies schema-driven decoding. The schema determines how keys
  are transformed:
  - `:map` keys are converted to kebab-case keywords
  - `:map-of` keys are decoded according to their key schema (e.g., position
    strings like \"0,1\" become vectors [0 1])"
  [js-value]
  (-> js-value
      gql-core/js->clj-preserve-keys
      decode-response))

;; -----------------------------------------------------------------------------
;; Legacy API (backward compatibility)

(defn- collect-enums
  "Recursively collects all enum schemas from a Malli schema.

  Returns a map of {string-value -> namespaced-keyword} for all enums
  that have namespaced keyword options."
  [schema]
  (let [result (atom {})]
    (m/walk schema
            (fn [s _path _children _options]
              (when (= :enum (m/type s))
                (let [options (m/children s)]
                  (when (and (seq options)
                             (keyword? (first options))
                             (namespace (first options)))
                    (doseq [opt options]
                      (when (keyword? opt)
                        (swap! result assoc (name opt) opt))))))
              s))
    @result))

(defn- transform-value
  "Transforms a value using the enum map if it's a matching string."
  [enum-map value]
  (if (string? value)
    (get enum-map value value)
    value))

(defn- js->clj-decoded
  "Converts a JavaScript value to Clojure with kebab-case keywords and decoded enums.

  Preserves uppercase keys like HOME and AWAY that have :graphql/name overrides."
  [enum-map x]
  (cond
    (object? x)
    (into {}
          (map (fn [k]
                 [(gql-core/convert-key k)
                  (js->clj-decoded enum-map (gobj/get x k))]))
          (js-keys x))

    (array? x)
    (mapv #(js->clj-decoded enum-map %) x)

    (string? x)
    (transform-value enum-map x)

    :else x))

(defn- clj->clj-decoded
  "Transforms a Clojure data structure, converting enum strings to keywords."
  [enum-map x]
  (cond
    (map? x)
    (into {}
          (map (fn [[k v]]
                 [k (clj->clj-decoded enum-map v)]))
          x)

    (vector? x)
    (mapv #(clj->clj-decoded enum-map %) x)

    (sequential? x)
    (map #(clj->clj-decoded enum-map %) x)

    (string? x)
    (transform-value enum-map x)

    :else x))

(defn decode
  "Decodes a GraphQL response value using the given Malli schema.

  DEPRECATED: Use [[decode-response]] for automatic __typename-based decoding.

  Extracts enum definitions from the schema, then converts the value:
  1. JS objects/arrays become Clojure maps/vectors
  2. camelCase keys become kebab-case keywords
  3. Enum string values become namespaced keywords"
  [schema value]
  (let [enum-map (collect-enums schema)]
    (if (object? value)
      (js->clj-decoded enum-map value)
      (clj->clj-decoded enum-map value))))

(defn decode-seq
  "Decodes a sequence of GraphQL response values using the given schema.

  DEPRECATED: Use [[decode-response]] for automatic __typename-based decoding.

  Convenience function for decoding arrays of items like query results."
  [schema values]
  (let [enum-map (collect-enums schema)]
    (if (array? values)
      (mapv #(js->clj-decoded enum-map %) values)
      (mapv #(clj->clj-decoded enum-map %) values))))
