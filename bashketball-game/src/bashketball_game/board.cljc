(ns bashketball-game.board
  "Hex grid utilities for the Bashketball court.

  Uses odd-column offset coordinates [q, r] where q is row (0-4) and r is
  column (0-13). Odd columns are visually offset down by half a hex height.
  The court is oriented with hoops at r=0 (home) and r=13 (away).")

(def width 5)
(def height 14)

(defn valid-position?
  "Returns true if the position is within the board bounds."
  [[q r]]
  (and (>= q 0) (< q width)
       (>= r 0) (< r height)))

(defn- offset->cube
  "Converts odd-column offset coordinates [q r] to cube coordinates [x y z]."
  [[q r]]
  (let [x r
        z (- q (quot (- r (bit-and r 1)) 2))
        y (- (- x) z)]
    [x y z]))

(defn- cube->offset
  "Converts cube coordinates [x y z] to odd-column offset coordinates [q r]."
  [[x _y z]]
  (let [r x
        q (+ z (quot (- x (bit-and x 1)) 2))]
    [q r]))

(def ^:private offset-directions-even
  "Neighbor offsets for even columns (r % 2 == 0)."
  [[-1 1] [0 1] [1 0] [0 -1] [-1 -1] [-1 0]])

(def ^:private offset-directions-odd
  "Neighbor offsets for odd columns (r % 2 == 1)."
  [[0 1] [1 1] [1 0] [1 -1] [0 -1] [-1 0]])

(defn hex-neighbors
  "Returns all valid neighboring hex positions using offset coordinates."
  [[q r]]
  (let [directions (if (even? r) offset-directions-even offset-directions-odd)]
    (->> directions
         (map (fn [[dq dr]] [(+ q dq) (+ r dr)]))
         (filter valid-position?))))

(defn hex-distance
  "Calculates the distance between two hex positions using offset coordinates."
  [[q1 r1] [q2 r2]]
  (let [[x1 y1 z1] (offset->cube [q1 r1])
        [x2 y2 z2] (offset->cube [q2 r2])]
    (/ (+ (abs (- x1 x2)) (abs (- y1 y2)) (abs (- z1 z2))) 2)))

(defn hex-range
  "Returns all hex positions within distance n of the center position."
  [[q r] n]
  (let [[cx cy cz] (offset->cube [q r])]
    (for [dx    (range (- n) (inc n))
          dy    (range (max (- n) (- (- n) dx)) (inc (min n (- n dx))))
          :let  [dz (- (- dx) dy)
                 cube [(+ cx dx) (+ cy dy) (+ cz dz)]
                 pos (cube->offset cube)]
          :when (valid-position? pos)]
      pos)))

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

(defn hex-line
  "Returns all hex positions along a line from start to end (inclusive)."
  [start end]
  (let [n          (hex-distance start end)
        [x1 y1 z1] (offset->cube start)
        [x2 y2 z2] (offset->cube end)]
    (if (zero? n)
      [start]
      (for [i    (range (inc n))
            :let [t (/ i n)
                  x (lerp x1 x2 t)
                  y (lerp y1 y2 t)
                  z (lerp z1 z2 t)
                  [rx ry rz] (cube-round x y z)]]
        (cube->offset [rx ry rz])))))

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

(defn check-occupant-invariants
  "Validates occupant invariants on the board.

  Checks that no occupant ID appears in multiple positions.
  Returns nil if valid, or a map with :error and :details if invalid."
  [board]
  (let [occupants    (:occupants board)
        id-positions (->> occupants
                          (filter #(:id (val %)))
                          (group-by #(:id (val %))))]
    (when-let [duplicates (->> id-positions
                               (filter #(> (count (val %)) 1))
                               seq)]
      {:error :duplicate-occupant-ids
       :details (into {}
                      (map (fn [[id entries]]
                             [id (mapv first entries)])
                           duplicates))})))

(defn valid-occupants?
  "Returns true if the board's occupants satisfy all invariants.

  Currently checks that no occupant ID appears in multiple positions."
  [board]
  (nil? (check-occupant-invariants board)))
