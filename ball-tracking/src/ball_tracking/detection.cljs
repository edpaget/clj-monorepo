(ns ball-tracking.detection
  "Object detection using TensorFlow.js COCO-SSD model."
  (:require [promesa.core :as p]))

(defonce model (atom nil))
(defonce loading? (atom false))

(defn load-coco-ssd
  "Loads the COCO-SSD model with mobilenet_v2 base for better accuracy.
   Returns a promise resolving to the loaded model."
  []
  (when-not @loading?
    (reset! loading? true)
    (-> (.load js/cocoSsd #js {:base "mobilenet_v2"})
        (p/then (fn [m]
                  (reset! model m)
                  (reset! loading? false)
                  m))
        (p/catch (fn [err]
                   (reset! loading? false)
                   (throw err))))))

(defn model-loaded?
  "Returns true if the detection model is loaded and ready."
  []
  (some? @model))

(defn detect-objects
  "Runs object detection on an image source (video, canvas, or img element).
   Returns a promise resolving to a vector of detection maps with keys
   :bbox, :class, and :score."
  [source]
  (when-let [^js m @model]
    (-> (.detect m source)
        (p/then (fn [predictions]
                  (mapv (fn [^js pred]
                          {:bbox (vec (.-bbox pred))
                           :class (.-class pred)
                           :score (.-score pred)})
                        predictions))))))

(defn filter-balls
  "Filters detections to only include sports balls above confidence threshold."
  [detections & {:keys [threshold] :or {threshold 0.5}}]
  (->> detections
       (filter #(= "sports ball" (:class %)))
       (filter #(>= (:score %) threshold))))
