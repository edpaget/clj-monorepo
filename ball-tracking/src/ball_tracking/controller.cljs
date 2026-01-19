(ns ball-tracking.controller
  "Camera and detection loop control functions."
  (:require [ball-tracking.camera :as camera]
            [ball-tracking.canvas :as canvas]
            [ball-tracking.collision :as collision]
            [ball-tracking.state :refer [app-state]]
            [ball-tracking.tracking :as tracking]
            [ball-tracking.yolo :as yolo]))

(defn update-fps
  "Updates FPS counter in app state."
  []
  (let [now                                 (js/performance.now)
        {:keys [last-fps-time frame-count]} @app-state
        elapsed                             (- now last-fps-time)]
    (swap! app-state update :frame-count inc)
    (when (> elapsed 1000)
      (swap! app-state assoc
             :fps (js/Math.round (* 1000 (/ frame-count elapsed)))
             :frame-count 0
             :last-fps-time now))))

(defn- invoke-collision-callback
  "Invokes the collision callback for each new collision event."
  [collisions]
  (when-let [callback (:collision-callback @app-state)]
    (doseq [c collisions]
      (callback (clj->js c)))))

(defn detection-loop
  "Main detection loop using requestAnimationFrame."
  [video-el canvas-el]
  (let [ctx                    (.getContext canvas-el "2d")
        {:keys [width height]} (camera/get-video-dimensions video-el)]
    (letfn [(loop-fn []
              (when (:running? @app-state)
                (update-fps)
                (-> (yolo/detect-objects video-el :score-threshold 0.25)
                    (.then (fn [detections]
                             (let [candidates (->> detections
                                                   (remove #(= "person" (:class %))))]
                               ;; Update tracks
                               (swap! app-state update :tracks
                                      #(-> %
                                           (tracking/update-tracks candidates 100)
                                           (tracking/prune-stale-tracks 500)))
                               ;; Detect collisions
                               (let [tracks     (:tracks @app-state)
                                     collisions (collision/detect-collisions tracks)]
                                 (swap! app-state assoc :collisions collisions)
                                 (when (seq collisions)
                                   (invoke-collision-callback collisions))
                                 ;; Render
                                 (canvas/clear-canvas ctx width height)
                                 (canvas/draw-tracks ctx tracks collisions)))))
                    (.catch (fn [err]
                              (js/console.error "Detection error:" err)))
                    (.finally #(js/requestAnimationFrame loop-fn)))))]
      (swap! app-state assoc :last-fps-time (js/performance.now))
      (js/requestAnimationFrame loop-fn))))

(defn start-tracking
  "Initializes camera and starts the detection loop."
  [video-el canvas-el]
  (-> (camera/request-camera)
      (.then (fn [stream]
               (camera/attach-stream video-el stream)
               (.addEventListener video-el "loadedmetadata"
                                  (fn []
                                    (let [{:keys [width height]} (camera/get-video-dimensions video-el)]
                                      (set! (.-width canvas-el) width)
                                      (set! (.-height canvas-el) height)
                                      (swap! app-state assoc :running? true :error nil)
                                      (detection-loop video-el canvas-el))))))
      (.catch (fn [err]
                (js/console.error "Camera access denied:" err)
                (swap! app-state assoc :error (str "Camera access denied: " (.-message err)))))))

(defn stop-tracking
  "Stops tracking and releases camera."
  [video-el]
  (swap! app-state assoc :running? false :tracks {})
  (when-let [stream (.-srcObject video-el)]
    (camera/stop-stream stream)
    (set! (.-srcObject video-el) nil)))
