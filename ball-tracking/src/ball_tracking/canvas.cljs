(ns ball-tracking.canvas
  "Canvas rendering utilities for drawing detection overlays and tracking trails."
  (:require [ball-tracking.collision :as collision]))

(defn clear-canvas
  "Clears the entire canvas."
  [ctx width height]
  (.clearRect ctx 0 0 width height))

(defn draw-bbox
  "Draws a bounding box with optional label."
  [ctx [x y w h] & {:keys [color label line-width]
                    :or {color "#00ff00" line-width 3}}]
  (set! (.-strokeStyle ctx) color)
  (set! (.-lineWidth ctx) line-width)
  (.strokeRect ctx x y w h)
  (when label
    (set! (.-fillStyle ctx) "rgba(0, 0, 0, 0.6)")
    (.fillRect ctx x (- y 24) (+ (.-width (.measureText ctx label)) 8) 22)
    (set! (.-fillStyle ctx) color)
    (set! (.-font ctx) "14px monospace")
    (.fillText ctx label (+ x 4) (- y 8))))

(defn draw-trail
  "Draws a fading trail from position history."
  [ctx history & {:keys [color max-points]
                  :or {color "#00ffff" max-points 30}}]
  (let [points (take-last max-points history)
        len    (count points)]
    (when (> len 1)
      (set! (.-lineCap ctx) "round")
      (set! (.-lineJoin ctx) "round")
      (doseq [[i [x y]] (map-indexed vector points)]
        (let [alpha  (/ (inc i) len)
              radius (* 4 alpha)]
          (set! (.-globalAlpha ctx) alpha)
          (set! (.-fillStyle ctx) color)
          (.beginPath ctx)
          (.arc ctx x y radius 0 (* 2 js/Math.PI))
          (.fill ctx))))
    (set! (.-globalAlpha ctx) 1.0)))

(defn draw-crosshair
  "Draws a crosshair at the center of a bounding box."
  [ctx [x y w h] & {:keys [color size] :or {color "#ff0000" size 10}}]
  (let [cx (+ x (/ w 2))
        cy (+ y (/ h 2))]
    (set! (.-strokeStyle ctx) color)
    (set! (.-lineWidth ctx) 2)
    (.beginPath ctx)
    (.moveTo ctx (- cx size) cy)
    (.lineTo ctx (+ cx size) cy)
    (.moveTo ctx cx (- cy size))
    (.lineTo ctx cx (+ cy size))
    (.stroke ctx)))

(defn draw-collision-point
  "Draws a collision indicator at the intersection point."
  [ctx [x y]]
  (let [now    (js/Date.now)
        pulse  (+ 0.5 (* 0.5 (js/Math.sin (/ now 100))))
        radius (* 15 pulse)]
    (set! (.-globalAlpha ctx) pulse)
    (set! (.-fillStyle ctx) "#ff4444")
    (.beginPath ctx)
    (.arc ctx x y radius 0 (* 2 js/Math.PI))
    (.fill ctx)
    (set! (.-strokeStyle ctx) "#ffffff")
    (set! (.-lineWidth ctx) 2)
    (.stroke ctx)
    (set! (.-globalAlpha ctx) 1.0)))

(defn draw-tracks
  "Draws all active tracks with bounding boxes, crosshairs, and trails.
   Highlights tracks involved in collisions."
  [ctx tracks collisions]
  (let [colliding-ids (collision/colliding-track-ids collisions)]
    (doseq [[_ track] tracks]
      (let [colliding? (contains? colliding-ids (:id track))
            box-color  (if colliding? "#ff4444" "#00ff00")
            motion-tag (when colliding?
                         (if (= :moving (:motion track)) " [MOVING]" " [STATIC]"))]
        (draw-trail ctx (:history track) :color "#00ffff")
        (draw-bbox ctx (:bbox track)
                   :color box-color
                   :label (str (:class track) " "
                               (int (* 100 (:score track))) "%"
                               motion-tag))
        (draw-crosshair ctx (:bbox track) :color (if colliding? "#ffffff" "#ff0000"))))
    ;; Draw collision points
    (doseq [{:keys [point]} collisions]
      (draw-collision-point ctx point))))
