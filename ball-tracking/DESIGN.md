# Ball Tracking Web Application - Client-Side Architecture

A browser-based ball tracking application using ClojureScript with real-time camera input and machine learning inference.

## Overview

This document describes a client-side only approach where all video capture, processing, and object detection happens in the browser. No server is required for the core tracking functionality.

```
┌─────────────────────────────────────────────────────────────────┐
│  Browser                                                        │
│                                                                 │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐  │
│  │  Camera  │ →  │  Video   │ →  │ Detection│ →  │  Canvas  │  │
│  │  Stream  │    │  Element │    │  Model   │    │  Overlay │  │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘  │
│       ↑                              ↓                          │
│  getUserMedia              TensorFlow.js / tracking.js          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| UI Framework | UIx (React wrapper) | Component rendering |
| Styling | Tailwind CSS | UI styling |
| ML Runtime | TensorFlow.js | Neural network inference |
| Camera Access | WebRTC getUserMedia | Video stream capture |
| Rendering | HTML5 Canvas | Drawing detection overlays |
| Build | shadow-cljs | ClojureScript compilation |

## Detection Approaches

### Option 1: TensorFlow.js with COCO-SSD (Recommended for Starting)

Pre-trained model that detects 80 object classes including "sports ball".

**Pros:**
- No training required
- Good accuracy out of the box
- ~20MB model size
- 20-30 FPS on modern hardware

**Cons:**
- Generic "sports ball" class (not ball-type specific)
- May miss small or fast-moving balls

```clojure
;; Load COCO-SSD model
(defn load-model []
  (-> (js/cocoSsd.load)
      (.then (fn [model]
               (reset! model-atom model)))))

;; Run detection on video frame
(defn detect [model video-element]
  (-> (.detect model video-element)
      (.then (fn [predictions]
               ;; predictions is array of:
               ;; {:bbox [x y width height]
               ;;  :class "sports ball"
               ;;  :score 0.87}
               (filter #(= "sports ball" (.-class %)) predictions)))))
```

### Option 2: TensorFlow.js with YOLOv8 Nano

Smaller, faster YOLO variant optimized for edge devices.

**Pros:**
- Faster inference than COCO-SSD
- Better at detecting small objects
- 6MB quantized model

**Cons:**
- Requires model conversion from Ultralytics
- More complex post-processing (NMS)

```clojure
;; YOLOv8 requires manual post-processing
(defn process-yolo-output [output conf-threshold]
  (let [boxes (.-boxes output)
        scores (.-scores output)
        classes (.-classes output)]
    (->> (range (.-length scores))
         (filter #(> (aget scores %) conf-threshold))
         (map (fn [i]
                {:bbox (aget boxes i)
                 :score (aget scores i)
                 :class (aget classes i)})))))
```

### Option 3: tracking.js for Color-Based Tracking

Lightweight library for tracking objects by color signature.

**Pros:**
- Very fast (60 FPS easily)
- Tiny library (~10KB)
- Works well for distinctly colored balls

**Cons:**
- Requires known ball color
- Sensitive to lighting changes
- No ML-based recognition

```clojure
(defn setup-color-tracker [video canvas]
  (let [tracker (js/tracking.ColorTracker. #js ["magenta" "yellow"])]
    (.on tracker "track"
         (fn [event]
           (doseq [rect (.-data event)]
             (draw-rect canvas rect))))
    (js/tracking.track video tracker #js {:camera true})))
```

### Option 4: Custom Color Detection with OpenCV.js

HSV color space filtering for specific ball colors.

**Pros:**
- Fine-grained control over detection
- Can tune for specific ball color
- No external model needed

**Cons:**
- Requires manual calibration
- More code to write

```clojure
(defn detect-by-color [frame hsv-lower hsv-upper]
  (let [hsv (js/cv.Mat.)
        mask (js/cv.Mat.)
        contours (js/cv.MatVector.)]
    (js/cv.cvtColor frame hsv js/cv.COLOR_RGB2HSV)
    (js/cv.inRange hsv hsv-lower hsv-upper mask)
    (js/cv.findContours mask contours
                        js/cv.RETR_EXTERNAL
                        js/cv.CHAIN_APPROX_SIMPLE)
    ;; Find largest contour as ball candidate
    (->> (range (.size contours))
         (map #(.get contours %))
         (apply max-key #(js/cv.contourArea %)))))
```

## Core Implementation

### Project Structure

```
ball-tracking/
├── deps.edn
├── shadow-cljs.edn
├── package.json
├── resources/
│   └── public/
│       └── index.html
├── src/
│   └── ball_tracking/
│       ├── core.cljs          ;; Entry point, app initialization
│       ├── camera.cljs        ;; Camera access and stream management
│       ├── detection.cljs     ;; ML model loading and inference
│       ├── tracking.cljs      ;; Temporal tracking, smoothing
│       ├── canvas.cljs        ;; Visualization, drawing overlays
│       └── components/
│           ├── app.cljs       ;; Main app component
│           ├── video.cljs     ;; Video display component
│           └── controls.cljs  ;; UI controls
└── test/
    └── ball_tracking/
        └── tracking_test.cljs
```

### Camera Module

```clojure
(ns ball-tracking.camera)

(def default-constraints
  {:video {:width {:ideal 1280}
           :height {:ideal 720}
           :facingMode "environment"}  ;; Back camera on mobile
   :audio false})

(defn request-camera
  "Requests camera access and returns a promise resolving to MediaStream."
  ([]
   (request-camera default-constraints))
  ([constraints]
   (-> js/navigator.mediaDevices
       (.getUserMedia (clj->js constraints)))))

(defn attach-stream
  "Attaches a MediaStream to a video element."
  [video-element stream]
  (set! (.-srcObject video-element) stream)
  (.play video-element))

(defn stop-stream
  "Stops all tracks in a MediaStream."
  [stream]
  (doseq [track (.getTracks stream)]
    (.stop track)))

(defn get-video-dimensions
  "Returns {:width w :height h} of the video element."
  [video-element]
  {:width (.-videoWidth video-element)
   :height (.-videoHeight video-element)})
```

### Detection Module

```clojure
(ns ball-tracking.detection
  (:require [promesa.core :as p]))

(defonce model (atom nil))

(defn load-coco-ssd
  "Loads the COCO-SSD model. Returns a promise."
  []
  (-> (js/cocoSsd.load #js {:base "lite_mobilenet_v2"})
      (p/then (fn [m]
                (reset! model m)
                m))))

(defn detect-objects
  "Runs object detection on an image source (video, canvas, or img element).
   Returns a promise resolving to a vector of detections."
  [source]
  (when-let [m @model]
    (-> (.detect m source)
        (p/then (fn [predictions]
                  (mapv (fn [pred]
                          {:bbox (js->clj (.-bbox pred))
                           :class (.-class pred)
                           :score (.-score pred)})
                        predictions))))))

(defn filter-balls
  "Filters detections to only include sports balls above confidence threshold."
  [detections & {:keys [threshold] :or {threshold 0.5}}]
  (->> detections
       (filter #(= "sports ball" (:class %)))
       (filter #(>= (:score %) threshold))))
```

### Tracking Module

```clojure
(ns ball-tracking.tracking)

(defn euclidean-distance
  "Calculates distance between two points."
  [[x1 y1] [x2 y2]]
  (js/Math.sqrt (+ (js/Math.pow (- x2 x1) 2)
                   (js/Math.pow (- y2 y1) 2))))

(defn bbox-center
  "Returns center point [x y] of a bounding box [x y w h]."
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
                               [id (euclidean-distance det-center
                                                       (:position track))]))
                        (filter #(< (second %) max-distance))
                        (sort-by second))]
    (ffirst candidates)))

(defn update-tracks
  "Updates tracks with new detections. Returns updated tracks map."
  [tracks detections max-distance]
  (reduce
    (fn [acc detection]
      (let [center (bbox-center (:bbox detection))]
        (if-let [track-id (match-detection-to-track detection acc max-distance)]
          ;; Update existing track
          (update acc track-id
                  (fn [track]
                    (-> track
                        (assoc :position center)
                        (assoc :bbox (:bbox detection))
                        (update :history conj center)
                        (assoc :last-seen (js/Date.now)))))
          ;; Create new track
          (let [new-id (random-uuid)]
            (assoc acc new-id
                   {:id new-id
                    :position center
                    :bbox (:bbox detection)
                    :history [center]
                    :last-seen (js/Date.now)})))))
    tracks
    detections))

(defn prune-stale-tracks
  "Removes tracks not seen within timeout-ms."
  [tracks timeout-ms]
  (let [now (js/Date.now)]
    (->> tracks
         (filter (fn [[_ track]]
                   (< (- now (:last-seen track)) timeout-ms)))
         (into {}))))
```

### Canvas Rendering Module

```clojure
(ns ball-tracking.canvas)

(defn clear-canvas
  "Clears the canvas."
  [ctx width height]
  (.clearRect ctx 0 0 width height))

(defn draw-bbox
  "Draws a bounding box with label."
  [ctx [x y w h] & {:keys [color label line-width]
                    :or {color "#00ff00" line-width 2}}]
  (set! (.-strokeStyle ctx) color)
  (set! (.-lineWidth ctx) line-width)
  (.strokeRect ctx x y w h)
  (when label
    (set! (.-fillStyle ctx) color)
    (set! (.-font ctx) "16px monospace")
    (.fillText ctx label x (- y 5))))

(defn draw-trail
  "Draws a fading trail from position history."
  [ctx history & {:keys [color max-points]
                  :or {color "#00ff00" max-points 30}}]
  (let [points (take-last max-points history)
        len (count points)]
    (doseq [[i [x y]] (map-indexed vector points)]
      (let [alpha (/ (inc i) len)
            radius (* 3 alpha)]
        (set! (.-globalAlpha ctx) alpha)
        (set! (.-fillStyle ctx) color)
        (.beginPath ctx)
        (.arc ctx x y radius 0 (* 2 js/Math.PI))
        (.fill ctx)))
    (set! (.-globalAlpha ctx) 1.0)))

(defn draw-tracks
  "Draws all active tracks with bounding boxes and trails."
  [ctx tracks]
  (doseq [[_ track] tracks]
    (draw-trail ctx (:history track) :color "#00ffff")
    (draw-bbox ctx (:bbox track)
               :color "#00ff00"
               :label (str "Ball " (subs (str (:id track)) 0 8)))))
```

### Main Application Loop

```clojure
(ns ball-tracking.core
  (:require [ball-tracking.camera :as camera]
            [ball-tracking.detection :as detection]
            [ball-tracking.tracking :as tracking]
            [ball-tracking.canvas :as canvas]
            [uix.core :refer [defui $]]
            [uix.dom]))

(defonce app-state
  (atom {:running? false
         :tracks {}
         :fps 0
         :model-loaded? false}))

(defn detection-loop
  "Main detection loop using requestAnimationFrame."
  [video-el canvas-el]
  (let [ctx (.getContext canvas-el "2d")
        {:keys [width height]} (camera/get-video-dimensions video-el)
        last-time (atom (js/performance.now))
        frame-count (atom 0)]

    (letfn [(loop-fn []
              (when (:running? @app-state)
                ;; Calculate FPS
                (swap! frame-count inc)
                (let [now (js/performance.now)
                      elapsed (- now @last-time)]
                  (when (> elapsed 1000)
                    (swap! app-state assoc :fps @frame-count)
                    (reset! frame-count 0)
                    (reset! last-time now)))

                ;; Run detection
                (-> (detection/detect-objects video-el)
                    (.then (fn [detections]
                             (let [balls (detection/filter-balls detections)]
                               ;; Update tracks
                               (swap! app-state update :tracks
                                      #(-> %
                                           (tracking/update-tracks balls 100)
                                           (tracking/prune-stale-tracks 500)))

                               ;; Render
                               (canvas/clear-canvas ctx width height)
                               (canvas/draw-tracks ctx (:tracks @app-state)))))
                    (.finally #(js/requestAnimationFrame loop-fn)))))]

      (js/requestAnimationFrame loop-fn))))

(defn start-tracking
  "Initializes camera and starts the detection loop."
  [video-el canvas-el]
  (-> (camera/request-camera)
      (.then (fn [stream]
               (camera/attach-stream video-el stream)
               ;; Wait for video to be ready
               (.addEventListener video-el "loadedmetadata"
                 (fn []
                   (let [{:keys [width height]} (camera/get-video-dimensions video-el)]
                     (set! (.-width canvas-el) width)
                     (set! (.-height canvas-el) height)
                     (swap! app-state assoc :running? true)
                     (detection-loop video-el canvas-el))))))
      (.catch (fn [err]
                (js/console.error "Camera access denied:" err)))))

(defn stop-tracking
  "Stops tracking and releases camera."
  [video-el]
  (swap! app-state assoc :running? false)
  (when-let [stream (.-srcObject video-el)]
    (camera/stop-stream stream)))
```

### UIx Components

```clojure
(ns ball-tracking.components.app
  (:require [ball-tracking.core :as core]
            [ball-tracking.detection :as detection]
            [uix.core :refer [defui $ use-state use-effect use-ref]]))

(defui video-canvas
  "Overlays a canvas on top of a video element for drawing detections."
  []
  (let [video-ref (use-ref nil)
        canvas-ref (use-ref nil)
        [running? set-running!] (use-state false)
        [fps _] (use-state 0)]

    ($ :div {:class "relative w-full max-w-4xl mx-auto"}
       ($ :video {:ref video-ref
                  :class "w-full rounded-lg"
                  :autoPlay true
                  :playsInline true
                  :muted true})
       ($ :canvas {:ref canvas-ref
                   :class "absolute top-0 left-0 w-full h-full pointer-events-none"})

       ($ :div {:class "absolute top-4 left-4 bg-black/50 text-white px-2 py-1 rounded"}
          (str "FPS: " (:fps @core/app-state)))

       ($ :div {:class "mt-4 flex gap-4 justify-center"}
          (if running?
            ($ :button {:class "px-4 py-2 bg-red-600 text-white rounded"
                        :on-click (fn []
                                    (core/stop-tracking @video-ref)
                                    (set-running! false))}
               "Stop")
            ($ :button {:class "px-4 py-2 bg-green-600 text-white rounded"
                        :on-click (fn []
                                    (core/start-tracking @video-ref @canvas-ref)
                                    (set-running! true))}
               "Start Tracking"))))))

(defui app []
  (let [[model-ready? set-model-ready!] (use-state false)]

    (use-effect
      (fn []
        (-> (detection/load-coco-ssd)
            (.then #(set-model-ready! true)))
        js/undefined)
      [])

    ($ :div {:class "min-h-screen bg-gray-900 text-white p-8"}
       ($ :h1 {:class "text-3xl font-bold text-center mb-8"}
          "Ball Tracking")

       (if model-ready?
         ($ video-canvas)
         ($ :div {:class "text-center"}
            ($ :p "Loading model...")
            ($ :div {:class "animate-spin w-8 h-8 border-4 border-white border-t-transparent rounded-full mx-auto mt-4"}))))))
```

## Configuration Files

### shadow-cljs.edn

```clojure
{:source-paths ["src"]

 :dependencies [[com.pitch/uix.core "1.3.1"]
                [com.pitch/uix.dom "1.3.1"]
                [funcool/promesa "11.0.678"]]

 :builds
 {:app {:target :browser
        :output-dir "resources/public/js"
        :asset-path "/js"
        :modules {:main {:init-fn ball-tracking.core/init}}
        :devtools {:http-root "resources/public"
                   :http-port 8080}}}}
```

### package.json

```json
{
  "name": "ball-tracking",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "npx shadow-cljs watch app",
    "build": "npx shadow-cljs release app"
  },
  "devDependencies": {
    "shadow-cljs": "^2.28.0"
  },
  "dependencies": {
    "@tensorflow/tfjs": "^4.22.0",
    "@tensorflow-models/coco-ssd": "^2.2.3",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  }
}
```

### resources/public/index.html

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Ball Tracking</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <script src="https://cdn.jsdelivr.net/npm/@tensorflow/tfjs"></script>
  <script src="https://cdn.jsdelivr.net/npm/@tensorflow-models/coco-ssd"></script>
</head>
<body>
  <div id="root"></div>
  <script src="/js/main.js"></script>
</body>
</html>
```

## Performance Optimization

### 1. Reduce Detection Frequency

Run detection every N frames instead of every frame:

```clojure
(defn throttled-detection-loop [video-el canvas-el detect-every-n]
  (let [frame-counter (atom 0)]
    (letfn [(loop-fn []
              (swap! frame-counter inc)
              (when (zero? (mod @frame-counter detect-every-n))
                ;; Run detection
                ...)
              ;; Always render from cached tracks
              (render-tracks ...)
              (js/requestAnimationFrame loop-fn))]
      (loop-fn))))
```

### 2. Use OffscreenCanvas for Detection

Process detection on a smaller resolution:

```clojure
(defn create-detection-canvas [width height scale]
  (let [canvas (js/document.createElement "canvas")]
    (set! (.-width canvas) (* width scale))
    (set! (.-height canvas) (* height scale))
    canvas))

(defn sample-frame [video detection-canvas]
  (let [ctx (.getContext detection-canvas "2d")]
    (.drawImage ctx video 0 0
                (.-width detection-canvas)
                (.-height detection-canvas))))
```

### 3. Web Worker for Heavy Processing

Move detection to a web worker (requires additional setup):

```clojure
;; Main thread
(defn create-detection-worker []
  (js/Worker. "/js/detection-worker.js"))

(defn send-frame-to-worker [worker image-data]
  (.postMessage worker #js {:type "detect" :imageData image-data}))
```

### 4. Model Quantization

Use int8 quantized models for faster inference:

```javascript
// In package.json, use quantized model variant
"@tensorflow-models/coco-ssd": "^2.2.3"  // Supports quantized versions
```

## Browser Compatibility

| Feature | Chrome | Firefox | Safari | Edge |
|---------|--------|---------|--------|------|
| getUserMedia | 53+ | 36+ | 11+ | 12+ |
| WebGL | 9+ | 4+ | 8+ | 12+ |
| OffscreenCanvas | 69+ | 105+ | 16.4+ | 79+ |
| Web Workers | 4+ | 3.5+ | 4+ | 12+ |

## Limitations

1. **Mobile Performance**: Expect 10-20 FPS on mobile devices
2. **Battery Drain**: Continuous ML inference uses significant power
3. **Model Loading**: Initial 20-50MB download for models
4. **Lighting Sensitivity**: Detection accuracy varies with lighting
5. **Small/Fast Objects**: May miss very small or fast-moving balls
6. **No Persistence**: Tracking state lost on page refresh

## Future Enhancements

1. **Custom Model Training**: Fine-tune YOLO on specific ball types
2. **Kalman Filter**: Add predictive tracking for smoother trails
3. **Multi-Ball Tracking**: Improve track assignment algorithm
4. **Recording/Export**: Save tracking data and video clips
5. **WebRTC Streaming**: Add option to stream to server for heavy processing

## Model Export

The project includes a Python script to export YOLO models to TensorFlow.js format.

### Prerequisites

- [uv](https://github.com/astral-sh/uv) - Fast Python package manager

### Available Models

| Model | Size | Speed | Best For |
|-------|------|-------|----------|
| `yolo26n` | ~6MB | Fastest | Mobile, low-end devices |
| **`yolo26s`** | **~20MB** | **Fast** | **Default, good balance** |
| `yolo26m` | ~50MB | Medium | Higher accuracy |
| `yolo11n` | ~12MB | Fast | Small object detection |
| `yolov8n` | ~12MB | Fast | Legacy compatibility |

### Export Commands

```bash
# Export default model (yolo26s)
npm run export-model

# Export specific models
npm run export-model:yolo26n  # Nano - smallest, fastest
npm run export-model:yolo26s  # Small - recommended
npm run export-model:yolo11n  # YOLO11 nano

# Custom model
cd scripts && uv run python export_model.py --model yolo26m
```

### How It Works

1. The script downloads the specified YOLO model from Ultralytics
2. Exports it to TensorFlow.js format using `model.export(format="tfjs")`
3. Copies the output files to `resources/public/model/`

Model files are excluded from git (see `.gitignore`) since they can be regenerated.
