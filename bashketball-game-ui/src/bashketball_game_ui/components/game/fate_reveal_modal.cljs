(ns bashketball-game-ui.components.game.fate-reveal-modal
  "Modal component for displaying revealed fate value."
  (:require
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui]]))

(defui fate-reveal-modal
  "Displays the revealed fate value.

  Props:
  - open?: boolean, true if modal should be shown
  - fate: integer, the revealed fate value
  - on-close: fn [] called to close modal"
  [{:keys [open? fate on-close]}]
  (when open?
    ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
       ;; Backdrop
       ($ :div {:class    "fixed inset-0 bg-black/50"
                :on-click on-close})
       ;; Modal content
       ($ :div {:class "relative bg-white rounded-lg shadow-xl p-8 w-full max-w-xs mx-4 text-center"}
          ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-4"}
             "Fate Revealed")
          ;; Large fate number
          ($ :div {:class "text-7xl font-bold text-purple-600 mb-4"}
             fate)
          ($ :p {:class "text-sm text-gray-500 mb-6"}
             "Card moved to discard pile")
          ($ button {:variant  :default
                     :on-click on-close
                     :class    "w-full"}
             "OK")))))
