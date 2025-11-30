(ns bashketball-game-api.graphql.resolvers.game
  "GraphQL resolvers for game queries and mutations.

  Provides Query resolvers for listing and fetching games, Mutation resolvers
  for game lifecycle operations, and field resolvers for player references."
  (:require
   [bashketball-game-api.services.game :as game-svc]
   [graphql-server.core :refer [defresolver def-resolver-map]]))

(def GameResponse
  "Schema for game data returned by resolvers."
  [:map {:graphql/type :Game}
   [:id :uuid]
   [:player1Id :uuid]
   [:status :string]
   [:createdAt :string]])

(def ActionInput
  "Merged input schema for all game action types.

  Combines all fields from bashketball-game action schemas into a single
  GraphQL input type. The `type` field determines which other fields are
  required. Server-side validation ensures the correct fields are present.
  Note: GraphQL camelCase field names become kebab-case in Clojure."
  [:map {:graphql/type :ActionInput}
   [:type :string]
   [:phase {:optional true} :string]
   [:player {:optional true} :string]
   [:amount {:optional true} :int]
   [:count {:optional true} :int]
   [:card-slugs {:optional true} [:vector :string]]
   [:player-id {:optional true} :string]
   [:position {:optional true} [:vector :int]]
   [:modifier-id {:optional true} :string]
   [:starter-id {:optional true} :string]
   [:bench-id {:optional true} :string]
   [:holder-id {:optional true} :string]
   [:origin {:optional true} [:vector :int]]
   [:target {:optional true} [:vector :int]]
   [:target-player-id {:optional true} :string]
   [:action-type {:optional true} :string]
   [:team {:optional true} :string]
   [:points {:optional true} :int]
   [:stat {:optional true} :string]
   [:base {:optional true} :int]
   [:fate {:optional true} :int]
   [:modifiers {:optional true} [:vector :int]]
   [:total {:optional true} :int]])

(def GameActionResult
  "GraphQL schema for action submission result."
  [:map {:graphql/type :GameActionResult}
   [:success :boolean]])

(defn- get-user-id
  "Extracts and parses the user ID from the request context."
  [ctx]
  (when-let [id (get-in ctx [:request :authn/user-id])]
    (parse-uuid id)))

(defn- authenticated?
  "Returns true if the request is authenticated."
  [ctx]
  (get-in ctx [:request :authn/authenticated?]))

(defn- require-auth!
  "Throws an exception if the user is not authenticated."
  [ctx]
  (when-not (authenticated? ctx)
    (throw (ex-info "Authentication required" {:type :unauthorized}))))

(defn- game->graphql
  "Transforms a game record to GraphQL response format."
  [game]
  {:id (:id game)
   :player1Id (:player-1-id game)
   :player2Id (:player-2-id game)
   :status (name (:status game))
   :gameState (:game-state game)
   :winnerId (:winner-id game)
   :createdAt (str (:created-at game))
   :startedAt (some-> (:started-at game) str)})

(defn- get-game-service
  "Gets the game service from the request context."
  [ctx]
  (get-in ctx [:request :resolver-map :game-service]))

;; ---------------------------------------------------------------------------
;; Query Resolvers

(defresolver :Query :myGames
  "Returns all games where the authenticated user is a participant."
  [:=> [:cat :any :any :any] [:vector GameResponse]]
  [ctx {:keys [status]} _value]
  (require-auth! ctx)
  (let [game-service (get-game-service ctx)
        user-id      (get-user-id ctx)]
    (if status
      (mapv game->graphql (game-svc/list-user-games-by-status game-service user-id status))
      (mapv game->graphql (game-svc/list-user-games game-service user-id)))))

(defresolver :Query :game
  "Returns a game by ID if the authenticated user is a participant."
  [:=> [:cat :any [:map [:id :uuid]] :any] [:maybe GameResponse]]
  [ctx {:keys [id]} _value]
  (require-auth! ctx)
  (let [game-service (get-game-service ctx)
        user-id      (get-user-id ctx)]
    (when-let [game (game-svc/get-game-for-player game-service id user-id)]
      (game->graphql game))))

(defresolver :Query :availableGames
  "Returns games in WAITING status available for the authenticated user to join."
  [:=> [:cat :any :any :any] [:vector GameResponse]]
  [ctx _args _value]
  (require-auth! ctx)
  (let [game-service (get-game-service ctx)
        user-id      (get-user-id ctx)]
    (mapv game->graphql (game-svc/list-available-games game-service user-id))))

;; ---------------------------------------------------------------------------
;; Mutation Resolvers

(defresolver :Mutation :createGame
  "Creates a new game with the authenticated user as player 1."
  [:=> [:cat :any [:map [:deck-id :uuid]] :any] [:maybe GameResponse]]
  [ctx {:keys [deck-id]} _value]
  (require-auth! ctx)
  (let [game-service (get-game-service ctx)
        user-id      (get-user-id ctx)]
    (when-let [game (game-svc/create-game! game-service user-id deck-id)]
      (game->graphql game))))

(defresolver :Mutation :joinGame
  "Joins an existing WAITING game as player 2, starting the game."
  [:=> [:cat :any [:map [:game-id :uuid] [:deck-id :uuid]] :any] [:maybe GameResponse]]
  [ctx {:keys [game-id deck-id]} _value]
  (require-auth! ctx)
  (let [game-service (get-game-service ctx)
        user-id      (get-user-id ctx)]
    (when-let [game (game-svc/join-game! game-service game-id user-id deck-id)]
      (game->graphql game))))

(defn- input->action
  "Converts GraphQL ActionInput to bashketball-game action format.

  Lacinia sends args with kebab-case keys. This function converts string
  enum values to keywords where needed for the game engine."
  [{:keys [type phase player amount count card-slugs player-id position
           modifier-id starter-id bench-id holder-id origin target target-player-id
           action-type team points stat base fate modifiers total]}]
  (let [action-type-kw (keyword type)]
    (cond-> {:type action-type-kw}
      phase            (assoc :phase (keyword phase))
      player           (assoc :player (keyword player))
      amount           (assoc :amount amount)
      count            (assoc :count count)
      card-slugs       (assoc :card-slugs card-slugs)
      player-id        (assoc :player-id player-id)
      position         (assoc :position (vec position))
      modifier-id      (assoc :modifier-id modifier-id)
      starter-id       (assoc :starter-id starter-id)
      bench-id         (assoc :bench-id bench-id)
      holder-id        (assoc :holder-id holder-id)
      origin           (assoc :origin (vec origin))
      target           (assoc :target (vec target))
      target-player-id (assoc :target target-player-id)
      action-type      (assoc :action-type (keyword action-type))
      team             (assoc :team (keyword team))
      points           (assoc :points points)
      stat             (assoc :stat (keyword stat))
      base             (assoc :base base)
      fate             (assoc :fate fate)
      modifiers        (assoc :modifiers (vec modifiers))
      total            (assoc :total total))))

(defresolver :Mutation :submitAction
  "Submits a game action for the authenticated user.

  The action is validated against the bashketball-game Action schema."
  [:=> [:cat :any [:map [:game-id :uuid] [:action ActionInput]] :any] GameActionResult]
  [ctx {:keys [game-id action]} _value]
  (require-auth! ctx)
  (let [game-service (get-game-service ctx)
        user-id      (get-user-id ctx)
        game-action  (input->action action)
        result       (game-svc/submit-action! game-service game-id user-id game-action)]
    {:success (:success result)
     :gameId (when (:game result) (:id (:game result)))
     :error (:error result)}))

(defresolver :Mutation :forfeitGame
  "Forfeits the game, opponent wins."
  [:=> [:cat :any [:map [:game-id :uuid]] :any] [:maybe GameResponse]]
  [ctx {:keys [game-id]} _value]
  (require-auth! ctx)
  (let [game-service (get-game-service ctx)
        user-id      (get-user-id ctx)]
    (when-let [game (game-svc/forfeit-game! game-service game-id user-id)]
      (game->graphql game))))

(defresolver :Mutation :leaveGame
  "Leaves a WAITING game (cancels it)."
  [:=> [:cat :any [:map [:game-id :uuid]] :any] :boolean]
  [ctx {:keys [game-id]} _value]
  (require-auth! ctx)
  (let [game-service (get-game-service ctx)
        user-id      (get-user-id ctx)]
    (boolean (game-svc/leave-game! game-service game-id user-id))))

(def-resolver-map)
