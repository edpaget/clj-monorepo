(ns bashketball-game-ui.components.game.player-view-panel
  "Player view panel showing both teams' rosters.

  Displays HOME and AWAY team rosters side by side with player
  tokens, field status, and info buttons for card details."
  (:require
   [bashketball-game-ui.components.game.team-roster-panel :refer [team-roster-panel]]
   [bashketball-game-ui.game.selectors :as sel]
   [uix.core :refer [$ defui use-memo]]))

(defui player-view-panel
  "Container showing both teams' rosters.

  Props:
  - home-players: Map of HOME team players {id -> BasketballPlayer}
  - away-players: Map of AWAY team players {id -> BasketballPlayer}
  - home-starters: Vector of HOME starter IDs
  - away-starters: Vector of AWAY starter IDs
  - home-bench: Vector of HOME bench IDs
  - away-bench: Vector of AWAY bench IDs
  - on-info-click: fn [card-slug] to open card detail modal"
  [{:keys [home-players away-players
           home-starters away-starters
           home-bench away-bench
           on-info-click]}]
  (let [home-indices (use-memo #(sel/build-player-index-map home-players) [home-players])
        away-indices (use-memo #(sel/build-player-index-map away-players) [away-players])]
    ($ :div {:class "flex flex-col h-full overflow-y-auto"}
       ;; HOME team
       ($ :div {:class "border-b border-slate-200"}
          ($ team-roster-panel
             {:team           :HOME
              :team-label     "HOME"
              :players        home-players
              :starters       home-starters
              :bench          home-bench
              :player-indices home-indices
              :on-info-click  on-info-click}))

       ;; AWAY team
       ($ team-roster-panel
          {:team           :AWAY
           :team-label     "AWAY"
           :players        away-players
           :starters       away-starters
           :bench          away-bench
           :player-indices away-indices
           :on-info-click  on-info-click}))))
