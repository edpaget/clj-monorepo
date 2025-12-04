(ns bashketball-game-ui.components.game.action-bar
  "Action bar component for the game interface.

  Displays context-sensitive action buttons based on game state,
  selected player, and whose turn it is."
  (:require
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-game-ui.game.selectors :as sel]
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui]]))

(defui action-bar
  "Displays available actions for current game state.

  Props:
  - game-state: Current game state map
  - my-team: :home or :away
  - is-my-turn: boolean
  - phase: Current game phase keyword
  - selected-player: Selected player ID or nil
  - selected-card: Selected card slug or nil
  - pass-mode: boolean, true when in pass target selection mode
  - discard-mode: boolean, true when in discard selection mode
  - discard-count: Number of cards selected for discard
  - setup-placed-count: Number of starters placed during setup (0-3)
  - on-end-turn: fn [] called when End Turn clicked
  - on-shoot: fn [] called when Shoot clicked
  - on-pass: fn [] called when Pass clicked (enters pass mode)
  - on-cancel-pass: fn [] called to cancel pass mode
  - on-play-card: fn [] called when Play Card clicked
  - on-draw: fn [] called when Draw Card clicked
  - on-enter-discard: fn [] called to enter discard mode
  - on-cancel-discard: fn [] called to cancel discard mode
  - on-submit-discard: fn [] called to submit selected cards for discard
  - on-start-game: fn [] called when Start Game clicked (setup phase)
  - on-next-phase: fn [] called when Next Phase clicked
  - loading: boolean to disable all buttons"
  [{:keys [game-state my-team is-my-turn phase selected-player selected-card
           pass-mode discard-mode discard-count setup-placed-count
           on-end-turn on-shoot on-pass on-cancel-pass on-play-card on-draw
           on-enter-discard on-cancel-discard on-submit-discard on-start-game on-next-phase loading]}]
  (let [can-shoot        (and is-my-turn
                              selected-player
                              (actions/player-has-ball? game-state selected-player)
                              (actions/can-shoot? game-state my-team))
        can-pass         (and is-my-turn
                              selected-player
                              (actions/player-has-ball? game-state selected-player)
                              (actions/can-pass? game-state my-team))
        has-moves        (and is-my-turn
                              selected-player
                              (seq (actions/valid-move-positions game-state selected-player)))
        setup-mode       (sel/setup-mode? phase)
        setup-ready      (= setup-placed-count 3)
        next-phase-value (sel/next-phase phase)
        can-advance      (and is-my-turn (sel/can-advance-phase? phase))]

    ($ :div {:class "flex items-center justify-between gap-4"}
       ;; Left side: status text
       ($ :div {:class "text-sm text-slate-600"}
          (cond
            setup-mode           (if selected-player
                                   "Click a teal hex to place player"
                                   (str "Select a player to place (" setup-placed-count "/3)"))
            (not is-my-turn)     "Waiting for opponent..."
            loading              "Submitting action..."
            discard-mode         "Click cards to select for discard"
            pass-mode            "Click a teammate to pass the ball"
            selected-player      (if has-moves
                                   "Click a highlighted hex to move"
                                   "Select a player or action")
            :else                "Select a player to move"))

       ;; Right side: action buttons
       ($ :div {:class "flex items-center gap-2"}
          (cond
            ;; Discard mode buttons
            discard-mode
            ($ :<>
               ($ button {:variant  :outline
                          :size     :sm
                          :on-click on-cancel-discard
                          :disabled loading}
                  "Cancel")
               ($ button {:variant  :default
                          :size     :sm
                          :on-click on-submit-discard
                          :disabled (or loading (zero? discard-count))
                          :class    "bg-red-600 hover:bg-red-700"}
                  (str "Discard (" discard-count ")")))

            ;; Pass mode buttons
            pass-mode
            ($ button {:variant  :outline
                       :size     :sm
                       :on-click on-cancel-pass
                       :disabled loading}
               "Cancel")

            ;; Setup phase - show Start Game when all starters placed
            setup-mode
            ($ button {:variant  :default
                       :size     :sm
                       :on-click on-start-game
                       :disabled (or loading (not setup-ready))
                       :class    (if setup-ready
                                   "bg-green-600 hover:bg-green-700"
                                   "bg-slate-400")}
               (if setup-ready "Start Game" (str "Place Players (" setup-placed-count "/3)")))

            ;; Normal action buttons
            :else
            ($ :<>
               ;; Pass button
               (when can-pass
                 ($ button {:variant  :outline
                            :size     :sm
                            :on-click on-pass
                            :disabled (or (not is-my-turn) loading)}
                    "Pass"))

               ;; Shoot button
               (when can-shoot
                 ($ button {:variant  :outline
                            :size     :sm
                            :on-click on-shoot
                            :disabled (or (not is-my-turn) loading)
                            :class    "text-orange-600 border-orange-300 hover:bg-orange-50"}
                    "Shoot"))

               ;; Play Card button
               (when (and is-my-turn selected-card)
                 ($ button {:variant  :default
                            :size     :sm
                            :on-click on-play-card
                            :disabled loading
                            :class    "bg-purple-600 hover:bg-purple-700"}
                    "Play Card"))

               (when (and is-my-turn (= (keyword phase) :UPKEEP))
                 ($ button {:variant  :outline
                            :size     :sm
                            :on-click on-draw
                            :disabled loading}
                    "Draw Card"))

               ;; Discard button (to enter discard mode)
               (when is-my-turn
                 ($ button {:variant  :outline
                            :size     :sm
                            :on-click on-enter-discard
                            :disabled loading
                            :class    "text-red-600 border-red-300 hover:bg-red-50"}
                    "Discard"))

               ;; Next Phase button (visible in non-SETUP phases during your turn)
               (when can-advance
                 ($ button {:variant  :outline
                            :size     :sm
                            :on-click on-next-phase
                            :disabled loading
                            :class    "text-blue-600 border-blue-300 hover:bg-blue-50"}
                    (str "Next Phase (" (sel/phase-label next-phase-value) ")")))

               ;; End Turn button (always visible when it's my turn)
               ($ button {:variant  (if is-my-turn :default :secondary)
                          :size     :sm
                          :on-click on-end-turn
                          :disabled (or (not is-my-turn) loading)}
                  (if loading "..." "End Turn"))))))))
