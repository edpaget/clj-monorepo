(ns bashketball-game-ui.views.game.sections
  "Sub-components for the game view.

  Each section consumes [[use-game-context]], [[use-game-derived]], and
  [[use-game-handlers]] directly to access game state and actions,
  eliminating prop drilling from the parent component."
  (:require
   [bashketball-game-ui.components.game.action-bar :refer [action-bar]]
   [bashketball-game-ui.components.game.game-log :refer [game-log]]
   [bashketball-game-ui.components.game.hex-grid :refer [hex-grid]]
   [bashketball-game-ui.components.game.player-hand :refer [player-hand]]
   [bashketball-game-ui.components.game.player-info :refer [player-info]]
   [bashketball-game-ui.components.game.player-view-panel :refer [player-view-panel]]
   [bashketball-game-ui.components.game.roster-panel :refer [roster-panel]]
   [bashketball-game-ui.components.game.turn-indicator :refer [turn-indicator]]
   [bashketball-game-ui.context.game :refer [use-game-context]]
   [bashketball-game-ui.hooks.use-game-derived :refer [use-game-derived]]
   [bashketball-game-ui.hooks.use-game-handlers :refer [use-game-handlers]]
   [bashketball-ui.components.loading :refer [spinner]]
   [bashketball-ui.utils :refer [cn]]
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
  []
  (let [{:keys [connected]} (use-game-context)]
    (when-not connected
      ($ :div {:class "bg-yellow-50 border-b border-yellow-200 px-4 py-2 text-sm text-yellow-800"}
         "Connecting to game server..."))))

(defui game-header
  "Header bar with game ID and turn indicator."
  []
  (let [{:keys [game game-state is-my-turn]} (use-game-context)
        {:keys [phase active-player]}        (use-game-derived)]
    ($ :div {:class "flex justify-between items-center p-4 border-b bg-white"}
       ($ :div {:class "text-sm text-slate-500"}
          ($ :span "Game: ")
          ($ :span {:class "font-mono"} (subs (str (:id game)) 0 8)))
       ($ turn-indicator {:turn-number   (:turn-number game-state)
                          :phase         phase
                          :active-player active-player
                          :is-my-turn    is-my-turn}))))

(defui board-section
  "Main game board with opponent info, hex grid, and player info."
  []
  (let [{:keys [game-state my-team]}                                 (use-game-context)
        {:keys [opponent opponent-team my-player score active-player
                home-players away-players selected-player-id
                valid-moves valid-setup-positions pass-active
                valid-pass-targets ball-active]}                     (use-game-derived)
        {:keys [on-hex-click on-player-click on-ball-click]}         (use-game-handlers)]
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
          ($ hex-grid {:board              (:board game-state)
                       :ball               (:ball game-state)
                       :home-players       home-players
                       :away-players       away-players
                       :selected-player    selected-player-id
                       :valid-moves        valid-moves
                       :setup-highlights   valid-setup-positions
                       :pass-mode          pass-active
                       :valid-pass-targets valid-pass-targets
                       :ball-selected      ball-active
                       :on-hex-click       on-hex-click
                       :on-player-click    on-player-click
                       :on-ball-click      on-ball-click}))

       ;; My info
       ($ :div {:class "mt-2"}
          ($ player-info {:player    my-player
                          :team      my-team
                          :score     (get score my-team 0)
                          :is-active (= active-player my-team)})))))

(defui side-panel-tabs
  "Tab buttons for switching between log and players views."
  [{:keys [mode on-toggle]}]
  ($ :div {:class "flex border-b border-slate-200"}
     ($ :button
        {:class    (cn "flex-1 px-3 py-2 text-xs font-medium"
                       (if (= mode :log)
                         "text-slate-800 bg-white border-b-2 border-blue-500"
                         "text-slate-500 bg-slate-50 hover:bg-slate-100"))
         :on-click #(when (not= mode :log) (on-toggle))}
        "Game Log")
     ($ :button
        {:class    (cn "flex-1 px-3 py-2 text-xs font-medium"
                       (if (= mode :players)
                         "text-slate-800 bg-white border-b-2 border-blue-500"
                         "text-slate-500 bg-slate-50 hover:bg-slate-100"))
         :on-click #(when (not= mode :players) (on-toggle))}
        "Players")))

(defui side-panel
  "Right side panel - roster panel during setup, tabbed log/players otherwise.

  Props:
  - side-panel-mode: map from use-side-panel-mode hook
  - on-info-click: fn [card-slug] for viewing player cards"
  [{:keys [side-panel-mode on-info-click]}]
  (let [{:keys [my-team selection]}                                 (use-game-context)
        {:keys [setup-mode my-players my-starters events
                selected-player-id home-players away-players
                home-starters away-starters home-bench away-bench]} (use-game-derived)
        {:keys [mode toggle]}                                       side-panel-mode]
    (if setup-mode
      ($ :div {:class "w-64 flex flex-col gap-2"}
         ($ roster-panel {:players          my-players
                          :starters         my-starters
                          :team             my-team
                          :selected-player  selected-player-id
                          :on-player-select (:set-selected-player selection)}))
      ($ :div {:class "w-64 h-full min-h-0 flex flex-col bg-white rounded-lg border border-slate-200 overflow-hidden"}
         ($ side-panel-tabs {:mode mode :on-toggle toggle})
         ($ :div {:class "flex-1 min-h-0 overflow-hidden"}
            (if (= mode :players)
              ($ player-view-panel {:home-players   home-players
                                    :away-players   away-players
                                    :home-starters  home-starters
                                    :away-starters  away-starters
                                    :home-bench     home-bench
                                    :away-bench     away-bench
                                    :on-info-click  on-info-click})
              ($ game-log {:events events})))))))

(defui hand-section
  "Player's hand display with mode-aware label."
  []
  (let [{:keys [is-my-turn discard detail-modal]}                    (use-game-context)
        {:keys [my-hand selected-card discard-active discard-cards]} (use-game-derived)
        {:keys [on-card-click]}                                      (use-game-handlers)
        discard-count                                                (count discard-cards)]
    ($ :div {:class "px-4 pt-3 pb-1 border-b"}
       ($ :div {:class "text-xs font-medium text-slate-500 mb-1"}
          (if discard-active
            (str "Select cards to discard (" discard-count " selected)")
            "Your Hand"))
       ($ player-hand {:hand            my-hand
                       :selected-card   selected-card
                       :discard-mode    discard-active
                       :discard-cards   discard-cards
                       :on-card-click   on-card-click
                       :on-detail-click (:show detail-modal)
                       :disabled        (not is-my-turn)}))))

(defui action-section
  "Action bar with all game actions."
  []
  (let [{:keys [game-state my-team is-my-turn actions pass discard substitute-mode]}
        (use-game-context)

        {:keys [phase selected-player-id selected-card pass-active discard-active
                setup-placed-count my-setup-complete both-teams-ready]}
        (use-game-derived)

        {:keys [on-end-turn on-shoot on-play-card on-draw on-start-game
                on-setup-done on-next-phase on-submit-discard on-reveal-fate
                on-shuffle on-return-discard]}
        (use-game-handlers)]
    ($ :div {:class "px-4 py-3"}
       ($ action-bar {:game-state         game-state
                      :my-team            my-team
                      :is-my-turn         is-my-turn
                      :phase              phase
                      :selected-player    selected-player-id
                      :selected-card      selected-card
                      :pass-mode          pass-active
                      :discard-mode       discard-active
                      :discard-count      (count (:cards discard))
                      :setup-placed-count (or setup-placed-count 0)
                      :my-setup-complete  my-setup-complete
                      :both-teams-ready   both-teams-ready
                      :on-end-turn        on-end-turn
                      :on-shoot           on-shoot
                      :on-pass            (:start pass)
                      :on-cancel-pass     (:cancel pass)
                      :on-play-card       on-play-card
                      :on-draw            on-draw
                      :on-enter-discard   (:enter discard)
                      :on-cancel-discard  (:cancel discard)
                      :on-submit-discard  on-submit-discard
                      :on-start-game      on-start-game
                      :on-setup-done      on-setup-done
                      :on-next-phase      on-next-phase
                      :on-reveal-fate     on-reveal-fate
                      :on-shuffle         on-shuffle
                      :on-return-discard  on-return-discard
                      :on-substitute      (:enter substitute-mode)
                      :loading            (:loading actions)}))))
