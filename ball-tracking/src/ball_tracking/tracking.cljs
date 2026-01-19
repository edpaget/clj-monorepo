(ns ball-tracking.tracking
  "Temporal object tracking with trail history for detected objects."
  (:require [ball-tracking.collision :as collision]))

(defn euclidean-distance
  "Calculates Euclidean distance between two [x y] points."
  [[x1 y1] [x2 y2]]
  (js/Math.sqrt (+ (js/Math.pow (- x2 x1) 2)
                   (js/Math.pow (- y2 y1) 2))))

(defn bbox-center
  "Returns center point [x y] of a bounding box [x y width height]."
  [[x y w h]]
  [(+ x (/ w 2))
   (+ y (/ h 2))])

(defn match-detection-to-track
  "Matches a new detection to existing track by proximity.
   Returns track-id or nil if no match within max-distance."
  [detection tracks max-distance]
  (let [det-center (bbox-center (:bbox detection))
        candidates (->> tracks
                        (map (fn [[id track]]
                               [id (euclidean-distance det-center (:position track))]))
                        (filter #(< (second %) max-distance))
                        (sort-by second))]
    (ffirst candidates)))

(defn create-track
  "Creates a new track from a detection."
  [detection]
  (let [center (bbox-center (:bbox detection))
        id     (random-uuid)
        now    (js/Date.now)]
    {:id         id
     :class      (:class detection)
     :score      (:score detection)
     :position   center
     :bbox       (:bbox detection)
     :history    [center]
     :timestamps [now]
     :velocity   [0 0]
     :motion     :stationary
     :last-seen  now}))

(defn update-track
  "Updates an existing track with a new detection."
  [track detection]
  (let [center     (bbox-center (:bbox detection))
        now        (js/Date.now)
        history    (take-last 50 (conj (:history track) center))
        timestamps (take-last 50 (conj (:timestamps track) now))
        velocity   (collision/calculate-velocity history timestamps)
        motion     (collision/classify-motion velocity)]
    (-> track
        (assoc :position center)
        (assoc :bbox (:bbox detection))
        (assoc :class (:class detection))
        (assoc :score (:score detection))
        (assoc :history (vec history))
        (assoc :timestamps (vec timestamps))
        (assoc :velocity velocity)
        (assoc :motion motion)
        (assoc :last-seen now))))

(defn update-tracks
  "Updates tracks with new detections. Returns updated tracks map.
   Creates new tracks for unmatched detections."
  [tracks detections max-distance]
  (let [matched-track-ids (atom #{})]
    (reduce
     (fn [acc detection]
       (if-let [track-id (match-detection-to-track detection acc max-distance)]
         (do
           (swap! matched-track-ids conj track-id)
           (update acc track-id update-track detection))
         (let [new-track (create-track detection)]
           (assoc acc (:id new-track) new-track))))
     tracks
     detections)))

(defn prune-stale-tracks
  "Removes tracks not seen within timeout-ms."
  [tracks timeout-ms]
  (let [now (js/Date.now)]
    (->> tracks
         (filter (fn [[_ track]]
                   (< (- now (:last-seen track)) timeout-ms)))
         (into {}))))
