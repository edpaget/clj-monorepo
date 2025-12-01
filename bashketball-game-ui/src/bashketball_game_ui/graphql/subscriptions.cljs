(ns bashketball-game-ui.graphql.subscriptions
  "GraphQL subscription definitions.

  Defines subscriptions for real-time game and lobby updates.
  These are used with the SSE link to receive server-pushed events."
  (:require
   ["@apollo/client" :as apollo]))

(def GAME_UPDATED_SUBSCRIPTION
  "Subscription for real-time game state updates.

  Receives events when:
  - Game state changes (actions applied)
  - Player joins the game
  - Game starts or ends"
  (apollo/gql "
    subscription GameUpdated($gameId: ID!) {
      gameUpdated(gameId: $gameId) {
        type
        game {
          id
          player1Id
          player2Id
          status
          gameState
          winnerId
          createdAt
          startedAt
        }
        event {
          type
          playerId
          timestamp
        }
      }
    }
  "))

(def LOBBY_UPDATED_SUBSCRIPTION
  "Subscription for lobby updates.

  Receives events when:
  - New game is created (waiting for opponent)
  - Game is filled (no longer available)
  - Game is cancelled"
  (apollo/gql "
    subscription LobbyUpdated {
      lobbyUpdated {
        type
        game {
          id
          player1Id
          status
          createdAt
        }
      }
    }
  "))
