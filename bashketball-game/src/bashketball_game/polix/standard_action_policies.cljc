(ns bashketball-game.polix.standard-action-policies
  "Standard action policies with skill test integration.

  Provides policy functions for Shoot/Block, Pass/Steal, and Screen/Check
  standard actions, integrating with the skill test and ZoC systems.

  Each action has:
  - Precondition checking
  - Advantage source collection
  - Skill test setup with proper difficulty and advantage"
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.polix.operators :as ops]
   [bashketball-game.polix.skill-tests :as skill]
   [bashketball-game.state :as state]))

(defn target-basket
  "Returns the target basket position for a team.

  HOME team shoots at [2 13], AWAY team shoots at [2 0]."
  [team]
  (if (= team :team/HOME)
    [2 13]
    [2 0]))

(defn opposing-team
  "Returns the opposing team."
  [team]
  (if (= team :team/HOME) :team/AWAY :team/HOME))

;; =============================================================================
;; Shoot Action
;; =============================================================================

(defn shoot-precondition?
  "Returns true if the actor can perform a Shoot action.

  Requires:
  - Actor has the ball
  - Actor is within 7 hexes of their target basket
  - Actor is not exhausted"
  [game-state actor-id]
  (let [actor      (state/get-basketball-player game-state actor-id)
        actor-team (state/get-basketball-player-team game-state actor-id)
        basket     (target-basket actor-team)
        actor-pos  (:position actor)]
    (and (ops/has-ball? game-state actor-id)
         (not (:exhausted actor))
         actor-pos
         (<= (board/hex-distance actor-pos basket) 7))))

(defn shoot-advantage-sources
  "Collects all advantage sources for a Shoot action.

  Sources:
  - Distance to basket
  - ZoC from defenders (or uncontested bonus)"
  [game-state shooter-id]
  (let [shooter-team   (state/get-basketball-player-team game-state shooter-id)
        defending-team (opposing-team shooter-team)
        basket         (target-basket shooter-team)]
    (skill/shooting-advantage-sources game-state shooter-id basket defending-team)))

(defn shoot-difficulty
  "Returns the difficulty for a Shoot skill test.

  Difficulty = 8 - effective shooting stat"
  [game-state shooter-id]
  (let [shooting (ops/stat-value game-state shooter-id :stat/SHOOTING)]
    (skill/compute-difficulty shooting)))

;; =============================================================================
;; Block Action
;; =============================================================================

(defn block-precondition?
  "Returns true if the actor can perform a Block action.

  Requires:
  - Actor is adjacent to target
  - Target has the ball
  - Target is within 4 hexes of their basket"
  [game-state actor-id target-id]
  (let [target      (state/get-basketball-player game-state target-id)
        target-team (state/get-basketball-player-team game-state target-id)
        basket      (target-basket target-team)
        target-pos  (:position target)]
    (and (ops/adjacent? game-state actor-id target-id)
         (ops/has-ball? game-state target-id)
         target-pos
         (<= (board/hex-distance target-pos basket) 4))))

;; =============================================================================
;; Pass Action
;; =============================================================================

(defn pass-precondition?
  "Returns true if the actor can perform a Pass action.

  Requires:
  - Actor has the ball
  - Target is on court
  - Target is within 6 hexes of actor
  - Target is on the same team"
  [game-state actor-id target-id]
  (let [actor-team  (state/get-basketball-player-team game-state actor-id)
        target-team (state/get-basketball-player-team game-state target-id)
        target      (state/get-basketball-player game-state target-id)]
    (and (ops/has-ball? game-state actor-id)
         (:position target)
         (ops/within-range? game-state actor-id target-id 6)
         (= actor-team target-team))))

(defn pass-advantage-sources
  "Collects all advantage sources for a Pass action.

  Sources:
  - Distance of pass
  - ZoC in pass path (or uncontested bonus)"
  [game-state passer-id target-id]
  (let [passer-team    (state/get-basketball-player-team game-state passer-id)
        defending-team (opposing-team passer-team)
        target         (state/get-basketball-player game-state target-id)
        target-pos     (:position target)]
    (skill/passing-advantage-sources game-state passer-id target-pos defending-team)))

(defn pass-difficulty
  "Returns the difficulty for a Pass skill test.

  Difficulty = 8 - effective passing stat"
  [game-state passer-id]
  (let [passing (ops/stat-value game-state passer-id :stat/PASSING)]
    (skill/compute-difficulty passing)))

;; =============================================================================
;; Steal Action
;; =============================================================================

(defn steal-precondition?
  "Returns true if the actor can perform a Steal action.

  Requires:
  - Actor is adjacent to target
  - Target has the ball
  - Actor and target are on different teams"
  [game-state actor-id target-id]
  (let [actor-team  (state/get-basketball-player-team game-state actor-id)
        target-team (state/get-basketball-player-team game-state target-id)]
    (and (ops/adjacent? game-state actor-id target-id)
         (ops/has-ball? game-state target-id)
         (not= actor-team target-team))))

(defn steal-advantage-sources
  "Collects all advantage sources for a Steal action.

  Sources:
  - Size comparison (defender vs ball carrier)
  Note: Small defenders ignore size disadvantage per rules"
  [game-state stealer-id target-id]
  (let [stealer-size (ops/player-size game-state stealer-id)
        base-sources (skill/defense-advantage-sources game-state stealer-id target-id)]
    ;; Small defenders ignore size disadvantage on steals
    (if (= stealer-size :size/SM)
      (map (fn [s]
             (if (and (= (:source s) :size)
                      (= (:advantage s) :disadvantage))
               (assoc s :advantage :normal)
               s))
           base-sources)
      base-sources)))

(defn steal-difficulty
  "Returns the difficulty for a Steal skill test.

  Difficulty = 8 - (effective defense stat - 2)
  Note: Steal has a -2 penalty (Defend-2)"
  [game-state stealer-id]
  (let [defense (ops/stat-value game-state stealer-id :stat/DEFENSE)]
    (skill/compute-difficulty (- defense 2))))

;; =============================================================================
;; Screen Action
;; =============================================================================

(defn screen-precondition?
  "Returns true if the actor can perform a Screen action.

  Requires:
  - Actor is adjacent to target
  - Actor and target are on different teams
  - Actor is not exhausted"
  [game-state actor-id target-id]
  (let [actor       (state/get-basketball-player game-state actor-id)
        actor-team  (state/get-basketball-player-team game-state actor-id)
        target-team (state/get-basketball-player-team game-state target-id)]
    (and (ops/adjacent? game-state actor-id target-id)
         (not= actor-team target-team)
         (not (:exhausted actor)))))

(defn screen-advantage-sources
  "Collects all advantage sources for a Screen action.

  Sources:
  - Size comparison (screener vs target)"
  [game-state screener-id target-id]
  (skill/defense-advantage-sources game-state screener-id target-id))

(defn screen-difficulty
  "Returns the difficulty for a Screen skill test.

  Difficulty = 8 - (effective defense stat - 1)
  Note: Screen has a -1 penalty (Defend-1)"
  [game-state screener-id]
  (let [defense (ops/stat-value game-state screener-id :stat/DEFENSE)]
    (skill/compute-difficulty (- defense 1))))

;; =============================================================================
;; Check Action
;; =============================================================================

(defn check-precondition?
  "Returns true if the actor can perform a Check action.

  Requires:
  - Actor is adjacent to target
  - Actor and target are on different teams
  - Actor is not exhausted"
  [game-state actor-id target-id]
  ;; Same preconditions as screen
  (screen-precondition? game-state actor-id target-id))

(defn check-advantage-sources
  "Collects all advantage sources for a Check action.

  Sources:
  - Size comparison (checker vs target)"
  [game-state checker-id target-id]
  (skill/defense-advantage-sources game-state checker-id target-id))

(defn check-difficulty
  "Returns the difficulty for a Check skill test.

  Difficulty = 8 - (effective defense stat - 1)
  Note: Check has a -1 penalty (Defend-1)"
  [game-state checker-id]
  (let [defense (ops/stat-value game-state checker-id :stat/DEFENSE)]
    (skill/compute-difficulty (- defense 1))))

;; =============================================================================
;; Skill Test Setup Helpers
;; =============================================================================

(defn setup-shoot-test
  "Creates a skill test configuration for a Shoot action.

  Returns a map with :difficulty, :advantage-sources, and :advantage."
  [game-state shooter-id]
  (let [sources  (shoot-advantage-sources game-state shooter-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (shoot-difficulty game-state shooter-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))

(defn setup-pass-test
  "Creates a skill test configuration for a Pass action.

  Returns a map with :difficulty, :advantage-sources, and :advantage."
  [game-state passer-id target-id]
  (let [sources  (pass-advantage-sources game-state passer-id target-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (pass-difficulty game-state passer-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))

(defn setup-steal-test
  "Creates a skill test configuration for a Steal action.

  Returns a map with :difficulty, :advantage-sources, and :advantage."
  [game-state stealer-id target-id]
  (let [sources  (steal-advantage-sources game-state stealer-id target-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (steal-difficulty game-state stealer-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))

(defn setup-screen-test
  "Creates a skill test configuration for a Screen action.

  Returns a map with :difficulty, :advantage-sources, and :advantage."
  [game-state screener-id target-id]
  (let [sources  (screen-advantage-sources game-state screener-id target-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (screen-difficulty game-state screener-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))

(defn setup-check-test
  "Creates a skill test configuration for a Check action.

  Returns a map with :difficulty, :advantage-sources, and :advantage."
  [game-state checker-id target-id]
  (let [sources  (check-advantage-sources game-state checker-id target-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (check-difficulty game-state checker-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))
