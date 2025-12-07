(ns bashketball-game-ui.views.game.sections
  "Sub-components for the game view.

  Each section consumes [[use-game-context]], [[use-game-derived]], and
  [[use-game-handlers]] directly to access game state and actions,
  eliminating prop drilling from the parent component."
  (:require
   [bashketball-game-ui.components.game.action-bar :refer [action-bar]]
   [bashketball-game-ui.components.game.bottom-bar :as bottom-bar]
   [bashketball-game-ui.components.game.game-header :as header]
   [bashketball-game-ui.components.game.game-log :refer [game-log]]
   [bashketball-game-ui.components.game.hex-grid :refer [hex-grid]]
   [bashketball-game-ui.components.game.player-hand :refer [player-hand]]
   [bashketball-game-ui.components.game.player-view-panel :refer [player-view-panel]]
   [bashketball-game-ui.components.game.roster-panel :refer [roster-panel]]
   [bashketball-game-ui.components.game.team-column :refer [team-column]]
   [bashketball-game-ui.context.game :refer [use-game-context]]
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-game-ui.game.selectors :as sel]
   [bashketball-game-ui.hooks.use-game-derived :refer [use-game-derived]]
   [bashketball-game-ui.hooks.use-game-handlers :refer [use-game-handlers]]
   [bashketball-ui.components.loading :refer [spinner]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui use-memo use-state]]))

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
  "Header bar with game ID and turn indicator.

  Props:
  - on-log-click: fn [] to open game log modal
  - on-roster-click: fn [] to open roster modal"
  [{:keys [on-log-click on-roster-click]}]
  (let [{:keys [game game-state is-my-turn]} (use-game-context)
        {:keys [phase score]}                (use-game-derived)]
    ($ header/game-header {:game-id         (:id game)
                           :turn-number     (:turn-number game-state)
                           :phase           phase
                           :home-score      (get score :HOME 0)
                           :away-score      (get score :AWAY 0)
                           :is-my-turn      is-my-turn
                           :on-log-click    on-log-click
                           :on-roster-click on-roster-click})))

(defui board-section
  "Main game board hex grid (player info moved to team columns)."
  []
  (let [{:keys [game-state]}                                  (use-game-context)
        {:keys [home-players away-players selected-player-id
                valid-moves valid-setup-positions pass-active
                valid-pass-targets ball-active]}              (use-game-derived)
        {:keys [on-hex-click on-player-click on-ball-click]}  (use-game-handlers)]
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
                    :on-ball-click      on-ball-click}))))

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

;; -----------------------------------------------------------------------------
;; New Three-Column Layout Sections

(defui team-column-section
  "Team column for the three-column layout.

  Props:
  - team: :HOME or :AWAY
  - on-info-click: fn [card-slug] for card detail modal"
  [{:keys [team on-info-click]}]
  (let [{:keys [game-state my-team selection]}              (use-game-context)
        {:keys [home-players away-players score
                active-player selected-player-id setup-mode
                home-starters away-starters]}               (use-game-derived)
        {:keys [on-player-click]}                           (use-game-handlers)

        players                                             (if (= team :HOME) home-players away-players)
        starters                                            (if (= team :HOME) home-starters away-starters)

        ;; Filter to only on-court players (starters with positions)
        on-court                                            (use-memo
                                                             #(into {}
                                                                    (filter (fn [[_id p]] (some? (:position p))))
                                                                    players)
                                                             [players])

        player-indices                                      (use-memo
                                                             #(sel/build-player-index-map on-court)
                                                             [on-court])

        ;; Get selected player if from this team
        selected-player                                     (when (and selected-player-id
                                                                       (contains? on-court selected-player-id))
                                                              (get on-court selected-player-id))

        ;; Deck stats for this team
        deck-stats                                          (use-memo
                                                             #(sel/deck-stats game-state team)
                                                             [game-state team])

        ;; Only show setup roster for user's own team
        show-setup                                          (and setup-mode (= team my-team))

        ;; TODO: assets will come from game-state when implemented
        assets                                              []]
    ($ team-column {:team               team
                    :score              (get score team 0)
                    :is-active          (= active-player team)
                    :deck-stats         deck-stats
                    :players            on-court
                    :player-indices     player-indices
                    :selected-player    selected-player
                    :assets             assets
                    :on-select          on-player-click
                    :on-deselect        (:clear-selected-player selection)
                    :on-info-click      on-info-click
                    :setup-mode         show-setup
                    :all-players        players
                    :starters           starters
                    :selected-player-id selected-player-id
                    :on-player-select   (:set-selected-player selection)})))

(defn- build-action-list
  "Builds the action list for the bottom bar from game state and handlers."
  [{:keys [game-state my-team is-my-turn phase
           selected-player-id selected-card pass-active discard-active
           discard-count my-setup-complete both-teams-ready
           on-end-turn on-shoot on-pass on-cancel-pass on-play-card on-draw
           on-enter-discard on-cancel-discard on-submit-discard on-start-game
           on-setup-done on-next-phase on-reveal-fate on-shuffle on-return-discard
           on-substitute]}]
  (let [setup-mode      (sel/setup-mode? phase)
        can-shoot       (and is-my-turn
                             selected-player-id
                             (actions/player-has-ball? game-state selected-player-id)
                             (actions/can-shoot? game-state my-team))
        can-pass        (and is-my-turn
                             selected-player-id
                             (actions/player-has-ball? game-state selected-player-id)
                             (actions/can-pass? game-state my-team))
        can-advance     (and is-my-turn (sel/can-advance-phase? phase))
        next-phase-val  (sel/next-phase phase)
        ;; Away player sees Start Game when both teams ready during setup
        _ (prn setup-mode both-teams-ready my-team)
        show-start-game (and setup-mode both-teams-ready (= my-team :AWAY))
        _ (prn show-start-game)
        ;; Show End Turn unless it's setup with both teams ready and we're away
        show-end-turn   (and is-my-turn
                             (not discard-active)
                             (not pass-active)
                             (not show-start-game))]

    (cond-> []
      ;; End turn (primary) - also available during setup to pass turn
      show-end-turn
      (conj {:id       :end-turn
             :label    "End Turn"
             :on-click on-end-turn
             :variant  :default})

      ;; Pass (primary)
      (and can-pass (not pass-active) (not discard-active))
      (conj {:id       :pass
             :label    "Pass"
             :on-click on-pass})

      ;; Cancel pass
      pass-active
      (conj {:id       :cancel-pass
             :label    "Cancel"
             :on-click on-cancel-pass})

      ;; Shoot (primary)
      (and can-shoot (not pass-active) (not discard-active))
      (conj {:id       :shoot
             :label    "Shoot"
             :on-click on-shoot
             :class    "text-orange-600 border-orange-300 hover:bg-orange-50"})

      ;; Play Card (primary)
      (and is-my-turn selected-card (not pass-active) (not discard-active))
      (conj {:id       :play-card
             :label    "Play Card"
             :on-click on-play-card
             :variant  :default
             :class    "bg-purple-600 hover:bg-purple-700"})

      ;; Draw Card (secondary)
      (and is-my-turn (not setup-mode) (not pass-active) (not discard-active))
      (conj {:id       :draw
             :label    "Draw Card"
             :on-click on-draw})

      ;; Reveal Fate (secondary)
      (and is-my-turn (actions/can-reveal-fate? game-state my-team) (not pass-active) (not discard-active))
      (conj {:id       :reveal-fate
             :label    "Reveal Fate"
             :on-click on-reveal-fate
             :class    "text-purple-600 border-purple-300 hover:bg-purple-50"})

      ;; Enter Discard (secondary)
      (and is-my-turn (not setup-mode) (not pass-active) (not discard-active))
      (conj {:id       :discard
             :label    "Discard"
             :on-click on-enter-discard
             :class    "text-red-600 border-red-300 hover:bg-red-50"})

      ;; Cancel Discard
      discard-active
      (conj {:id       :cancel-discard
             :label    "Cancel"
             :on-click on-cancel-discard})

      ;; Submit Discard
      discard-active
      (conj {:id       :submit-discard
             :label    (str "Discard (" discard-count ")")
             :on-click on-submit-discard
             :disabled (zero? discard-count)
             :variant  :default
             :class    "bg-red-600 hover:bg-red-700"})

      ;; Shuffle (secondary)
      (and is-my-turn (not setup-mode) (not pass-active) (not discard-active))
      (conj {:id       :shuffle
             :label    "Shuffle"
             :on-click on-shuffle
             :class    "text-emerald-600 border-emerald-300 hover:bg-emerald-50"})

      ;; Return Discard (secondary)
      (and is-my-turn (not setup-mode) (not pass-active) (not discard-active))
      (conj {:id       :return-discard
             :label    "Return Discard"
             :on-click on-return-discard
             :class    "text-emerald-600 border-emerald-300 hover:bg-emerald-50"})

      ;; Substitute (secondary)
      (and is-my-turn (actions/can-substitute? game-state my-team) (not pass-active) (not discard-active))
      (conj {:id       :substitute
             :label    "Substitute"
             :on-click on-substitute
             :class    "text-amber-600 border-amber-300 hover:bg-amber-50"})

      ;; Next Phase (secondary)
      (and can-advance (not pass-active) (not discard-active))
      (conj {:id       :next-phase
             :label    (str "Next (" (sel/phase-label next-phase-val) ")")
             :on-click on-next-phase
             :class    "text-blue-600 border-blue-300 hover:bg-blue-50"})

      ;; Setup: Start Game (away player starts the game when both teams ready)
      show-start-game
      (conj {:id       :start-game
             :label    "Start Game"
             :on-click on-start-game
             :variant  :default
             :class    "bg-green-600 hover:bg-green-700"})

      ;; Setup: Done
      (and setup-mode my-setup-complete (not both-teams-ready) is-my-turn)
      (conj {:id       :setup-done
             :label    "Done"
             :on-click on-setup-done
             :variant  :default
             :class    "bg-blue-600 hover:bg-blue-700"}))))

(defn- build-status-text
  "Builds status text for the action bar."
  [{:keys [is-my-turn setup-mode pass-active discard-active
           selected-player-id setup-placed-count my-setup-complete
           has-moves loading]}]
  (cond
    loading              "Submitting action..."
    setup-mode           (cond
                           (not is-my-turn)     "Waiting for opponent..."
                           my-setup-complete    "Waiting for opponent"
                           selected-player-id   "Click hex to place"
                           :else                (str "Place (" setup-placed-count "/3)"))
    (not is-my-turn)     "Opponent's turn"
    discard-active       "Select cards to discard"
    pass-active          "Select pass target"
    selected-player-id   (if has-moves "Click hex to move" "Select action")
    :else                "Select a player"))

(defui bottom-bar-section
  "Bottom bar with hand and actions."
  []
  (let [{:keys [game-state catalog my-team is-my-turn actions pass discard substitute-mode detail-modal]}
        (use-game-context)

        {:keys [phase selected-player-id selected-card pass-active discard-active
                discard-cards setup-placed-count my-setup-complete both-teams-ready
                my-hand]}
        (use-game-derived)

        {:keys [on-card-click on-end-turn on-shoot on-play-card on-draw on-start-game
                on-setup-done on-next-phase on-submit-discard on-reveal-fate
                on-shuffle on-return-discard]}
        (use-game-handlers)

        [hand-expanded set-hand-expanded]                                                                 (use-state false)

        setup-mode                                                                                        (sel/setup-mode? phase)
        has-moves                                                                                         (and is-my-turn
                                                                                                               selected-player-id
                                                                                                               (seq (actions/valid-move-positions game-state selected-player-id)))
        loading                                                                                           (:loading actions)

        action-list                                                                                       (build-action-list
                                                                                                           {:game-state         game-state
                                                                                                            :my-team            my-team
                                                                                                            :is-my-turn         is-my-turn
                                                                                                            :phase              phase
                                                                                                            :selected-player-id selected-player-id
                                                                                                            :selected-card      selected-card
                                                                                                            :pass-active        pass-active
                                                                                                            :discard-active     discard-active
                                                                                                            :discard-count      (count discard-cards)
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
                                                                                                            :on-substitute      (:enter substitute-mode)})

        status-text                                                                                       (build-status-text
                                                                                                           {:is-my-turn         is-my-turn
                                                                                                            :setup-mode         setup-mode
                                                                                                            :pass-active        pass-active
                                                                                                            :discard-active     discard-active
                                                                                                            :selected-player-id selected-player-id
                                                                                                            :setup-placed-count (or setup-placed-count 0)
                                                                                                            :my-setup-complete  my-setup-complete
                                                                                                            :has-moves          has-moves
                                                                                                            :loading            loading})]

    ($ bottom-bar/bottom-bar
       {:hand             my-hand
        :catalog          catalog
        :expanded         hand-expanded
        :on-expand-toggle #(set-hand-expanded not)
        :selected-card    selected-card
        :discard-mode     discard-active
        :discard-cards    discard-cards
        :on-card-click    on-card-click
        :on-detail-click  (:show detail-modal)
        :disabled         (not is-my-turn)
        :actions          action-list
        :loading          loading
        :status-text      status-text})))

(defui compact-team-panel
  "Compact team panel for small screens (below board).

  Props:
  - team: :HOME or :AWAY
  - on-info-click: fn [card-slug] for card detail modal"
  [{:keys [team on-info-click]}]
  (let [{:keys [selection]}                        (use-game-context)
        {:keys [home-players away-players score
                active-player selected-player-id]} (use-game-derived)
        {:keys [on-player-click]}                  (use-game-handlers)

        players                                    (if (= team :HOME) home-players away-players)
        team-label                                 (if (= team :HOME) "Home" "Away")
        team-color                                 (if (= team :HOME) "bg-blue-500" "bg-red-500")
        is-active                                  (= active-player team)

        ;; Filter to only on-court players
        on-court                                   (use-memo
                                                    #(into {}
                                                           (filter (fn [[_id p]] (some? (:position p))))
                                                           players)
                                                    [players])

        player-indices                             (use-memo
                                                    #(sel/build-player-index-map on-court)
                                                    [on-court])

        ;; TODO: assets will come from game-state when implemented
        assets                                     []]

    ($ :div {:class (cn "flex-1 p-2 rounded-lg border"
                        (if is-active "bg-slate-50 border-slate-300" "bg-white border-slate-200"))}
       ;; Header row
       ($ :div {:class "flex items-center justify-between mb-2"}
          ($ :div {:class "flex items-center gap-2"}
             ($ :div {:class (cn "w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold text-white" team-color)}
                (first team-label))
             ($ :span {:class "text-sm font-medium"} team-label))
          ($ :span {:class "text-lg font-bold"} (get score team 0)))

       ;; Players list (collapsed)
       ($ :details {:class "text-xs"}
          ($ :summary {:class "cursor-pointer text-slate-500 hover:text-slate-700"}
             (str "Players (" (count on-court) ")"))
          ($ :div {:class "mt-1 pl-2"}
             (for [[id player] on-court
                   :let        [idx (get player-indices id 1)
                                prefix (if (= team :HOME) "H" "A")]]
               ($ :div {:key   id
                        :class "flex items-center gap-1 py-0.5 cursor-pointer hover:bg-slate-100 rounded"
                        :on-click #(on-player-click id)}
                  ($ :span {:class (cn "w-4 h-4 rounded-full flex items-center justify-center text-[8px] font-bold text-white" team-color)}
                     (str prefix idx))
                  ($ :span {:class "truncate"} (:name player))))))

       ;; Assets (collapsed)
       ($ :details {:class "text-xs mt-1"}
          ($ :summary {:class "cursor-pointer text-slate-500 hover:text-slate-700"}
             (str "Assets (" (count assets) ")"))
          (when (seq assets)
            ($ :div {:class "mt-1 pl-2"}
               (for [{:keys [card-slug name]} assets]
                 ($ :div {:key      card-slug
                          :class    "py-0.5 cursor-pointer hover:bg-slate-100 rounded"
                          :on-click #(on-info-click card-slug)}
                    (or name card-slug)))))))))
