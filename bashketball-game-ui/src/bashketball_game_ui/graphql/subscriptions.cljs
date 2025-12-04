(ns bashketball-game-ui.graphql.subscriptions
  "GraphQL subscription definitions.

  Defines subscriptions for real-time game and lobby updates.
  These are used with the SSE link to receive server-pushed events."
  (:require
   ["@apollo/client" :as apollo]))

(def GAME_UPDATED_SUBSCRIPTION
  "Subscription for real-time game state updates.

  Receives event notifications when:
  - Connection established (type: connected)
  - Game state changes (type: state-changed)
  - Player joins the game (type: player-joined)
  - Game starts or ends (type: game-started, game-ended)

  Note: This returns event metadata only. The client should refetch
  the full game state when receiving state-change events."
  (apollo/gql "
    subscription GameUpdated($gameId: Uuid!) {
      gameUpdated(gameId: $gameId) {
        id
        type
        gameId
        playerId
        winnerId
        reason
        userId
      }
    }
  "))

(def LOBBY_UPDATED_SUBSCRIPTION
  "Subscription for lobby updates.

  Receives event notifications when:
  - Connection established (type: connected)
  - New game is created (type: game-created)
  - Game is started/filled (type: game-started)
  - Game is cancelled (type: game-cancelled)

  Note: This returns event metadata only. The client should refetch
  the games list when receiving updates."
  (apollo/gql "
    subscription LobbyUpdated {
      lobbyUpdated {
        type
        gameId
        userId
      }
    }
  "))
