(ns bashketball-editor-api.graphql.schema
  "GraphQL schema definition.

  Combines resolvers from all resolver namespaces."
  (:require
   [bashketball-editor-api.graphql.resolvers.mutation :as mutation]
   [bashketball-editor-api.graphql.resolvers.query :as query]))

(defn resolver-map
  "Returns the resolver map for GraphQL.

  Merges resolvers from query and mutation namespaces."
  []
  (merge query/resolvers
         mutation/resolvers))
