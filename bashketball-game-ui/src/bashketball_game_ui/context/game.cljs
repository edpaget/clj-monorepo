(ns bashketball-game-ui.context.game
  "Game state context for active games.

  Provides real-time game state via SSE subscription and action
  dispatch functions to child components.
  Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.graphql.subscriptions :as subscriptions]
   [bashketball-game-ui.hooks.use-game-actions :refer [use-game-actions]]
   [bashketball-ui.core]
   [uix.core :refer [$ defui create-context use-context use-state use-effect use-memo use-ref]]))

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
    (= user-id (:player-1-id game)) :HOME
    (= user-id (:player-2-id game)) :AWAY
    :else nil))

(defn- is-my-turn?
  "Returns true if it's the user's turn to act."
  [game-state my-team]
  (when (and game-state my-team)
    (let [active-player (:active-player game-state)
          active-kw     (some-> active-player name keyword)]
      (= active-kw my-team))))

(defui game-provider
  "Provides game state context to children.

  Fetches initial game state via query, then subscribes to updates.
  Refetches game data when subscription events indicate state changes.

  Props:
  - `game-id` - UUID of the game to load
  - `user-id` - UUID of the current user
  - `children` - Child components"
  [{:keys [game-id user-id children]}]
  (let [;; Initial game fetch (automatically decoded)
        {:keys [data loading error refetch] :as query-result}
        (gql/use-query queries/GAME_QUERY
                       {:variables {:id game-id}
                        :skip      (nil? game-id)})

        ;; Subscribe to real-time updates (automatically decoded)
        ;; NOTE: Do not destructure :data here - it would shadow the query's data binding
        subscription-result
        (gql/use-subscription subscriptions/GAME_UPDATED_SUBSCRIPTION
                              {:variables {:game-id game-id}
                               :skip      (nil? game-id)})

        ;; Local state for merged game data
        [game set-game]                                               (use-state nil)
        [connected set-connected]                                     (use-state false)

        ;; Decode initial game from query
        initial-game                                                  (use-memo
                                                                       (fn [] (some-> data :game))
                                                                       [data])

        ;; Store in ref so we can access latest value without triggering effect
        initial-game-ref                                              (use-ref nil)
        _                                                             (do (reset! initial-game-ref initial-game) nil)

        ;; Extract phase and turn for stable comparison
        initial-phase                                                 (get-in initial-game [:game-state :phase])
        initial-turn                                                  (get-in initial-game [:game-state :turn-number])
        current-phase                                                 (get-in game [:game-state :phase])
        current-turn                                                  (get-in game [:game-state :turn-number])

        ;; Actions hook
        actions                                                       (use-game-actions game-id)

        ;; Derived values
        my-team                                                       (when game (determine-my-team game user-id))
        game-state                                                    (:game-state game)
        is-turn                                                       (is-my-turn? game-state my-team)]

    ;; Set initial game from query
    ;; Compare phase and turn to detect actual game state changes
    (use-effect
     (fn []
       (when-let [latest-game @initial-game-ref]
         ;; Update if game doesn't exist or if phase/turn changed
         (when (or (nil? game)
                   (not= initial-phase current-phase)
                   (not= initial-turn current-turn))
           (set-game latest-game)))
       js/undefined)
     [game initial-phase initial-turn current-phase current-turn])

    ;; Handle subscription events
    (use-effect
     (fn []
       (when-let [event (some-> subscription-result :data :game-updated)]
         (let [event-type (:type event)]
           ;; Mark as connected on first message (guard prevents infinite loop)
           (when-not connected
             (set-connected true))
           ;; Refetch game data when state changes
           (when (contains? #{"state-changed" "STATE_CHANGED"
                              "player-joined" "PLAYER_JOINED"
                              "game-started" "GAME_STARTED"
                              "game-ended" "GAME_ENDED"}
                            event-type)
             (when refetch
               (refetch)))))
       js/undefined)
     [connected subscription-result refetch])

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
