(ns ball-tracking.yolo
  "YOLO object detection using TensorFlow.js.
   Supports YOLO26 (end-to-end, no NMS needed) and YOLOv8 models."
  (:require [promesa.core :as p]))

(defonce model (atom nil))
(defonce loading? (atom false))
(defonce model-input-shape (atom [640 640]))

(def coco-labels
  ["person" "bicycle" "car" "motorcycle" "airplane" "bus" "train" "truck" "boat"
   "traffic light" "fire hydrant" "stop sign" "parking meter" "bench" "bird" "cat"
   "dog" "horse" "sheep" "cow" "elephant" "bear" "zebra" "giraffe" "backpack"
   "umbrella" "handbag" "tie" "suitcase" "frisbee" "skis" "snowboard" "sports ball"
   "kite" "baseball bat" "baseball glove" "skateboard" "surfboard" "tennis racket"
   "bottle" "wine glass" "cup" "fork" "knife" "spoon" "bowl" "banana" "apple"
   "sandwich" "orange" "broccoli" "carrot" "hot dog" "pizza" "donut" "cake" "chair"
   "couch" "potted plant" "bed" "dining table" "toilet" "tv" "laptop" "mouse"
   "remote" "keyboard" "cell phone" "microwave" "oven" "toaster" "sink"
   "refrigerator" "book" "clock" "vase" "scissors" "teddy bear" "hair drier"
   "toothbrush"])

(defn load-yolov8
  "Loads a YOLO model from the specified path.
   Supports both YOLO26 (end-to-end) and YOLOv8 models.
   Returns a promise resolving to the loaded model."
  [model-url]
  (when-not @loading?
    (reset! loading? true)
    (js/console.log "Loading YOLO model from:" model-url)
    (-> (js/tf.loadGraphModel model-url)
        (p/then (fn [^js m]
                  (js/console.log "Model loaded successfully")
                  (let [[h w] @model-input-shape
                        dummy (js/tf.zeros #js [1 h w 3])]
                    (.execute m dummy)
                    (js/tf.dispose dummy))
                  (reset! model m)
                  (reset! loading? false)
                  m))
        (p/catch (fn [err]
                   (js/console.error "Failed to load model:" err)
                   (reset! loading? false)
                   (throw err))))))

(defn model-loaded?
  "Returns true if the YOLO model is loaded and ready."
  []
  (some? @model))

(defn preprocess
  "Preprocesses an image for YOLOv8 inference.
   Returns [input-tensor x-ratio y-ratio] where ratios are for scaling boxes back."
  [source]
  (let [[model-h model-w] @model-input-shape
        ;; Convert source to tensor
        img-tensor        (js/tf.browser.fromPixels source)
        [img-h img-w]     (.-shape img-tensor)
        ;; Calculate padding to make square
        max-size          (max img-h img-w)
        pad-h             (- max-size img-h)
        pad-w             (- max-size img-w)
        ;; Pad image to square
        padded            (js/tf.pad img-tensor #js [#js [0 pad-h] #js [0 pad-w] #js [0 0]])
        ;; Resize to model input size
        resized           (-> padded
                              (js/tf.image.resizeBilinear #js [model-h model-w]))
        ;; Normalize to 0-1
        normalized        (js/tf.div resized 255.0)
        ;; Add batch dimension
        batched           (js/tf.expandDims normalized 0)
        ;; Calculate ratios for scaling boxes back to original size
        x-ratio           (/ max-size model-w)
        y-ratio           (/ max-size model-h)]
    ;; Cleanup intermediate tensors
    (js/tf.dispose #js [img-tensor padded resized normalized])
    [batched x-ratio y-ratio]))

(defn- get-primary-output
  "Extracts the primary output tensor from model execution result.
   Handles both single tensor and multi-output models."
  [^js output]
  (cond
    ;; Array of tensors - get the largest one (typically the detection output)
    (array? output)
    (reduce (fn [best t]
              (if (> (reduce * (js->clj (.-shape t)))
                     (reduce * (js->clj (.-shape best))))
                t best))
            (first output) (rest output))

    ;; Object with named outputs
    (and (object? output) (not (.-shape output)))
    (aget output (aget (js/Object.keys output) 0))

    ;; Single tensor
    :else output))

(defn- detect-model-type
  "Detects whether this is a YOLO26 (end-to-end) or YOLOv8 model based on output shape."
  [^js output]
  (let [shape (js->clj (.-shape output))]
    (if (and (= (count shape) 3) (= (last shape) 6))
      :yolo26
      :yolov8)))

(defn- postprocess-yolo26
  "Postprocesses YOLO26 end-to-end output.
   Output shape: [1, 300, 6] where 6 = [x1, y1, x2, y2, confidence, class_id]."
  [output x-ratio y-ratio score-threshold]
  (let [;; Remove batch dimension -> [300, 6]
        squeezed       (js/tf.squeeze output #js [0])
        data           (.dataSync squeezed)
        num-detections (first (js->clj (.-shape squeezed)))
        detections     (loop [i       0
                              results []]
                         (if (>= i num-detections)
                           results
                           (let [idx        (* i 6)
                                 x1         (* (aget data idx) x-ratio)
                                 y1         (* (aget data (+ idx 1)) y-ratio)
                                 x2         (* (aget data (+ idx 2)) x-ratio)
                                 y2         (* (aget data (+ idx 3)) y-ratio)
                                 confidence (aget data (+ idx 4))
                                 class-id   (int (aget data (+ idx 5)))]
                             (if (>= confidence score-threshold)
                               (recur (inc i)
                                      (conj results
                                            {:bbox [x1 y1 (- x2 x1) (- y2 y1)]
                                             :class (get coco-labels class-id "unknown")
                                             :score confidence}))
                               (recur (inc i) results)))))]
    (js/tf.dispose squeezed)
    detections))

(defn- postprocess-yolov8
  "Postprocesses YOLOv8 output tensor. Returns tensors for NMS."
  [output x-ratio y-ratio]
  (js/tf.tidy
   (fn []
      ;; YOLOv8 output shape: [1, 84, 8400] where 84 = 4 bbox + 80 classes
      ;; Transpose to [1, 8400, 84] for easier processing
     (let [transposed    (js/tf.transpose output #js [0 2 1])
           squeezed      (js/tf.squeeze transposed #js [0])
           boxes-raw     (js/tf.slice squeezed #js [0 0] #js [-1 4])
           scores-raw    (js/tf.slice squeezed #js [0 4] #js [-1 80])
           max-scores    (js/tf.max scores-raw 1)
           class-indices (js/tf.argMax scores-raw 1)
            ;; Convert from center format (cx, cy, w, h) to corner format
           cx            (js/tf.slice boxes-raw #js [0 0] #js [-1 1])
           cy            (js/tf.slice boxes-raw #js [0 1] #js [-1 1])
           w             (js/tf.slice boxes-raw #js [0 2] #js [-1 1])
           h             (js/tf.slice boxes-raw #js [0 3] #js [-1 1])
           x1            (js/tf.sub cx (js/tf.div w 2))
           y1            (js/tf.sub cy (js/tf.div h 2))
           x2            (js/tf.add cx (js/tf.div w 2))
           y2            (js/tf.add cy (js/tf.div h 2))
           boxes-nms     (js/tf.squeeze (js/tf.concat #js [y1 x1 y2 x2] 1))]
       #js {:boxes boxes-nms
            :scores (js/tf.squeeze max-scores)
            :classes (js/tf.squeeze class-indices)
            :xRatio x-ratio
            :yRatio y-ratio}))))

(defn run-nms
  "Runs non-max suppression and extracts final detections."
  [^js postprocessed score-threshold iou-threshold]
  (let [^js boxes   (.-boxes postprocessed)
        ^js scores  (.-scores postprocessed)
        ^js classes (.-classes postprocessed)
        x-ratio     (.-xRatio postprocessed)
        y-ratio     (.-yRatio postprocessed)]
    (-> (js/tf.image.nonMaxSuppressionAsync boxes scores 100 iou-threshold score-threshold)
        (p/then (fn [nms-indices]
                  (let [^js selected-boxes   (.gather boxes nms-indices 0)
                        ^js selected-scores  (.gather scores nms-indices 0)
                        ^js selected-classes (.gather classes nms-indices 0)
                        boxes-data           (.dataSync selected-boxes)
                        scores-data          (.dataSync selected-scores)
                        classes-data         (.dataSync selected-classes)
                        num-detections       (.-length scores-data)
                        ;; Build detection maps
                        detections           (loop [i       0
                                                    results []]
                                               (if (>= i num-detections)
                                                 results
                                                 (let [idx       (* i 4)
                                             ;; boxes are [y1, x1, y2, x2], convert to [x, y, w, h]
                                                       y1        (* (aget boxes-data idx) y-ratio)
                                                       x1        (* (aget boxes-data (+ idx 1)) x-ratio)
                                                       y2        (* (aget boxes-data (+ idx 2)) y-ratio)
                                                       x2        (* (aget boxes-data (+ idx 3)) x-ratio)
                                                       class-idx (int (aget classes-data i))]
                                                   (recur (inc i)
                                                          (conj results
                                                                {:bbox [x1 y1 (- x2 x1) (- y2 y1)]
                                                                 :class (get coco-labels class-idx "unknown")
                                                                 :score (aget scores-data i)})))))]
                    ;; Cleanup tensors
                    (js/tf.dispose #js [boxes scores classes nms-indices
                                        selected-boxes selected-scores selected-classes])
                    detections))))))

(defn detect-objects
  "Runs YOLO object detection on an image source.
   Automatically detects model type (YOLO26 or YOLOv8).
   Returns a promise resolving to a vector of detection maps."
  [source & {:keys [score-threshold iou-threshold]
             :or {score-threshold 0.25 iou-threshold 0.45}}]
  (when-let [^js m @model]
    (let [[input x-ratio y-ratio] (preprocess source)
          raw-output              (.execute m input)
          output                  (get-primary-output raw-output)
          model-type              (detect-model-type output)]
      (js/tf.dispose input)
      (case model-type
        :yolo26
        (let [detections (postprocess-yolo26 output x-ratio y-ratio score-threshold)]
          (js/tf.dispose raw-output)
          (p/resolved detections))

        :yolov8
        (let [postprocessed (postprocess-yolov8 output x-ratio y-ratio)]
          (js/tf.dispose raw-output)
          (run-nms postprocessed score-threshold iou-threshold))))))

(defn filter-balls
  "Filters detections to only include sports balls above confidence threshold."
  [detections & {:keys [threshold] :or {threshold 0.5}}]
  (->> detections
       (filter #(= "sports ball" (:class %)))
       (filter #(>= (:score %) threshold))))
