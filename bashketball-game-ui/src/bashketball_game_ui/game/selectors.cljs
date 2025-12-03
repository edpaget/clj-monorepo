(ns bashketball-game-ui.game.selectors
  "Pure functions for extracting and computing game data.

  All functions are pure and independently testable. These selectors extract
  derived data from the game state without any React dependencies.")

(defn opponent-team
  "Returns the opponent's team keyword given the player's team."
  [my-team]
  (if (= my-team :home) :away :home))

(defn setup-mode?
  "Returns true if the game is in setup phase."
  [phase]
  (or (= phase :setup) (= phase "SETUP")))

(defn get-player-by-id
  "Looks up a player by ID, handling both string and keyword keys."
  [players id]
  (or (get players id)
      (get players (keyword id))))

(defn valid-pass-targets
  "Returns set of player IDs that can receive a pass.

  Excludes the current ball holder from valid targets."
  [my-players ball-holder-id]
  (when (and ball-holder-id my-players)
    (->> (vals my-players)
         (remove #(= (:id %) ball-holder-id))
         (map :id)
         set)))

(defn setup-placed-count
  "Returns count of starters that have been placed on the board."
  [my-starters my-players]
  (when my-starters
    (->> my-starters
         (map #(get-player-by-id my-players %))
         (filter :position)
         count)))

(defn target-basket
  "Returns the target basket position for shooting based on team."
  [my-team]
  (if (= my-team :home) [2 13] [2 0]))

(defn my-player-data
  "Extracts all player-related data from game state for the given team.

  Returns a map with :my-player, :my-players, :my-starters, and :my-hand."
  [game-state my-team]
  (let [my-player (get-in game-state [:players my-team])]
    {:my-player   my-player
     :my-players  (get-in my-player [:team :players])
     :my-starters (get-in my-player [:team :starters])
     :my-hand     (get-in my-player [:deck :hand])}))

(defn opponent-data
  "Extracts opponent player data from game state."
  [game-state opponent-team]
  (get-in game-state [:players opponent-team]))

(defn all-players
  "Returns home and away player maps for the hex grid."
  [game-state]
  {:home-players (get-in game-state [:players :home :team :players])
   :away-players (get-in game-state [:players :away :team :players])})
