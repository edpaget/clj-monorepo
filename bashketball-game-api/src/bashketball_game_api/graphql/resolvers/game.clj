(ns bashketball-game-api.graphql.resolvers.game
  "GraphQL resolvers for game queries and mutations.

  Provides Query resolvers for listing and fetching games, Mutation resolvers
  for game lifecycle operations, and field resolvers for player references."
  (:require
   [bashketball-game-api.services.game :as game-svc]
   [bashketball-game.schema :as game-schema]
   [bashketball-schemas.card :as card-schema]
   [bashketball-schemas.enums :as enums]
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [graphql-server.core :refer [defresolver def-resolver-map]]
   [malli.util :as mu]))

(def GameStatus
  "GraphQL enum for game status. Re-exported from [[bashketball-schemas.enums]]."
  enums/GameStatus)

(def DeckStateWithCards
  "Extended DeckState schema with hydrated cards field.

  Extends [[game-schema/DeckState]] to include the `:cards` field with proper
  GraphQL typing via [[card-schema/Card]]."
  (mu/assoc game-schema/DeckState :cards [:vector card-schema/Card]))

(def GamePlayerWithCards
  "Extended GamePlayer schema with hydrated deck cards."
  (mu/assoc game-schema/GamePlayer :deck DeckStateWithCards))

(def PlayersWithCards
  "Extended Players schema with hydrated deck cards."
  [:map {:graphql/type :Players}
   [:team/HOME {:graphql/name :HOME} GamePlayerWithCards]
   [:team/AWAY {:graphql/name :AWAY} GamePlayerWithCards]])

(def GameStateWithCards
  "Extended GameState schema with hydrated deck cards."
  (mu/assoc game-schema/GameState :players PlayersWithCards))

(def GameResponse
  "Schema for game data returned by resolvers.

  Uses kebab-case keys; graphql-server converts to camelCase for GraphQL.
  Uses [[GameStateWithCards]] to include hydrated card data in deck responses."
  [:map {:graphql/type :Game}
   [:id :uuid]
   [:player-1-id :uuid]
   [:player-2-id {:optional true} [:maybe :uuid]]
   [:status GameStatus]
   [:game-state {:optional true} [:maybe GameStateWithCards]]
   [:winner-id {:optional true} [:maybe :uuid]]
   [:created-at :string]
   [:started-at {:optional true} [:maybe :string]]])

(def TargetInput
  "Input schema for ball target (position or player).

  Uses `target-type` field to discriminate between position and player targets."
  [:map {:graphql/type :TargetInput}
   [:target-type :string]
   [:position {:optional true} [:vector :int]]
   [:player-id {:optional true} :string]])

(def TokenCardInput
  "Input schema for creating token cards.

  Tokens are temporary cards created during gameplay, not from the card catalog."
  [:map {:graphql/type :TokenCardInput}
   [:name :string]
   [:slug {:optional true} :string]
   [:card-type {:optional true} :string]
   [:token {:optional true} :boolean]])

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
   [:instance-id {:optional true} :string]
   [:instance-ids {:optional true} [:vector :string]]
   [:player-id {:optional true} :string]
   [:position {:optional true} [:vector :int]]
   [:modifier-id {:optional true} :string]
   [:starter-id {:optional true} :string]
   [:bench-id {:optional true} :string]
   [:holder-id {:optional true} :string]
   [:origin {:optional true} [:vector :int]]
   [:target {:optional true} TargetInput]
   [:action-type {:optional true} :string]
   [:team {:optional true} :string]
   [:points {:optional true} :int]
   [:stat {:optional true} :string]
   [:base {:optional true} :int]
   [:fate {:optional true} :int]
   [:modifiers {:optional true} [:vector :int]]
   [:total {:optional true} :int]
   [:card {:optional true} TokenCardInput]
   [:placement {:optional true} game-schema/TokenPlacement]
   [:target-player-id {:optional true} :string]
   [:destination {:optional true} game-schema/AssetDestination]
   [:discard-instance-ids {:optional true} [:vector :string]]
   [:card-slug {:optional true} :string]])

(def GameActionResult
  "GraphQL schema for action submission result."
  [:map {:graphql/type :GameActionResult}
   [:success :boolean]
   [:game-id {:optional true} [:maybe :uuid]]
   [:error {:optional true} [:maybe :string]]
   [:revealed-fate {:optional true} [:maybe :int]]])

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
  "Converts a HexPosition vector key to an edn string.
  [0 1] -> \"[0 1]\""
  [k]
  (pr-str k))

(defn- uppercase-keyword?
  "Returns true if keyword name is all uppercase (e.g., :HOME, :AWAY, :team/HOME)."
  [k]
  (and (keyword? k)
       (let [n (name k)]
         (and (not (str/blank? n))
              (= n (str/upper-case n))))))

(defn- transform-map-key
  "Transforms a map key for GraphQL JSON output.
  - Namespaced uppercase keywords (e.g., :team/HOME) -> strip namespace (:HOME)
  - Uppercase keywords (e.g., :HOME, :AWAY) -> preserved as-is
  - Field-name keywords -> camelCase keywords
  - HexPosition vectors -> stringified vectors
  - Other keys -> unchanged"
  [k]
  (cond
    (uppercase-keyword? k) (keyword (name k))
    (hex-position-key? k)  (stringify-hex-key k)
    (field-name? k)        (csk/->camelCaseKeyword k)
    :else                  k))

(defn- json-blob-transform
  "Transforms keys in JSON blob fields for GraphQL output.

  Only used for map-of fields that become opaque JSON in GraphQL (like tiles,
  occupants, player maps). These fields aren't walked by graphql-server's
  schema-driven encoder.

  Converts:
  - Field-name keyword keys to camelCase
  - Namespaced uppercase keyword keys to plain uppercase (:team/HOME -> :HOME)
  - HexPosition vector keys [0 1] already stringified by stringify-board-hex-keys"
  [x]
  (cond
    (map? x) (into {}
                   (map (fn [[k v]]
                          [(transform-map-key k)
                           (json-blob-transform v)])
                        x))
    (vector? x) (mapv json-blob-transform x)
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

(defn- transform-board-for-json
  "Transforms board data for GraphQL JSON output.

  Board tiles and occupants are map-of types that become opaque JSON blobs in
  GraphQL. Transforms:
  - HexPosition tuple keys to stringified format
  - Nested keys to camelCase
  - Values recursively"
  [game-state]
  (if-let [board (:board game-state)]
    (assoc game-state :board
           (cond-> board
             (:tiles board)
             (update :tiles
                     (fn [tiles]
                       (into {} (map (fn [[k v]]
                                       [(stringify-hex-key k)
                                        (json-blob-transform v)])
                                     tiles))))
             (:occupants board)
             (update :occupants
                     (fn [occupants]
                       (into {} (map (fn [[k v]]
                                       [(stringify-hex-key k)
                                        (json-blob-transform v)])
                                     occupants))))))
    game-state))

(defn- transform-game-state-for-graphql
  "Applies all transformations needed for GraphQL output.

  Only transforms board data (tiles/occupants) which are JSON blobs.
  Other fields are handled by graphql-server's schema-driven encoder."
  [game-state]
  (when game-state
    (transform-board-for-json game-state)))

(defn- collect-deck-slugs
  "Extracts all unique card slugs from a deck state."
  [deck]
  (->> (concat (:draw-pile deck)
               (:hand deck)
               (:discard deck)
               (:removed deck))
       (map :card-slug)
       distinct))

(defn- collect-player-attachment-slugs
  "Extracts card slugs from player attachments."
  [team-roster]
  (->> (vals (:players team-roster))
       (mapcat :attachments)
       (keep :card-slug)))

(defn- collect-asset-slugs
  "Extracts card slugs from team assets."
  [assets]
  (keep :card-slug assets))

(defn- collect-extra-slugs
  "Collects card slugs from play area, assets, and attachments."
  [game-state]
  (let [home-assets (get-in game-state [:players :team/HOME :assets] [])
        away-assets (get-in game-state [:players :team/AWAY :assets] [])
        home-roster (get-in game-state [:players :team/HOME :team])
        away-roster (get-in game-state [:players :team/AWAY :team])
        play-area   (get game-state :play-area [])]
    (concat (collect-asset-slugs home-assets)
            (collect-asset-slugs away-assets)
            (collect-player-attachment-slugs home-roster)
            (collect-player-attachment-slugs away-roster)
            (map :card-slug play-area))))

(defn- hydrate-deck
  "Adds cards field to deck state with hydrated card data."
  [deck catalog]
  (let [slugs (collect-deck-slugs deck)
        cards (if catalog
                (->> slugs
                     (map #(get catalog %))
                     (filter some?)
                     vec)
                [])]
    (assoc deck :cards cards)))

(defn- hydrate-game-state
  "Hydrates all deck cards in game state including play area, assets, and attachments."
  [game-state catalog]
  (if game-state
    (let [extra-slugs (collect-extra-slugs game-state)
          extra-cards (when catalog
                        (->> extra-slugs
                             (map #(get catalog %))
                             (filter some?)
                             vec))
          home-deck   (get-in game-state [:players :team/HOME :deck])
          away-deck   (get-in game-state [:players :team/AWAY :deck])
          home-cards  (:cards (hydrate-deck home-deck catalog))
          away-cards  (:cards (hydrate-deck away-deck catalog))
          home-all    (->> (concat home-cards extra-cards)
                           (filter some?)
                           distinct
                           vec)
          away-all    (->> (concat away-cards extra-cards)
                           (filter some?)
                           distinct
                           vec)]
      (-> game-state
          (assoc-in [:players :team/HOME :deck :cards] home-all)
          (assoc-in [:players :team/AWAY :deck :cards] away-all)))
    game-state))

(defn- game->graphql
  "Transforms a game record to GraphQL response format.

  Returns kebab-case keys; graphql-server's encoder converts to camelCase.
  Returns nil for game-state when empty (waiting games have no state yet).
  Enum values and union types are handled by the graphql-server encoder.
  When a catalog is provided, hydrates deck cards with full card data."
  ([game]
   (game->graphql game nil))
  ([game catalog]
   {:id           (:id game)
    :player-1-id  (:player-1-id game)
    :player-2-id  (:player-2-id game)
    :status       (name (:status game))
    :game-state   (-> (not-empty (:game-state game))
                      (hydrate-game-state catalog)
                      normalize-events
                      transform-game-state-for-graphql)
    :winner-id    (:winner-id game)
    :created-at   (str (:created-at game))
    :started-at   (some-> (:started-at game) str)}))

(defn- get-game-service
  "Gets the game service from the request context."
  [ctx]
  (get-in ctx [:request :resolver-map :game-service]))

(defn- get-card-catalog
  "Gets the card catalog map from the request context."
  [ctx]
  (some-> (get-in ctx [:request :resolver-map :card-catalog])
          :cards-by-slug))

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
        catalog      (get-card-catalog ctx)
        user-id      (get-user-id ctx)]
    (when-let [game (game-svc/get-game-for-player game-service id user-id)]
      (game->graphql game catalog))))

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

(defn- ns-enum-keyword
  "Converts a string to a namespaced enum keyword.

  The game engine uses namespaced enum values. Supported namespaces:
  - phase: SETUP, TIP_OFF, UPKEEP, ACTIONS, RESOLUTION, END_OF_TURN, GAME_OVER
  - team: HOME, AWAY
  - ball-action: SHOT, PASS
  - stat: SPEED, SHOOTING, PASSING, DRIBBLING, DEFENSE

  Input strings should be in format \"namespace/VALUE\" or just \"VALUE\" for legacy.
  For backwards compatibility, simple uppercase strings are mapped to namespaces
  based on known values."
  [s ns-hint]
  (when s
    (let [upper (str/upper-case (name s))]
      (if (str/includes? upper "/")
        ;; Already namespaced, convert to keyword
        (keyword upper)
        ;; Add namespace based on hint
        (keyword (name ns-hint) upper)))))

(defn- convert-token-card
  "Converts a token card map from GraphQL input format.

  Transforms card-type string to namespaced keyword if present."
  [card]
  (cond-> card
    (:card-type card) (update :card-type #(ns-enum-keyword % :card-type))))

(defn- input->action
  "Converts GraphQL ActionInput to bashketball-game action format.

  Lacinia sends args with kebab-case keys. This function converts string
  enum values to namespaced keywords to match the game engine's enums."
  [{:keys [type phase player amount count instance-id instance-ids player-id position
           modifier-id starter-id bench-id holder-id origin target
           action-type team points stat base fate modifiers total
           card placement target-player-id destination
           discard-instance-ids card-slug]}]
  (let [action-type-kw (keyword type)]
    (cond-> {:type action-type-kw}
      phase                (assoc :phase (ns-enum-keyword phase :phase))
      player               (assoc :player (ns-enum-keyword player :team))
      amount               (assoc :amount amount)
      count                (assoc :count count)
      instance-id          (assoc :instance-id instance-id)
      instance-ids         (assoc :instance-ids instance-ids)
      player-id            (assoc :player-id player-id)
      position             (assoc :position (vec position))
      modifier-id          (assoc :modifier-id modifier-id)
      starter-id           (assoc :starter-id starter-id)
      bench-id             (assoc :bench-id bench-id)
      holder-id            (assoc :holder-id holder-id)
      origin               (assoc :origin (vec origin))
      target               (assoc :target (case (:target-type target)
                                            "position" {:type :position
                                                        :position (vec (:position target))}
                                            "player" {:type :player
                                                      :player-id (:player-id target)}))
      action-type          (assoc :action-type (ns-enum-keyword action-type :ball-action))
      team                 (assoc :team (ns-enum-keyword team :team))
      points               (assoc :points points)
      stat                 (assoc :stat (ns-enum-keyword stat :stat))
      base                 (assoc :base base)
      fate                 (assoc :fate fate)
      modifiers            (assoc :modifiers (vec modifiers))
      total                (assoc :total total)
      card                 (assoc :card (convert-token-card card))
      placement            (assoc :placement (ns-enum-keyword placement :placement))
      target-player-id     (assoc :target-player-id target-player-id)
      destination          (assoc :destination (keyword destination))
      discard-instance-ids (assoc :discard-instance-ids (vec discard-instance-ids))
      card-slug            (assoc :card-slug card-slug))))

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
    (cond-> {:success (:success result)
             :game-id (when (:game result) (:id (:game result)))
             :error   (:error result)}
      (:revealed-fate result) (assoc :revealed-fate (:revealed-fate result)))))

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
