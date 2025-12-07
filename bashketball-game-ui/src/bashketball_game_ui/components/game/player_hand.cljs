(ns bashketball-game-ui.components.game.player-hand
  "Player hand component displaying cards available to play.

  Shows a horizontal scrollable list of cards from the player's hand.
  Supports two display modes: compact (name buttons) and preview (scaled cards)."
  (:require
   [bashketball-ui.cards.card-preview :refer [card-preview]]
   [bashketball-ui.utils :refer [cn]]
   [clojure.string :as str]
   [uix.core :refer [$ defui]]))

(defui info-icon
  "Small info icon button."
  [{:keys [on-click disabled]}]
  ($ :button
     {:class    (cn "w-5 h-5 rounded-full bg-slate-200 text-slate-500 text-xs font-bold"
                    "flex items-center justify-center flex-shrink-0"
                    "hover:bg-slate-300 hover:text-slate-700"
                    (when disabled "opacity-50 cursor-not-allowed"))
      :disabled disabled
      :on-click (fn [e]
                  (.stopPropagation e)
                  (when on-click (on-click)))}
     "i"))

(defui hand-card
  "A single card in the player's hand.

  Props:
  - card: CardInstance map with :instance-id and :card-slug
  - card-name: Display name from catalog (optional, falls back to formatted slug)
  - selected: boolean if this card is selected
  - discard-selected: boolean if this card is selected for discard
  - disabled: boolean to prevent interaction
  - on-click: fn [instance-id] called when clicked
  - on-detail-click: fn [card-slug] called when info icon clicked"
  [{:keys [card card-name selected discard-selected disabled on-click on-detail-click]}]
  (let [{:keys [instance-id card-slug]} card
        display-name                    (or card-name
                                            (-> card-slug
                                                (str/replace #"-" " ")
                                                (str/replace #"_" " ")))]
    ($ :div {:class "flex items-center gap-1 flex-shrink-0"}
       ($ :button
          {:class    (cn "px-3 py-2 rounded-lg border-2 text-sm font-medium"
                         "transition-all hover:scale-105"
                         (cond
                           discard-selected "border-red-500 bg-red-50 text-red-800"
                           selected         "border-cyan-500 bg-cyan-50 text-cyan-800"
                           :else            "border-slate-300 bg-white text-slate-700 hover:border-slate-400")
                         (when disabled "opacity-50 cursor-not-allowed hover:scale-100"))
           :disabled disabled
           :on-click (fn [_] (when on-click (on-click instance-id)))}
          display-name)
       (when on-detail-click
         ($ info-icon {:on-click #(on-detail-click card-slug)})))))

(defui hand-card-preview
  "Scaled card preview for expanded hand display.

  Props:
  - card: Full card data map (from catalog)
  - instance-id: Card instance ID for selection
  - selected: boolean for normal selection state
  - discard-selected: boolean for discard mode selection
  - disabled: boolean to prevent interaction
  - on-click: fn [instance-id] called when clicked"
  [{:keys [card instance-id selected discard-selected disabled on-click]}]
  ($ :button
     {:class    (cn "w-[112px] h-[157px] flex-shrink-0 rounded-lg overflow-hidden"
                    "transition-all hover:scale-105"
                    "ring-2 ring-offset-1"
                    (cond
                      discard-selected "ring-red-500"
                      selected         "ring-cyan-500"
                      :else            "ring-transparent hover:ring-slate-300")
                    (when disabled "opacity-50 cursor-not-allowed hover:scale-100"))
      :disabled disabled
      :on-click #(when on-click (on-click instance-id))}
     ($ :div {:class "origin-top-left scale-[0.35]"}
        ($ card-preview {:card card}))))

(defui player-hand
  "Displays cards in player's hand.

  Props:
  - hand: Vector of CardInstance maps with :instance-id and :card-slug
  - catalog: Map of card-slug to full card data (for preview mode)
  - display-mode: :compact (default) or :preview
  - selected-card: Currently selected instance-id or nil
  - discard-mode: boolean, true when in discard selection mode
  - discard-cards: Set of instance-ids selected for discard
  - on-card-click: fn [instance-id] called when card clicked
  - on-detail-click: fn [card-slug] called when info icon clicked
  - disabled: boolean to prevent all interaction"
  [{:keys [hand catalog display-mode selected-card discard-mode discard-cards on-card-click on-detail-click disabled]}]
  (if (empty? hand)
    ($ :div {:class "text-sm text-slate-400 italic py-2"}
       "No cards in hand")
    ($ :div {:class "flex gap-2 overflow-x-auto py-2 px-1"}
       (for [card-instance hand]
         (let [instance-id (:instance-id card-instance)
               card-slug   (:card-slug card-instance)]
           (if (= display-mode :preview)
             ($ hand-card-preview
                {:key              instance-id
                 :card             (get catalog card-slug)
                 :instance-id      instance-id
                 :selected         (and (not discard-mode) (= instance-id selected-card))
                 :discard-selected (and discard-mode (contains? discard-cards instance-id))
                 :disabled         disabled
                 :on-click         on-card-click})
             ($ hand-card
                {:key              instance-id
                 :card             card-instance
                 :card-name        (get-in catalog [card-slug :name])
                 :selected         (and (not discard-mode) (= instance-id selected-card))
                 :discard-selected (and discard-mode (contains? discard-cards instance-id))
                 :disabled         disabled
                 :on-click         on-card-click
                 :on-detail-click  on-detail-click})))))))
