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

  Note: Operators use non-namespaced keywords to work with polix parser.
  The bashketball-game.polix.operators namespace provides the implementation.

  Operators registered:
  - `:hex-distance` - Distance between two positions
  - `:distance-to-basket` - Distance from position to target basket
  - `:path-clear?` - Whether line of sight is unobstructed
  - `:can-move-to?` - Whether player can legally move to position
  - `:valid-position?` - Whether position is on the board
  - `:player-exhausted?` - Whether player is exhausted
  - `:player-on-court?` - Whether player has a position
  - `:has-ball?` - Whether player possesses the ball
  - `:player-team` - Team that owns the player
  - `:count-on-court` - Count of on-court players for a team
  - `:card-in-hand?` - Whether card instance is in team's hand
  - `:actions-remaining` - Actions remaining for a team"
  []

  (op/register-operator! :hex-distance
                         {:eval (fn [pos1 pos2]
                                  (board/hex-distance pos1 pos2))})

  (op/register-operator! :distance-to-basket
                         {:eval (fn [position team]
                                  (let [basket (if (= team :team/HOME)
                                                 [2 13]
                                                 [2 0])]
                                    (board/hex-distance position basket)))})

  (op/register-operator! :path-clear?
                         {:eval (fn [game-state start end]
                                  (board/path-clear? (:board game-state) start end))})

  (op/register-operator! :can-move-to?
                         {:eval (fn [game-state player-id position]
                                  (movement/can-move-to? game-state player-id position))})

  (op/register-operator! :valid-position?
                         {:eval (fn [position _]
                                  (board/valid-position? position))})

  (op/register-operator! :player-exhausted?
                         {:eval (fn [game-state player-id]
                                  (boolean (:exhausted (state/get-basketball-player game-state player-id))))})

  (op/register-operator! :player-on-court?
                         {:eval (fn [game-state player-id]
                                  (some? (:position (state/get-basketball-player game-state player-id))))})

  (op/register-operator! :has-ball?
                         {:eval (fn [game-state player-id]
                                  (let [ball (state/get-ball game-state)]
                                    (and (= (:status ball) :ball-status/POSSESSED)
                                         (= (:holder-id ball) player-id))))})

  (op/register-operator! :player-team
                         {:eval (fn [game-state player-id]
                                  (state/get-basketball-player-team game-state player-id))})

  (op/register-operator! :count-on-court
                         {:eval (fn [game-state team]
                                  (count (state/get-on-court-players game-state team)))})

  (op/register-operator! :card-in-hand?
                         {:eval (fn [game-state team instance-id]
                                  (boolean
                                   (some #(= (:instance-id %) instance-id)
                                         (state/get-hand game-state team))))})

  (op/register-operator! :actions-remaining
                         {:eval (fn [game-state team]
                                  (get-in game-state [:players team :actions-remaining] 0))}))

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

(defn card-in-hand?
  "Returns true if a card with the given instance-id is in the team's hand.

  Convenience wrapper for use in tests and direct calls."
  [game-state team instance-id]
  (boolean
   (some #(= (:instance-id %) instance-id)
         (state/get-hand game-state team))))

(defn actions-remaining
  "Returns the number of actions remaining for a team.

  Convenience wrapper for use in tests and direct calls."
  [game-state team]
  (get-in game-state [:players team :actions-remaining] 0))
