(ns bashketball-game-ui.game.actions
  "Game action construction and validation helpers.

  Provides functions for constructing action maps and validating
  whether actions are available given the current game state."
  (:require
   [bashketball-game.board :as board]))

(defn get-ball-holder-position
  "Returns the position of the ball holder, or nil if ball is not held."
  [game-state]
  (when-let [holder-id (get-in game-state [:ball :holder-id])]
    (board/find-occupant (:board game-state) holder-id)))

(defn ball-held-by-team?
  "Returns true if the given team currently holds the ball."
  [game-state team]
  (when-let [holder-id (get-in game-state [:ball :holder-id])]
    (let [team-players (get-in game-state [:players team :team :players])]
      (some #(= (:id %) holder-id) (vals team-players)))))

(defn player-has-ball?
  "Returns true if the player with given ID holds the ball."
  [game-state player-id]
  (= player-id (get-in game-state [:ball :holder-id])))

(defn player-exhausted?
  "Returns true if the player has used their action this turn."
  [game-state player-id]
  (contains? (get game-state :exhausted-players #{}) player-id))

(defn can-move-player?
  "Returns true if player can move to the given position."
  [game-state player-id position]
  (and (board/valid-position? position)
       (not (player-exhausted? game-state player-id))
       (nil? (board/occupant-at (:board game-state) position))
       (let [current-pos (board/find-occupant (:board game-state) player-id)]
         (and current-pos
              (<= (board/hex-distance current-pos position) 2)))))

(defn can-shoot?
  "Returns true if the current team can attempt a shot."
  [game-state my-team]
  (and (ball-held-by-team? game-state my-team)
       (let [holder-pos  (get-ball-holder-position game-state)
             target-hoop (if (= my-team :home) [2 13] [2 0])]
         (and holder-pos
              (<= (board/hex-distance holder-pos target-hoop) 4)))))

(defn can-pass?
  "Returns true if the ball holder can pass."
  [game-state my-team]
  (ball-held-by-team? game-state my-team))

(defn valid-move-positions
  "Returns set of valid positions the player can move to."
  [game-state player-id]
  (when-let [current-pos (board/find-occupant (:board game-state) player-id)]
    (when-not (player-exhausted? game-state player-id)
      (->> (board/hex-range current-pos 2)
           (remove #(board/occupant-at (:board game-state) %))
           (remove #(= % current-pos))
           set))))

(defn make-move-action
  "Constructs a move-player action map."
  [player-id [q r]]
  {:type "bashketball/move-player"
   :player-id player-id
   :position [q r]})

(defn make-shoot-action
  "Constructs a shoot action map."
  [[origin-q origin-r] [target-q target-r]]
  {:type "bashketball/set-ball-in-air"
   :origin [origin-q origin-r]
   :target [target-q target-r]
   :action-type "shot"})

(defn make-pass-action
  "Constructs a pass action map.

  Target can be a position [q r] or a player ID string."
  [[origin-q origin-r] target]
  {:type "bashketball/set-ball-in-air"
   :origin [origin-q origin-r]
   :target target
   :action-type "pass"})

(defn make-end-turn-action
  "Constructs an end-turn action map."
  []
  {:type "bashketball/advance-turn"})

(defn make-set-phase-action
  "Constructs a set-phase action map."
  [phase]
  {:type "bashketball/set-phase"
   :phase (name phase)})

(defn make-draw-cards-action
  "Constructs a draw-cards action map."
  [team count]
  {:type "bashketball/draw-cards"
   :player (name team)
   :count count})

(defn make-discard-cards-action
  "Constructs a discard-cards action map."
  [team card-slugs]
  {:type "bashketball/discard-cards"
   :player (name team)
   :card-slugs card-slugs})
