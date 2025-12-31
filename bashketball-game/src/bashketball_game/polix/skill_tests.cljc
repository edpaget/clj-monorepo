(ns bashketball-game.polix.skill-tests
  "Skill test resolution with source-tracked advantage/disadvantage.

  Implements the Bashketball skill test system:
  - Difficulty = 8 - skill value
  - Fate reveal with advantage/disadvantage affects dice count
  - Success by 2+ draws a card
  - Multiple advantage sources are tracked and can cancel each other

  Advantage sources:
  - `:distance` - Close/medium/long range
  - `:zoc` - Zone of Control from defenders
  - `:size` - Size comparison between actors
  - `:cards` - Card effects providing bonuses
  - `:uncontested` - No defenders contesting"
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.polix.zoc :as zoc]
   [bashketball-game.state :as state]
   [clojure.string :as str]))

(def advantage-order
  "Ordering of advantage levels from best to worst."
  {:double-advantage 2
   :advantage 1
   :normal 0
   :disadvantage -1
   :double-disadvantage -2})

(defn compute-difficulty
  "Computes skill test difficulty from effective stat value.

  Difficulty = 8 - skill value
  The fate roll must meet or exceed this to succeed."
  [skill-value]
  (- 8 skill-value))

(defn advantage-value
  "Converts an advantage keyword to its numeric value for computation."
  [adv]
  (get advantage-order adv 0))

(defn value->advantage
  "Converts a net advantage value back to a keyword.

  Clamps to the valid range [-2, 2]."
  [value]
  (let [clamped (max -2 (min 2 value))]
    (case clamped
      2 :double-advantage
      1 :advantage
      0 :normal
      -1 :disadvantage
      -2 :double-disadvantage)))

(defn combine-advantage-sources
  "Combines multiple advantage sources into a net advantage level.

  Takes a sequence of sources with :advantage key and returns
  {:sources sources :net-level keyword}."
  [sources]
  (let [total     (reduce + 0 (map #(advantage-value (:advantage %)) sources))
        net-level (value->advantage total)]
    {:sources sources
     :net-level net-level}))

(defn distance->advantage
  "Returns advantage level based on distance.

  Close (1-2 hexes): :advantage
  Medium (3-4 hexes): :normal
  Long (5+ hexes): :disadvantage"
  [distance]
  (cond
    (<= distance 2) :advantage
    (<= distance 4) :normal
    :else :disadvantage))

(defn distance-advantage-source
  "Creates a distance-based advantage source.

  Close (1-2 hexes): advantage
  Medium (3-4 hexes): normal
  Long (5+ hexes): disadvantage"
  [distance]
  (let [adv (distance->advantage distance)]
    {:source :distance
     :distance distance
     :advantage adv}))

(def size-order
  "Ordering of player sizes."
  {:size/SM 0 :size/MD 1 :size/LG 2})

(defn size->advantage
  "Returns advantage level from size comparison.

  Larger actor: :advantage
  Same size: :normal
  Smaller actor: :disadvantage"
  [game-state actor-id target-id]
  (let [actor-size  (get-in (state/get-basketball-player game-state actor-id) [:stats :size])
        target-size (get-in (state/get-basketball-player game-state target-id) [:stats :size])
        ord-actor   (get size-order actor-size 1)
        ord-target  (get size-order target-size 1)]
    (cond
      (> ord-actor ord-target) :advantage
      (< ord-actor ord-target) :disadvantage
      :else :normal)))

(defn size-advantage-source
  "Creates a size-based advantage source.

  Larger actor: advantage
  Same size: normal
  Smaller actor: disadvantage"
  [game-state actor-id target-id]
  (let [adv (size->advantage game-state actor-id target-id)]
    {:source :size
     :actor-id actor-id
     :target-id target-id
     :advantage adv}))

(defn uncontested-advantage-source
  "Creates an uncontested shot/pass advantage source.

  Uncontested actions get advantage."
  []
  {:source :uncontested
   :advantage :advantage})

(defn shooting-advantage-sources
  "Collects all advantage sources for a shot attempt.

  Sources:
  - Distance to basket
  - ZoC from defenders (or uncontested bonus)
  - Card effects (from pending-skill-test modifiers)"
  [game-state shooter-id target-basket defending-team]
  (let [shooter         (state/get-basketball-player game-state shooter-id)
        shooter-pos     (:position shooter)
        distance        (when shooter-pos
                          (board/hex-distance shooter-pos target-basket))
        zoc-sources     (zoc/collect-shooting-zoc-sources game-state shooter-id defending-team)
        distance-src    (when distance
                          (distance-advantage-source distance))
        uncontested-src (when (empty? zoc-sources)
                          (uncontested-advantage-source))
        card-sources    (when-let [test (:pending-skill-test game-state)]
                          (->> (:modifiers test)
                               (filter :advantage)
                               (map (fn [m]
                                      {:source :card
                                       :card-id (:source m)
                                       :advantage (:advantage m)}))))]
    (cond-> []
      distance-src (conj distance-src)
      uncontested-src (conj uncontested-src)
      (seq zoc-sources) (into zoc-sources)
      (seq card-sources) (into card-sources))))

(defn passing-advantage-sources
  "Collects all advantage sources for a pass attempt.

  Sources:
  - Distance of pass
  - ZoC intersecting pass path (or uncontested bonus)
  - Card effects"
  [game-state passer-id target-pos defending-team]
  (let [passer          (state/get-basketball-player game-state passer-id)
        passer-pos      (:position passer)
        distance        (when (and passer-pos target-pos)
                          (board/hex-distance passer-pos target-pos))
        zoc-sources     (zoc/collect-passing-zoc-sources game-state passer-id target-pos defending-team)
        distance-src    (when distance
                          (distance-advantage-source distance))
        uncontested-src (when (empty? zoc-sources)
                          (uncontested-advantage-source))
        card-sources    (when-let [test (:pending-skill-test game-state)]
                          (->> (:modifiers test)
                               (filter :advantage)
                               (map (fn [m]
                                      {:source :card
                                       :card-id (:source m)
                                       :advantage (:advantage m)}))))]
    (cond-> []
      distance-src (conj distance-src)
      uncontested-src (conj uncontested-src)
      (seq zoc-sources) (into zoc-sources)
      (seq card-sources) (into card-sources))))

(defn defense-advantage-sources
  "Collects all advantage sources for a defensive action.

  Sources:
  - Size comparison (defender vs ball carrier)
  - Card effects"
  [game-state defender-id target-id]
  (let [size-src     (size-advantage-source game-state defender-id target-id)
        card-sources (when-let [test (:pending-skill-test game-state)]
                       (->> (:modifiers test)
                            (filter :advantage)
                            (map (fn [m]
                                   {:source :card
                                    :card-id (:source m)
                                    :advantage (:advantage m)}))))]
    (cond-> [size-src]
      (seq card-sources) (into card-sources))))

(defn tipoff-advantage-sources
  "Collects advantage sources for tip-off.

  Only size comparison matters."
  [game-state player-a-id player-b-id]
  [(size-advantage-source game-state player-a-id player-b-id)])

(defn fate-reveal-count
  "Returns the number of fate cards to reveal based on advantage level.

  Double advantage: 3 (pick best)
  Advantage: 2 (pick best)
  Normal: 1
  Disadvantage: 2 (pick worst)
  Double disadvantage: 3 (pick worst)"
  [advantage-level]
  (case advantage-level
    :double-advantage 3
    :advantage 2
    :normal 1
    :disadvantage 2
    :double-disadvantage 3))

(defn fate-selection-mode
  "Returns how to select from revealed fate cards.

  :best - pick the highest value
  :worst - pick the lowest value
  :single - only one card, use it"
  [advantage-level]
  (case advantage-level
    (:double-advantage :advantage) :best
    :normal :single
    (:disadvantage :double-disadvantage) :worst))

(defn select-fate
  "Selects the fate value from revealed cards based on advantage.

  Takes a sequence of fate values and the selection mode."
  [fate-values mode]
  (case mode
    :best (apply max fate-values)
    :worst (apply min fate-values)
    :single (first fate-values)))

(defn success?
  "Returns true if fate meets or exceeds difficulty."
  [fate difficulty]
  (>= fate difficulty))

(defn success-margin
  "Returns the margin of success (positive) or failure (negative)."
  [fate difficulty]
  (- fate difficulty))

(defn success-by-two-plus?
  "Returns true if the skill test succeeded by 2 or more.

  This triggers a bonus card draw."
  [fate difficulty]
  (>= (success-margin fate difficulty) 2))

(defn format-advantage-summary
  "Formats advantage sources for display.

  Returns a map with :sources (the raw data) and :summary (human readable)."
  [{:keys [sources net-level]}]
  {:sources sources
   :net-level net-level
   :summary (str (name net-level)
                 " ("
                 (->> sources
                      (map (fn [s]
                             (str (name (:source s)) ": " (name (:advantage s)))))
                      (str/join ", "))
                 ")")})
