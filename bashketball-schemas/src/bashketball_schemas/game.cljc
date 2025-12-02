(ns bashketball-schemas.game
  "API-level game schemas for persistence and GraphQL responses.

   These schemas represent game records as stored in the database and
   returned by the API. For game engine state schemas, see
   [[bashketball-game.schema]].

   The [[Game]] schema uses `:game-state` as an opaque map since the full
   [[bashketball-game.schema/GameState]] would create a circular dependency.
   API layers can compose with the specific GameState type as needed."
  (:require [bashketball-schemas.enums :as enums]))

(def Game
  "Full game record including game engine state.

   Represents the API response format for game data. The `:game-state` field
   contains the game engine state when the game is active, nil for waiting games."
  [:map {:graphql/type :Game}
   [:id :uuid]
   [:player-1-id :uuid]
   [:player-2-id {:optional true} [:maybe :uuid]]
   [:status enums/GameStatus]
   [:game-state {:optional true} [:maybe :map]]
   [:winner-id {:optional true} [:maybe :uuid]]
   [:created-at :string]
   [:started-at {:optional true} [:maybe :string]]])

(def GameSummary
  "Game summary for list views without full game state.

   Used in lobby listings and game history where the full game state
   is not needed."
  [:map {:graphql/type :GameSummary}
   [:id :uuid]
   [:player-1-id :uuid]
   [:player-2-id {:optional true} [:maybe :uuid]]
   [:status enums/GameStatus]
   [:created-at :string]
   [:started-at {:optional true} [:maybe :string]]])

(def GameUser
  "Minimal user info embedded in game responses.

   Used for displaying opponent information in game UIs."
  [:map {:graphql/type :GameUser}
   [:id :uuid]
   [:name {:optional true} [:maybe :string]]
   [:avatar-url {:optional true} [:maybe :string]]])
