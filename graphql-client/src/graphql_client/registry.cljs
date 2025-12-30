(ns graphql-client.registry
  "Registry utilities for mapping GraphQL __typename to Malli schemas.

  Provides functions for extracting :graphql/type annotations from schemas
  and building typename-to-schema mappings. This enables automatic response
  decoding based on __typename."
  (:require
   [malli.core :as m]))

(defn extract-graphql-type
  "Extracts :graphql/type from a Malli schema's properties.

  Returns the type name as a string (PascalCase), or nil if not annotated.

  Example:
    (extract-graphql-type [:map {:graphql/type :User} [:id :uuid]])
    ;; => \"User\""
  [schema]
  (some-> schema m/properties :graphql/type name))

(defn build-typename-registry
  "Builds typename->schema map from a collection of schemas.

  Extracts :graphql/type annotations and creates a map from GraphQL
  typename strings to their corresponding Malli schemas. Schemas without
  :graphql/type annotations are ignored.

  Example:
    (def User [:map {:graphql/type :User} [:id :uuid]])
    (def Post [:map {:graphql/type :Post} [:title :string]])
    (build-typename-registry [User Post])
    ;; => {\"User\" [:map {...} ...], \"Post\" [:map {...} ...]}"
  [schemas]
  (->> schemas
       (keep (fn [schema]
               (when-let [typename (extract-graphql-type schema)]
                 [typename schema])))
       (into {})))
