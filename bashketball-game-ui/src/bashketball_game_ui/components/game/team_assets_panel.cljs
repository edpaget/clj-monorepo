(ns bashketball-game-ui.components.game.team-assets-panel
  "Team assets panel component.

  Displays active team assets (cards that remain in play for the
  rest of the game) in a scrollable section."
  (:require
   [uix.core :refer [$ defui]]))

(defui team-assets-panel
  "Scrollable team assets section.

  Props:
  - assets: vector of asset maps with :card-slug and :name
  - on-info-click: fn [card-slug] to show card detail modal"
  [{:keys [assets on-info-click]}]
  (let [asset-count (count assets)]
    ($ :div {:class "flex flex-col min-h-0 flex-1"}
       ;; Header
       ($ :div {:class "text-xs font-medium text-slate-500 px-1 flex-shrink-0"}
          (str "Assets (" asset-count ")"))

       ;; Scrollable content
       ($ :div {:class "flex-1 overflow-y-auto mt-1"}
          (if (seq assets)
            ($ :div {:class "flex flex-col gap-1"}
               (for [{:keys [card-slug name]} assets]
                 ($ :button {:key      card-slug
                             :class    "text-left text-xs px-2 py-1.5 bg-slate-100 rounded
                                        hover:bg-slate-200 truncate min-h-[32px]
                                        flex items-center"
                             :on-click #(when on-info-click (on-info-click card-slug))}
                    (or name card-slug))))
            ($ :div {:class "text-xs text-slate-400 text-center py-2"}
               "No active assets"))))))
