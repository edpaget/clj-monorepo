(ns bashketball-editor-ui.graphql.queries
  "GraphQL query and mutation definitions.

  Stub queries for the bashketball-editor-api. These will be implemented
  once the backend card API is complete."
  (:require
   ["@apollo/client" :as apollo]))

(def ME_QUERY
  "Query for the current authenticated user."
  (apollo/gql "
    query Me {
      me {
        id
        githubUsername
        displayName
      }
    }
  "))

(def CARDS_QUERY
  "Query for listing cards, optionally filtered by set."
  (apollo/gql "
    query Cards($setId: ID) {
      cards(setId: $setId) {
        id
        name
        cardType
        updatedAt
      }
    }
  "))

(def CARD_QUERY
  "Query for a single card by ID."
  (apollo/gql "
    query Card($id: ID!) {
      card(id: $id) {
        id
        name
        cardType
        attributes
        imagePrompt
        createdAt
        updatedAt
      }
    }
  "))
