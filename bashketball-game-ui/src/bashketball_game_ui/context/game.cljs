(ns bashketball-game-ui.context.game
  "Game state context for active games.

  Provides real-time game state via SSE subscription and action
  dispatch functions to child components.
  Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.context.dispatch :refer [dispatch-provider]]
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-game-ui.game.dispatcher :as dispatcher]
   [bashketball-game-ui.game.selection-machine :as sm]
   [bashketball-game-ui.game.selectors :as sel]
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.graphql.subscriptions :as subscriptions]
   [bashketball-game-ui.hooks.use-game-actions :refer [use-game-actions]]
   [bashketball-game-ui.hooks.use-game-ui :as ui]
   [bashketball-ui.core]
   [uix.core :refer [$ defui create-context use-context use-state use-effect use-memo use-callback]]))

(def game-context
  "React context for game state."
  (create-context nil))

(defn use-game-context
  "Returns current game context value.

  Returns map with:
  - `:game` - Current game record (decoded)
  - `:game-state` - Inner game state from bashketball-game
  - `:catalog` - Card catalog map `{slug -> card}` from [[build-catalog]]
  - `:my-team` - :team/HOME or :team/AWAY for current user
  - `:is-my-turn` - boolean indicating if it's the user's turn
  - `:actions` - Action dispatch functions from [[use-game-actions]]
  - `:loading` - Initial load state
  - `:error` - Error object if any
  - `:connected` - SSE connection status
  - `:selection-mode` - Current selection machine state keyword
  - `:selection-data` - Selection machine data (selected player, cards, etc.)
  - `:send` - Function to send events to selection machine
  - `:can-send?` - Predicate to check if event type is valid
  - `:discard` - Discard mode state from [[ui/use-discard-mode]]
  - `:detail-modal` - Card detail modal state from [[ui/use-detail-modal]]
  - `:fate-reveal` - Fate reveal modal state from [[ui/use-fate-reveal]]
  - `:substitute-mode` - Substitution mode state from [[ui/use-substitute-mode]]
  - `:create-token-modal` - Create token modal state from [[ui/use-create-token-modal]]
  - `:attach-ability-modal` - Attach ability modal state from [[ui/use-attach-ability-modal]]
  - `:peek-deck-modal` - Peek deck modal state from [[ui/use-peek-deck-modal]]"
  []
  (use-context game-context))

(defn- determine-my-team
  "Determines which team the user is playing as."
  [game user-id]
  (cond
    (= user-id (:player-1-id game)) :team/HOME
    (= user-id (:player-2-id game)) :team/AWAY
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
  (let [home-cards (get-in game-state [:players :team/HOME :deck :cards])
        away-cards (get-in game-state [:players :team/AWAY :deck :cards])]
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

        ;; Selection machine state (replaces selection, pass, ball-mode, standard-action-mode)
        [machine-state set-machine-state]                             (use-state (sm/init))

        ;; UI state hooks (remaining hooks not replaced by machine)
        discard                                                       (ui/use-discard-mode)
        detail-modal                                                  (ui/use-detail-modal)
        fate-reveal                                                   (ui/use-fate-reveal)
        substitute-mode                                               (ui/use-substitute-mode)
        create-token-modal                                            (ui/use-create-token-modal)
        attach-ability-modal                                          (ui/use-attach-ability-modal)
        peek-deck-modal                                               (ui/use-peek-deck-modal)

        ;; Create dispatcher for selection machine
        get-ball-holder-position                                      (use-callback
                                                                       #(actions/get-ball-holder-position game-state)
                                                                       [game-state])

        dispatch-action                                               (use-memo
                                                                       #(dispatcher/create-dispatcher
                                                                         {:actions                  actions
                                                                          :my-team                  my-team
                                                                          :get-ball-holder-position get-ball-holder-position})
                                                                       [actions my-team get-ball-holder-position])

        ;; Selection machine send function
        send                                                          (use-callback
                                                                       (fn [event]
                                                                         (set-machine-state
                                                                          (fn [current]
                                                                            (let [result (sm/transition current event)]
                                                                              (when-let [action (:action result)]
                                                                                (dispatch-action action))
                                                                              (select-keys result [:state :data])))))
                                                                       [dispatch-action])

        ;; Predicate to check if event is valid in current state
        can-send?                                                     (use-callback
                                                                       (fn [event-type]
                                                                         (contains? (sm/valid-events (:state machine-state)) event-type))
                                                                       [machine-state])]

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

    ($ dispatch-provider
       {:dispatch dispatch-action}
       ($ (:Provider game-context)
          {:value {:game                  game
                   :game-state            game-state
                   :catalog               catalog
                   :my-team               my-team
                   :is-my-turn            is-turn
                   :actions               actions
                   :loading               (and loading (not connected))
                   :error                 (or error (:error subscription-result))
                   :connected             connected
                   ;; Selection machine
                   :selection-mode        (:state machine-state)
                   :selection-data        (:data machine-state)
                   :send                  send
                   :can-send?             can-send?
                   ;; Remaining UI hooks
                   :discard               discard
                   :detail-modal          detail-modal
                   :fate-reveal           fate-reveal
                   :substitute-mode       substitute-mode
                   :create-token-modal    create-token-modal
                   :attach-ability-modal  attach-ability-modal
                   :peek-deck-modal       peek-deck-modal}}
          children))))
