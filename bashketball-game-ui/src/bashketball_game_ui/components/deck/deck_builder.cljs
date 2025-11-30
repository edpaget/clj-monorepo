(ns bashketball-game-ui.components.deck.deck-builder
  "Deck builder component showing deck contents.

  Displays cards in the deck organized by type with remove functionality."
  (:require
   ["lucide-react" :refer [Minus AlertCircle CheckCircle2]]
   [bashketball-game-ui.schemas.deck :as deck-schema]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui use-memo]]))

(defui deck-card-item
  "A card in the deck with count and remove button."
  [{:keys [card count on-remove]}]
  ($ :div {:class "flex items-center justify-between py-2 px-3 hover:bg-gray-50 rounded"}
     ($ :div {:class "flex items-center gap-2 flex-1 min-w-0"}
        ($ :span {:class "text-sm font-medium text-gray-500 w-6"}
           (str count "×"))
        ($ :span {:class "text-sm text-gray-900 truncate"}
           (:name card)))
     ($ :button
        {:class "w-6 h-6 rounded-full flex items-center justify-center bg-red-100 text-red-600 hover:bg-red-200 transition-colors"
         :on-click #(on-remove card)
         :title "Remove one copy"}
        ($ Minus {:className "w-3 h-3"}))))

(defui deck-section
  "A section of the deck (e.g., Player Cards, Action Cards)."
  [{:keys [title cards card-counts on-remove-card target-min target-max]}]
  (let [total     (reduce + 0 (vals card-counts))
        in-range? (and (>= total target-min) (<= total target-max))]
    ($ :div {:class "space-y-2"}
       ($ :div {:class "flex items-center justify-between"}
          ($ :h4 {:class "font-medium text-gray-700"} title)
          ($ :span {:class (cn "text-sm"
                               (if in-range? "text-green-600" "text-amber-600"))}
             (str total "/" target-min "-" target-max)))
       (if (seq cards)
         ($ :div {:class "space-y-1"}
            (for [card cards]
              ($ deck-card-item
                 {:key (:slug card)
                  :card card
                  :count (get card-counts (:slug card) 0)
                  :on-remove on-remove-card})))
         ($ :div {:class "text-sm text-gray-400 italic py-2"}
            "No cards added")))))

(defui deck-builder
  "Deck contents panel showing organized cards.

  Props:
  - `:deck` - Deck map with :name, :card-slugs, :is-valid, :validation-errors
  - `:cards` - Vector of card objects in the deck
  - `:on-remove-card` - Callback when a card is removed (receives card)
  - `:class` - Additional CSS classes"
  [{:keys [deck cards on-remove-card class]}]
  (prn deck)
  (prn cards)
  (let [card-slugs                              (:card-slugs deck)
        slug-counts                             (use-memo
                                                 (fn [] (frequencies card-slugs))
                                                 [card-slugs])
        cards-by-slug                           (use-memo
                                                 (fn []
                                                   (into {} (map (juxt :slug identity)) cards))
                                                 [cards])
        unique-cards                            (use-memo
                                                 (fn []
                                                   (keep #(get cards-by-slug %) (distinct card-slugs)))
                                                 [cards-by-slug card-slugs])
        player-cards                            (filter deck-schema/player-card? unique-cards)
        action-cards                            (filter deck-schema/action-card? unique-cards)
        player-counts                           (select-keys slug-counts (map :slug player-cards))
        action-counts                           (select-keys slug-counts (map :slug action-cards))
        total-cards                             (count card-slugs)
        {:keys [is-valid validation-errors]} deck]
    ($ :div {:class (cn "flex flex-col h-full bg-white rounded-lg shadow border border-gray-200" class)}
       ($ :div {:class "p-4 border-b border-gray-200"}
          ($ :div {:class "flex items-center justify-between"}
             ($ :h3 {:class "font-semibold text-gray-900"} "Deck Contents")
             ($ :span {:class "text-sm text-gray-500"}
                (str total-cards " cards")))
          ($ :div {:class "flex items-center gap-2 mt-2"}
             (if is-valid
               ($ :<>
                  ($ CheckCircle2 {:className "w-4 h-4 text-green-500"})
                  ($ :span {:class "text-sm text-green-600"} "Valid deck"))
               ($ :<>
                  ($ AlertCircle {:className "w-4 h-4 text-amber-500"})
                  ($ :span {:class "text-sm text-amber-600"} "Incomplete deck")))))
       ($ :div {:class "flex-1 overflow-y-auto p-4 space-y-6"}
          ($ deck-section
             {:title "Player Cards"
              :cards player-cards
              :card-counts player-counts
              :on-remove-card on-remove-card
              :target-min (:min-player-cards deck-schema/deck-rules)
              :target-max (:max-player-cards deck-schema/deck-rules)})
          ($ deck-section
             {:title "Action Cards"
              :cards action-cards
              :card-counts action-counts
              :on-remove-card on-remove-card
              :target-min (:min-action-cards deck-schema/deck-rules)
              :target-max (:max-action-cards deck-schema/deck-rules)}))
       (when (seq validation-errors)
         ($ :div {:class "p-4 border-t border-gray-200 bg-amber-50"}
            ($ :h4 {:class "text-sm font-medium text-amber-800 mb-2"} "Validation Issues:")
            ($ :ul {:class "text-sm text-amber-700 list-disc list-inside space-y-1"}
               (for [[idx error] (map-indexed vector validation-errors)]
                 ($ :li {:key idx} error))))))))
