(ns bashketball-game.polix.standard-action-policies
  "Standard action policies with skill test integration.

  Provides policy functions for Shoot/Block, Pass/Steal, and Screen/Check
  standard actions, integrating with the skill test and ZoC systems.

  Each action has:
  - Precondition checking
  - Advantage source collection
  - Skill test setup with proper difficulty and advantage

  Setup functions that compute difficulty require context with `:state` and
  `:registry` to support event-based modifier injection."
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.polix.operators :as ops]
   [bashketball-game.polix.skill-tests :as skill]
   [bashketball-game.polix.stats :as stats]
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

  Difficulty = 8 - effective shooting stat.
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state registry] :as ctx} shooter-id]
  {:pre [(some? state) (some? registry)]}
  (let [{:keys [value]} (stats/get-effective-stat ctx shooter-id :shooting)]
    (skill/compute-difficulty value)))

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

  Difficulty = 8 - effective passing stat.
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state registry] :as ctx} passer-id]
  {:pre [(some? state) (some? registry)]}
  (let [{:keys [value]} (stats/get-effective-stat ctx passer-id :passing)]
    (skill/compute-difficulty value)))

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
                      (= (:advantage s) :advantage/DISADVANTAGE))
               (assoc s :advantage :advantage/NORMAL)
               s))
           base-sources)
      base-sources)))

(defn steal-difficulty
  "Returns the difficulty for a Steal skill test.

  Difficulty = 8 - (effective defense stat - 2)
  Note: Steal has a -2 penalty (Defend-2).
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state registry] :as ctx} stealer-id]
  {:pre [(some? state) (some? registry)]}
  (let [{:keys [value]} (stats/get-effective-stat ctx stealer-id :defense)]
    (skill/compute-difficulty (- value 2))))

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
  Note: Screen has a -1 penalty (Defend-1).
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state registry] :as ctx} screener-id]
  {:pre [(some? state) (some? registry)]}
  (let [{:keys [value]} (stats/get-effective-stat ctx screener-id :defense)]
    (skill/compute-difficulty (- value 1))))

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
  Note: Check has a -1 penalty (Defend-1).
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state registry] :as ctx} checker-id]
  {:pre [(some? state) (some? registry)]}
  (let [{:keys [value]} (stats/get-effective-stat ctx checker-id :defense)]
    (skill/compute-difficulty (- value 1))))

;; =============================================================================
;; Skill Test Setup Helpers
;; =============================================================================

(defn setup-shoot-test
  "Creates a skill test configuration for a Shoot action.

  Returns a map with :difficulty, :advantage-sources, and :advantage.
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state] :as ctx} shooter-id]
  {:pre [(some? state) (some? (:registry ctx))]}
  (let [sources  (shoot-advantage-sources state shooter-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (shoot-difficulty ctx shooter-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))

(defn setup-pass-test
  "Creates a skill test configuration for a Pass action.

  Returns a map with :difficulty, :advantage-sources, and :advantage.
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state] :as ctx} passer-id target-id]
  {:pre [(some? state) (some? (:registry ctx))]}
  (let [sources  (pass-advantage-sources state passer-id target-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (pass-difficulty ctx passer-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))

(defn setup-steal-test
  "Creates a skill test configuration for a Steal action.

  Returns a map with :difficulty, :advantage-sources, and :advantage.
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state] :as ctx} stealer-id target-id]
  {:pre [(some? state) (some? (:registry ctx))]}
  (let [sources  (steal-advantage-sources state stealer-id target-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (steal-difficulty ctx stealer-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))

(defn setup-screen-test
  "Creates a skill test configuration for a Screen action.

  Returns a map with :difficulty, :advantage-sources, and :advantage.
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state] :as ctx} screener-id target-id]
  {:pre [(some? state) (some? (:registry ctx))]}
  (let [sources  (screen-advantage-sources state screener-id target-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (screen-difficulty ctx screener-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))

(defn setup-check-test
  "Creates a skill test configuration for a Check action.

  Returns a map with :difficulty, :advantage-sources, and :advantage.
  Requires context with `:state` and `:registry` for event-based modifiers."
  [{:keys [state] :as ctx} checker-id target-id]
  {:pre [(some? state) (some? (:registry ctx))]}
  (let [sources  (check-advantage-sources state checker-id target-id)
        combined (skill/combine-advantage-sources sources)]
    {:difficulty (check-difficulty ctx checker-id)
     :advantage-sources (:sources combined)
     :advantage (:net-level combined)}))
