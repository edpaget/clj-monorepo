(ns bashketball-editor-api.graphql.schema
  "GraphQL schema definition.

  Combines resolvers from all resolver namespaces."
  (:require
   [bashketball-editor-api.graphql.resolvers.card :as card]
   [bashketball-editor-api.graphql.resolvers.cardset :as cardset]
   [bashketball-editor-api.graphql.resolvers.mutation :as mutation]
   [bashketball-editor-api.graphql.resolvers.query :as query]
   [bashketball-editor-api.graphql.resolvers.user :as user]))

(defn resolver-map
  "Returns the resolver map for GraphQL.

  Merges resolvers from query, mutation, user, card, and cardset namespaces."
  []
  (merge query/resolvers
         mutation/resolvers
         user/resolvers
         card/resolvers
         cardset/resolvers))
