(ns bashketball-game.polix.scoring
  "Scoring zone and distance modifier policies.

  Implements the Bashketball scoring system:
  - Two-point zone: columns 0-3 (left) or 10-13 (right)
  - Three-point zone: columns 4-9 (middle)

  Distance modifiers affect advantage:
  - Close (1-2 hexes): Advantage
  - Medium (3-4 hexes): Normal
  - Long (5+ hexes): Disadvantage"
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.state :as state]))

(def left-basket
  "Position of the left basket (AWAY team's target)."
  [2 0])

(def right-basket
  "Position of the right basket (HOME team's target)."
  [2 13])

(defn target-basket-for-team
  "Returns the basket position that a team shoots at.

  HOME team shoots at the right basket [2 13].
  AWAY team shoots at the left basket [2 0]."
  [team]
  (if (= team :team/HOME)
    right-basket
    left-basket))

(defn scoring-zone
  "Returns the scoring zone for a position.

  Returns:
  - :two-point for columns 0-3 (left paint) or 10-13 (right paint)
  - :three-point for columns 4-9 (middle of court)"
  [position]
  (let [[_q r] position]
    (if (or (<= r 3) (>= r 10))
      :two-point
      :three-point)))

(defn point-value
  "Returns the point value for a shot from the given position.

  Returns 2 for two-point zone, 3 for three-point zone."
  [position]
  (case (scoring-zone position)
    :two-point 2
    :three-point 3))

(defn distance-to-basket
  "Returns the hex distance from a position to the target basket."
  [position basket]
  (board/hex-distance position basket))

(defn shooting-distance
  "Returns the shooting distance for a player.

  Takes the game state and player ID, returns distance to their target basket."
  [game-state player-id]
  (let [player   (state/get-basketball-player game-state player-id)
        team     (state/get-basketball-player-team game-state player-id)
        basket   (target-basket-for-team team)
        position (:position player)]
    (when position
      (distance-to-basket position basket))))

(defn passing-distance
  "Returns the distance between two players for passing.

  Takes the game state and two player IDs."
  [game-state passer-id target-id]
  (let [passer     (state/get-basketball-player game-state passer-id)
        target     (state/get-basketball-player game-state target-id)
        passer-pos (:position passer)
        target-pos (:position target)]
    (when (and passer-pos target-pos)
      (board/hex-distance passer-pos target-pos))))

(defn distance-category
  "Returns the distance category for advantage calculation.

  - :close (1-2 hexes) - Advantage
  - :medium (3-4 hexes) - Normal
  - :long (5+ hexes) - Disadvantage"
  [distance]
  (cond
    (<= distance 2) :close
    (<= distance 4) :medium
    :else :long))

(defn distance-advantage
  "Returns the advantage level for a given distance.

  - Close (1-2): :advantage/ADVANTAGE
  - Medium (3-4): :advantage/NORMAL
  - Long (5+): :advantage/DISADVANTAGE"
  [distance]
  (case (distance-category distance)
    :close :advantage/ADVANTAGE
    :medium :advantage/NORMAL
    :long :advantage/DISADVANTAGE))

(defn in-paint?
  "Returns true if the position is in the paint (close to basket).

  Paint is defined as columns 0-2 (left) or 11-13 (right)."
  [position]
  (let [[_q r] position]
    (or (<= r 2) (>= r 11))))

(defn on-three-point-line?
  "Returns true if the position is on the three-point line.

  Three-point line is at columns 3 and 10."
  [position]
  (let [[_q r] position]
    (or (= r 3) (= r 10))))

(defn in-mid-range?
  "Returns true if the position is in mid-range (between paint and 3pt line).

  Mid-range is columns 4-9."
  [position]
  (let [[_q r] position]
    (and (>= r 4) (<= r 9))))

(defn court-side
  "Returns which side of the court a position is on.

  - :left for columns 0-6
  - :right for columns 7-13"
  [position]
  (let [[_q r] position]
    (if (<= r 6) :left :right)))

(defn in-own-half?
  "Returns true if a team's player is in their own defensive half.

  HOME team's half is columns 0-6 (left side).
  AWAY team's half is columns 7-13 (right side)."
  [position team]
  (let [side (court-side position)]
    (if (= team :team/HOME)
      (= side :left)
      (= side :right))))

(defn in-opponent-half?
  "Returns true if a team's player is in the opponent's half (offensive half)."
  [position team]
  (not (in-own-half? position team)))

(defn shooting-position-summary
  "Returns a summary of a shooting position for display.

  Includes zone, point value, distance, and court area."
  [game-state player-id]
  (let [player   (state/get-basketball-player game-state player-id)
        position (:position player)
        team     (state/get-basketball-player-team game-state player-id)
        basket   (target-basket-for-team team)
        distance (when position (distance-to-basket position basket))]
    (when position
      {:position position
       :zone (scoring-zone position)
       :point-value (point-value position)
       :distance distance
       :distance-category (when distance (distance-category distance))
       :distance-advantage (when distance (distance-advantage distance))
       :in-paint (in-paint? position)
       :on-three-point-line (on-three-point-line? position)})))
