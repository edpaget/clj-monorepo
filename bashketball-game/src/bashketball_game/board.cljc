(ns bashketball-game.board
  "Hex grid utilities for the Bashketball court.

  Uses axial coordinates (q, r) for the 5x14 hex grid. The court is oriented
  with hoops at r=0 (home) and r=13 (away).")

(def width 5)
(def height 14)

(defn valid-position?
  "Returns true if the position is within the board bounds."
  [[q r]]
  (and (>= q 0) (< q width)
       (>= r 0) (< r height)))

(def ^:private axial-directions
  "The six directions in axial coordinates."
  [[1 0] [1 -1] [0 -1] [-1 0] [-1 1] [0 1]])

(defn hex-neighbors
  "Returns all valid neighboring hex positions."
  [[q r]]
  (->> axial-directions
       (map (fn [[dq dr]] [(+ q dq) (+ r dr)]))
       (filter valid-position?)))

(defn hex-distance
  "Calculates the distance between two hex positions using axial coordinates."
  [[q1 r1] [q2 r2]]
  (let [dq (- q1 q2)
        dr (- r1 r2)]
    (/ (+ (abs dq) (abs (+ dq dr)) (abs dr)) 2)))

(defn hex-range
  "Returns all hex positions within distance n of the center position."
  [[q r] n]
  (for [dq    (range (- n) (inc n))
        dr    (range (max (- n) (- (- n) dq)) (inc (min n (- n dq))))
        :let  [pos [(+ q dq) (+ r dr)]]
        :when (valid-position? pos)]
    pos))

(defn- lerp
  "Linear interpolation between a and b."
  [a b t]
  (+ a (* t (- b a))))

(defn- cube-round
  "Rounds fractional cube coordinates to nearest integer cube coordinates."
  [x y z]
  (let [rx     (Math/round (double x))
        ry     (Math/round (double y))
        rz     (Math/round (double z))
        x-diff (abs (- rx x))
        y-diff (abs (- ry y))
        z-diff (abs (- rz z))]
    (cond
      (and (> x-diff y-diff) (> x-diff z-diff))
      [(- (- ry) rz) ry rz]

      (> y-diff z-diff)
      [rx (- (- rx) rz) rz]

      :else
      [rx ry (- (- rx) ry)])))

(defn- axial->cube
  "Converts axial coordinates to cube coordinates."
  [[q r]]
  [q r (- (- q) r)])

(defn- cube->axial
  "Converts cube coordinates to axial coordinates."
  [[x y _z]]
  [x y])

(defn hex-line
  "Returns all hex positions along a line from start to end (inclusive)."
  [start end]
  (let [n          (hex-distance start end)
        [x1 y1 z1] (axial->cube start)
        [x2 y2 z2] (axial->cube end)]
    (if (zero? n)
      [start]
      (for [i    (range (inc n))
            :let [t (/ i n)
                  x (lerp x1 x2 t)
                  y (lerp y1 y2 t)
                  z (lerp z1 z2 t)
                  [rx ry rz] (cube-round x y z)]]
        (cube->axial [rx ry rz])))))

(defn- terrain-at
  "Determines the terrain type for a given position."
  [[q r]]
  (cond
    (and (= q 2) (= r 0)) {:terrain :HOOP :side :HOME}
    (and (= q 2) (= r 13)) {:terrain :HOOP :side :AWAY}
    (or (<= r 2) (>= r 11)) {:terrain :PAINT}
    (or (= r 3) (= r 10)) {:terrain :THREE_POINT_LINE}
    :else {:terrain :COURT}))

(defn create-board
  "Creates a new 5x14 hex board with terrain."
  []
  {:width width
   :height height
   :tiles (into {}
                (for [q    (range width)
                      r    (range height)
                      :let [pos [q r]]]
                  [pos (terrain-at pos)]))
   :occupants {}})

(defn occupant-at
  "Returns the occupant at the given position, or nil if empty."
  [board position]
  (get-in board [:occupants position]))

(defn set-occupant
  "Sets an occupant at the given position."
  [board position occupant]
  (assoc-in board [:occupants position] occupant))

(defn remove-occupant
  "Removes the occupant at the given position."
  [board position]
  (update board :occupants dissoc position))

(defn move-occupant
  "Moves an occupant from one position to another."
  [board from to]
  (if-let [occupant (occupant-at board from)]
    (-> board
        (remove-occupant from)
        (set-occupant to occupant))
    board))

(defn find-occupant
  "Finds the position of an occupant by its id. Returns nil if not found."
  [board id]
  (->> (:occupants board)
       (some (fn [[pos occ]]
               (when (= (:id occ) id)
                 pos)))))

(defn positions-in-range
  "Returns all valid positions within range of the given position."
  [position range]
  (hex-range position range))

(defn path-clear?
  "Returns true if the path between two positions has no occupants (excluding endpoints)."
  [board start end]
  (let [path   (hex-line start end)
        middle (butlast (rest path))]
    (every? #(nil? (occupant-at board %)) middle)))
