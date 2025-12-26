(ns bashketball-game-ui.components.game.choice-modal
  "Modal component for presenting choices to players.

  Used when game effects require player decisions, such as:
  - Defender response options
  - Ability activation choices
  - Card selection"
  (:require
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui]]))

(defn- choice-type-label
  "Returns human-readable label for a choice type."
  [choice-type]
  (case choice-type
    :defender-response "Defender Response"
    :ability-activation "Activate Ability"
    :target-selection "Select Target"
    :mode-selection "Select Mode"
    (name choice-type)))

(defui option-button
  "Button for selecting a choice option."
  [{:keys [option on-select]}]
  (let [{:keys [id label disabled reason]} option]
    ($ :button
       {:class (str "w-full flex flex-col p-4 rounded-lg border-2 transition-colors text-left "
                    (if disabled
                      "border-gray-200 bg-gray-50 opacity-60 cursor-not-allowed"
                      "border-slate-200 hover:border-indigo-400 hover:bg-indigo-50"))
        :disabled disabled
        :on-click (when-not disabled #(on-select id))}
       ($ :span {:class (if disabled
                          "font-semibold text-gray-400"
                          "font-semibold text-slate-800")}
          label)
       (when reason
         ($ :span {:class "text-xs text-gray-400 mt-1"}
            reason)))))

(defui choice-modal
  "Modal for presenting choices to a player.

  Props:
  - open?: boolean, whether modal is open
  - choice: map with choice data
    - :type - choice type keyword
    - :options - vector of option maps [{:id :label :disabled :reason}]
    - :waiting-for - team that needs to respond
  - on-select: fn [option-id] called when option selected
  - on-cancel: fn [] called to cancel (optional, hides cancel button if nil)"
  [{:keys [open? choice on-select on-cancel]}]
  (when open?
    (let [{:keys [type options waiting-for]} choice]
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ;; Backdrop (no click-to-close since choice is required)
         ($ :div {:class "fixed inset-0 bg-black/50"})
         ;; Modal content
         ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"}
            ;; Header
            ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-2"}
               (choice-type-label type))

            ;; Waiting for indicator
            (when waiting-for
              ($ :p {:class "text-sm text-slate-500 mb-4"}
                 (str (if (= waiting-for :team/HOME) "Home" "Away")
                      " team must choose")))

            ;; Options
            ($ :div {:class "space-y-3 mb-4"}
               (for [option options]
                 ($ option-button {:key (:id option)
                                   :option option
                                   :on-select on-select})))

            ;; Cancel button (optional)
            (when on-cancel
              ($ :div {:class "flex justify-end pt-2 border-t border-gray-100"}
                 ($ button {:variant :outline
                            :on-click on-cancel}
                    "Cancel"))))))))
