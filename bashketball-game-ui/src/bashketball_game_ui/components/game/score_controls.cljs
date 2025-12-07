(ns bashketball-game-ui.components.game.score-controls
  "Manual score adjustment controls for both teams.

  Provides +1, +2, +3 and -1 buttons for each team to manually
  increment or decrement scores during gameplay."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(defui score-button
  "Individual score adjustment button."
  [{:keys [label on-click disabled class]}]
  ($ :button {:class    (cn "px-2 py-1 text-xs font-medium rounded transition-colors"
                             "border border-slate-200 bg-white hover:bg-slate-100"
                             "disabled:opacity-50 disabled:cursor-not-allowed"
                             class)
              :disabled disabled
              :on-click on-click}
     label))

(defui team-score-controls
  "Score controls for a single team."
  [{:keys [team on-add-score disabled]}]
  (let [team-label (if (= team :HOME) "HOME" "AWAY")
        team-color (if (= team :HOME) "text-blue-600" "text-red-600")]
    ($ :div {:class "flex items-center gap-1"}
       ($ :span {:class (cn "text-xs font-medium w-10" team-color)} team-label)
       ($ score-button {:label    "-1"
                        :on-click #(on-add-score team -1)
                        :disabled disabled
                        :class    "text-red-600 hover:bg-red-50"})
       ($ score-button {:label    "+1"
                        :on-click #(on-add-score team 1)
                        :disabled disabled}))))

(defui score-controls
  "Manual score adjustment controls for both teams.

  Props:
  - on-add-score: fn [team points] - callback to add points to a team
  - disabled: boolean to disable all controls
  - loading: boolean to show loading state"
  [{:keys [on-add-score disabled loading]}]
  ($ :div {:class "flex items-center gap-4"}
     ($ :span {:class "text-xs font-medium text-slate-500"} "Score:")
     ($ team-score-controls {:team         :HOME
                             :on-add-score on-add-score
                             :disabled     (or disabled loading)})
     ($ team-score-controls {:team         :AWAY
                             :on-add-score on-add-score
                             :disabled     (or disabled loading)})))
