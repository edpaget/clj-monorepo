(ns bashketball-game-ui.components.game.game-log-modal
  "Modal wrapper for the game log component."
  (:require
   [bashketball-game-ui.components.game.game-log :refer [game-log]]
   [uix.core :refer [$ defui]]))

(defui game-log-modal
  "Dismissable modal showing the game event log.

  Props:
  - open?: boolean to show/hide modal
  - events: vector of game events
  - on-close: fn [] to close the modal"
  [{:keys [open? events on-close]}]
  (when open?
    ($ :div {:class    "fixed inset-0 z-50 flex items-center justify-center bg-black/50"
             :on-click on-close}
       ($ :div {:class    "relative bg-white rounded-lg shadow-xl w-full max-w-md mx-4 max-h-[80vh] flex flex-col"
                :on-click #(.stopPropagation %)}
          ;; Header
          ($ :div {:class "flex items-center justify-between px-4 py-3 border-b"}
             ($ :h2 {:class "text-lg font-semibold text-slate-900"} "Game Log")
             ($ :button {:class    "p-1 hover:bg-slate-100 rounded text-slate-500 hover:text-slate-700"
                         :on-click on-close}
                "\u2715"))
          ;; Content
          ($ :div {:class "flex-1 overflow-y-auto p-4"}
             ($ game-log {:events events}))))))
