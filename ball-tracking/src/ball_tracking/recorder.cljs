(ns ball-tracking.recorder
  "Video recording with canvas overlay compositing."
  (:require [ball-tracking.state :refer [app-state]]))

(defonce recorder-state
  (atom {:media-recorder nil
         :chunks         []
         :composite-canvas nil
         :animation-frame nil}))

(defn- create-composite-canvas
  "Creates an offscreen canvas for compositing video + overlay."
  [width height]
  (let [canvas (js/document.createElement "canvas")]
    (set! (.-width canvas) width)
    (set! (.-height canvas) height)
    canvas))

(defn- composite-frame
  "Draws video and overlay canvas onto composite canvas."
  [composite-ctx video-el overlay-canvas]
  (let [width  (.-width (.-canvas composite-ctx))
        height (.-height (.-canvas composite-ctx))]
    (.drawImage composite-ctx video-el 0 0 width height)
    (.drawImage composite-ctx overlay-canvas 0 0 width height)))

(defn start-recording
  "Starts recording the video with overlay.
   Returns true if recording started successfully."
  [video-el overlay-canvas]
  (let [width  (.-videoWidth video-el)
        height (.-videoHeight video-el)]
    (when (and (pos? width) (pos? height))
      (let [composite-canvas (create-composite-canvas width height)
            composite-ctx    (.getContext composite-canvas "2d")
            stream           (.captureStream composite-canvas 30)
            media-recorder   (js/MediaRecorder. stream #js {:mimeType "video/webm"})]

        ;; Set up MediaRecorder events
        (set! (.-ondataavailable media-recorder)
              (fn [e]
                (when (pos? (.-size (.-data e)))
                  (swap! recorder-state update :chunks conj (.-data e)))))

        (set! (.-onstop media-recorder)
              (fn []
                (let [chunks (:chunks @recorder-state)
                      blob   (js/Blob. (clj->js chunks) #js {:type "video/webm"})
                      url    (js/URL.createObjectURL blob)
                      clip   {:id        (random-uuid)
                              :url       url
                              :blob      blob
                              :timestamp (js/Date.now)
                              :duration  nil}]
                  (swap! app-state update :clips conj clip)
                  (swap! app-state assoc :recording? false)
                  (swap! recorder-state assoc :chunks []))))

        ;; Start compositing loop
        (letfn [(render-loop []
                  (when (:recording? @app-state)
                    (composite-frame composite-ctx video-el overlay-canvas)
                    (swap! recorder-state assoc :animation-frame
                           (js/requestAnimationFrame render-loop))))]
          (swap! recorder-state assoc
                 :media-recorder   media-recorder
                 :composite-canvas composite-canvas
                 :chunks           [])
          (swap! app-state assoc :recording? true)
          (.start media-recorder)
          (render-loop)
          true)))))

(defn stop-recording
  "Stops the current recording."
  []
  (when-let [recorder (:media-recorder @recorder-state)]
    (when (= "recording" (.-state recorder))
      (.stop recorder))
    (when-let [frame (:animation-frame @recorder-state)]
      (js/cancelAnimationFrame frame))
    (swap! recorder-state assoc
           :media-recorder nil
           :animation-frame nil)))

(defn delete-clip
  "Deletes a clip by ID and revokes its object URL."
  [clip-id]
  (let [clips (:clips @app-state)
        clip  (first (filter #(= clip-id (:id %)) clips))]
    (when clip
      (js/URL.revokeObjectURL (:url clip))
      (swap! app-state update :clips
             (fn [clips] (vec (remove #(= clip-id (:id %)) clips)))))))

(defn download-clip
  "Triggers download of a clip."
  [clip]
  (let [a (js/document.createElement "a")]
    (set! (.-href a) (:url clip))
    (set! (.-download a) (str "tracking-" (:timestamp clip) ".webm"))
    (.click a)))

(defn clear-all-clips
  "Deletes all clips and revokes their URLs."
  []
  (doseq [clip (:clips @app-state)]
    (js/URL.revokeObjectURL (:url clip)))
  (swap! app-state assoc :clips []))
