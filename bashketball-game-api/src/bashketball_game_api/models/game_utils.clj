(ns bashketball-game-api.models.game-utils
  "Game state transition helpers.

  Provides convenience functions for common game operations that compose
  multiple field updates. These functions orchestrate repository calls
  for complex state transitions."
  (:require
   [bashketball-game-api.models.protocol :as proto]))

(defn start-game!
  "Transitions a game to ACTIVE status with player2 joining.

  Sets player2 details, initial game state, current player, and timestamps
  the game start. Returns the updated game."
  [game-repo game-id player2-id player2-deck-id initial-state current-player-id]
  (proto/update! game-repo game-id
                 {:player-2-id player2-id
                  :player-2-deck-id player2-deck-id
                  :status :game-status/ACTIVE
                  :game-state initial-state
                  :current-player-id current-player-id
                  :started-at (java.time.Instant/now)}))

(defn end-game!
  "Transitions a game to COMPLETED or ABANDONED status.

  Sets the final status, winner, and timestamps the game end.
  Returns the updated game."
  [game-repo game-id status winner-id]
  (proto/update! game-repo game-id
                 {:status status
                  :winner-id winner-id
                  :ended-at (java.time.Instant/now)}))
