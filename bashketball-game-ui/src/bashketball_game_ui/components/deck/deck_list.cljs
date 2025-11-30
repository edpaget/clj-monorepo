(ns bashketball-game-ui.components.deck.deck-list
  "Deck list component for displaying multiple decks."
  (:require
   [bashketball-game-ui.components.deck.deck-card :refer [deck-card deck-card-skeleton]]
   [uix.core :refer [$ defui]]))

(defui deck-list
  "Displays a grid of deck cards.

  Props:
  - `:decks` - Vector of deck maps
  - `:on-edit` - Callback when edit is clicked (receives deck)
  - `:on-delete` - Callback when delete is clicked (receives deck)
  - `:loading` - Show loading skeletons"
  [{:keys [decks on-edit on-delete loading]}]
  (if loading
    ($ :div {:class "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"}
       (for [i (range 3)]
         ($ deck-card-skeleton {:key i})))
    (if (seq decks)
      ($ :div {:class "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"}
         (for [deck decks]
           ($ deck-card
              {:key (:id deck)
               :deck deck
               :on-edit on-edit
               :on-delete on-delete})))
      ($ :div {:class "bg-white rounded-lg shadow p-8 text-center text-gray-500"}
         "You don't have any decks yet. Create one to get started!"))))
