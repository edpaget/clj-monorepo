(ns bashketball-editor-api.graphql.schema
  "GraphQL schema definition.

  Combines resolvers from all resolver namespaces and compiles them into
  a Lacinia schema."
  (:require
   [bashketball-editor-api.graphql.resolvers.mutation :as mutation]
   [bashketball-editor-api.graphql.resolvers.query :as query]
   [graphql-server.ring :as gql-ring]))

(defn create-schema
  "Creates the compiled GraphQL schema.

  Merges resolvers from query and mutation namespaces and compiles them
  into a Lacinia schema ready for execution."
  []
  (gql-ring/build-lacinia-schema
   (merge query/resolvers
          mutation/resolvers)))
