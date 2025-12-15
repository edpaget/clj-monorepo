(ns bashketball-game-ui.components.game.selected-player-panel
  "Selected player stats panel component.

  Displays full stats for the currently selected player in the
  team column sidebar."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def ^:private team-colors
  {:team/HOME {:bg "bg-blue-500" :text "text-white"}
   :team/AWAY {:bg "bg-red-500" :text "text-white"}})

(def ^:private size-labels
  {:size/SM "S"
   :size/MD "M"
   :size/LG "B"})

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

(defui attachment-badge
  "Clickable badge showing an attached ability card name."
  [{:keys [attachment catalog on-click]}]
  (let [token?    (:token attachment)
        card      (:card attachment)
        card-slug (or (:card-slug attachment)
                      (:slug card)
                      (get card "slug"))
        card-name (or (when token?
                        (or (:name card)
                            (get card "name")))
                      (get-in catalog [card-slug :name])
                      card-slug)]
    ($ :button {:class    (cn "px-1.5 py-0.5 rounded text-[10px] font-medium"
                              "bg-purple-100 text-purple-700 hover:bg-purple-200")
                :on-click #(when on-click (on-click card-slug))}
       card-name)))

(defui selected-player-panel
  "Displays full stats for the selected player.

  Props:
  - player: BasketballPlayer map
  - token-label: string like 'H1' or 'A2'
  - team: :HOME or :AWAY
  - catalog: map of card-slug -> card for name lookups
  - on-deselect: fn [] to clear selection
  - on-info-click: fn [card-slug] to show card detail
  - on-attachment-click: fn [card-slug] to show attachment card detail"
  [{:keys [player token-label team catalog on-deselect on-info-click on-attachment-click]}]
  (let [{:keys [name stats exhausted modifiers attachments card-slug]} player
        {:keys [speed shooting defense dribbling passing size]}        stats
        colors                                                         (get team-colors team (:team/HOME team-colors))
        size-label                                                     (get size-labels (keyword size) "?")]
    ($ :div {:class "p-2 bg-slate-50 rounded border border-slate-200"}
       ;; Header with token, name, close button
       ($ :div {:class "flex items-center gap-1.5 mb-2"}
          ($ :div {:class (cn "w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0"
                              "text-[10px] font-bold"
                              (:bg colors) (:text colors))}
             token-label)
          ($ :span {:class (cn "flex-1 font-medium text-sm truncate"
                               (when exhausted "text-slate-400 line-through"))}
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

       ;; Attachments
       (when (seq attachments)
         ($ :div {:class "mt-2"}
            ($ :div {:class "text-[10px] text-slate-500 mb-1"} "Attachments")
            ($ :div {:class "flex flex-wrap gap-1"}
               (for [att attachments]
                 ($ attachment-badge {:key      (:instance-id att)
                                      :attachment att
                                      :catalog  catalog
                                      :on-click on-attachment-click})))))

       ;; Footer with exhausted status and view card button
       ($ :div {:class "mt-2 flex items-center justify-between"}
          (when exhausted
            ($ :span {:class "text-[10px] text-slate-400"} "Exhausted"))
          ($ :button {:class    "text-[10px] text-blue-500 hover:text-blue-700 ml-auto"
                      :on-click #(when on-info-click (on-info-click card-slug))}
             "View Card")))))
