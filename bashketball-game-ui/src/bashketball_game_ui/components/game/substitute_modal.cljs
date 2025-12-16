(ns bashketball-game-ui.components.game.substitute-modal
  "Modal component for player substitution.

  Allows selecting an on-court player to take out and an off-court player to put in.
  Uses the substitute state machine for state management."
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

  Uses the substitute state machine for state management. The machine handles:
  - State transitions between selecting on-court and off-court players
  - Emitting the substitute action when selection is complete

  Props:
  - machine-state: substitute machine state `{:state :data}`
  - send: fn to send events to the substitute machine
  - on-court-players: vector of on-court player maps (players on court)
  - off-court-players: vector of off-court player maps"
  [{:keys [machine-state send on-court-players off-court-players]}]
  (let [state              (:state machine-state)
        data               (:data machine-state)
        open?              (not= state :inactive)
        selected-on-court  (:on-court-id data)
        in-off-court-phase (= state :selecting-off-court)]
    (when open?
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ;; Backdrop
         ($ :div {:class    "fixed inset-0 bg-black/50"
                  :on-click #(send {:type :cancel})})
         ;; Modal content
         ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"}
            ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-4"}
               "Substitute Player")

            ;; Step 1: Select on-court player to take out
            ($ :div {:class "mb-4"}
               ($ :p {:class "text-sm text-slate-600 mb-2"}
                  "Select player to take out:")
               ($ :div {:class "flex flex-wrap gap-2"}
                  (for [player on-court-players]
                    ($ player-button {:key      (:id player)
                                      :player   player
                                      :selected (= (:id player) selected-on-court)
                                      :on-click #(send {:type :select-on-court
                                                        :data {:player-id (:id player)}})}))))

            ;; Step 2: Select off-court player to put in
            ($ :div {:class "mb-6"}
               ($ :p {:class (str "text-sm mb-2 "
                                  (if in-off-court-phase "text-slate-600" "text-slate-400"))}
                  "Select replacement:")
               ($ :div {:class "flex flex-wrap gap-2"}
                  (for [player off-court-players]
                    ($ player-button {:key      (:id player)
                                      :player   player
                                      :selected false
                                      :on-click (when in-off-court-phase
                                                  #(send {:type :select-off-court
                                                          :data {:player-id (:id player)}}))}))))

            ;; Cancel button
            ($ :div {:class "flex justify-end"}
               ($ button {:variant  :outline
                          :on-click #(send {:type :cancel})}
                  "Cancel")))))))
