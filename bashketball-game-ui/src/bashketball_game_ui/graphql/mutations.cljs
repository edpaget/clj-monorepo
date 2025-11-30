(ns bashketball-game-ui.graphql.mutations
  "GraphQL mutation definitions.

  Provides GraphQL mutations for deck CRUD operations."
  (:require
   ["@apollo/client" :as apollo]))

(def CREATE_DECK_MUTATION
  "Mutation to create a new deck."
  (apollo/gql "
    mutation CreateDeck($name: String!) {
      createDeck(name: $name) {
        id
        name
        cardSlugs
        isValid
        validationErrors
      }
    }
  "))

(def UPDATE_DECK_MUTATION
  "Mutation to update an existing deck."
  (apollo/gql "
    mutation UpdateDeck($id: Uuid!, $name: String, $cardSlugs: [String!]) {
      updateDeck(id: $id, name: $name, cardSlugs: $cardSlugs) {
        id
        name
        cardSlugs
        isValid
        validationErrors
      }
    }
  "))

(def DELETE_DECK_MUTATION
  "Mutation to delete a deck."
  (apollo/gql "
    mutation DeleteDeck($id: Uuid!) {
      deleteDeck(id: $id)
    }
  "))

(def VALIDATE_DECK_MUTATION
  "Mutation to validate a deck server-side."
  (apollo/gql "
    mutation ValidateDeck($id: Uuid!) {
      validateDeck(id: $id) {
        id
        isValid
        validationErrors
      }
    }
  "))
