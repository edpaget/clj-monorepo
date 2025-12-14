(ns bashketball-game-ui.components.game.roster-panel
  "Roster panel component showing all team players.

  Shows all players and marks which ones are on court."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(defui player-card
  "A single player card in the roster.

  Props:
  - player: Player map with :id, :name, :card-slug, :stats
  - team: :HOME or :AWAY
  - selected: boolean
  - on-court: boolean (has position)"
  [{:keys [player team on-court]}]
  (let [team-colors  {:team/HOME "border-blue-500 bg-blue-50"
                      :team/AWAY "border-red-500 bg-red-50"}
        stats        (:stats player)
        display-name (or (:name player)
                         (:card-slug player)
                         (:id player)
                         "Unknown")]
    ($ :div
       {:class (cn "w-full p-2 rounded-lg border-2 text-left"
                   (if on-court
                     (get team-colors team)
                     "border-slate-200 bg-slate-50"))}
       ($ :div {:class "flex justify-between items-center"}
          ($ :div {:class "font-medium text-sm truncate"} display-name)
          (when on-court
            ($ :span {:class "text-xs px-1.5 py-0.5 rounded bg-slate-200 text-slate-600"} "On Court")))
       ($ :div {:class "flex gap-2 mt-1 text-xs text-slate-500"}
          (when-let [size (:size stats)]
            ($ :span (if (keyword? size) (name size) (str size))))
          (when-let [speed (:speed stats)]
            ($ :span (str "SPD " speed)))
          (when-let [shooting (:shooting stats)]
            ($ :span (str "SHT " shooting)))))))

(defui roster-panel
  "Shows all team players and their on-court status.

  Props:
  - players: Map of player-id -> player (all team players)
  - team: :HOME or :AWAY"
  [{:keys [players team]}]
  (let [team-label    (if (= team :team/HOME) "Home" "Away")
        all-players   (vals players)
        on-court-count (count (filter :position all-players))]
    ($ :div {:class "bg-white rounded-lg border border-slate-200 p-3"}
       ($ :div {:class "flex justify-between items-center mb-2"}
          ($ :div {:class "text-sm font-medium text-slate-700"}
             (str team-label " Roster"))
          ($ :div {:class "text-xs text-slate-500"}
             (str on-court-count "/3 on court")))
       (if (empty? all-players)
         ($ :div {:class "text-sm text-slate-400 py-2"} "No players")
         ($ :div {:class "space-y-2"}
            (for [player all-players
                  :let   [player-id (:id player)]]
              ($ player-card {:key      player-id
                              :player   player
                              :team     team
                              :on-court (some? (:position player))})))))))
