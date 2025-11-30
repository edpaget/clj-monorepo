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

;; ---------------------------------------------------------------------------
;; Game Mutations

(def CREATE_GAME_MUTATION
  "Mutation to create a new game with selected deck."
  (apollo/gql "
    mutation CreateGame($deckId: Uuid!) {
      createGame(deckId: $deckId) {
        id
        player1Id
        status
        createdAt
      }
    }
  "))

(def JOIN_GAME_MUTATION
  "Mutation to join an existing game as player 2."
  (apollo/gql "
    mutation JoinGame($gameId: Uuid!, $deckId: Uuid!) {
      joinGame(gameId: $gameId, deckId: $deckId) {
        id
        player1Id
        player2Id
        status
        startedAt
      }
    }
  "))

(def LEAVE_GAME_MUTATION
  "Mutation to leave a waiting game."
  (apollo/gql "
    mutation LeaveGame($gameId: Uuid!) {
      leaveGame(gameId: $gameId)
    }
  "))

(def FORFEIT_GAME_MUTATION
  "Mutation to forfeit an active game."
  (apollo/gql "
    mutation ForfeitGame($gameId: Uuid!) {
      forfeitGame(gameId: $gameId) {
        id
        status
        winnerId
      }
    }
  "))

(def SUBMIT_ACTION_MUTATION
  "Mutation to submit a game action."
  (apollo/gql "
    mutation SubmitAction($gameId: Uuid!, $action: ActionInput!) {
      submitAction(gameId: $gameId, action: $action) {
        success
        gameId
        error
      }
    }
  "))
