(ns bashketball-game-ui.components.game.standard-action-modal
  "Modal component for selecting a standard action to play.

  Displays the three standard action options (Shoot/Block, Pass/Steal, Screen/Check)
  and calls the callback with the selected card slug."
  (:require
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui]]))

(def ^:private standard-actions
  "The three standard action cards available to all players."
  [{:slug        "shoot-block"
    :name        "Shoot / Block"
    :offense     "Ball carrier within 7 hexes of basket attempts Shot"
    :defense     "Force opponent to shoot or exhaust"}
   {:slug        "pass-steal"
    :name        "Pass / Steal"
    :offense     "Ball carrier attempts Pass to teammate within 6 hexes"
    :defense     "Engage ball carrier within 2 hexes. Attempt steal."}
   {:slug        "screen-check"
    :name        "Screen / Check"
    :offense     "Engage defender within 2 hexes. Screen play."
    :defense     "Engage opponent within 2 hexes. Check play."}])

(defui action-button
  "Button for selecting a standard action."
  [{:keys [action on-click]}]
  ($ :button
     {:class    "flex flex-col p-4 rounded-lg border-2 border-slate-200
                hover:border-indigo-400 hover:bg-indigo-50 transition-colors text-left"
      :on-click #(on-click (:slug action))}
     ($ :span {:class "font-semibold text-slate-800 mb-2"}
        (:name action))
     ($ :div {:class "text-xs text-slate-500 space-y-1"}
        ($ :div
           ($ :span {:class "font-medium text-green-600"} "OFF: ")
           (:offense action))
        ($ :div
           ($ :span {:class "font-medium text-red-600"} "DEF: ")
           (:defense action)))))

(defui standard-action-modal
  "Modal for selecting a standard action to play.

  Props:
  - open?: boolean, whether modal is open
  - on-select: fn [card-slug] called when an action is selected
  - on-close: fn [] to close modal without selection"
  [{:keys [open? on-select on-close]}]
  (when open?
    ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
       ($ :div {:class    "fixed inset-0 bg-black/50"
                :on-click on-close})
       ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-lg mx-4"}
          ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-2"}
             "Select Standard Action")
          ($ :p {:class "text-sm text-slate-500 mb-4"}
             "Choose which standard action to play")

          ($ :div {:class "space-y-3 mb-4"}
             (for [action standard-actions]
               ($ action-button {:key      (:slug action)
                                 :action   action
                                 :on-click on-select})))

          ($ :div {:class "flex justify-end"}
             ($ button {:variant  :outline
                        :on-click on-close}
                "Cancel"))))))
