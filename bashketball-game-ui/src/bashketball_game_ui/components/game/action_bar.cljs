(ns bashketball-game-ui.components.game.action-bar
  "Action bar component for the game interface.

  Displays context-sensitive action buttons based on game state,
  selected player, and whose turn it is."
  (:require
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(defui action-bar
  "Displays available actions for current game state.

  Props:
  - game-state: Current game state map
  - my-team: :home or :away
  - is-my-turn: boolean
  - selected-player: Selected player ID or nil
  - on-end-turn: fn [] called when End Turn clicked
  - on-shoot: fn [] called when Shoot clicked
  - on-pass: fn [] called when Pass clicked
  - loading: boolean to disable all buttons"
  [{:keys [game-state my-team is-my-turn selected-player
           on-end-turn on-shoot on-pass loading]}]
  (let [can-shoot    (and is-my-turn
                          selected-player
                          (actions/player-has-ball? game-state selected-player)
                          (actions/can-shoot? game-state my-team))
        can-pass     (and is-my-turn
                          selected-player
                          (actions/player-has-ball? game-state selected-player)
                          (actions/can-pass? game-state my-team))
        has-moves    (and is-my-turn
                          selected-player
                          (seq (actions/valid-move-positions game-state selected-player)))]

    ($ :div {:class "flex items-center justify-between gap-4"}
       ;; Left side: status text
       ($ :div {:class "text-sm text-slate-600"}
          (cond
            (not is-my-turn)     "Waiting for opponent..."
            loading              "Submitting action..."
            selected-player      (if has-moves
                                   "Click a highlighted hex to move"
                                   "Select a player or action")
            :else                "Select a player to move"))

       ;; Right side: action buttons
       ($ :div {:class "flex items-center gap-2"}
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

          ;; End Turn button (always visible when it's my turn)
          ($ button {:variant  (if is-my-turn :default :secondary)
                     :size     :sm
                     :on-click on-end-turn
                     :disabled (or (not is-my-turn) loading)}
             (if loading "..." "End Turn"))))))
