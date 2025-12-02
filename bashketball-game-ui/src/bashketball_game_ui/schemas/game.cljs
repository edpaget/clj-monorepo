(ns bashketball-game-ui.schemas.game
  "API-level schemas for game data from GraphQL.

  Game state schemas are imported from bashketball-game.schema.
  API-level schemas (Game, GameSummary, GameUser, GameStatus) are
  imported from bashketball-schemas for consistency with the API."
  (:require [bashketball-game.schema :as game-schema]
            [bashketball-schemas.enums :as enums]
            [bashketball-schemas.game :as game-schemas]))

(def GameStatus
  "API game status. Re-exported from [[bashketball-schemas.enums]]."
  enums/GameStatus)

(def GameUser
  "Minimal user info for game displays. Re-exported from [[bashketball-schemas.game]]."
  game-schemas/GameUser)

(def GameSummary
  "Game summary for list views. Re-exported from [[bashketball-schemas.game]]."
  game-schemas/GameSummary)

(def Game
  "Full game including game engine state. Re-exported from [[bashketball-schemas.game]]."
  game-schemas/Game)

;; Re-export commonly used schemas from bashketball-game
(def GameState game-schema/GameState)
(def Phase game-schema/Phase)
(def Team game-schema/Team)
(def Action game-schema/Action)
(def Ball game-schema/Ball)
(def Board game-schema/Board)
(def GamePlayer game-schema/GamePlayer)
(def BasketballPlayer game-schema/BasketballPlayer)
