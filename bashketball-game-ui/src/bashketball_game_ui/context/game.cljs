(ns bashketball-game-ui.context.game
  "Game state context for active games.

  Provides real-time game state via SSE subscription and action
  dispatch functions to child components.
  Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.game.selectors :as sel]
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.graphql.subscriptions :as subscriptions]
   [bashketball-game-ui.hooks.use-game-actions :refer [use-game-actions]]
   [bashketball-game-ui.hooks.use-game-ui :as ui]
   [bashketball-ui.core]
   [uix.core :refer [$ defui create-context use-context use-state use-effect]]))

(def game-context
  "React context for game state."
  (create-context nil))

(defn use-game-context
  "Returns current game context value.

  Returns map with:
  - `:game` - Current game record (decoded)
  - `:game-state` - Inner game state from bashketball-game
  - `:catalog` - Card catalog map `{slug -> card}` from [[build-catalog]]
  - `:my-team` - :home or :away for current user
  - `:is-my-turn` - boolean indicating if it's the user's turn
  - `:actions` - Action dispatch functions from [[use-game-actions]]
  - `:loading` - Initial load state
  - `:error` - Error object if any
  - `:connected` - SSE connection status
  - `:selection` - Player/card selection state from [[ui/use-selection]]
  - `:pass` - Pass mode state from [[ui/use-pass-mode]]
  - `:discard` - Discard mode state from [[ui/use-discard-mode]]
  - `:detail-modal` - Card detail modal state from [[ui/use-detail-modal]]
  - `:ball-mode` - Ball selection state from [[ui/use-ball-mode]]
  - `:fate-reveal` - Fate reveal modal state from [[ui/use-fate-reveal]]
  - `:substitute-mode` - Substitution mode state from [[ui/use-substitute-mode]]"
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
  "Returns true if it's the user's turn to act.

  During TIP_OFF phase, both players can act simultaneously."
  [game-state my-team]
  (when (and game-state my-team)
    (or (sel/tip-off-mode? (:phase game-state))
        (= (:active-player game-state) my-team))))

(defn build-catalog
  "Builds a card catalog map from game state deck cards.

  Merges cards from both HOME and AWAY decks into a single
  `{slug -> card}` map for lookups."
  [game-state]
  (let [home-cards (get-in game-state [:players :HOME :deck :cards])
        away-cards (get-in game-state [:players :AWAY :deck :cards])]
    (into {}
          (map (juxt :slug identity))
          (concat home-cards away-cards))))

(defui game-provider
  "Provides game state context to children.

  Fetches initial game state via query, then subscribes to updates.
  Refetches game data when subscription events indicate state changes.

  Props:
  - `game-id` - UUID of the game to load
  - `user-id` - UUID of the current user
  - `children` - Child components"
  [{:keys [game-id user-id children]}]
  (let [{:keys [data loading error refetch]}
        (gql/use-query queries/GAME_QUERY
                       {:variables {:id game-id}
                        :skip      (nil? game-id)})

        subscription-result
        (gql/use-subscription subscriptions/GAME_UPDATED_SUBSCRIPTION
                              {:variables {:game-id game-id}
                               :skip      (nil? game-id)})

        [connected set-connected]                                     (use-state false)

        game                                                          (:game data)
        game-state                                                    (:game-state game)
        catalog                                                       (build-catalog game-state)
        actions                                                       (use-game-actions game-id)
        my-team                                                       (when game (determine-my-team game user-id))
        is-turn                                                       (is-my-turn? game-state my-team)

        ;; UI state hooks
        selection                                                     (ui/use-selection)
        pass                                                          (ui/use-pass-mode)
        discard                                                       (ui/use-discard-mode)
        detail-modal                                                  (ui/use-detail-modal)
        ball-mode                                                     (ui/use-ball-mode)
        fate-reveal                                                   (ui/use-fate-reveal)
        substitute-mode                                               (ui/use-substitute-mode)]

    (prn subscription-result)
    ;; Handle subscription events - refetch on state changes
    (use-effect
     (fn []
       (when-let [event (some-> subscription-result :data :game-updated)]
         (when-not connected
           (set-connected true))
         (when (contains? #{"STATE_CHANGED"
                            "PLAYER_JOINED"
                            "GAME_STARTED"
                            "GAME_ENDED"}
                          (:type event))
           (when refetch
             (prn "REFETCHING")
             (refetch))))
       js/undefined)
     [connected subscription-result refetch])

    ($ (:Provider game-context)
       {:value {:game            game
                :game-state      game-state
                :catalog         catalog
                :my-team         my-team
                :is-my-turn      is-turn
                :actions         actions
                :loading         (and loading (not connected))
                :error           (or error (:error subscription-result))
                :connected       connected
                :selection       selection
                :pass            pass
                :discard         discard
                :detail-modal    detail-modal
                :ball-mode       ball-mode
                :fate-reveal     fate-reveal
                :substitute-mode substitute-mode}}
       children)))
