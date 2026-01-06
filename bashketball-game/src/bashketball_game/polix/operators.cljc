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
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [clojure.string :as str]
   [polix.operators :as op]))

(defn- operator-context
  "Creates a context for operator evaluation with state and empty registry.

  Operators check current state validity, they don't fire triggers."
  [game-state]
  {:state game-state
   :registry (triggers/create-registry)})

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
  - `:actions-remaining` - Actions remaining for a team
  - `:skill-test-active?` - Whether there's a pending skill test
  - `:skill-test-stat` - Stat being tested
  - `:skill-test-actor` - Player ID performing the test
  - `:skill-test-base` - Base stat value
  - `:skill-test-total-modifiers` - Sum of all modifier amounts
  - `:choice-pending?` - Whether there's a pending choice
  - `:choice-waiting-for` - Team that needs to respond to the choice
  - `:adjacent?` - Whether two players are in neighboring hexes
  - `:within-range?` - Whether entity is within N hexes of target (player or :basket)
  - `:ball-holder` - Player ID holding the ball, or nil
  - `:opponent-team` - The opposing team keyword
  - `:stat-value` - Effective stat value with modifiers
  - `:player-size` - Player's size (:size/SM, :size/MD, :size/LG)
  - `:size-comparison` - Compare two players' sizes
  - `:in-zoc?` - Whether position is in defender's ZoC
  - `:zoc-defenders` - Set of defenders exerting ZoC on position
  - `:pass-path-zoc` - Defenders whose ZoC intersects pass path
  - `:scoring-zone` - Scoring zone (:two-point or :three-point)
  - `:cards-in-hand` - Number of cards in team's hand
  - `:distance-advantage` - Advantage level from distance
  - `:size-advantage` - Advantage level from size comparison"
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
                                  (movement/can-move-to? (operator-context game-state) player-id position))})

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
                                  (get-in game-state [:players team :actions-remaining] 0))})

  ;; Skill Test Operators

  (op/register-operator! :skill-test-active?
                         {:eval (fn [game-state]
                                  (some? (:pending-skill-test game-state)))})

  (op/register-operator! :skill-test-stat
                         {:eval (fn [game-state]
                                  (get-in game-state [:pending-skill-test :stat]))})

  (op/register-operator! :skill-test-actor
                         {:eval (fn [game-state]
                                  (get-in game-state [:pending-skill-test :actor-id]))})

  (op/register-operator! :skill-test-base
                         {:eval (fn [game-state]
                                  (get-in game-state [:pending-skill-test :base-value]))})

  (op/register-operator! :skill-test-total-modifiers
                         {:eval (fn [game-state]
                                  (let [modifiers (get-in game-state [:pending-skill-test :modifiers])]
                                    (reduce + 0 (map :amount modifiers))))})

  ;; Choice Operators

  (op/register-operator! :choice-pending?
                         {:eval (fn [game-state]
                                  (some? (:pending-choice game-state)))})

  (op/register-operator! :choice-waiting-for
                         {:eval (fn [game-state]
                                  (get-in game-state [:pending-choice :waiting-for]))})

  ;; Standard Action Operators

  (op/register-operator! :adjacent?
                         {:eval (fn [game-state player-id-1 player-id-2]
                                  (let [p1 (state/get-basketball-player game-state player-id-1)
                                        p2 (state/get-basketball-player game-state player-id-2)]
                                    (when (and (:position p1) (:position p2))
                                      (= 1 (board/hex-distance (:position p1) (:position p2))))))})

  (op/register-operator! :within-range?
                         {:eval (fn [game-state entity-id target max-range]
                                  (let [entity     (state/get-basketball-player game-state entity-id)
                                        entity-pos (:position entity)
                                        target-pos (if (= target :basket)
                                                     (let [team (state/get-basketball-player-team game-state entity-id)]
                                                       (if (= team :team/HOME)
                                                         [2 13]
                                                         [2 0]))
                                                     (:position (state/get-basketball-player game-state target)))]
                                    (when (and entity-pos target-pos)
                                      (<= (board/hex-distance entity-pos target-pos) max-range))))})

  (op/register-operator! :ball-holder
                         {:eval (fn [game-state]
                                  (let [ball (state/get-ball game-state)]
                                    (when (= (:status ball) :ball-status/POSSESSED)
                                      (:holder-id ball))))})

  (op/register-operator! :opponent-team
                         {:eval (fn [team]
                                  (case team
                                    :team/HOME :team/AWAY
                                    :team/AWAY :team/HOME
                                    nil))})

  ;; Phase 1 Operators: Stats, Size, ZoC, Scoring

  (op/register-operator! :stat-value
                         {:eval (fn [game-state player-id stat]
                                  (let [player     (state/get-basketball-player game-state player-id)
                                        stat-name  (name stat)
                                        stats-key  (keyword (str/lower-case stat-name))
                                        base-value (get-in player [:stats stats-key] 0)
                                        modifiers  (:modifiers player)
                                        stat-mods  (filter #(= (:stat %) stat) modifiers)
                                        mod-total  (reduce + 0 (map :amount stat-mods))]
                                    (+ base-value mod-total)))})

  (op/register-operator! :player-size
                         {:eval (fn [game-state player-id]
                                  (get-in (state/get-basketball-player game-state player-id)
                                          [:stats :size]))})

  (op/register-operator! :size-comparison
                         {:eval (fn [game-state player-a-id player-b-id]
                                  (let [size-order {:size/SM 0 :size/MD 1 :size/LG 2}
                                        size-a     (get-in (state/get-basketball-player game-state player-a-id)
                                                           [:stats :size])
                                        size-b     (get-in (state/get-basketball-player game-state player-b-id)
                                                           [:stats :size])
                                        ord-a      (get size-order size-a 1)
                                        ord-b      (get size-order size-b 1)]
                                    (cond
                                      (> ord-a ord-b) :larger
                                      (< ord-a ord-b) :smaller
                                      :else :same)))})

  (op/register-operator! :in-zoc?
                         {:eval (fn [game-state position defender-id]
                                  (let [defender (state/get-basketball-player game-state defender-id)
                                        def-pos  (:position defender)]
                                    (and def-pos
                                         (not (:exhausted defender))
                                         (<= (board/hex-distance position def-pos) 1))))})

  (op/register-operator! :zoc-defenders
                         {:eval (fn [game-state position defending-team]
                                  (let [defenders (state/get-on-court-players game-state defending-team)]
                                    (->> defenders
                                         (filter (fn [player-id]
                                                   (let [player  (state/get-basketball-player game-state player-id)
                                                         def-pos (:position player)]
                                                     (and def-pos
                                                          (not (:exhausted player))
                                                          (<= (board/hex-distance position def-pos) 1)))))
                                         set)))})

  (op/register-operator! :pass-path-zoc
                         {:eval (fn [game-state from-pos to-pos defending-team]
                                  (let [path-hexes (board/hex-line from-pos to-pos)
                                        defenders  (state/get-on-court-players game-state defending-team)]
                                    (->> defenders
                                         (filter (fn [player-id]
                                                   (let [player  (state/get-basketball-player game-state player-id)
                                                         def-pos (:position player)]
                                                     (and def-pos
                                                          (not (:exhausted player))
                                                          (some #(<= (board/hex-distance % def-pos) 1)
                                                                path-hexes)))))
                                         set)))})

  (op/register-operator! :scoring-zone
                         {:eval (fn [position _target-basket]
                                  (let [[_q r] position]
                                    (if (or (<= r 3) (>= r 10))
                                      :two-point
                                      :three-point)))})

  (op/register-operator! :cards-in-hand
                         {:eval (fn [game-state team]
                                  (count (state/get-hand game-state team)))})

  (op/register-operator! :distance-advantage
                         {:eval (fn [distance]
                                  (cond
                                    (<= distance 2) :advantage
                                    (<= distance 4) :normal
                                    :else :disadvantage))})

  (op/register-operator! :size-advantage
                         {:eval (fn [game-state actor-id target-id]
                                  (let [size-order  {:size/SM 0 :size/MD 1 :size/LG 2}
                                        actor-size  (get-in (state/get-basketball-player game-state actor-id)
                                                            [:stats :size])
                                        target-size (get-in (state/get-basketball-player game-state target-id)
                                                            [:stats :size])
                                        ord-actor   (get size-order actor-size 1)
                                        ord-target  (get size-order target-size 1)]
                                    (cond
                                      (> ord-actor ord-target) :advantage
                                      (< ord-actor ord-target) :disadvantage
                                      :else :normal)))}))

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

  Convenience wrapper for use in tests and direct calls.
  Creates an empty registry context for movement validation."
  [game-state player-id position]
  (movement/can-move-to? (operator-context game-state) player-id position))

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

(defn skill-test-active?
  "Returns true if there's a pending skill test.

  Convenience wrapper for use in tests and direct calls."
  [game-state]
  (some? (:pending-skill-test game-state)))

(defn skill-test-stat
  "Returns the stat being tested, or nil if no pending test.

  Convenience wrapper for use in tests and direct calls."
  [game-state]
  (get-in game-state [:pending-skill-test :stat]))

(defn skill-test-actor
  "Returns the player ID performing the test, or nil if no pending test.

  Convenience wrapper for use in tests and direct calls."
  [game-state]
  (get-in game-state [:pending-skill-test :actor-id]))

(defn skill-test-base
  "Returns the base stat value for the test, or nil if no pending test.

  Convenience wrapper for use in tests and direct calls."
  [game-state]
  (get-in game-state [:pending-skill-test :base-value]))

(defn skill-test-total-modifiers
  "Returns the sum of all modifier amounts, or 0 if no pending test.

  Convenience wrapper for use in tests and direct calls."
  [game-state]
  (let [modifiers (get-in game-state [:pending-skill-test :modifiers])]
    (reduce + 0 (map :amount modifiers))))

(defn choice-pending?
  "Returns true if there's a pending choice awaiting player input.

  Convenience wrapper for use in tests and direct calls."
  [game-state]
  (some? (:pending-choice game-state)))

(defn choice-waiting-for
  "Returns the team that needs to respond to the pending choice, or nil.

  Convenience wrapper for use in tests and direct calls."
  [game-state]
  (get-in game-state [:pending-choice :waiting-for]))

;; Standard Action Convenience Wrappers

(defn adjacent?
  "Returns true if two players are in neighboring hexes (distance 1).

  Returns nil if either player is not on the court."
  [game-state player-id-1 player-id-2]
  (let [p1 (state/get-basketball-player game-state player-id-1)
        p2 (state/get-basketball-player game-state player-id-2)]
    (when (and (:position p1) (:position p2))
      (= 1 (board/hex-distance (:position p1) (:position p2))))))

(defn within-range?
  "Returns true if entity is within `max-range` hexes of target.

  `target` can be a player-id string or the keyword `:basket`.
  When `:basket`, uses the entity's target basket based on their team.
  Returns nil if either entity is not on the court."
  [game-state entity-id target max-range]
  (let [entity     (state/get-basketball-player game-state entity-id)
        entity-pos (:position entity)
        target-pos (if (= target :basket)
                     (let [team (state/get-basketball-player-team game-state entity-id)]
                       (if (= team :team/HOME)
                         [2 13]
                         [2 0]))
                     (:position (state/get-basketball-player game-state target)))]
    (when (and entity-pos target-pos)
      (<= (board/hex-distance entity-pos target-pos) max-range))))

(defn ball-holder
  "Returns the player-id of the player holding the ball, or nil if ball is loose/in-air."
  [game-state]
  (let [ball (state/get-ball game-state)]
    (when (= (:status ball) :ball-status/POSSESSED)
      (:holder-id ball))))

(defn opponent-team
  "Returns the opposing team keyword."
  [team]
  (case team
    :team/HOME :team/AWAY
    :team/AWAY :team/HOME
    nil))

;; Phase 1 Convenience Wrappers

(defn stat-value
  "Returns the effective stat value for a player, including modifiers.

  Takes a stat keyword like `:stat/SHOOTING` and returns the base value
  plus any active modifiers for that stat."
  [game-state player-id stat]
  (let [player     (state/get-basketball-player game-state player-id)
        stat-name  (name stat)
        stats-key  (keyword (str/lower-case stat-name))
        base-value (get-in player [:stats stats-key] 0)
        modifiers  (:modifiers player)
        stat-mods  (filter #(= (:stat %) stat) modifiers)
        mod-total  (reduce + 0 (map :amount stat-mods))]
    (+ base-value mod-total)))

(defn player-size
  "Returns the size of a player (:size/SM, :size/MD, or :size/LG)."
  [game-state player-id]
  (get-in (state/get-basketball-player game-state player-id)
          [:stats :size]))

(defn size-comparison
  "Compares two players' sizes, returning :larger, :same, or :smaller.

  Returns the comparison from player-a's perspective relative to player-b."
  [game-state player-a-id player-b-id]
  (let [size-order {:size/SM 0 :size/MD 1 :size/LG 2}
        size-a     (player-size game-state player-a-id)
        size-b     (player-size game-state player-b-id)
        ord-a      (get size-order size-a 1)
        ord-b      (get size-order size-b 1)]
    (cond
      (> ord-a ord-b) :larger
      (< ord-a ord-b) :smaller
      :else :same)))

(defn in-zoc?
  "Returns true if position is within defender's Zone of Control.

  A defender exerts ZoC if they are unexhausted and within 1 hex of the position."
  [game-state position defender-id]
  (let [defender (state/get-basketball-player game-state defender-id)
        def-pos  (:position defender)]
    (and def-pos
         (not (:exhausted defender))
         (<= (board/hex-distance position def-pos) 1))))

(defn zoc-defenders
  "Returns set of defender IDs exerting ZoC on the given position.

  Only includes unexhausted defenders within 1 hex of the position."
  [game-state position defending-team]
  (let [defenders (state/get-on-court-players game-state defending-team)]
    (->> defenders
         (filter #(in-zoc? game-state position %))
         set)))

(defn pass-path-zoc
  "Returns set of defenders whose ZoC intersects the pass path.

  Checks all hexes along the line from `from-pos` to `to-pos` and returns
  any unexhausted defenders from `defending-team` whose ZoC covers any hex."
  [game-state from-pos to-pos defending-team]
  (let [path-hexes (board/hex-line from-pos to-pos)
        defenders  (state/get-on-court-players game-state defending-team)]
    (->> defenders
         (filter (fn [player-id]
                   (let [player  (state/get-basketball-player game-state player-id)
                         def-pos (:position player)]
                     (and def-pos
                          (not (:exhausted player))
                          (some #(<= (board/hex-distance % def-pos) 1)
                                path-hexes)))))
         set)))

(defn scoring-zone
  "Returns :two-point or :three-point based on position on the court.

  Two-point zone: columns 0-3 (left basket) or 10-13 (right basket).
  Three-point zone: columns 4-9 (middle of court)."
  [position]
  (let [[_q r] position]
    (if (or (<= r 3) (>= r 10))
      :two-point
      :three-point)))

(defn cards-in-hand
  "Returns the number of cards in the team's hand."
  [game-state team]
  (count (state/get-hand game-state team)))

(defn distance-advantage
  "Returns advantage level based on distance.

  Close (1-2 hexes): :advantage
  Medium (3-4 hexes): :normal
  Long (5+ hexes): :disadvantage"
  [distance]
  (cond
    (<= distance 2) :advantage
    (<= distance 4) :normal
    :else :disadvantage))

(defn size-advantage
  "Returns advantage level from size comparison between actor and target.

  Larger actor: :advantage
  Same size: :normal
  Smaller actor: :disadvantage"
  [game-state actor-id target-id]
  (case (size-comparison game-state actor-id target-id)
    :larger :advantage
    :smaller :disadvantage
    :normal))
