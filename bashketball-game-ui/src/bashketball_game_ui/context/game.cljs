(ns bashketball-game-ui.context.game
  "Game state context for active games.

  Provides real-time game state via SSE subscription and action
  dispatch functions to child components.
  Uses automatic __typename-based decoding."
  (:require
   [bashketball-game-ui.context.dispatch :refer [dispatch-provider]]
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-game-ui.game.discard-machine :as dm]
   [bashketball-game-ui.game.dispatcher :as dispatcher]
   [bashketball-game-ui.game.peek-machine :as pm]
   [bashketball-game-ui.game.selection-machine :as sm]
   [bashketball-game-ui.game.selectors :as sel]
   [bashketball-game-ui.game.substitute-machine :as subm]
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.graphql.subscriptions :as subscriptions]
   [bashketball-game-ui.hooks.use-game-actions :refer [use-game-actions]]
   [bashketball-game-ui.hooks.use-game-ui :as ui]
   [bashketball-ui.core]
   [uix.core :refer [$ defui create-context use-state use-effect use-memo use-callback use-ref]]))

(def game-context
  "React context for game state.

  Contains all game-related state including:
  - Core: game, game-state, catalog, my-team, is-my-turn, actions, loading, error, connected
  - Selection machine: selection-mode, selection-data, send, can-send?
  - Modal machines: discard-machine, substitute-machine, peek-machine (+ send/can-send for each)
  - UI modals: detail-modal, fate-reveal, create-token-modal, attach-ability-modal

  Access via focused selector hooks in [[bashketball-game-ui.hooks.selectors]]."
  (create-context nil))

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

        ;; Modal state machines
        [discard-machine set-discard-machine]                         (use-state (dm/init))
        [substitute-machine set-substitute-machine]                   (use-state (subm/init))
        [peek-machine set-peek-machine]                               (use-state (pm/init))

        ;; UI state hooks (remaining hooks not replaced by machine)
        detail-modal                                                  (ui/use-detail-modal)
        fate-reveal                                                   (ui/use-fate-reveal)
        create-token-modal                                            (ui/use-create-token-modal)
        attach-ability-modal                                          (ui/use-attach-ability-modal)

        ;; Refs to break circular dependency between dispatcher and send functions
        send-ref                                                      (use-ref nil)
        send-discard-ref                                              (use-ref nil)

        ;; Create dispatcher with full context
        get-ball-holder-position                                      (use-callback
                                                                       #(actions/get-ball-holder-position game-state)
                                                                       [game-state])

        get-game-state                                                (use-callback
                                                                       (fn [] game-state)
                                                                       [game-state])

        get-selection-data                                            (use-callback
                                                                       (fn [] (:data machine-state))
                                                                       [machine-state])

        get-discard-machine                                           (use-callback
                                                                       (fn [] discard-machine)
                                                                       [discard-machine])

        dispatch-action                                               (use-memo
                                                                       #(dispatcher/create-dispatcher
                                                                         {:actions                  actions
                                                                          :my-team                  my-team
                                                                          :get-ball-holder-position get-ball-holder-position
                                                                          :get-game-state           get-game-state
                                                                          :get-selection-data       get-selection-data
                                                                          :get-discard-machine      get-discard-machine
                                                                          :send                     (fn [event] (when-let [s @send-ref] (s event)))
                                                                          :send-discard             (fn [event] (when-let [s @send-discard-ref] (s event)))
                                                                          :fate-reveal              fate-reveal
                                                                          :attach-ability-modal     attach-ability-modal
                                                                          :detail-modal             detail-modal
                                                                          :create-token-modal       create-token-modal})
                                                                       [actions my-team get-ball-holder-position get-game-state
                                                                        get-selection-data get-discard-machine fate-reveal
                                                                        attach-ability-modal detail-modal create-token-modal])

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
                                                                       [machine-state])

        ;; Discard machine send function
        send-discard                                                  (use-callback
                                                                       (fn [event]
                                                                         (set-discard-machine
                                                                          (fn [current]
                                                                            (let [result (dm/transition current event)]
                                                                              (when-let [action (:action result)]
                                                                                (dispatch-action action))
                                                                              (select-keys result [:state :data])))))
                                                                       [dispatch-action])

        can-send-discard?                                             (use-callback
                                                                       (fn [event-type]
                                                                         (contains? (dm/valid-events discard-machine) event-type))
                                                                       [discard-machine])

        ;; Substitute machine send function
        send-substitute                                               (use-callback
                                                                       (fn [event]
                                                                         (set-substitute-machine
                                                                          (fn [current]
                                                                            (let [result (subm/transition current event)]
                                                                              (when-let [action (:action result)]
                                                                                (dispatch-action action))
                                                                              (select-keys result [:state :data])))))
                                                                       [dispatch-action])

        can-send-substitute?                                          (use-callback
                                                                       (fn [event-type]
                                                                         (contains? (subm/valid-events substitute-machine) event-type))
                                                                       [substitute-machine])

        ;; Peek machine send function
        send-peek                                                     (use-callback
                                                                       (fn [event]
                                                                         (set-peek-machine
                                                                          (fn [current]
                                                                            (let [result (pm/transition current event)]
                                                                              (when-let [action (:action result)]
                                                                                (dispatch-action action))
                                                                              (select-keys result [:state :data])))))
                                                                       [dispatch-action])

        can-send-peek?                                                (use-callback
                                                                       (fn [event-type]
                                                                         (contains? (pm/valid-events peek-machine) event-type))
                                                                       [peek-machine])

        ;; Update refs after send functions are created
        _                                                             (do
                                                                        (reset! send-ref send)
                                                                        (reset! send-discard-ref send-discard))]

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
                   ;; Discard machine
                   :discard-machine       discard-machine
                   :send-discard          send-discard
                   :can-send-discard?     can-send-discard?
                   ;; Substitute machine
                   :substitute-machine    substitute-machine
                   :send-substitute       send-substitute
                   :can-send-substitute?  can-send-substitute?
                   ;; Peek machine
                   :peek-machine          peek-machine
                   :send-peek             send-peek
                   :can-send-peek?        can-send-peek?
                   ;; Remaining UI hooks
                   :detail-modal          detail-modal
                   :fate-reveal           fate-reveal
                   :create-token-modal    create-token-modal
                   :attach-ability-modal  attach-ability-modal}}
          children))))
