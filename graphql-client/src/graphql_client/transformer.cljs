(ns graphql-client.transformer
  "Malli transformers for GraphQL response decoding.

  Provides composable transformers for:
  - camelCase to kebab-case key conversion
  - GraphQL enum strings to namespaced keywords
  - Default value application

  Uses Malli's transformer compilation for efficient schema-driven decoding."
  (:require
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [malli.core :as m]
   [malli.transform :as mt]))

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

(def kebab-key-transformer
  "Malli transformer for map keys.

  Decodes keys from camelCase to kebab-case keywords, preserving __typename
  and uppercase keys (like HOME, AWAY)."
  (mt/key-transformer
   {:decode preserve-typename-kebab}))

(def enum-transformer
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

(def decoding-transformer
  "Composite transformer for decoding GraphQL responses.

  Order of operations:
  1. Transform map keys (camelCase to kebab-case)
  2. Transform enum values (string to namespaced keyword)
  3. Apply default values for missing fields

  Apps can extend this transformer by composing additional transformers
  for app-specific types (like tuples or map-of with special keys)."
  (mt/transformer
   kebab-key-transformer
   enum-transformer
   mt/default-value-transformer))

(defn decode
  "Decodes a value according to the given Malli schema.

  Applies the [[decoding-transformer]] to transform:
  - camelCase keys to kebab-case keywords
  - Enum strings to namespaced keywords
  - Default values for missing fields"
  [value schema]
  (m/decode schema value decoding-transformer))

(defn decode-with-transformer
  "Decodes a value with a custom transformer.

  Allows apps to use their own composite transformer that extends
  the base [[decoding-transformer]] with app-specific transformers."
  [value schema transformer]
  (m/decode schema value transformer))
