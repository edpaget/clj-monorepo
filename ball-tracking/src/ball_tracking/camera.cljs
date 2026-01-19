(ns ball-tracking.camera
  "Camera access and stream management using WebRTC getUserMedia API.")

(def default-constraints
  "Default camera constraints preferring back camera on mobile."
  {:video {:width {:ideal 1280}
           :height {:ideal 720}
           :facingMode "environment"}
   :audio false})

(defn request-camera
  "Requests camera access and returns a promise resolving to MediaStream.
   Uses default constraints if none provided."
  ([]
   (request-camera default-constraints))
  ([constraints]
   (-> js/navigator.mediaDevices
       (.getUserMedia (clj->js constraints)))))

(defn attach-stream
  "Attaches a MediaStream to a video element and starts playback."
  [video-element stream]
  (set! (.-srcObject video-element) stream)
  (.play video-element))

(defn stop-stream
  "Stops all tracks in a MediaStream, releasing the camera."
  [stream]
  (doseq [track (.getTracks stream)]
    (.stop track)))

(defn get-video-dimensions
  "Returns the actual video dimensions as a map with :width and :height."
  [video-element]
  {:width (.-videoWidth video-element)
   :height (.-videoHeight video-element)})
