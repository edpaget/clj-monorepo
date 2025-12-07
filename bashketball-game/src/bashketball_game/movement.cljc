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

(defn valid-move-positions
  "Returns set of valid positions the player can move to.

  Uses the player's speed stat to determine movement range. Excludes
  occupied positions and the player's current position. Returns nil
  if the player is not on the board."
  ([game-state player-id]
   (valid-move-positions game-state player-id nil))
  ([game-state player-id catalog]
   (when-let [current-pos (board/find-occupant (:board game-state) player-id)]
     (let [speed (get-player-speed game-state player-id catalog)]
       (->> (board/hex-range current-pos speed)
            (remove #(board/occupant-at (:board game-state) %))
            (remove #(= % current-pos))
            set)))))

(defn can-move-to?
  "Returns true if player can move to the given position.

  Validates that the position is on the board, not occupied, and within
  the player's movement range. Returns false if the player is not on
  the board."
  ([game-state player-id position]
   (can-move-to? game-state player-id position nil))
  ([game-state player-id position catalog]
   (and (board/valid-position? position)
        (nil? (board/occupant-at (:board game-state) position))
        (when-let [current-pos (board/find-occupant (:board game-state) player-id)]
          (<= (board/hex-distance current-pos position)
              (get-player-speed game-state player-id catalog))))))
