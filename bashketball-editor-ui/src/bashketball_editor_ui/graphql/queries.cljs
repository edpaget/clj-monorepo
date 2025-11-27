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
    query Cards($setSlug: String, $cardType: String) {
      cards(setSlug: $setSlug, cardType: $cardType) {
        data {
          ... on PlayerCard {
            slug
            setSlug
            name
            updatedAt
          }
          ... on PlayCard {
            slug
            setSlug
            name
            updatedAt
          }
          ... on AbilityCard {
            slug
            setSlug
            name
            updatedAt
          }
          ... on SplitPlayCard {
            slug
            setSlug
            name
            updatedAt
          }
          ... on CoachingCard {
            slug
            setSlug
            name
            updatedAt
          }
          ... on TeamAssetCard {
            slug
            setSlug
            name
            updatedAt
          }
          ... on StandardActionCard {
            slug
            setSlug
            name
            updatedAt
          }
        }
      }
    }
  "))

(def CARD_QUERY
  "Query for a single card by slug and setSlug."
  (apollo/gql "
    query Card($slug: String!, $setSlug: String!) {
      card(slug: $slug, setSlug: $setSlug) {
        slug
        name
        cardType
        setSlug
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
    query CardSet($slug: String!) {
      cardSet(slug: $slug) {
        slug
        name
        createdAt
        updatedAt
      }
    }
"))

;; -----------------------------------------------------------------------------
;; Mutations
;; -----------------------------------------------------------------------------

(def PULL_FROM_REMOTE_MUTATION
  "Mutation to pull changes from the remote Git repository."
  (apollo/gql "
    mutation PullFromRemote {
      pullFromRemote {
        status
        message
        error
        conflicts
      }
    }
"))

(def PUSH_TO_REMOTE_MUTATION
  "Mutation to push changes to the remote Git repository."
  (apollo/gql "
    mutation PushToRemote {
      pushToRemote {
        status
        message
        error
        conflicts
      }
    }
"))
