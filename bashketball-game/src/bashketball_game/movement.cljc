(ns bashketball-game.movement
  "Movement calculations using event-based stat resolution.

  All functions require a context map with `:state` and `:registry` keys.
  Stat calculations fire events allowing triggers to inject modifiers."
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.polix.stats :as stats]
   [bashketball-game.polix.zoc :as zoc]
   [bashketball-game.state :as state]))

(defn get-player-speed
  "Returns the effective movement speed for a player.

  Fires `:bashketball/calculate-speed.request` to allow trigger-based modifiers.
  Returns `{:value speed :state state :registry registry}`.

  Requires context with `:state` and `:registry`."
  [{:keys [state registry] :as ctx} player-id]
  {:pre [(some? state) (some? registry)]}
  (stats/get-effective-speed ctx player-id))

(defn valid-move-positions
  "Returns set of valid positions the player can move to.

  Uses BFS pathfinding to find positions reachable within the player's
  effective speed, accounting for obstacles. Players cannot move through
  occupied hexes. Returns nil if the player is not on the board.

  Requires context with `:state` and `:registry`."
  [{:keys [state] :as ctx} player-id]
  {:pre [(some? state) (some? (:registry ctx))]}
  (when-let [current-pos (board/find-occupant (:board state) player-id)]
    (let [{:keys [value]} (get-player-speed ctx player-id)
          occupied (set (keys (get-in state [:board :occupants])))]
      (board/reachable-positions current-pos value occupied))))

(defn can-move-to?
  "Returns true if player can move to the given position.

  Uses pathfinding to verify the position is actually reachable, not just
  within distance. Returns false if the player is not on the board.

  Requires context with `:state` and `:registry`."
  [ctx player-id position]
  (contains? (valid-move-positions ctx player-id) position))

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
  - Smaller: +0

  Requires context with `:state` and `:registry`."
  [{:keys [state] :as ctx} mover-id to-position]
  {:pre [(some? state) (some? (:registry ctx))]}
  (let [mover-team    (state/get-basketball-player-team state mover-id)
        opposing-team (if (= mover-team :team/HOME) :team/AWAY :team/HOME)
        defenders     (zoc/zoc-defenders state to-position opposing-team)
        zoc-costs     (map #(zoc/zoc-movement-cost state mover-id %) defenders)
        zoc-cost      (if (seq zoc-costs) (apply max zoc-costs) 0)]
    {:base-cost 1
     :zoc-cost zoc-cost
     :total-cost (+ 1 zoc-cost)
     :zoc-defender-ids (vec defenders)}))
