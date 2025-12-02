(ns bashketball-game-ui.views.game
  "Game page view.

  Displays the active game with board, players, and actions.
  Uses the game context provider for real-time state updates."
  (:require
   [bashketball-game-ui.components.game.action-bar :refer [action-bar]]
   [bashketball-game-ui.components.game.card-detail-modal :refer [card-detail-modal]]
   [bashketball-game-ui.components.game.game-log :refer [game-log]]
   [bashketball-game-ui.components.game.hex-grid :refer [hex-grid]]
   [bashketball-game-ui.components.game.player-hand :refer [player-hand]]
   [bashketball-game-ui.components.game.player-info :refer [player-info]]
   [bashketball-game-ui.components.game.turn-indicator :refer [turn-indicator]]
   [bashketball-game-ui.context.auth :refer [use-auth]]
   [bashketball-game-ui.context.game :refer [game-provider use-game-context]]
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-ui.components.loading :refer [spinner]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-state use-callback use-memo]]))

(defui game-content
  "Inner game content that consumes the game context."
  []
  (let [{:keys [game game-state my-team is-my-turn loading error connected actions]}
        (use-game-context)

        score                         (:score game-state)
        opponent-team                 (if (= my-team :home) :away :home)
        my-player                     (get-in game-state [:players my-team])
        opponent                      (get-in game-state [:players opponent-team])
        active-player                 (:active-player game-state)
        events                        (:events game-state)

        ;; Selection state
        [selected-player set-selected-player] (use-state nil)
        [selected-card set-selected-card]     (use-state nil)

        ;; Card detail modal state
        [detail-card set-detail-card]         (use-state nil)

        ;; Player maps for the hex grid
        home-players                  (get-in game-state [:players :home :team :players])
        away-players                  (get-in game-state [:players :away :team :players])

        ;; Compute valid moves for selected player
        valid-moves                   (use-memo
                                       #(when (and is-my-turn selected-player game-state)
                                          (actions/valid-move-positions game-state selected-player))
                                       [is-my-turn selected-player game-state])

        ;; My hand cards
        my-hand                       (get-in my-player [:deck :hand])

        ;; Action loading state
        action-loading                (:loading actions)

        ;; Callbacks
        handle-hex-click              (use-callback
                                       (fn [q r]
                                         (when (and is-my-turn
                                                    selected-player
                                                    (contains? valid-moves [q r]))
                                           ((:move-player actions) selected-player q r)
                                           (set-selected-player nil)))
                                       [is-my-turn selected-player valid-moves actions])

        handle-player-click           (use-callback
                                       (fn [player-id]
                                         (set-selected-player
                                          (fn [current]
                                            (if (= current player-id) nil player-id))))
                                       [])

        handle-end-turn               (use-callback
                                       (fn []
                                         ((:end-turn actions))
                                         (set-selected-player nil)
                                         (set-selected-card nil))
                                       [actions])

        handle-card-click             (use-callback
                                       (fn [card-slug]
                                         (set-selected-card
                                          (fn [current]
                                            (if (= current card-slug) nil card-slug))))
                                       [])

        handle-detail-click           (use-callback
                                       (fn [card-slug]
                                         (set-detail-card card-slug))
                                       [])

        handle-close-detail           (use-callback
                                       (fn []
                                         (set-detail-card nil))
                                       [])]

    (cond
      loading
      ($ :div {:class "flex flex-col items-center justify-center h-64 gap-4"}
         ($ spinner)
         ($ :p {:class "text-slate-500"} "Loading game..."))

      error
      ($ :div {:class "flex flex-col items-center justify-center h-64 gap-4"}
         ($ :div {:class "text-red-500 text-xl"} "!")
         ($ :p {:class "text-slate-700 font-medium"} "Error loading game")
         ($ :p {:class "text-slate-500 text-sm"} (str error)))

      (nil? game)
      ($ :div {:class "flex flex-col items-center justify-center h-64 gap-4"}
         ($ :p {:class "text-slate-500"} "Game not found"))

      :else
      ($ :div {:class "flex flex-col h-full"}
         ;; Connection status
         (when-not connected
           ($ :div {:class "bg-yellow-50 border-b border-yellow-200 px-4 py-2 text-sm text-yellow-800"}
              "Connecting to game server..."))

         ;; Header with game info
         ($ :div {:class "flex justify-between items-center p-4 border-b bg-white"}
            ($ :div {:class "text-sm text-slate-500"}
               ($ :span "Game: ")
               ($ :span {:class "font-mono"} (subs (:id game) 0 8)))
            ($ turn-indicator {:turn-number (:turn-number game-state)
                               :phase       (:phase game-state)
                               :active-player active-player
                               :is-my-turn  is-my-turn}))

         ;; Main content area
         ($ :div {:class "flex-1 flex p-4 bg-slate-100 gap-4 min-h-0"}
            ;; Left: Board and player info
            ($ :div {:class "flex-1 flex flex-col min-h-0"}
               ;; Opponent info
               ($ :div {:class "mb-2"}
                  ($ player-info {:player      opponent
                                  :team        opponent-team
                                  :score       (get score opponent-team 0)
                                  :is-active   (= active-player opponent-team)
                                  :is-opponent true}))

               ;; Game board
               ($ :div {:class "flex-1 bg-white rounded-lg border border-slate-200 p-2 min-h-0"}
                  ($ hex-grid {:board           (:board game-state)
                               :ball            (:ball game-state)
                               :home-players    home-players
                               :away-players    away-players
                               :selected-player selected-player
                               :valid-moves     valid-moves
                               :on-hex-click    handle-hex-click
                               :on-player-click handle-player-click}))

               ;; My info
               ($ :div {:class "mt-2"}
                  ($ player-info {:player     my-player
                                  :team       my-team
                                  :score      (get score my-team 0)
                                  :is-active  (= active-player my-team)})))

            ;; Right: Game log
            ($ :div {:class "w-64 bg-white rounded-lg border border-slate-200 overflow-hidden"}
               ($ game-log {:events     events
                            :max-height "100%"})))

         ;; Bottom: Hand and action bar
         ($ :div {:class "border-t bg-white"}
            ;; Player hand
            ($ :div {:class "px-4 pt-3 pb-1 border-b"}
               ($ :div {:class "text-xs font-medium text-slate-500 mb-1"} "Your Hand")
               ($ player-hand {:hand            my-hand
                               :selected-card   selected-card
                               :on-card-click   handle-card-click
                               :on-detail-click handle-detail-click
                               :disabled        (not is-my-turn)}))

            ;; Action bar
            ($ :div {:class "px-4 py-3"}
               ($ action-bar {:game-state      game-state
                              :my-team         my-team
                              :is-my-turn      is-my-turn
                              :selected-player selected-player
                              :on-end-turn     handle-end-turn
                              :loading         action-loading})))

         ;; Card detail modal
         ($ card-detail-modal {:open?     (some? detail-card)
                               :card-slug detail-card
                               :on-close  handle-close-detail})))))

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
