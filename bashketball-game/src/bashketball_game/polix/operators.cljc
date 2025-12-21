(ns bashketball-game.polix.operators
  "Bashketball-specific operators for polix policy evaluation.

  Registers operators that evaluate game state conditions. Operators are pure
  functions that compute values from game state - they never mutate. Use
  [[register-operators!]] at application startup to register all operators.

  Operators delegate to existing helper functions in [[bashketball-game.board]],
  [[bashketball-game.state]], and [[bashketball-game.movement]]."
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.movement :as movement]
   [bashketball-game.state :as state]
   [polix.operators :as op]))

(defn register-operators!
  "Registers all bashketball operators with polix.

  Call once at application startup before evaluating any policies.

  Operators registered:
  - `:bashketball/hex-distance` - Distance between two positions
  - `:bashketball/distance-to-basket` - Distance from position to target basket
  - `:bashketball/path-clear?` - Whether line of sight is unobstructed
  - `:bashketball/can-move-to?` - Whether player can legally move to position
  - `:bashketball/valid-position?` - Whether position is on the board
  - `:bashketball/player-exhausted?` - Whether player is exhausted
  - `:bashketball/player-on-court?` - Whether player has a position
  - `:bashketball/has-ball?` - Whether player possesses the ball
  - `:bashketball/player-team` - Team that owns the player
  - `:bashketball/count-on-court` - Count of on-court players for a team"
  []

  (op/register-operator! :bashketball/hex-distance
                         {:eval (fn [pos1 pos2]
                                  (board/hex-distance pos1 pos2))})

  (op/register-operator! :bashketball/distance-to-basket
                         {:eval (fn [position team]
                                  (let [basket (if (= team :team/HOME)
                                                 [2 13]
                                                 [2 0])]
                                    (board/hex-distance position basket)))})

  (op/register-operator! :bashketball/path-clear?
                         {:eval (fn [game-state start end]
                                  (board/path-clear? (:board game-state) start end))})

  (op/register-operator! :bashketball/can-move-to?
                         {:eval (fn [game-state player-id position]
                                  (movement/can-move-to? game-state player-id position))})

  (op/register-operator! :bashketball/valid-position?
                         {:eval (fn [position _]
                                  (board/valid-position? position))})

  (op/register-operator! :bashketball/player-exhausted?
                         {:eval (fn [game-state player-id]
                                  (boolean (:exhausted (state/get-basketball-player game-state player-id))))})

  (op/register-operator! :bashketball/player-on-court?
                         {:eval (fn [game-state player-id]
                                  (some? (:position (state/get-basketball-player game-state player-id))))})

  (op/register-operator! :bashketball/has-ball?
                         {:eval (fn [game-state player-id]
                                  (let [ball (state/get-ball game-state)]
                                    (and (= (:status ball) :ball-status/POSSESSED)
                                         (= (:holder-id ball) player-id))))})

  (op/register-operator! :bashketball/player-team
                         {:eval (fn [game-state player-id]
                                  (state/get-basketball-player-team game-state player-id))})

  (op/register-operator! :bashketball/count-on-court
                         {:eval (fn [game-state team]
                                  (count (state/get-on-court-players game-state team)))}))

(defn hex-distance
  "Computes hex distance between two positions.

  Convenience wrapper for use in tests and direct calls."
  [pos1 pos2]
  (board/hex-distance pos1 pos2))

(defn player-exhausted?
  "Returns true if the player is exhausted.

  Convenience wrapper for use in tests and direct calls."
  [game-state player-id]
  (boolean (:exhausted (state/get-basketball-player game-state player-id))))

(defn player-on-court?
  "Returns true if the player has a position on the court.

  Convenience wrapper for use in tests and direct calls."
  [game-state player-id]
  (some? (:position (state/get-basketball-player game-state player-id))))

(defn has-ball?
  "Returns true if the player possesses the ball.

  Convenience wrapper for use in tests and direct calls."
  [game-state player-id]
  (let [ball (state/get-ball game-state)]
    (and (= (:status ball) :ball-status/POSSESSED)
         (= (:holder-id ball) player-id))))

(defn path-clear?
  "Returns true if the path between two positions is unobstructed.

  Convenience wrapper for use in tests and direct calls."
  [game-state start end]
  (board/path-clear? (:board game-state) start end))

(defn can-move-to?
  "Returns true if the player can legally move to the position.

  Convenience wrapper for use in tests and direct calls."
  [game-state player-id position]
  (movement/can-move-to? game-state player-id position))
