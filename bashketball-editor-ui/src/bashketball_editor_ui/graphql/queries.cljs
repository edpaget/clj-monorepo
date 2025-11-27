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
        githubLogin
        email
        avatarUrl
        name
      }
    }
  "))

(def CARDS_QUERY
  "Query for listing cards, optionally filtered by set."
  (apollo/gql "
    query Cards($setId: ID, $cardType: String) {
      cards(setId: $setId, cardType: $cardType) {
        data {
          slug
          name
          cardType
          setId
          updatedAt
        }
      }
    }
  "))

(def CARD_QUERY
  "Query for a single card by slug and setId."
  (apollo/gql "
    query Card($slug: String!, $setSlug: String!) {
      card(slug: $slug, setSlug: $setSlug) {
        slug
        name
        cardType
        setId
        imagePrompt
        createdAt
        updatedAt
      }
    }
  "))

(def CARD_SETS_QUERY
  "Query for list sets."
  (apollo/gql "
    query CardSets {
      cardSets {
        data {
          slug
          name
          createdAt
          updatedAt
        }
      }
    }
"))

(def CARD_SET_QUERY
  "Query for getting a single set by its slug"
  (apollo/gql "
    query CardSets {
      cardSets {
        slug
        name
        createdAt
        updatedAt
      }
    }
"))
