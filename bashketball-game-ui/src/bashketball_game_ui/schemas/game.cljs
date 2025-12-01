(ns bashketball-game-ui.schemas.game
  "API-level schemas for game data from GraphQL.

  Game state schemas are imported from bashketball-game.schema.
  This namespace only defines API-specific wrapper types."
  (:require [bashketball-game.schema :as game-schema]))

(def GameStatus
  "API game status (persistence layer, not game engine phase)."
  [:enum
   :game-status/waiting
   :game-status/active
   :game-status/completed
   :game-status/abandoned])

(def GameUser
  "Minimal user info for game displays."
  [:map
   [:id :uuid]
   [:name {:optional true} [:maybe :string]]
   [:avatar-url {:optional true} [:maybe :string]]])

(def GameSummary
  "Game summary for list views (no full state)."
  [:map
   [:id :uuid]
   [:player-1-id :uuid]
   [:player-2-id {:optional true} [:maybe :uuid]]
   [:status GameStatus]
   [:created-at :string]
   [:started-at {:optional true} [:maybe :string]]])

(def Game
  "Full game including game engine state."
  [:map
   [:id :uuid]
   [:player-1-id :uuid]
   [:player-2-id {:optional true} [:maybe :uuid]]
   [:status GameStatus]
   [:game-state {:optional true} [:maybe :map]]
   [:winner-id {:optional true} [:maybe :uuid]]
   [:created-at :string]
   [:started-at {:optional true} [:maybe :string]]])

;; Re-export commonly used schemas from bashketball-game
(def GameState game-schema/GameState)
(def Phase game-schema/Phase)
(def Team game-schema/Team)
(def Action game-schema/Action)
(def Ball game-schema/Ball)
(def Board game-schema/Board)
(def GamePlayer game-schema/GamePlayer)
(def BasketballPlayer game-schema/BasketballPlayer)
