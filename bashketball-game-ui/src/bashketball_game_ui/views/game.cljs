(ns bashketball-game-ui.views.game
  "Game page view.

  Displays the active game with board, players, and actions.
  Uses the game context provider for real-time state updates."
  (:require
   [bashketball-game-ui.components.game.card-detail-modal :refer [card-detail-modal]]
   [bashketball-game-ui.components.game.fate-reveal-modal :refer [fate-reveal-modal]]
   [bashketball-game-ui.components.game.substitute-modal :refer [substitute-modal]]
   [bashketball-game-ui.context.auth :refer [use-auth]]
   [bashketball-game-ui.context.game :refer [game-provider use-game-context]]
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-game-ui.game.handlers :as h]
   [bashketball-game-ui.game.selectors :as sel]
   [bashketball-game-ui.hooks.use-game-ui :as ui]
   [bashketball-game-ui.views.game.sections :as sections]
   [bashketball-ui.components.loading :refer [spinner]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-callback use-memo]]))

(defui game-content
  "Inner game content that consumes the game context.

  Uses extracted selectors for data derivation, custom hooks for UI state,
  and handler functions for action logic."
  []
  (let [;; Context data
        {:keys [game game-state my-team is-my-turn loading error connected actions]}
        (use-game-context)

        ;; Derived data using pure selectors
        opponent-team                                                                (sel/opponent-team my-team)
        phase                                                                        (:phase game-state)
        setup-mode                                                                   (sel/setup-mode? phase)
        {:keys [my-player my-players my-starters my-hand]}
        (sel/my-player-data game-state my-team)
        opponent                                                                     (sel/opponent-data game-state opponent-team)
        {:keys [home-players away-players]}                                          (sel/all-players game-state)
        ball-holder-id                                                               (get-in game-state [:ball :holder-id])
        score                                                                        (:score game-state)
        active-player                                                                (:active-player game-state)
        events                                                                       (:events game-state)

        ;; UI state using custom hooks
        selection                                                                    (ui/use-selection)
        pass                                                                         (ui/use-pass-mode)
        discard                                                                      (ui/use-discard-mode)
        detail-modal                                                                 (ui/use-detail-modal)
        ball-mode                                                                    (ui/use-ball-mode)
        fate-reveal                                                                  (ui/use-fate-reveal)
        substitute-mode                                                              (ui/use-substitute-mode)

        ;; Computed values
        selected-player-id                                                           (:selected-player selection)
        pass-active                                                                  (:active pass)
        ball-active                                                                  (:active ball-mode)

        valid-moves                                                                  (use-memo
                                                                                      #(when (and is-my-turn selected-player-id game-state)
                                                                                         (actions/valid-move-positions game-state selected-player-id))
                                                                                      [is-my-turn selected-player-id game-state])

        valid-pass-targets                                                           (use-memo
                                                                                      #(when pass-active
                                                                                         (sel/valid-pass-targets my-players ball-holder-id))
                                                                                      [pass-active my-players ball-holder-id])

        valid-setup-positions                                                        (use-memo
                                                                                      #(when (and setup-mode selected-player-id)
                                                                                         (actions/valid-setup-positions game-state my-team))
                                                                                      [setup-mode selected-player-id game-state my-team])

        setup-placed-count                                                           (use-memo
                                                                                      #(when setup-mode
                                                                                         (sel/setup-placed-count my-starters my-players))
                                                                                      [setup-mode my-starters my-players])

        my-setup-complete                                                            (use-memo
                                                                                      #(when setup-mode
                                                                                         (sel/team-setup-complete? game-state my-team))
                                                                                      [setup-mode game-state my-team])

        both-teams-ready                                                             (use-memo
                                                                                      #(when setup-mode
                                                                                         (sel/both-teams-setup-complete? game-state))
                                                                                      [setup-mode game-state])

        ;; Starters and bench for substitution modal
        my-bench-ids                                                                 (get-in game-state [:players my-team :team :bench])
        my-starter-players                                                           (use-memo
                                                                                      #(when my-starters
                                                                                         (->> my-starters
                                                                                              (map (fn [id] (get my-players id)))
                                                                                              (filter some?)
                                                                                              vec))
                                                                                      [my-starters my-players])
        my-bench-players                                                             (use-memo
                                                                                      #(when my-bench-ids
                                                                                         (->> my-bench-ids
                                                                                              (map (fn [id] (get my-players id)))
                                                                                              (filter some?)
                                                                                              vec))
                                                                                      [my-bench-ids my-players])

        ;; Action loading state
        action-loading                                                               (:loading actions)

        ;; Additional extracted values for callbacks
        discard-active                                                               (:active discard)
        discard-cards                                                                (:cards discard)
        selected-card                                                                (:selected-card selection)

        ;; Event handlers using pure handler functions
        handle-hex-click                                                             (use-callback
                                                                                      (fn [q r]
                                                                                        (cond
                                                                                          ;; Ball mode: move ball to clicked hex
                                                                                          ball-active
                                                                                          (do
                                                                                            (-> ((:set-ball-loose actions) q r)
                                                                                                (.then #(js/console.log "Ball move result:" %))
                                                                                                (.catch #(js/console.error "Ball move error:" %)))
                                                                                            ((:cancel ball-mode)))

                                                                                          ;; Normal player movement
                                                                                          :else
                                                                                          (when-let [action (h/hex-click-action
                                                                                                             {:setup-mode            setup-mode
                                                                                                              :is-my-turn            is-my-turn
                                                                                                              :selected-player       selected-player-id
                                                                                                              :valid-setup-positions valid-setup-positions
                                                                                                              :valid-moves           valid-moves}
                                                                                                             q r)]
                                                                                            (-> ((:move-player actions) (:player-id action) (first (:position action)) (second (:position action)))
                                                                                                (.then #(js/console.log "Move result:" %))
                                                                                                (.catch #(js/console.error "Move error:" %)))
                                                                                            ((:set-selected-player selection) nil))))
                                                                                      [ball-active ball-mode setup-mode is-my-turn selected-player-id
                                                                                       valid-setup-positions valid-moves actions selection])

        handle-ball-click                                                            (use-callback
                                                                                      (fn []
                                                                                        (if ball-active
                                                                                          ((:cancel ball-mode))
                                                                                          ((:select ball-mode))))
                                                                                      [ball-active ball-mode])

        handle-player-click                                                          (use-callback
                                                                                      (fn [player-id]
                                                                                        (let [action (h/player-click-action
                                                                                                      {:pass-mode            pass-active
                                                                                                       :valid-pass-targets   valid-pass-targets
                                                                                                       :ball-holder-position (actions/get-ball-holder-position game-state)
                                                                                                       :selected-player      selected-player-id}
                                                                                                      player-id)]
                                                                                          (case (:action action)
                                                                                            :pass (do
                                                                                                    ((:pass-ball actions) (:origin action) (:target-player-id action))
                                                                                                    ((:cancel pass))
                                                                                                    ((:set-selected-player selection) nil))
                                                                                            :toggle-selection ((:toggle-player selection) player-id))))
                                                                                      [selected-player-id pass-active valid-pass-targets game-state actions pass selection])

        handle-card-click                                                            (use-callback
                                                                                      (fn [card-slug]
                                                                                        (let [action (h/card-click-action
                                                                                                      {:discard-mode  discard-active
                                                                                                       :discard-cards discard-cards
                                                                                                       :selected-card selected-card}
                                                                                                      card-slug)]
                                                                                          (case (:action action)
                                                                                            :toggle-discard ((:toggle-card discard) card-slug)
                                                                                            :toggle-card-selection ((:toggle-card selection) card-slug))))
                                                                                      [discard-active discard-cards selected-card discard selection])

        handle-end-turn                                                              (use-callback
                                                                                      (fn []
                                                                                        ((:end-turn actions))
                                                                                        ((:clear selection)))
                                                                                      [actions selection])

        handle-shoot                                                                 (use-callback
                                                                                      (fn []
                                                                                        (when-let [origin (actions/get-ball-holder-position game-state)]
                                                                                          (let [target (sel/target-basket my-team)]
                                                                                            ((:shoot-ball actions) origin target)
                                                                                            ((:set-selected-player selection) nil))))
                                                                                      [game-state my-team actions selection])

        handle-play-card                                                             (use-callback
                                                                                      (fn []
                                                                                        (when selected-card
                                                                                          ((:submit actions) {:type      "bashketball/play-card"
                                                                                                              :card-slug selected-card
                                                                                                              :player    (name my-team)})
                                                                                          ((:set-selected-card selection) nil)))
                                                                                      [selected-card my-team actions selection])

        handle-draw                                                                  (use-callback
                                                                                      (fn []
                                                                                        ((:draw-cards actions) my-team 1))
                                                                                      [my-team actions])

        handle-start-game                                                            (use-callback
                                                                                      (fn []
                                                                                        ((:set-phase actions) "UPKEEP"))
                                                                                      [actions])

        handle-setup-done                                                            (use-callback
                                                                                      (fn []
                                                                                        ((:end-turn actions))
                                                                                        ((:clear selection)))
                                                                                      [actions selection])

        handle-next-phase                                                            (use-callback
                                                                                      (fn []
                                                                                        (when-let [next (sel/next-phase phase)]
                                                                                          ((:set-phase actions) (name next))))
                                                                                      [phase actions])

        handle-submit-discard                                                        (use-callback
                                                                                      (fn []
                                                                                        (when (pos? (:count discard))
                                                                                          (let [cards ((:get-cards-and-exit discard))]
                                                                                            ((:discard-cards actions) my-team (vec cards)))))
                                                                                      [discard my-team actions])

        handle-reveal-fate                                                           (use-callback
                                                                                      (fn []
                                                                                        (-> ((:reveal-fate actions) my-team)
                                                                                            (.then (fn [result]
                                                                                                     (prn result)
                                                                                                     (when-let [fate (-> result :data :submit-action :revealed-fate)]
                                                                                                       ((:show fate-reveal) fate))))
                                                                                            (.catch #(js/console.error "Reveal fate error:" %))))
                                                                                      [my-team actions fate-reveal])

        handle-shuffle                                                               (use-callback
                                                                                      (fn []
                                                                                        ((:shuffle-deck actions) my-team))
                                                                                      [my-team actions])

        handle-return-discard                                                        (use-callback
                                                                                      (fn []
                                                                                        ((:return-discard actions) my-team))
                                                                                      [my-team actions])

        handle-substitute                                                            (use-callback
                                                                                      (fn [bench-id]
                                                                                        (let [starter-id (:starter-id substitute-mode)]
                                                                                          (-> ((:substitute actions) starter-id bench-id)
                                                                                              (.then #(js/console.log "Substitute result:" %))
                                                                                              (.catch #(js/console.error "Substitute error:" %)))
                                                                                          ((:cancel substitute-mode))))
                                                                                      [actions substitute-mode])]

    (cond
      loading
      ($ sections/loading-state)

      error
      ($ sections/error-state {:error error})

      (nil? game)
      ($ sections/not-found-state)

      :else
      ($ :div {:class "flex flex-col h-full"}
         ;; Connection status
         ($ sections/connection-banner {:connected connected})

         ;; Header with game info
         ($ sections/game-header {:game-id       (:id game)
                                  :turn-number   (:turn-number game-state)
                                  :phase         phase
                                  :active-player active-player
                                  :is-my-turn    is-my-turn})

         ;; Main content area
         ($ :div {:class "flex-1 flex p-4 bg-slate-100 gap-4 min-h-0 overflow-hidden"}
            ;; Left: Board and player info
            ($ sections/board-section
               {:opponent              opponent
                :opponent-team         opponent-team
                :my-player             my-player
                :my-team               my-team
                :score                 score
                :active-player         active-player
                :board                 (:board game-state)
                :ball                  (:ball game-state)
                :home-players          home-players
                :away-players          away-players
                :selected-player       selected-player-id
                :valid-moves           valid-moves
                :valid-setup-positions valid-setup-positions
                :pass-mode             pass-active
                :valid-pass-targets    valid-pass-targets
                :ball-selected         ball-active
                :on-hex-click          handle-hex-click
                :on-player-click       handle-player-click
                :on-ball-click         handle-ball-click})

            ;; Right: Game log or roster panel during setup
            ($ sections/side-panel
               {:setup-mode       setup-mode
                :my-players       my-players
                :my-starters      my-starters
                :my-team          my-team
                :selected-player  selected-player-id
                :on-player-select (:set-selected-player selection)
                :events           events}))

         ;; Bottom: Hand and action bar
         ($ :div {:class "border-t bg-white"}
            ;; Player hand
            ($ sections/hand-section
               {:my-hand         my-hand
                :selected-card   selected-card
                :discard-mode    discard-active
                :discard-cards   discard-cards
                :discard-count   (:count discard)
                :on-card-click   handle-card-click
                :on-detail-click (:show detail-modal)
                :is-my-turn      is-my-turn})

            ;; Action bar
            ($ sections/action-section
               {:game-state         game-state
                :my-team            my-team
                :is-my-turn         is-my-turn
                :phase              phase
                :selected-player    selected-player-id
                :selected-card      selected-card
                :pass-mode          pass-active
                :discard-mode       discard-active
                :discard-count      (:count discard)
                :setup-placed-count (or setup-placed-count 0)
                :my-setup-complete  my-setup-complete
                :both-teams-ready   both-teams-ready
                :on-end-turn        handle-end-turn
                :on-shoot           handle-shoot
                :on-pass            (:start pass)
                :on-cancel-pass     (:cancel pass)
                :on-play-card       handle-play-card
                :on-draw            handle-draw
                :on-enter-discard   (:enter discard)
                :on-cancel-discard  (:cancel discard)
                :on-submit-discard  handle-submit-discard
                :on-start-game      handle-start-game
                :on-setup-done      handle-setup-done
                :on-next-phase      handle-next-phase
                :on-reveal-fate     handle-reveal-fate
                :on-shuffle         handle-shuffle
                :on-return-discard  handle-return-discard
                :on-substitute      (:enter substitute-mode)
                :loading            action-loading}))

         ;; Card detail modal
         ($ card-detail-modal {:open?     (:open? detail-modal)
                               :card-slug (:card-slug detail-modal)
                               :on-close  (:close detail-modal)})

         ;; Fate reveal modal
         ($ fate-reveal-modal {:open?    (:open? fate-reveal)
                               :fate     (:fate fate-reveal)
                               :on-close (:close fate-reveal)})

         ;; Substitute modal
         ($ substitute-modal {:open?             (:active substitute-mode)
                              :starters          my-starter-players
                              :bench             my-bench-players
                              :selected-starter  (:starter-id substitute-mode)
                              :on-starter-select (:set-starter substitute-mode)
                              :on-bench-select   handle-substitute
                              :on-close          (:cancel substitute-mode)})))))

(defui game-view
  "Game page component.

  Wraps the game content with the game provider for SSE subscription."
  []
  (let [{:keys [id]}   (router/use-params)
        {:keys [user]} (use-auth)]
    (if-not user
      ($ :div {:class "flex justify-center items-center h-64"}
         ($ spinner))
      ($ game-provider {:game-id id :user-id (:id user)}
         ($ game-content)))))
