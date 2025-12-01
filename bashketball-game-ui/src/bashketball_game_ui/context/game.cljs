(ns bashketball-game-ui.context.game
  "Game state context for active games.

  Provides real-time game state via SSE subscription and action
  dispatch functions to child components."
  (:require
   ["@apollo/client" :refer [useSubscription useQuery]]
   [bashketball-game-ui.graphql.decoder :as decoder]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.graphql.subscriptions :as subscriptions]
   [bashketball-game-ui.hooks.use-game-actions :refer [use-game-actions]]
   [bashketball-game-ui.schemas.game :as game-schema]
   [bashketball-ui.core]
   [clojure.string :as str]
   [uix.core :refer [$ defui create-context use-context use-state use-effect use-memo]]))

(def game-context
  "React context for game state."
  (create-context nil))

(defn use-game-context
  "Returns current game context value.

  Returns map with:
  - `:game` - Current game record (decoded)
  - `:game-state` - Inner game state from bashketball-game
  - `:my-team` - :home or :away for current user
  - `:is-my-turn` - boolean indicating if it's the user's turn
  - `:actions` - Action dispatch functions from use-game-actions
  - `:loading` - Initial load state
  - `:error` - Error object if any
  - `:connected` - SSE connection status"
  []
  (use-context game-context))

(defn- determine-my-team
  "Determines which team the user is playing as."
  [game user-id]
  (cond
    (= user-id (:player-1-id game)) :home
    (= user-id (:player-2-id game)) :away
    :else nil))

(defn- is-my-turn?
  "Returns true if it's the user's turn to act.

  Handles both lowercase and SCREAMING_SNAKE_CASE team values from the server."
  [game-state my-team]
  (when (and game-state my-team)
    (let [active-player (or (:active-player game-state)
                            (get game-state "activePlayer"))
          ;; Normalize to lowercase keyword for comparison
          active-kw     (when active-player
                          (-> active-player name str/lower-case keyword))]
      (= active-kw my-team))))

(defn- parse-game-state
  "Parses game state from server format to client format.

  The server returns game state as a JSON blob with string keys.
  This converts it to keyword keys for easier access."
  [game-state]
  (when game-state
    (if (map? game-state)
      game-state
      (js->clj game-state :keywordize-keys true))))

(defui game-provider
  "Provides game state context to children.

  Fetches initial game state via query, then subscribes to updates.
  Refetches game data when subscription events indicate state changes.

  Props:
  - `game-id` - UUID of the game to load
  - `user-id` - UUID of the current user
  - `children` - Child components"
  [{:keys [game-id user-id children]}]
  (let [;; Initial game fetch
        query-result              (useQuery queries/GAME_QUERY
                                            #js {:variables #js {:id game-id}
                                                 :skip (nil? game-id)})

        ;; Subscribe to real-time updates
        subscription-result       (useSubscription subscriptions/GAME_UPDATED_SUBSCRIPTION
                                                   #js {:variables #js {:gameId game-id}
                                                        :skip (nil? game-id)})

        ;; Local state for merged game data
        [game set-game]           (use-state nil)
        [connected set-connected] (use-state false)

        ;; Decode initial game from query
        initial-game              (use-memo
                                   (fn []
                                     (some->> query-result :data :game
                                              (decoder/decode game-schema/Game)))
                                   [query-result (:data query-result)])

        ;; Actions hook
        actions                   (use-game-actions game-id)

        ;; Derived values
        my-team                   (when game (determine-my-team game user-id))
        game-state                (parse-game-state (:game-state game))
        is-turn                   (is-my-turn? game-state my-team)]

    ;; Set initial game from query
    (use-effect
     (fn []
       (when initial-game
         (set-game initial-game))
       js/undefined)
     [initial-game])

    ;; Handle subscription events
    (use-effect
     (fn []
       (when-let [event (some-> subscription-result :data :gameUpdated)]
         (let [event-type (or (:type event) (get event "type"))]
           ;; Mark as connected on first message
           (when-not connected
             (set-connected true))
           ;; Refetch game data when state changes
           (when (contains? #{"state-changed" "STATE_CHANGED"
                              "player-joined" "PLAYER_JOINED"
                              "game-started" "GAME_STARTED"
                              "game-ended" "GAME_ENDED"}
                            event-type)
             (when-let [refetch (:refetch query-result)]
               (refetch)))))
       js/undefined)
     [connected subscription-result (:data subscription-result) query-result])

    ;; Provide context value
    ($ (.-Provider game-context)
       {:value {:game       game
                :game-state game-state
                :my-team    my-team
                :is-my-turn is-turn
                :actions    actions
                :loading    (and (:loading query-result) (nil? game))
                :error      (or (:error query-result) (:error subscription-result))
                :connected  connected}}
       children)))
