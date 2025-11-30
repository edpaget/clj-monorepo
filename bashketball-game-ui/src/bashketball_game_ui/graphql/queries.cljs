(ns bashketball-game-ui.graphql.queries
  "GraphQL query definitions.

  Provides GraphQL queries for the bashketball-game-api."
  (:require
   ["@apollo/client" :as apollo]))

(def ME_QUERY
  "Query for the current authenticated user."
  (apollo/gql "
    query Me {
      me {
        id
        email
        name
        avatarUrl
      }
    }
  "))
