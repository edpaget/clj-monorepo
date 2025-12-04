(ns bashketball-game-ui.components.game.roster-panel
  "Roster panel component for setup phase.

  Shows unplaced players that can be selected for placement on the board."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(defui player-card
  "A single player card in the roster.

  Props:
  - player: Player map with :id, :name, :card-slug, :stats
  - team: :HOME or :AWAY
  - selected: boolean
  - placed: boolean (has position)
  - on-click: fn [player-id]"
  [{:keys [player team selected placed on-click]}]
  (let [team-colors  {:HOME "border-blue-500 bg-blue-50"
                      :AWAY "border-red-500 bg-red-50"}
        stats        (:stats player)
        ;; Display name with fallback to card-slug or id
        display-name (or (:name player)
                         (:card-slug player)
                         (:id player)
                         "Unknown")]
    ($ :button
       {:class    (cn "w-full p-2 rounded-lg border-2 text-left transition-all"
                      (if placed
                        "border-slate-200 bg-slate-100 opacity-50 cursor-not-allowed"
                        (if selected
                          (get team-colors team)
                          "border-slate-300 bg-white hover:border-slate-400 cursor-pointer")))
        :disabled placed
        :on-click (fn [_] (when (and on-click (not placed))
                            (let [pid (:id player)]
                              (js/console.log "Clicking player with id:" pid "type:" (type pid))
                              (on-click pid))))}
       ($ :div {:class "font-medium text-sm truncate"}
          display-name)
       ($ :div {:class "flex gap-2 mt-1 text-xs text-slate-500"}
          (when-let [size (:size stats)]
            ($ :span (if (keyword? size) (name size) (str size))))
          (when-let [speed (:speed stats)]
            ($ :span (str "SPD " speed)))
          (when-let [shooting (:shooting stats)]
            ($ :span (str "SHT " shooting)))))))

(defn- get-player
  "Gets a player from the players map, handling both string and keyword keys.

  The decoder converts map keys to keywords, but starters are strings.
  Try both the original key and keyword version."
  [players id]
  (or (get players id)
      (get players (keyword id))))

(defui roster-panel
  "Shows unplaced players during setup phase.

  Props:
  - players: Map of player-id -> player (all team players)
  - starters: Vector of starter player IDs
  - team: :HOME or :AWAY
  - selected-player: Currently selected player ID
  - on-player-select: fn [player-id]"
  [{:keys [players starters team selected-player on-player-select]}]
  (let [team-label      (if (= team :HOME) "Home" "Away")
        ;; Get starter players, filtering out nils for missing entries
        starter-players (->> starters
                             (map #(get-player players %))
                             (filter some?))
        placed-count    (count (filter :position starter-players))]
    ($ :div {:class "bg-white rounded-lg border border-slate-200 p-3"}
       ($ :div {:class "flex justify-between items-center mb-2"}
          ($ :div {:class "text-sm font-medium text-slate-700"}
             (str team-label " Roster"))
          ($ :div {:class "text-xs text-slate-500"}
             (str placed-count "/" (count starters) " placed")))
       (if (empty? starter-players)
         ;; Show message when no players found
         ($ :div {:class "text-sm text-slate-400 py-2"}
            (if (empty? starters)
              "No starters defined"
              (str "Players not found for: " (pr-str starters))))
         ;; Show player cards
         ($ :div {:class "space-y-2"}
            (for [player starter-players
                  :let   [player-id (:id player)]]
              ($ player-card {:key             player-id
                              :player          player
                              :team            team
                              :selected        (= player-id selected-player)
                              :placed          (some? (:position player))
                              :on-click        on-player-select})))))))
