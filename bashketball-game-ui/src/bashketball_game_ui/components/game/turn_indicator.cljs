(ns bashketball-game-ui.components.game.turn-indicator
  "Turn and phase indicator component.

  Displays the current turn number, game phase, active player, and
  whether it's the current user's turn."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def ^:private phase-labels
  {:SETUP       "Setup"
   :UPKEEP      "Upkeep"
   :DRAW        "Draw"
   :ACTIONS     "Actions"
   :RESOLUTION  "Resolution"
   :END_OF_TURN "End of Turn"
   :GAME_OVER   "Game Over"})

(defn- phase-label
  "Returns human-readable label for a phase."
  [phase]
  (get phase-labels (keyword phase) (str phase)))

(defui turn-indicator
  "Displays current turn and phase information.

  Props:
  - turn-number: Current turn number
  - phase: Current phase keyword or string
  - active-player: :home or :away (or string)
  - is-my-turn: boolean indicating if it's the user's turn"
  [{:keys [turn-number phase active-player is-my-turn]}]
  (let [active-team (if (string? active-player)
                      (keyword active-player)
                      active-player)]
    ($ :div {:class "flex items-center gap-4 px-4 py-2 rounded-lg bg-slate-100"}
       ($ :div {:class "text-sm text-slate-600"}
          ($ :span {:class "font-medium"} "Turn ")
          ($ :span {:class "text-lg font-bold text-slate-900"} (or turn-number "—")))

       ($ :div {:class "w-px h-6 bg-slate-300"})

       ($ :div {:class "text-sm"}
          ($ :span {:class "text-slate-600"} "Phase: ")
          ($ :span {:class "font-medium text-slate-900"} (phase-label phase)))

       ($ :div {:class "w-px h-6 bg-slate-300"})

       ($ :div {:class (cn "flex items-center gap-2 px-3 py-1 rounded-full text-sm font-medium"
                           (if is-my-turn
                             "bg-green-100 text-green-800"
                             "bg-slate-200 text-slate-600"))}
          ($ :span {:class (cn "w-2 h-2 rounded-full"
                               (if (= active-team :home)
                                 "bg-blue-500"
                                 "bg-red-500"))})
          (if is-my-turn
            "Your Turn"
            (str (if (= active-team :home) "Home" "Away") "'s Turn"))))))
