(ns bashketball-game.polix.zoc
  "Zone of Control (ZoC) policies for Bashketball.

  Implements ZoC rules from the game:
  - Unexhausted players exert ZoC in a 1-hex radius
  - Movement through opponent ZoC costs extra movement based on size
  - Shooting in ZoC applies disadvantage based on defender size
  - Passing through ZoC removes baseline advantage

  ZoC affects three aspects of play:
  1. **Movement costs** - Moving through an opponent's ZoC costs extra
  2. **Shot contests** - Shooting while in ZoC applies disadvantage
  3. **Pass interception** - Passing through ZoC can be intercepted"
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.state :as state]))

(def size-order
  "Ordering of sizes for comparison."
  {:size/SM 0 :size/MD 1 :size/LG 2})

(defn player-size
  "Returns the size of a player."
  [game-state player-id]
  (get-in (state/get-basketball-player game-state player-id) [:stats :size]))

(defn in-zoc?
  "Returns true if position is within defender's Zone of Control.

  A defender exerts ZoC if they are unexhausted and within 1 hex."
  [game-state position defender-id]
  (let [defender (state/get-basketball-player game-state defender-id)
        def-pos  (:position defender)]
    (and def-pos
         (not (:exhausted defender))
         (<= (board/hex-distance position def-pos) 1))))

(defn zoc-defenders
  "Returns set of defender IDs exerting ZoC on the given position."
  [game-state position defending-team]
  (let [defenders (state/get-on-court-players game-state defending-team)]
    (->> defenders
         (filter #(in-zoc? game-state position %))
         set)))

(defn pass-path-zoc
  "Returns set of defenders whose ZoC intersects the pass path."
  [game-state from-pos to-pos defending-team]
  (let [path-hexes (board/hex-line from-pos to-pos)
        defenders  (state/get-on-court-players game-state defending-team)]
    (->> defenders
         (filter (fn [player-id]
                   (let [player  (state/get-basketball-player game-state player-id)
                         def-pos (:position player)]
                     (and def-pos
                          (not (:exhausted player))
                          (some #(<= (board/hex-distance % def-pos) 1) path-hexes)))))
         set)))

(defn exerts-zoc?
  "Returns true if a player exerts Zone of Control.

  A player exerts ZoC when:
  - They are on the court (have a position)
  - They are not exhausted"
  [game-state player-id]
  (let [player (state/get-basketball-player game-state player-id)]
    (and (:position player)
         (not (:exhausted player)))))

(defn zoc-movement-cost
  "Returns the extra movement cost to pass through a defender's ZoC.

  Based on size comparison:
  - Larger defender: +2 movement
  - Same size defender: +1 movement
  - Smaller defender: +0 movement

  Returns 0 if the defender doesn't exert ZoC."
  [game-state mover-id defender-id]
  (if-not (exerts-zoc? game-state defender-id)
    0
    (let [mover-size    (player-size game-state mover-id)
          defender-size (player-size game-state defender-id)
          mover-ord     (get size-order mover-size 1)
          defender-ord  (get size-order defender-size 1)]
      (cond
        (> defender-ord mover-ord) 2  ; defender larger
        (= defender-ord mover-ord) 1  ; same size
        :else 0))))                   ; defender smaller

(defn shooting-zoc-disadvantage
  "Returns the disadvantage level for shooting while in a defender's ZoC.

  Based on size comparison:
  - Smaller defender: :advantage/NORMAL (no disadvantage from ZoC)
  - Same size defender: :advantage/DISADVANTAGE
  - Larger defender: :advantage/DOUBLE_DISADVANTAGE

  Returns nil if the defender doesn't exert ZoC on the shooter's position."
  [game-state shooter-id defender-id]
  (let [shooter     (state/get-basketball-player game-state shooter-id)
        shooter-pos (:position shooter)]
    (when (and shooter-pos (in-zoc? game-state shooter-pos defender-id))
      (let [shooter-size  (player-size game-state shooter-id)
            defender-size (player-size game-state defender-id)
            shooter-ord   (get size-order shooter-size 1)
            defender-ord  (get size-order defender-size 1)]
        (cond
          (< defender-ord shooter-ord) :advantage/NORMAL              ; smaller defender
          (= defender-ord shooter-ord) :advantage/DISADVANTAGE        ; same size
          :else :advantage/DOUBLE_DISADVANTAGE)))))                ; larger defender

(defn passing-zoc-disadvantage
  "Returns the disadvantage level for passing through a defender's ZoC.

  For passes, smaller defenders are actually better at interception:
  - Larger defender in path: :advantage/NORMAL
  - Same size defender in path: :advantage/DISADVANTAGE
  - Smaller defender in path: :advantage/DOUBLE_DISADVANTAGE

  Returns nil if the defender's ZoC doesn't intersect the pass path."
  [game-state passer-id target-pos defender-id]
  (let [passer       (state/get-basketball-player game-state passer-id)
        passer-pos   (:position passer)
        defender     (state/get-basketball-player game-state defender-id)
        defender-pos (:position defender)]
    (when (and passer-pos defender-pos (exerts-zoc? game-state defender-id))
      (let [path-hexes (board/hex-line passer-pos target-pos)
            in-path?   (some #(<= (board/hex-distance % defender-pos) 1) path-hexes)]
        (when in-path?
          (let [passer-size   (player-size game-state passer-id)
                defender-size (player-size game-state defender-id)
                passer-ord    (get size-order passer-size 1)
                defender-ord  (get size-order defender-size 1)]
            (cond
              (> defender-ord passer-ord) :advantage/NORMAL              ; larger defender
              (= defender-ord passer-ord) :advantage/DISADVANTAGE        ; same size
              :else :advantage/DOUBLE_DISADVANTAGE)))))))             ; smaller defender

(defn collect-shooting-zoc-sources
  "Collects all ZoC-based disadvantage sources for a shot.

  Returns a vector of maps with :source, :defender-id, and :disadvantage."
  [game-state shooter-id defending-team]
  (let [defenders (state/get-on-court-players game-state defending-team)]
    (->> defenders
         (keep (fn [defender-id]
                 (when-let [disadv (shooting-zoc-disadvantage game-state shooter-id defender-id)]
                   {:source :zoc
                    :defender-id defender-id
                    :disadvantage disadv})))
         vec)))

(defn collect-passing-zoc-sources
  "Collects all ZoC-based disadvantage sources for a pass.

  Returns a vector of maps with :source, :defender-id, and :disadvantage."
  [game-state passer-id target-pos defending-team]
  (let [defenders (state/get-on-court-players game-state defending-team)]
    (->> defenders
         (keep (fn [defender-id]
                 (when-let [disadv (passing-zoc-disadvantage game-state passer-id target-pos defender-id)]
                   {:source :zoc
                    :defender-id defender-id
                    :disadvantage disadv})))
         vec)))

(defn worst-disadvantage
  "Returns the worst disadvantage from a collection of sources.

  Ordering: :advantage/DOUBLE_DISADVANTAGE > :advantage/DISADVANTAGE > :advantage/NORMAL > nil"
  [sources]
  (let [disadvantages (keep :disadvantage sources)
        priority      {:advantage/DOUBLE_DISADVANTAGE 2
                       :advantage/DISADVANTAGE 1
                       :advantage/NORMAL 0}]
    (when (seq disadvantages)
      (first (sort-by #(- (get priority % -1)) disadvantages)))))

(defn shooting-in-zoc?
  "Returns true if the shooter is in any defender's ZoC."
  [game-state shooter-id defending-team]
  (let [shooter     (state/get-basketball-player game-state shooter-id)
        shooter-pos (:position shooter)]
    (when shooter-pos
      (let [defenders (zoc-defenders game-state shooter-pos defending-team)]
        (seq defenders)))))

(defn uncontested-shot?
  "Returns true if no defenders exert ZoC on the shooter."
  [game-state shooter-id defending-team]
  (not (shooting-in-zoc? game-state shooter-id defending-team)))

(defn pass-path-contested?
  "Returns true if any defender's ZoC intersects the pass path."
  [game-state passer-id target-pos defending-team]
  (let [defenders (pass-path-zoc game-state
                                 (:position (state/get-basketball-player game-state passer-id))
                                 target-pos
                                 defending-team)]
    (seq defenders)))
