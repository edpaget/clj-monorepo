(ns bashketball-game-ui.components.game.player-roster-item
  "Single player row component for the roster view.

  Displays player token, field status, name, stats, and info button."
  (:require
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(def ^:private team-colors
  {:HOME {:bg "bg-blue-500" :text "text-white"}
   :AWAY {:bg "bg-red-500" :text "text-white"}})

(defui player-roster-item
  "A single player row in the roster view.

  Props:
  - player: BasketballPlayer map with :id, :name, :card-slug, :stats, :exhausted?
  - player-num: 1-based index for token label (1, 2, 3...)
  - team: :HOME or :AWAY
  - on-field?: boolean, true if player is a starter on the court
  - on-info-click: fn [card-slug] to open card detail modal"
  [{:keys [player player-num team on-field? on-info-click]}]
  (let [{:keys [name card-slug stats exhausted?]} player
        first-letter                              (or (some-> name first str) "?")
        token-label                               (str first-letter player-num)
        colors                                    (get team-colors team (:HOME team-colors))
        {:keys [shooting defense speed]}          stats]
    ($ :div {:class (cn "flex gap-2 px-2 py-1.5 rounded"
                        "hover:bg-slate-100"
                        (when exhausted? "opacity-50"))}
       ;; Token badge
       ($ :div {:class (cn "w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0"
                           "text-xs font-bold"
                           (:bg colors) (:text colors))}
          token-label)

       ;; Player info column (name + stats)
       ($ :div {:class "flex-1 min-w-0"}
          ;; Top row: field status, name, info button
          ($ :div {:class "flex items-center gap-1.5"}
             ;; Field status indicator
             (if on-field?
               ($ :div {:class "w-2 h-2 rounded-full bg-green-500 flex-shrink-0"
                        :title "On court"})
               ($ :div {:class "w-2 h-2 rounded-full border border-slate-400 flex-shrink-0"
                        :title "On bench"}))
             ;; Player name
             ($ :div {:class (cn "flex-1 text-sm font-medium text-slate-800 truncate"
                                 (when exhausted? "line-through"))}
                name)
             ;; Info button
             ($ :button
                {:class    "w-5 h-5 rounded-full bg-slate-200 text-slate-500 text-xs font-bold
                            flex items-center justify-center flex-shrink-0
                            hover:bg-slate-300 hover:text-slate-700"
                 :on-click (fn [e]
                             (.stopPropagation e)
                             (when on-info-click
                               (on-info-click card-slug)))}
                "i"))
          ;; Stats row
          ($ :div {:class "flex gap-3 text-xs text-slate-500 mt-0.5 pl-3.5"}
             ($ :span {:title "Shooting"} (str "S:" shooting))
             ($ :span {:title "Defense"} (str "D:" defense))
             ($ :span {:title "Speed"} (str "P:" speed)))))))
