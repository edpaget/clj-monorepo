(ns bashketball-game-ui.components.game.roster-modal
  "Modal wrapper for viewing both team rosters."
  (:require
   [bashketball-game-ui.components.game.roster-panel :refer [roster-panel]]
   [uix.core :refer [$ defui]]))

(defui roster-modal
  "Dismissable modal showing both team rosters.

  Props:
  - open?: boolean to show/hide modal
  - home-players: map of player-id -> player for home team
  - home-starters: vector of home starter IDs
  - away-players: map of player-id -> player for away team
  - away-starters: vector of away starter IDs
  - on-close: fn [] to close the modal"
  [{:keys [open? home-players home-starters away-players away-starters on-close]}]
  (when open?
    ($ :div {:class    "fixed inset-0 z-50 flex items-center justify-center bg-black/50"
             :on-click on-close}
       ($ :div {:class    "relative bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 max-h-[80vh] flex flex-col"
                :on-click #(.stopPropagation %)}
          ;; Header
          ($ :div {:class "flex items-center justify-between px-4 py-3 border-b"}
             ($ :h2 {:class "text-lg font-semibold text-slate-900"} "Team Rosters")
             ($ :button {:class    "p-1 hover:bg-slate-100 rounded text-slate-500 hover:text-slate-700"
                         :on-click on-close}
                "\u2715"))
          ;; Content
          ($ :div {:class "flex-1 overflow-y-auto p-4 space-y-4"}
             ($ roster-panel {:players   away-players
                              :starters  away-starters
                              :team      :team/AWAY})
             ($ roster-panel {:players   home-players
                              :starters  home-starters
                              :team      :team/HOME}))))))
