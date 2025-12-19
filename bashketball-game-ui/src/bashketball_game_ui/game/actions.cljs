(ns bashketball-game-ui.game.actions
  "Game action construction and validation helpers.

  Provides functions for constructing action maps and validating
  whether actions are available given the current game state."
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.movement :as movement]))

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
      (contains? team-players holder-id))))

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
  (and (not (player-exhausted? game-state player-id))
       (movement/can-move-to? game-state player-id position)))

(defn can-shoot?
  "Returns true if the current team can attempt a shot."
  [game-state my-team]
  (and (ball-held-by-team? game-state my-team)
       (let [holder-pos  (get-ball-holder-position game-state)
             target-hoop (if (= my-team :team/HOME) [2 13] [2 0])]
         (and holder-pos
              (<= (board/hex-distance holder-pos target-hoop) 12)))))

(defn can-pass?
  "Returns true if the ball holder can pass."
  [game-state my-team]
  (ball-held-by-team? game-state my-team))

(defn valid-move-positions
  "Returns set of valid positions the player can move to."
  [game-state player-id]
  (when-not (player-exhausted? game-state player-id)
    (movement/valid-move-positions game-state player-id)))

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
   :target {:target-type "position" :position [target-q target-r]}
   :action-type "shot"})

(defn make-pass-action
  "Constructs a pass action map.

  Target can be a position [q r] or a player ID string."
  [[origin-q origin-r] target]
  {:type "bashketball/set-ball-in-air"
   :origin [origin-q origin-r]
   :target (if (vector? target)
             {:target-type "position" :position target}
             {:target-type "player" :player-id target})
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
  [team instance-ids]
  {:type "bashketball/discard-cards"
   :player (name team)
   :instance-ids instance-ids})

(defn valid-setup-positions
  "Returns set of valid positions for placing a player during setup.

  Home team places on rows 0-6 (their half), away on rows 7-13.
  Cannot place on occupied hexes or hoop hexes [2,0] and [2,13]."
  [game-state team]
  (let [board     (:board game-state)
        row-range (if (= team :team/HOME) (range 0 7) (range 7 14))
        hoops     #{[2 0] [2 13]}]
    (->> (for [q (range 5) r row-range] [q r])
         (filter board/valid-position?)
         (remove hoops)
         (remove #(board/occupant-at board %))
         set)))

(defn unplaced-players
  "Returns map of player-id -> player for all players without positions."
  [game-state team]
  (let [player-map (get-in game-state [:players team :team :players])]
    (->> (vals player-map)
         (remove :position)
         (map (juxt :id identity))
         (into {}))))

(defn setup-complete?
  "Returns true if exactly 3 players have been placed on the court."
  [game-state team]
  (let [player-map (get-in game-state [:players team :team :players])]
    (= 3 (count (filter :position (vals player-map))))))

(defn can-reveal-fate?
  "Returns true if the team has cards in their draw pile."
  [game-state team]
  (let [draw-pile (get-in game-state [:players team :deck :draw-pile])]
    (and (sequential? draw-pile) (pos? (count draw-pile)))))

(defn can-substitute?
  "Returns true if the team has off-court players available for substitution."
  [game-state team]
  (let [player-map (get-in game-state [:players team :team :players])
        off-court  (filter (complement :position) (vals player-map))]
    (seq off-court)))

(defn make-start-from-tipoff-action
  "Constructs a start-from-tipoff action map."
  [team]
  {:type "bashketball/start-from-tipoff"
   :player (name team)})

(defn make-examine-cards-action
  "Constructs an examine-cards action map to peek at top N cards."
  [team count]
  {:type   "bashketball/examine-cards"
   :player (name team)
   :count  count})

(defn make-resolve-examined-cards-action
  "Constructs a resolve-examined-cards action map.

  Placements is a vector of maps with :instance-id and :destination keys.
  Destination should be \"TOP\", \"BOTTOM\", or \"DISCARD\"."
  [team placements]
  {:type       "bashketball/resolve-examined-cards"
   :player     (name team)
   :placements (mapv (fn [{:keys [instance-id destination]}]
                       {:instanceId  instance-id
                        :destination destination})
                     placements)})
