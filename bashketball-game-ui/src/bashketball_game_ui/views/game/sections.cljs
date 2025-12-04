(ns bashketball-game-ui.views.game.sections
  "Sub-components for the game view.

  Breaks down the game layout into reusable, focused components
  that handle specific sections of the UI."
  (:require
   [bashketball-game-ui.components.game.action-bar :refer [action-bar]]
   [bashketball-game-ui.components.game.game-log :refer [game-log]]
   [bashketball-game-ui.components.game.hex-grid :refer [hex-grid]]
   [bashketball-game-ui.components.game.player-hand :refer [player-hand]]
   [bashketball-game-ui.components.game.player-info :refer [player-info]]
   [bashketball-game-ui.components.game.roster-panel :refer [roster-panel]]
   [bashketball-game-ui.components.game.turn-indicator :refer [turn-indicator]]
   [bashketball-ui.components.loading :refer [spinner]]
   [uix.core :refer [$ defui]]))

(defui loading-state
  "Displays loading spinner while game data is being fetched."
  []
  ($ :div {:class "flex flex-col items-center justify-center h-64 gap-4"}
     ($ spinner)
     ($ :p {:class "text-slate-500"} "Loading game...")))

(defui error-state
  "Displays error message when game loading fails."
  [{:keys [error]}]
  ($ :div {:class "flex flex-col items-center justify-center h-64 gap-4"}
     ($ :div {:class "text-red-500 text-xl"} "!")
     ($ :p {:class "text-slate-700 font-medium"} "Error loading game")
     ($ :p {:class "text-slate-500 text-sm"} (str error))))

(defui not-found-state
  "Displays message when game is not found."
  []
  ($ :div {:class "flex flex-col items-center justify-center h-64 gap-4"}
     ($ :p {:class "text-slate-500"} "Game not found")))

(defui connection-banner
  "Shows connection status warning when disconnected."
  [{:keys [connected]}]
  (when-not connected
    ($ :div {:class "bg-yellow-50 border-b border-yellow-200 px-4 py-2 text-sm text-yellow-800"}
       "Connecting to game server...")))

(defui game-header
  "Header bar with game ID and turn indicator."
  [{:keys [game-id turn-number phase active-player is-my-turn]}]
  ($ :div {:class "flex justify-between items-center p-4 border-b bg-white"}
     ($ :div {:class "text-sm text-slate-500"}
        ($ :span "Game: ")
        ($ :span {:class "font-mono"} (subs (str game-id) 0 8)))
     ($ turn-indicator {:turn-number   turn-number
                        :phase         phase
                        :active-player active-player
                        :is-my-turn    is-my-turn})))

(defui board-section
  "Main game board with opponent info, hex grid, and player info."
  [{:keys [opponent opponent-team my-player my-team score active-player
           board ball home-players away-players
           selected-player valid-moves valid-setup-positions
           pass-mode valid-pass-targets
           on-hex-click on-player-click]}]
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
        ($ hex-grid {:board              board
                     :ball               ball
                     :home-players       home-players
                     :away-players       away-players
                     :selected-player    selected-player
                     :valid-moves        valid-moves
                     :setup-highlights   valid-setup-positions
                     :pass-mode          pass-mode
                     :valid-pass-targets valid-pass-targets
                     :on-hex-click       on-hex-click
                     :on-player-click    on-player-click}))

     ;; My info
     ($ :div {:class "mt-2"}
        ($ player-info {:player    my-player
                        :team      my-team
                        :score     (get score my-team 0)
                        :is-active (= active-player my-team)}))))

(defui side-panel
  "Right side panel - roster panel during setup, game log otherwise."
  [{:keys [setup-mode my-players my-starters my-team
           selected-player on-player-select events]}]
  (if setup-mode
    ($ :div {:class "w-64 flex flex-col gap-2"}
       ($ roster-panel {:players          my-players
                        :starters         my-starters
                        :team             my-team
                        :selected-player  selected-player
                        :on-player-select on-player-select}))
    ($ :div {:class "w-64 bg-white rounded-lg border border-slate-200 overflow-hidden"}
       ($ game-log {:events     events
                    :max-height "100%"}))))

(defui hand-section
  "Player's hand display with mode-aware label."
  [{:keys [my-hand selected-card discard-mode discard-cards discard-count
           on-card-click on-detail-click is-my-turn]}]
  ($ :div {:class "px-4 pt-3 pb-1 border-b"}
     ($ :div {:class "text-xs font-medium text-slate-500 mb-1"}
        (if discard-mode
          (str "Select cards to discard (" discard-count " selected)")
          "Your Hand"))
     ($ player-hand {:hand            my-hand
                     :selected-card   selected-card
                     :discard-mode    discard-mode
                     :discard-cards   discard-cards
                     :on-card-click   on-card-click
                     :on-detail-click on-detail-click
                     :disabled        (not is-my-turn)})))

(defui action-section
  "Action bar with all game actions."
  [{:keys [game-state my-team is-my-turn phase
           selected-player selected-card
           pass-mode discard-mode discard-count setup-placed-count
           my-setup-complete both-teams-ready
           on-end-turn on-shoot on-pass on-cancel-pass
           on-play-card on-draw
           on-enter-discard on-cancel-discard on-submit-discard
           on-start-game on-setup-done on-next-phase loading]}]
  ($ :div {:class "px-4 py-3"}
     ($ action-bar {:game-state         game-state
                    :my-team            my-team
                    :is-my-turn         is-my-turn
                    :phase              phase
                    :selected-player    selected-player
                    :selected-card      selected-card
                    :pass-mode          pass-mode
                    :discard-mode       discard-mode
                    :discard-count      discard-count
                    :setup-placed-count setup-placed-count
                    :my-setup-complete  my-setup-complete
                    :both-teams-ready   both-teams-ready
                    :on-end-turn        on-end-turn
                    :on-shoot           on-shoot
                    :on-pass            on-pass
                    :on-cancel-pass     on-cancel-pass
                    :on-play-card       on-play-card
                    :on-draw            on-draw
                    :on-enter-discard   on-enter-discard
                    :on-cancel-discard  on-cancel-discard
                    :on-submit-discard  on-submit-discard
                    :on-start-game      on-start-game
                    :on-setup-done      on-setup-done
                    :on-next-phase      on-next-phase
                    :loading            loading})))
