(ns bashketball-game-ui.views.decks
  "Deck management view.

  Shows user's decks and allows creating/editing decks."
  (:require
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui]]))

(defui decks-view
  "Decks page component.

  Placeholder for Phase 3: Deck Management."
  []
  ($ :div {:class "space-y-6"}
     ($ :div {:class "flex justify-between items-center"}
        ($ :h1 {:class "text-2xl font-bold text-gray-900"} "My Decks")
        ($ button {:variant :default} "Create Deck"))
     ($ :div {:class "bg-white rounded-lg shadow p-8 text-center text-gray-500"}
        "You don't have any decks yet. Create one to get started!")))
