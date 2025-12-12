(ns bashketball-game.movement
  "Movement calculations using player stats and optional card catalog.

  Provides functions to determine player movement range and valid move
  positions. The catalog is a simple map of `{slug -> card}` that can be
  constructed from any data source (GraphQL on frontend, classpath on backend)."
  (:require
   [bashketball-game.board :as board]
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

(defn- opposing-team
  "Returns the opposing team keyword."
  [team]
  (if (= team :team/HOME) :team/AWAY :team/HOME))

(defn- get-opposing-player-positions
  "Returns set of positions occupied by opposing team players."
  [game-state player-id]
  (let [team      (state/get-basketball-player-team game-state player-id)
        opponent  (opposing-team team)
        occupants (get-in game-state [:board :occupants])]
    (->> occupants
         (filter (fn [[_pos occ]]
                   (when-let [occ-id (:id occ)]
                     (= opponent (state/get-basketball-player-team game-state occ-id)))))
         (map first)
         set)))

(defn- contested-positions
  "Returns set of hexes adjacent to opposing players (zone of control)."
  [game-state player-id]
  (->> (get-opposing-player-positions game-state player-id)
       (mapcat board/hex-neighbors)
       set))

(defn valid-move-positions
  "Returns set of valid positions the player can move to.

  Uses BFS pathfinding to find positions reachable within the player's
  speed, accounting for obstacles. Players cannot move through occupied
  hexes. Moving through hexes adjacent to opposing players costs 2 movement.
  Returns nil if the player is not on the board."
  ([game-state player-id]
   (valid-move-positions game-state player-id nil))
  ([game-state player-id catalog]
   (when-let [current-pos (board/find-occupant (:board game-state) player-id)]
     (let [speed     (get-player-speed game-state player-id catalog)
           occupied  (set (keys (get-in game-state [:board :occupants])))
           contested (contested-positions game-state player-id)]
       (board/reachable-positions current-pos speed occupied contested)))))

(defn can-move-to?
  "Returns true if player can move to the given position.

  Uses pathfinding to verify the position is actually reachable, not just
  within distance. Returns false if the player is not on the board."
  ([game-state player-id position]
   (can-move-to? game-state player-id position nil))
  ([game-state player-id position catalog]
   (contains? (valid-move-positions game-state player-id catalog) position)))
