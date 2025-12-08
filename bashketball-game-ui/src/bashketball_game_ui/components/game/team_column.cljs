(ns bashketball-game-ui.components.game.team-column
  "Team column component for the three-column game layout.

  Displays team score, player list (or selected player stats),
  and collapsible team assets. During setup phase, shows a roster
  panel for player placement instead of the normal player list."
  (:require
   [bashketball-game-ui.components.game.selected-player-panel :refer [selected-player-panel]]
   [bashketball-game-ui.components.game.team-assets-panel :refer [team-assets-panel]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def ^:private team-colors
  {:HOME {:bg     "bg-blue-500"
          :text   "text-white"
          :light  "bg-blue-50"
          :border "border-blue-200"}
   :AWAY {:bg     "bg-red-500"
          :text   "text-white"
          :light  "bg-red-50"
          :border "border-red-200"}})

(defui team-score-panel
  "Displays team name and score."
  [{:keys [team score is-active is-my-team]}]
  (let [colors     (get team-colors team (:HOME team-colors))
        team-label (if (= team :HOME) "Home" "Away")]
    ($ :div {:class (cn "p-2 rounded-lg"
                        (if is-active
                          (:light colors)
                          "bg-slate-50"))}
       ($ :div {:class "flex items-center justify-between"}
          ($ :div {:class "flex items-center gap-2"}
             ($ :div {:class (cn "w-6 h-6 rounded-full flex items-center justify-center"
                                 "text-xs font-bold"
                                 (:bg colors) (:text colors))}
                (first team-label))
             ($ :span {:class "text-sm font-medium text-slate-700"}
                team-label
                (when is-my-team
                  ($ :span {:class "text-slate-400 ml-1"} "(me)"))))
          ($ :span {:class "text-xl font-bold text-slate-900"} (or score 0))))))

(defui deck-stat-item
  "Single deck stat with icon and count."
  [{:keys [label count icon-class]}]
  ($ :div {:class "flex items-center gap-1" :title label}
     ($ :div {:class (cn "w-4 h-4 rounded flex items-center justify-center text-[9px] font-bold" icon-class)}
        (first label))
     ($ :span {:class "text-xs text-slate-600"} count)))

(defui deck-stats-panel
  "Displays deck statistics: draw pile, hand, discard, removed."
  [{:keys [deck-stats]}]
  (let [{:keys [draw-pile-count hand-count discard-count removed-count]} deck-stats]
    ($ :div {:class "flex items-center justify-between px-1 py-1 bg-slate-50 rounded text-slate-500"}
       ($ deck-stat-item {:label "Draw" :count draw-pile-count :icon-class "bg-emerald-100 text-emerald-700"})
       ($ deck-stat-item {:label "Hand" :count hand-count :icon-class "bg-blue-100 text-blue-700"})
       ($ deck-stat-item {:label "Discard" :count discard-count :icon-class "bg-amber-100 text-amber-700"})
       ($ deck-stat-item {:label "Removed" :count removed-count :icon-class "bg-red-100 text-red-700"}))))

(defui player-list-item
  "Compact player row in the sidebar list."
  [{:keys [player token-label team on-click on-info-click]}]
  (let [{:keys [name card-slug exhausted?]} player
        colors                              (get team-colors team (:HOME team-colors))]
    ($ :div {:class    (cn "flex items-center gap-1.5 px-1 py-1 rounded cursor-pointer"
                           "hover:bg-slate-100 min-h-[32px]"
                           (when exhausted? "opacity-50"))
             :on-click #(on-click (:id player))}
       ;; Token badge
       ($ :div {:class (cn "w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0"
                           "text-[10px] font-bold"
                           (:bg colors) (:text colors))}
          token-label)
       ;; Name
       ($ :span {:class "flex-1 text-xs truncate"} name)
       ;; Info button
       ($ :button {:class    "w-5 h-5 rounded-full bg-slate-200 text-slate-500 text-[10px]
                              flex items-center justify-center flex-shrink-0
                              hover:bg-slate-300 hover:text-slate-700"
                   :on-click (fn [e]
                               (.stopPropagation e)
                               (when on-info-click
                                 (on-info-click card-slug)))}
          "i"))))

(defui player-list
  "List of on-court players with token indicators."
  [{:keys [players player-indices team on-select on-info-click]}]
  (let [prefix (if (= team :HOME) "H" "A")]
    ($ :div {:class "flex flex-col"}
       (when (empty? players)
         ($ :div {:class "text-xs text-slate-400 text-center py-2"} "No players"))
       (for [[id player] players
             :let        [idx (get player-indices id 1)
                          token-label (str prefix idx)]]
         ($ player-list-item {:key           id
                              :player        player
                              :token-label   token-label
                              :team          team
                              :on-click      on-select
                              :on-info-click on-info-click})))))

(defui setup-roster-item
  "A single player row in the setup roster.

  Shows player name and placement status. Clickable to select for placement."
  [{:keys [player team selected placed on-click]}]
  (let [colors       (get team-colors team (:HOME team-colors))
        display-name (or (:name player) (:card-slug player) "Unknown")]
    ($ :button
       {:class    (cn "w-full flex items-center gap-2 px-2 py-1.5 rounded text-left text-xs"
                      (cond
                        placed   "bg-slate-100 opacity-50 cursor-not-allowed"
                        selected (str (:light colors) " " (:border colors) " border")
                        :else    "hover:bg-slate-100 cursor-pointer"))
        :disabled placed
        :on-click (fn [_] (when (and on-click (not placed))
                            (on-click (:id player))))}
       ($ :span {:class "flex-1 truncate"} display-name)
       (if placed
         ($ :span {:class "text-slate-400"} "✓")
         (when selected
           ($ :span {:class "text-slate-500"} "→"))))))

(defn- get-player
  "Gets a player from the players map, handling both string and keyword keys."
  [players id]
  (or (get players id)
      (get players (keyword id))))

(defui setup-roster-list
  "Compact roster list for setup phase player placement.

  Shows starters with their placement status."
  [{:keys [players starters team selected-player on-player-select]}]
  (let [starter-players (->> starters
                             (map #(get-player players %))
                             (filter some?))
        placed-count    (count (filter :position starter-players))]
    ($ :div {:class "flex flex-col gap-1"}
       ($ :div {:class "text-[10px] text-slate-400 px-1"}
          (str placed-count "/" (count starters) " placed"))
       (if (empty? starter-players)
         ($ :div {:class "text-xs text-slate-400 text-center py-2"} "No starters")
         (for [player starter-players
               :let   [player-id (:id player)]]
           ($ setup-roster-item {:key      player-id
                                 :player   player
                                 :team     team
                                 :selected (= player-id selected-player)
                                 :placed   (some? (:position player))
                                 :on-click on-player-select}))))))

(defui team-players-section
  "Players section - shows setup roster, list, or selected player stats.

  During setup mode, shows the roster for player placement.
  Otherwise shows the on-court player list or selected player panel."
  [{:keys [players player-indices team selected-player on-select on-deselect on-info-click
           setup-mode all-players starters selected-player-id on-player-select]}]
  (let [selected-id       (:id selected-player)
        selected-in-team? (and selected-id (contains? players selected-id))
        prefix            (if (= team :HOME) "H" "A")]
    ($ :div {:class "flex-1 min-h-0 overflow-y-auto"}
       (cond
         setup-mode
         ($ setup-roster-list {:players          all-players
                               :starters         starters
                               :team             team
                               :selected-player  selected-player-id
                               :on-player-select on-player-select})

         selected-in-team?
         (let [idx (get player-indices selected-id 1)]
           ($ selected-player-panel {:player        selected-player
                                     :token-label   (str prefix idx)
                                     :team          team
                                     :on-deselect   on-deselect
                                     :on-info-click on-info-click}))

         :else
         ($ player-list {:players        players
                         :player-indices player-indices
                         :team           team
                         :on-select      on-select
                         :on-info-click  on-info-click})))))

(defui team-column
  "Team info column with score, deck stats, players/selection, and assets.

  Props:
  - team: :HOME or :AWAY
  - score: team score
  - is-active: boolean if this team's turn
  - is-my-team: boolean if this is the current user's team
  - deck-stats: map with :draw-pile-count, :hand-count, :discard-count, :removed-count
  - players: map of player-id -> BasketballPlayer for on-court players
  - player-indices: map of player-id -> display index (1, 2, 3)
  - selected-player: currently selected BasketballPlayer or nil
  - assets: vector of active team asset cards
  - on-select: fn [player-id] when player clicked
  - on-deselect: fn [] to clear selection
  - on-info-click: fn [card-slug] to show card detail
  - setup-mode: boolean, true if in setup phase
  - all-players: map of all team players (for setup roster)
  - starters: vector of starter player IDs (for setup roster)
  - selected-player-id: ID of selected player (for setup roster)
  - on-player-select: fn [player-id] for setup player selection"
  [{:keys [team score is-active is-my-team deck-stats players player-indices selected-player
           assets on-select on-deselect on-info-click
           setup-mode all-players starters selected-player-id on-player-select]}]
  ($ :div {:class "w-40 h-full flex flex-col gap-2 bg-white rounded-lg border border-slate-200 p-2"}
     ;; Fixed header sections
     ($ :div {:class "flex-shrink-0"}
        ($ team-score-panel {:team team :score score :is-active is-active :is-my-team is-my-team}))
     ($ :div {:class "flex-shrink-0"}
        ($ deck-stats-panel {:deck-stats deck-stats}))

     ;; Players section - fixed height, scrollable if needed
     ($ :div {:class "flex-shrink-0"}
        ($ :div {:class "text-xs font-medium text-slate-500 px-1"}
           (if setup-mode "Setup Roster" "Players"))
        ($ team-players-section {:players            players
                                 :player-indices     player-indices
                                 :team               team
                                 :selected-player    selected-player
                                 :on-select          on-select
                                 :on-deselect        on-deselect
                                 :on-info-click      on-info-click
                                 :setup-mode         setup-mode
                                 :all-players        all-players
                                 :starters           starters
                                 :selected-player-id selected-player-id
                                 :on-player-select   on-player-select}))

     ;; Assets section - takes remaining space, scrollable
     ($ :div {:class "flex-1 min-h-0 border-t border-slate-200 pt-2"}
        ($ team-assets-panel {:assets assets :on-info-click on-info-click}))))
