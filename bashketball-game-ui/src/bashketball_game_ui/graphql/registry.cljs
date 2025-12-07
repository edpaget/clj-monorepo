(ns bashketball-game-ui.graphql.registry
  "Registry mapping GraphQL __typename to Malli schemas.

  Automatically extracts :graphql/type annotations from schemas to build
  the typename->schema mapping. This ensures consistency between API and UI
  and enables automatic response decoding based on __typename."
  (:require
   [bashketball-game.schema :as game-schema]
   [bashketball-schemas.card :as card-schema]
   [bashketball-schemas.game :as game-schemas]
   [malli.core :as m]))

(def User
  "UI schema for User type from GraphQL API.

  Matches the server's User type with kebab-case keys."
  [:map {:graphql/type :User}
   [:id :uuid]
   [:email :string]
   [:name {:optional true} [:maybe :string]]
   [:avatar-url {:optional true} [:maybe :string]]])

(def Deck
  "UI schema for Deck type from GraphQL API."
  [:map {:graphql/type :Deck}
   [:id :uuid]
   [:name :string]
   [:user-id :uuid]
   [:card-slugs [:vector :string]]
   [:cards {:optional true} [:maybe [:vector :any]]]
   [:created-at :string]
   [:updated-at {:optional true} [:maybe :string]]])

(def PageInfo
  "Pagination metadata schema."
  [:map {:graphql/type :PageInfo}
   [:total-count :int]
   [:has-next-page :boolean]
   [:has-previous-page :boolean]])

(def GameConnection
  "Paginated game results schema."
  [:map {:graphql/type :GameConnection}
   [:data [:vector :any]]
   [:page-info PageInfo]])

(def GameActionResult
  "Game action submission result schema."
  [:map {:graphql/type :GameActionResult}
   [:success :boolean]
   [:game-id {:optional true} [:maybe :uuid]]
   [:error {:optional true} [:maybe :string]]
   [:revealed-fate {:optional true} [:maybe :int]]])

(def GameEvent
  "SSE game subscription event schema."
  [:map {:graphql/type :GameEvent}
   [:type :string]
   [:game-id {:optional true} [:maybe :string]]
   [:player-id {:optional true} [:maybe :string]]
   [:winner-id {:optional true} [:maybe :string]]
   [:reason {:optional true} [:maybe :string]]
   [:user-id {:optional true} [:maybe :string]]])

(def LobbyEvent
  "SSE lobby subscription event schema."
  [:map {:graphql/type :LobbyEvent}
   [:type :string]
   [:game-id {:optional true} [:maybe :string]]
   [:user-id {:optional true} [:maybe :string]]])

(defn extract-graphql-type
  "Extracts :graphql/type from a Malli schema's properties.

  Returns the type name as a string (PascalCase), or nil if not annotated."
  [schema]
  (some-> schema m/properties :graphql/type name))

(defn build-typename-registry
  "Builds typename->schema map from a collection of schemas.

  Extracts :graphql/type annotations and creates a map from GraphQL
  typename strings to their corresponding Malli schemas."
  [schemas]
  (->> schemas
       (keep (fn [schema]
               (when-let [typename (extract-graphql-type schema)]
                 [typename schema])))
       (into {})))

(def all-schemas
  "All schemas with :graphql/type annotations.

  Includes game engine schemas, card schemas, API schemas, and UI-only schemas."
  [;; Game engine schemas (from bashketball-game)
   game-schema/GameState
   game-schema/Board
   game-schema/Ball
   game-schema/BallPossessed
   game-schema/BallLoose
   game-schema/BallInAir
   game-schema/PositionTarget
   game-schema/PlayerTarget
   game-schema/GamePlayer
   game-schema/TeamRoster
   game-schema/BasketballPlayer
   game-schema/Tile
   game-schema/Occupant
   game-schema/Score
   game-schema/Players
   game-schema/Modifier
   game-schema/PlayerStats
   game-schema/DeckState
   game-schema/CardInstance
   game-schema/StackEffect
   game-schema/Event
   game-schema/Team
   game-schema/Phase
   game-schema/Terrain
   game-schema/Size
   game-schema/Stat
   game-schema/BallActionType
   game-schema/OccupantType

   ;; API game schemas (from bashketball-schemas)
   game-schemas/Game
   game-schemas/GameSummary
   game-schemas/GameUser

   ;; Card schemas (from bashketball-schemas)
   card-schema/Card
   card-schema/PlayerCard
   card-schema/AbilityCard
   card-schema/PlayCard
   card-schema/StandardActionCard
   card-schema/SplitPlayCard
   card-schema/CoachingCard
   card-schema/TeamAssetCard
   card-schema/CardSet

   ;; UI-only schemas (defined above)
   User
   Deck
   PageInfo
   GameConnection
   GameActionResult
   GameEvent
   LobbyEvent])

(def typename->schema
  "Map from GraphQL __typename to Malli schema.

  Auto-generated from schema annotations. Used by the decoder to dispatch
  on __typename and apply the correct schema transformations."
  (build-typename-registry all-schemas))
