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
  "Returns true if it's the user's turn to act."
  [game-state my-team]
  (when (and game-state my-team)
    (let [active-player (or (:active-player game-state)
                            (get game-state "activePlayer"))]
      (= (keyword active-player) my-team))))

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
  Merges subscription updates into local state.

  Props:
  - `game-id` - UUID of the game to load
  - `user-id` - UUID of the current user
  - `children` - Child components"
  [{:keys [game-id user-id children]}]
  (let [;; Initial game fetch
        query-result (useQuery queries/GAME_QUERY
                               #js {:variables #js {:id game-id}
                                    :skip (nil? game-id)})

        ;; Subscribe to real-time updates
        subscription-result (useSubscription subscriptions/GAME_UPDATED_SUBSCRIPTION
                                             #js {:variables #js {:gameId game-id}
                                                  :skip (nil? game-id)})

        ;; Local state for merged game data
        [game set-game] (use-state nil)
        [connected set-connected] (use-state false)

        ;; Decode initial game from query
        initial-game (use-memo
                      (fn []
                        (some->> query-result :data :game
                                 (decoder/decode game-schema/Game)))
                      [query-result (:data query-result)])

        ;; Actions hook
        actions (use-game-actions game-id)

        ;; Derived values
        my-team (when game (determine-my-team game user-id))
        game-state (parse-game-state (:game-state game))
        is-turn (is-my-turn? game-state my-team)]

    ;; Set initial game from query
    (use-effect
     (fn []
       (when initial-game
         (set-game initial-game))
       js/undefined)
     [initial-game])

    ;; Handle subscription updates
    (use-effect
     (fn []
       (when-let [update-data (some-> subscription-result :data :gameUpdated)]
         ;; Mark as connected on first message
         (when-not connected
           (set-connected true))
         ;; Update game state from subscription
         (when-let [updated-game (:game update-data)]
           (set-game (decoder/decode game-schema/Game updated-game))))
       js/undefined)
     [connected subscription-result (:data subscription-result)])

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
