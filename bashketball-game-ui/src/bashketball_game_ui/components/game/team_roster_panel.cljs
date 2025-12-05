(ns bashketball-game-ui.components.game.team-roster-panel
  "Team roster panel showing all players grouped by field/bench status.

  Displays starters (on court) and bench players separately."
  (:require
   [bashketball-game-ui.components.game.player-roster-item :refer [player-roster-item]]
   [bashketball-game-ui.game.selectors :as sel]
   [uix.core :refer [$ defui]]))

(defui team-roster-panel
  "Shows all players for one team, grouped by on-court and bench.

  Props:
  - team: :HOME or :AWAY
  - team-label: Display label for the team (e.g., \"HOME\" or custom name)
  - players: Map of all players {id -> BasketballPlayer}
  - starters: Vector of starter player IDs (on court)
  - bench: Vector of bench player IDs
  - player-indices: Map of {id -> player-num} for token labels
  - on-info-click: fn [card-slug] to open card detail modal"
  [{:keys [team team-label players starters bench player-indices on-info-click]}]
  (let [starter-players (->> starters
                             (map #(sel/get-player-by-id players %))
                             (filter some?))
        bench-players   (->> bench
                             (map #(sel/get-player-by-id players %))
                             (filter some?))]
    ($ :div {:class "flex flex-col"}
       ;; Team header
       ($ :div {:class "px-3 py-2 bg-slate-100 border-b border-slate-200"}
          ($ :span {:class "text-sm font-semibold text-slate-700"}
             team-label))

       ;; On Court section
       ($ :div {:class "px-2 py-1"}
          ($ :div {:class "text-xs font-medium text-slate-500 uppercase px-2 py-1"}
             (str "On Court (" (count starter-players) ")"))
          (if (seq starter-players)
            (for [player starter-players
                  :let   [id (:id player)]]
              ($ player-roster-item
                 {:key           id
                  :player        player
                  :player-num    (get player-indices id)
                  :team          team
                  :on-field?     true
                  :on-info-click on-info-click}))
            ($ :div {:class "text-xs text-slate-400 italic px-2 py-1"}
               "No players on court")))

       ;; Bench section
       ($ :div {:class "px-2 py-1 mt-1"}
          ($ :div {:class "text-xs font-medium text-slate-500 uppercase px-2 py-1"}
             (str "Bench (" (count bench-players) ")"))
          (if (seq bench-players)
            (for [player bench-players
                  :let   [id (:id player)]]
              ($ player-roster-item
                 {:key           id
                  :player        player
                  :player-num    (get player-indices id)
                  :team          team
                  :on-field?     false
                  :on-info-click on-info-click}))
            ($ :div {:class "text-xs text-slate-400 italic px-2 py-1"}
               "No players on bench"))))))
