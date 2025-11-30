(ns bashketball-game-ui.views.lobby
  "Game lobby view.

  Shows available games to join and allows creating new games."
  (:require
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui]]))

(defui lobby-view
  "Lobby page component.

  Placeholder for Phase 4: Game Lobby & Matchmaking."
  []
  ($ :div {:class "space-y-6"}
     ($ :div {:class "flex justify-between items-center"}
        ($ :h1 {:class "text-2xl font-bold text-gray-900"} "Game Lobby")
        ($ button {:variant :default} "Create Game"))
     ($ :div {:class "bg-white rounded-lg shadow p-8 text-center text-gray-500"}
        "No games available. Create a game to get started!")))
