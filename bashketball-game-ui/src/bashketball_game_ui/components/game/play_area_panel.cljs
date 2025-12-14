(ns bashketball-game-ui.components.game.play-area-panel
  "Play area panel component.

  Displays cards that have been staged but not yet resolved.
  Cards move from hand to play area to discard/assets/attachments."
  (:require
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(defn ability-card?
  "Returns true if the card is an ability card."
  [card-data]
  (= (:card-type card-data) :card-type/ABILITY_CARD))

(defui play-area-card
  "A single card in the play area with resolve and token buttons.

  Props:
  - card: map with :instance-id, :card-slug, :played-by
  - catalog: map of card-slug -> card data
  - on-resolve: fn [instance-id] to resolve the card (discard)
  - on-attach: fn [instance-id card-slug played-by] to open attach modal (for ability cards)
  - on-info-click: fn [card-slug] to show card detail
  - on-create-token: fn [] to open token creation modal"
  [{:keys [card catalog on-resolve on-attach on-info-click on-create-token]}]
  (let [{:keys [instance-id card-slug played-by]} card
        card-data                                 (get catalog card-slug)
        card-name                                 (or (:name card-data) card-slug)
        is-ability                                (ability-card? card-data)
        team-color                                (if (= played-by :team/HOME)
                                                    "border-blue-400 bg-blue-50"
                                                    "border-red-400 bg-red-50")]
    ($ :div {:class (cn "flex items-center gap-2 px-2 py-1.5 rounded-lg border-2"
                        team-color)}
       ($ :button {:class    "flex-1 text-left text-xs font-medium truncate
                              hover:text-slate-700"
                   :on-click #(when on-info-click (on-info-click card-slug))}
          card-name)
       ($ button {:variant  :outline
                  :size     :sm
                  :on-click #(when on-create-token (on-create-token))}
          "Token")
       (if is-ability
         ($ button {:variant  :outline
                    :size     :sm
                    :on-click #(when on-attach (on-attach instance-id card-slug played-by))}
            "Resolve")
         ($ button {:variant  :outline
                    :size     :sm
                    :on-click #(when on-resolve (on-resolve instance-id))}
            "Resolve")))))

(defui play-area-panel
  "Shared play area between players.

  Props:
  - play-area: vector of PlayAreaCard maps
  - catalog: card slug -> card data map
  - on-resolve: fn [instance-id] to resolve a card (discard)
  - on-attach: fn [instance-id card-slug played-by] to open attach modal (for ability cards)
  - on-info-click: fn [card-slug] to show card detail modal
  - on-create-token: fn [] to open token creation modal"
  [{:keys [play-area catalog on-resolve on-attach on-info-click on-create-token]}]
  (let [card-count (count play-area)]
    ($ :div {:class "bg-amber-50 rounded-lg border border-amber-200 p-2"}
       ($ :div {:class "text-xs font-medium text-amber-700 mb-2"}
          (str "Play Area (" card-count ")"))
       (if (seq play-area)
         ($ :div {:class "flex flex-wrap gap-2"}
            (for [card play-area]
              ($ play-area-card {:key             (:instance-id card)
                                 :card            card
                                 :catalog         catalog
                                 :on-resolve      on-resolve
                                 :on-attach       on-attach
                                 :on-info-click   on-info-click
                                 :on-create-token on-create-token})))
         ($ :div {:class "text-xs text-amber-400 text-center py-2"}
            "No cards in play area")))))
