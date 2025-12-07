(ns bashketball-game-ui.components.game.selected-player-panel
  "Selected player stats panel component.

  Displays full stats for the currently selected player in the
  team column sidebar."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def ^:private team-colors
  {:HOME {:bg "bg-blue-500" :text "text-white"}
   :AWAY {:bg "bg-red-500" :text "text-white"}})

(def ^:private size-labels
  {:SMALL "S"
   :MID   "M"
   :BIG   "B"})

(defui stat-row
  "Single stat display row."
  [{:keys [label value]}]
  ($ :div {:class "flex justify-between"}
     ($ :span {:class "text-slate-500"} label)
     ($ :span {:class "font-medium text-slate-900"} value)))

(defui modifier-badge
  "Badge showing a stat modifier."
  [{:keys [stat amount]}]
  (let [positive? (pos? amount)
        stat-name (if (keyword? stat) (name stat) (str stat))]
    ($ :span {:class (cn "px-1.5 py-0.5 rounded text-[10px] font-medium"
                         (if positive?
                           "bg-green-100 text-green-700"
                           "bg-red-100 text-red-700"))}
       (str (when positive? "+") amount " " stat-name))))

(defui selected-player-panel
  "Displays full stats for the selected player.

  Props:
  - player: BasketballPlayer map
  - token-label: string like 'H1' or 'A2'
  - team: :HOME or :AWAY
  - on-deselect: fn [] to clear selection
  - on-info-click: fn [card-slug] to show card detail"
  [{:keys [player token-label team on-deselect on-info-click]}]
  (let [{:keys [name stats exhausted? modifiers card-slug]}     player
        {:keys [speed shooting defense dribbling passing size]} stats
        colors                                                  (get team-colors team (:HOME team-colors))
        size-label                                              (get size-labels (keyword size) "?")]
    ($ :div {:class "p-2 bg-slate-50 rounded border border-slate-200"}
       ;; Header with token, name, close button
       ($ :div {:class "flex items-center gap-1.5 mb-2"}
          ($ :div {:class (cn "w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0"
                              "text-[10px] font-bold"
                              (:bg colors) (:text colors))}
             token-label)
          ($ :span {:class (cn "flex-1 font-medium text-sm truncate"
                               (when exhausted? "text-slate-400 line-through"))}
             name)
          ($ :button {:class    "w-5 h-5 rounded-full bg-slate-200 text-slate-500 text-xs
                                 flex items-center justify-center flex-shrink-0
                                 hover:bg-slate-300 hover:text-slate-700"
                      :on-click on-deselect}
             "\u00D7"))

       ;; Size indicator
       ($ :div {:class "text-[10px] text-slate-400 mb-2"}
          (str "Size: " size-label))

       ;; Stats grid
       ($ :div {:class "space-y-1 text-xs"}
          ($ stat-row {:label "SPD" :value speed})
          ($ stat-row {:label "SHT" :value shooting})
          ($ stat-row {:label "DEF" :value defense})
          ($ stat-row {:label "DRB" :value dribbling})
          ($ stat-row {:label "PAS" :value passing}))

       ;; Modifiers
       (when (seq modifiers)
         ($ :div {:class "mt-2 flex flex-wrap gap-1"}
            (for [{:keys [id stat amount]} modifiers]
              ($ modifier-badge {:key id :stat stat :amount amount}))))

       ;; Footer with exhausted status and view card button
       ($ :div {:class "mt-2 flex items-center justify-between"}
          (when exhausted?
            ($ :span {:class "text-[10px] text-slate-400"} "Exhausted"))
          ($ :button {:class    "text-[10px] text-blue-500 hover:text-blue-700 ml-auto"
                      :on-click #(when on-info-click (on-info-click card-slug))}
             "View Card")))))
