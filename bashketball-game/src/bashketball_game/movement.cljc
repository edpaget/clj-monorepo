(ns bashketball-game.movement
  "Movement calculations using player stats and optional card catalog.

  Provides functions to determine player movement range and valid move
  positions. The catalog is a simple map of `{slug -> card}` that can be
  constructed from any data source (GraphQL on frontend, classpath on backend)."
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.polix.zoc :as zoc]
   [bashketball-game.state :as state]))

(defn get-player-speed
  "Returns the movement speed for a player.

  Uses the player's stats from game state. Falls back to catalog lookup
  via `:card-slug` if provided. Returns default of 2 if neither source
  has speed data.

  The `catalog` parameter is an optional map of `{slug -> card}` where
  each card has a `:speed` key."
  ([game-state player-id]
   (get-player-speed game-state player-id nil))
  ([game-state player-id catalog]
   (let [player (state/get-basketball-player game-state player-id)]
     (or (get-in player [:stats :speed])
         (when catalog
           (get-in catalog [(:card-slug player) :speed]))
         2))))

(defn valid-move-positions
  "Returns set of valid positions the player can move to.

  Uses BFS pathfinding to find positions reachable within the player's
  speed, accounting for obstacles. Players cannot move through occupied
  hexes. Returns nil if the player is not on the board."
  ([game-state player-id]
   (valid-move-positions game-state player-id nil))
  ([game-state player-id catalog]
   (when-let [current-pos (board/find-occupant (:board game-state) player-id)]
     (let [speed    (get-player-speed game-state player-id catalog)
           occupied (set (keys (get-in game-state [:board :occupants])))]
       (board/reachable-positions current-pos speed occupied)))))

(defn can-move-to?
  "Returns true if player can move to the given position.

  Uses pathfinding to verify the position is actually reachable, not just
  within distance. Returns false if the player is not on the board."
  ([game-state player-id position]
   (can-move-to? game-state player-id position nil))
  ([game-state player-id position catalog]
   (contains? (valid-move-positions game-state player-id catalog) position)))

(defn compute-step-cost
  "Computes the total movement cost to step into a hex.

  Returns a map with:
  - `:base-cost` - always 1
  - `:zoc-cost` - extra cost from ZoC (0, 1, or 2 based on defender size)
  - `:total-cost` - sum of base and ZoC costs
  - `:zoc-defender-ids` - vector of defender IDs exerting ZoC

  The ZoC cost is the maximum from any defender exerting ZoC on the target hex:
  - Larger defender: +2
  - Same size: +1
  - Smaller: +0"
  [game-state mover-id to-position]
  (let [mover-team    (state/get-basketball-player-team game-state mover-id)
        opposing-team (if (= mover-team :team/HOME) :team/AWAY :team/HOME)
        defenders     (zoc/zoc-defenders game-state to-position opposing-team)
        zoc-costs     (map #(zoc/zoc-movement-cost game-state mover-id %) defenders)
        zoc-cost      (if (seq zoc-costs) (apply max zoc-costs) 0)]
    {:base-cost 1
     :zoc-cost zoc-cost
     :total-cost (+ 1 zoc-cost)
     :zoc-defender-ids (vec defenders)}))
