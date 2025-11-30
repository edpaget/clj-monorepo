(ns bashketball-game-ui.views.games
  "Games history view.

  Shows user's past and active games."
  (:require
   [uix.core :refer [$ defui]]))

(defui games-view
  "Games page component.

  Placeholder for Phase 4: Game history display."
  []
  ($ :div {:class "space-y-6"}
     ($ :h1 {:class "text-2xl font-bold text-gray-900"} "My Games")
     ($ :div {:class "bg-white rounded-lg shadow p-8 text-center text-gray-500"}
        "You haven't played any games yet.")))
