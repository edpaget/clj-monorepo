(ns ball-tracking.collision
  "Collision detection between tracked objects.
   Detects when moving objects collide with stationary ones.")

(def default-velocity-threshold
  "Default velocity magnitude below which object is considered stationary."
  15)

(def ^:private velocity-samples
  "Number of position samples to use for velocity calculation."
  5)

(defn calculate-velocity
  "Calculates velocity from position history and timestamps.
   Returns [vx vy] in pixels per second, or [0 0] if insufficient data."
  [history timestamps]
  (if (or (< (count history) 2)
          (< (count timestamps) 2))
    [0 0]
    (let [samples   (min velocity-samples (count history) (count timestamps))
          positions (take-last samples history)
          times     (take-last samples timestamps)
          [x1 y1]   (first positions)
          [x2 y2]   (last positions)
          t1        (first times)
          t2        (last times)
          dt        (- t2 t1)]
      (if (zero? dt)
        [0 0]
        [(* (/ (- x2 x1) dt) 1000)
         (* (/ (- y2 y1) dt) 1000)]))))

(defn velocity-magnitude
  "Returns the magnitude of a velocity vector."
  [[vx vy]]
  (js/Math.sqrt (+ (* vx vx) (* vy vy))))

(defn classify-motion
  "Classifies an object as :moving or :stationary based on velocity."
  ([velocity]
   (classify-motion velocity default-velocity-threshold))
  ([velocity threshold]
   (if (< (velocity-magnitude velocity) threshold)
     :stationary
     :moving)))

(defn aabb-intersect?
  "Tests if two axis-aligned bounding boxes intersect.
   Each bbox is [x y width height]."
  [[x1 y1 w1 h1] [x2 y2 w2 h2]]
  (and (<= x1 (+ x2 w2))
       (>= (+ x1 w1) x2)
       (<= y1 (+ y2 h2))
       (>= (+ y1 h1) y2)))

(defn bbox-center
  "Returns the center point of a bounding box."
  [[x y w h]]
  [(+ x (/ w 2))
   (+ y (/ h 2))])

(defn intersection-point
  "Calculates approximate intersection point between two bboxes.
   Returns midpoint between the two centers."
  [bbox1 bbox2]
  (let [[cx1 cy1] (bbox-center bbox1)
        [cx2 cy2] (bbox-center bbox2)]
    [(/ (+ cx1 cx2) 2)
     (/ (+ cy1 cy2) 2)]))

(defn detect-collisions
  "Detects collisions between tracks.
   Returns collision events where a moving object intersects a stationary one."
  [tracks]
  (let [track-list (vals tracks)
        moving     (filter #(= :moving (:motion %)) track-list)
        stationary (filter #(= :stationary (:motion %)) track-list)]
    (for [m     moving
          s     stationary
          :when (aabb-intersect? (:bbox m) (:bbox s))]
      {:moving     (select-keys m [:id :class :velocity :bbox :position])
       :stationary (select-keys s [:id :class :bbox :position])
       :point      (intersection-point (:bbox m) (:bbox s))
       :timestamp  (js/Date.now)})))

(defn colliding-track-ids
  "Returns set of track IDs involved in any collision."
  [collisions]
  (into #{}
        (mapcat (fn [{:keys [moving stationary]}]
                  [(:id moving) (:id stationary)]))
        collisions))
