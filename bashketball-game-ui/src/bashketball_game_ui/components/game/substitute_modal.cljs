(ns bashketball-game-ui.components.game.substitute-modal
  "Modal component for player substitution.

  Allows selecting a starter to take out and a bench player to put in."
  (:require
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui]]))

(defui player-button
  "Button representing a player for selection."
  [{:keys [player selected on-click]}]
  ($ :button
     {:class    (str "flex flex-col items-center p-2 rounded-lg border-2 transition-colors "
                     (if selected
                       "border-amber-500 bg-amber-50"
                       "border-slate-200 hover:border-slate-300 hover:bg-slate-50"))
      :on-click on-click}
     ($ :span {:class "font-medium text-sm truncate max-w-[80px]"}
        (:name player))
     ($ :span {:class "text-xs text-slate-500"}
        (str "SPD " (get-in player [:stats :speed])
             " SHT " (get-in player [:stats :shooting])))))

(defui substitute-modal
  "Modal for substituting players.

  Props:
  - open?: boolean, whether modal is open
  - starters: vector of starter player maps (players on court)
  - bench: vector of bench player maps
  - selected-starter: currently selected starter ID or nil
  - on-starter-select: fn [id] to select a starter
  - on-bench-select: fn [id] called when bench player selected (confirms substitution)
  - on-close: fn [] to close modal"
  [{:keys [open? starters bench selected-starter on-starter-select on-bench-select on-close]}]
  (when open?
    ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
       ;; Backdrop
       ($ :div {:class    "fixed inset-0 bg-black/50"
                :on-click on-close})
       ;; Modal content
       ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"}
          ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-4"}
             "Substitute Player")

          ;; Step 1: Select starter to take out
          ($ :div {:class "mb-4"}
             ($ :p {:class "text-sm text-slate-600 mb-2"}
                "Select player to take out:")
             ($ :div {:class "flex flex-wrap gap-2"}
                (for [player starters]
                  ($ player-button {:key          (:id player)
                                    :player       player
                                    :selected     (= (:id player) selected-starter)
                                    :on-click     #(on-starter-select (:id player))}))))

          ;; Step 2: Select bench player to put in
          ($ :div {:class "mb-6"}
             ($ :p {:class (str "text-sm mb-2 "
                                (if selected-starter "text-slate-600" "text-slate-400"))}
                "Select replacement from bench:")
             ($ :div {:class "flex flex-wrap gap-2"}
                (for [player bench]
                  ($ player-button {:key          (:id player)
                                    :player       player
                                    :selected     false
                                    :on-click     (when selected-starter
                                                    #(on-bench-select (:id player)))}))))

          ;; Cancel button
          ($ :div {:class "flex justify-end"}
             ($ button {:variant  :outline
                        :on-click on-close}
                "Cancel"))))))
