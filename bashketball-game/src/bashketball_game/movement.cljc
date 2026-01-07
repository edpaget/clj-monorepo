(ns bashketball-game.movement
  "Movement calculations using event-based stat resolution.

  All functions require a context map with `:state` and `:registry` keys.
  Stat calculations fire events allowing triggers to inject modifiers."
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.polix.scoring :as scoring]
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
          occupied        (set (keys (get-in state [:board :occupants])))]
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

;; =============================================================================
;; Movement Constraints
;; =============================================================================

(defn- get-ball-position
  "Returns the ball position (either loose position or holder's position)."
  [state]
  (let [ball (state/get-ball state)]
    (case (:status ball)
      :ball-status/LOOSE (:position ball)
      :ball-status/POSSESSED (:position (state/get-basketball-player state (:holder-id ball)))
      nil)))

(defn- filter-toward-basket
  "Filters positions to only those closer to the target basket than current."
  [positions current-pos team]
  (let [basket       (scoring/target-basket-for-team team)
        current-dist (board/hex-distance current-pos basket)]
    (filter #(< (board/hex-distance % basket) current-dist) positions)))

(defn- filter-away-from-basket
  "Filters positions to only those farther from the target basket than current."
  [positions current-pos team]
  (let [basket       (scoring/target-basket-for-team team)
        current-dist (board/hex-distance current-pos basket)]
    (filter #(> (board/hex-distance % basket) current-dist) positions)))

(defn- filter-toward-ball
  "Filters positions to only those closer to the ball than current."
  [positions current-pos state]
  (when-let [ball-pos (get-ball-position state)]
    (let [current-dist (board/hex-distance current-pos ball-pos)]
      (filter #(< (board/hex-distance % ball-pos) current-dist) positions))))

(defn- filter-adjacent-to-ball
  "Filters positions to only those adjacent to the ball."
  [positions state]
  (when-let [ball-pos (get-ball-position state)]
    (filter #(= 1 (board/hex-distance % ball-pos)) positions)))

(defn- filter-toward-player
  "Filters positions to only those closer to target player than current."
  [positions current-pos state target-player-id]
  (when-let [target (state/get-basketball-player state target-player-id)]
    (when-let [target-pos (:position target)]
      (let [current-dist (board/hex-distance current-pos target-pos)]
        (filter #(< (board/hex-distance % target-pos) current-dist) positions)))))

(defn- filter-into-zoc
  "Filters positions to only those inside opposing ZoC."
  [positions state player-id]
  (let [team     (state/get-basketball-player-team state player-id)
        opposing (if (= team :team/HOME) :team/AWAY :team/HOME)]
    (filter #(seq (zoc/zoc-defenders state % opposing)) positions)))

(defn- filter-out-of-zoc
  "Filters positions to only those outside opposing ZoC."
  [positions state player-id]
  (let [team     (state/get-basketball-player-team state player-id)
        opposing (if (= team :team/HOME) :team/AWAY :team/HOME)]
    (filter #(empty? (zoc/zoc-defenders state % opposing)) positions)))

(defn apply-constraint
  "Applies a movement constraint to filter valid positions.

  Returns the filtered set of positions, or the original set if the constraint
  would filter out all positions (prevents soft-locks).

  Supported constraints:
  - `:toward-basket` - must end closer to target basket
  - `:away-from-basket` - must end farther from target basket
  - `:toward-ball` - must end closer to the ball
  - `:adjacent-to-ball` - must end adjacent to the ball
  - `{:type :toward-player :target-player-id id}` - must end closer to target player
  - `:into-zoc` - must end in opposing ZoC
  - `:out-of-zoc` - must end outside opposing ZoC"
  [{:keys [state]} player-id positions constraint]
  (let [player          (state/get-basketball-player state player-id)
        current-pos     (:position player)
        team            (state/get-basketball-player-team state player-id)
        constraint-type (if (map? constraint) (:type constraint) constraint)
        filtered        (case constraint-type
                          :toward-basket (filter-toward-basket positions current-pos team)
                          :away-from-basket (filter-away-from-basket positions current-pos team)
                          :toward-ball (filter-toward-ball positions current-pos state)
                          :adjacent-to-ball (filter-adjacent-to-ball positions state)
                          :toward-player (filter-toward-player positions current-pos state
                                                               (:target-player-id constraint))
                          :into-zoc (filter-into-zoc positions state player-id)
                          :out-of-zoc (filter-out-of-zoc positions state player-id)
                          positions)]
    (if (seq filtered)
      (set filtered)
      positions)))

(defn constrained-move-positions
  "Returns valid move positions with optional constraint applied.

  If constraint is nil, returns all valid positions.
  If constraint filters all positions, returns all valid positions."
  [ctx player-id constraint]
  (let [positions (valid-move-positions ctx player-id)]
    (if constraint
      (apply-constraint ctx player-id positions constraint)
      positions)))
