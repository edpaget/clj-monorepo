(ns bashketball-game-api.graphql.resolvers.game
  "GraphQL resolvers for game queries and mutations.

  Provides Query resolvers for listing and fetching games, Mutation resolvers
  for game lifecycle operations, and field resolvers for player references."
  (:require
   [bashketball-game-api.services.game :as game-svc]
   [bashketball-game.schema :as game-schema]
   [bashketball-schemas.enums :as enums]
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [graphql-server.core :refer [defresolver def-resolver-map]]))

(defn- keywordize-game-state
  "Wrapper for [[game-svc/keywordize-game-state]] for use in resolvers."
  [game-state]
  (when game-state
    (game-svc/keywordize-game-state game-state)))

(def GameStatus
  "GraphQL enum for game status. Re-exported from [[bashketball-schemas.enums]]."
  enums/GameStatus)

(def GameResponse
  "Schema for game data returned by resolvers.

  Uses kebab-case keys; graphql-server converts to camelCase for GraphQL."
  [:map {:graphql/type :Game}
   [:id :uuid]
   [:player-1-id :uuid]
   [:player-2-id {:optional true} [:maybe :uuid]]
   [:status GameStatus]
   [:game-state {:optional true} [:maybe game-schema/GameState]]
   [:winner-id {:optional true} [:maybe :uuid]]
   [:created-at :string]
   [:started-at {:optional true} [:maybe :string]]])

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
   [:instance-ids {:optional true} [:vector :string]]
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
   [:success :boolean]
   [:game-id {:optional true} [:maybe :uuid]]
   [:error {:optional true} [:maybe :string]]])

(def PageInfo
  "GraphQL schema for pagination metadata."
  [:map {:graphql/type :PageInfo}
   [:total-count :int]
   [:has-next-page :boolean]
   [:has-previous-page :boolean]])

(def GameConnection
  "GraphQL schema for paginated game results."
  [:map {:graphql/type :GameConnection}
   [:data [:vector GameResponse]]
   [:page-info PageInfo]])

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

(defn- field-name?
  "Returns true if the keyword looks like a field name rather than a data value.

  Field names are pure alphabetic kebab-case (e.g., :actions-remaining).
  Data values like player IDs contain numbers (e.g., :home-michael-jordan-0)."
  [k]
  (and (keyword? k)
       (not (re-find #"\d" (name k)))))

(defn- hex-position-key?
  "Returns true if the key is a HexPosition tuple (vector of 2 ints)."
  [k]
  (and (vector? k)
       (= 2 (count k))
       (every? int? k)))

(defn- stringify-hex-key
  "Converts a HexPosition vector key to comma-separated string.
  [0 1] -> \"0,1\""
  [k]
  (str/join "," k))

(defn- uppercase-keyword?
  "Returns true if keyword is all uppercase (e.g., :HOME, :AWAY)."
  [k]
  (and (keyword? k)
       (let [n (name k)]
         (and (not (str/blank? n))
              (= n (str/upper-case n))))))

(defn- transform-map-key
  "Transforms a map key for GraphQL JSON output.
  - Uppercase keywords (e.g., :HOME, :AWAY) -> preserved as-is
  - Field-name keywords -> camelCase keywords
  - HexPosition vectors -> comma-separated strings
  - Other keys -> unchanged"
  [k]
  (cond
    (uppercase-keyword? k) k
    (hex-position-key? k)  (stringify-hex-key k)
    (field-name? k)        (csk/->camelCaseKeyword k)
    :else                  k))

(defn- kebab->camel-keys
  "Recursively transforms map keys for GraphQL JSON output.

  Converts:
  - Field-name keyword keys to camelCase
  - HexPosition vector keys [0 1] to comma-separated strings \"0,1\"

  Keys that appear to be data values (like player IDs with numbers) are preserved.
  Required because map-of types become Json blobs in GraphQL, and the encoding
  transformer doesn't recurse into Json content to transform keys."
  [x]
  (cond
    (map? x) (into {}
                   (map (fn [[k v]]
                          [(transform-map-key k)
                           (kebab->camel-keys v)])
                        x))
    (vector? x) (mapv kebab->camel-keys x)
    :else x))

(defn- normalize-event
  "Transforms an event to GraphQL format by moving action data into :data field.

  The game engine logs events with action fields at the top level. GraphQL Event
  schema expects :type, :timestamp at top level and other fields in :data."
  [event]
  (let [standard-keys #{:type :timestamp}
        event-data    (apply dissoc event standard-keys)]
    {:type      (:type event)
     :timestamp (:timestamp event)
     :data      (when (seq event-data) event-data)}))

(defn- normalize-events
  "Transforms all events in game state to GraphQL format."
  [game-state]
  (if-let [events (:events game-state)]
    (assoc game-state :events (mapv normalize-event events))
    game-state))

(defn- game->graphql
  "Transforms a game record to GraphQL response format.

  Returns kebab-case keys; graphql-server converts to camelCase for GraphQL.
  Returns nil for game-state when empty (waiting games have no state yet).
  Keywordizes game-state to ensure enum values (like Ball status) are keywords
  for proper GraphQL union type tagging."
  [game]
  {:id           (:id game)
   :player-1-id  (:player-1-id game)
   :player-2-id  (:player-2-id game)
   :status       (name (:status game))
   :game-state   (-> (not-empty (:game-state game))
                     keywordize-game-state
                     normalize-events
                     kebab->camel-keys)
   :winner-id    (:winner-id game)
   :created-at   (str (:created-at game))
   :started-at   (some-> (:started-at game) str)})

(defn- get-game-service
  "Gets the game service from the request context."
  [ctx]
  (get-in ctx [:request :resolver-map :game-service]))

(def ^:private default-limit 20)
(def ^:private max-limit 100)

;; ---------------------------------------------------------------------------
;; Query Resolvers

(defresolver :Query :myGames
  "Returns paginated games where the authenticated user is a participant.

  Accepts optional `status` to filter by game status (waiting, active, completed),
  `limit` (default 20, max 100), and `offset` (default 0) for pagination."
  [:=> [:cat :any [:map
                   [:status {:optional true} [:maybe GameStatus]]
                   [:limit {:optional true} :int]
                   [:offset {:optional true} :int]]
        :any]
   GameConnection]
  [ctx {:keys [status limit offset]} _value]
  (require-auth! ctx)
  (let [game-service (get-game-service ctx)
        user-id      (get-user-id ctx)
        limit        (min (or limit default-limit) max-limit)
        offset       (or offset 0)
        opts         (cond-> {:limit limit :offset offset}
                       status (assoc :status status))
        result       (game-svc/list-user-games-paginated game-service user-id opts)
        total-count  (:total-count result)
        games        (mapv game->graphql (:data result))]
    {:data games
     :page-info {:total-count       total-count
                 :has-next-page     (< (+ offset (count games)) total-count)
                 :has-previous-page (pos? offset)}}))

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

(defn- uppercase-keyword
  "Converts a string to an uppercase keyword for enum values.

  The game engine uses uppercase enum values like :ACTIONS, :HOME, :SHOT."
  [s]
  (when s
    (keyword (str/upper-case (name s)))))

(defn- input->action
  "Converts GraphQL ActionInput to bashketball-game action format.

  Lacinia sends args with kebab-case keys. This function converts string
  enum values to uppercase keywords to match the game engine's enums."
  [{:keys [type phase player amount count instance-ids player-id position
           modifier-id starter-id bench-id holder-id origin target target-player-id
           action-type team points stat base fate modifiers total]}]
  (let [action-type-kw (keyword type)]
    (cond-> {:type action-type-kw}
      phase            (assoc :phase (uppercase-keyword phase))
      player           (assoc :player (uppercase-keyword player))
      amount           (assoc :amount amount)
      count            (assoc :count count)
      instance-ids     (assoc :instance-ids instance-ids)
      player-id        (assoc :player-id player-id)
      position         (assoc :position (vec position))
      modifier-id      (assoc :modifier-id modifier-id)
      starter-id       (assoc :starter-id starter-id)
      bench-id         (assoc :bench-id bench-id)
      holder-id        (assoc :holder-id holder-id)
      origin           (assoc :origin (vec origin))
      target           (assoc :target (vec target))
      target-player-id (assoc :target target-player-id)
      action-type      (assoc :action-type (uppercase-keyword action-type))
      team             (assoc :team (uppercase-keyword team))
      points           (assoc :points points)
      stat             (assoc :stat (uppercase-keyword stat))
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
     :game-id (when (:game result) (:id (:game result)))
     :error   (:error result)}))

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
