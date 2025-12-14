(ns bashketball-game-ui.components.game.team-assets-panel
  "Team assets panel component.

  Displays active team assets (cards that remain in play for the
  rest of the game) in a scrollable section. Clicking an asset
  shows a dropdown to discard or remove from game."
  (:require
   [bashketball-game-ui.components.ui.dropdown-menu :as dm]
   [uix.core :refer [$ defui]]))

(defui asset-item
  "Single asset with dropdown menu for discard/remove actions.

  Props:
  - card-slug: the card's slug
  - instance-id: the card instance ID
  - name: display name
  - on-info-click: fn [card-slug] to show card detail modal
  - on-move-asset: fn [instance-id destination] to move asset
  - is-my-team: boolean, true if this is the current user's team"
  [{:keys [card-slug instance-id name on-info-click on-move-asset is-my-team]}]
  (if is-my-team
    ($ dm/dropdown-menu
       ($ dm/dropdown-menu-trigger {:asChild true}
          ($ :button {:class "w-full text-left text-xs px-2 py-1.5 bg-slate-100 rounded
                              hover:bg-slate-200 truncate min-h-[32px]
                              flex items-center"}
             (or name card-slug)))
       ($ dm/dropdown-menu-content {:align "start"}
          ($ dm/dropdown-menu-item
             {:on-select #(when on-info-click (on-info-click card-slug))}
             "View Card")
          ($ dm/dropdown-menu-separator)
          ($ dm/dropdown-menu-item
             {:on-select #(when on-move-asset (on-move-asset instance-id :DISCARD))}
             "Discard")
          ($ dm/dropdown-menu-item
             {:on-select #(when on-move-asset (on-move-asset instance-id :REMOVED))
              :class     "text-red-600 focus:text-red-600"}
             "Remove from Game")))
    ($ :button {:class    "text-left text-xs px-2 py-1.5 bg-slate-100 rounded
                           hover:bg-slate-200 truncate min-h-[32px]
                           flex items-center"
                :on-click #(when on-info-click (on-info-click card-slug))}
       (or name card-slug))))

(defui team-assets-panel
  "Scrollable team assets section.

  Props:
  - assets: vector of asset maps with :card-slug, :instance-id, and :name
  - on-info-click: fn [card-slug] to show card detail modal
  - on-move-asset: fn [instance-id destination] to move asset
  - is-my-team: boolean, true if this is the current user's team"
  [{:keys [assets on-info-click on-move-asset is-my-team]}]
  (let [asset-count (count assets)]
    ($ :div {:class "flex flex-col min-h-0 flex-1"}
       ($ :div {:class "text-xs font-medium text-slate-500 px-1 flex-shrink-0"}
          (str "Assets (" asset-count ")"))
       ($ :div {:class "flex-1 overflow-y-auto mt-1"}
          (if (seq assets)
            ($ :div {:class "flex flex-col gap-1"}
               (for [{:keys [card-slug instance-id name]} assets]
                 ($ asset-item {:key           instance-id
                                :card-slug     card-slug
                                :instance-id   instance-id
                                :name          name
                                :on-info-click on-info-click
                                :on-move-asset on-move-asset
                                :is-my-team    is-my-team})))
            ($ :div {:class "text-xs text-slate-400 text-center py-2"}
               "No active assets"))))))
