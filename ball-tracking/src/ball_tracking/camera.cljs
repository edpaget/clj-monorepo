(ns ball-tracking.camera
  "Camera access and stream management using WebRTC getUserMedia API."
  (:require [promesa.core :as p]))

(def default-constraints
  "Default camera constraints preferring back camera on mobile."
  {:video {:width {:ideal 1280}
           :height {:ideal 720}
           :facingMode "environment"}
   :audio false})

(defn enumerate-video-devices
  "Returns a promise resolving to a vector of video input devices.
   Each device is a map with :device-id and :label keys."
  []
  (-> (js/navigator.mediaDevices.enumerateDevices)
      (p/then (fn [devices]
                (->> devices
                     (filter #(= "videoinput" (.-kind %)))
                     (map (fn [d]
                            {:device-id (.-deviceId d)
                             :label     (or (not-empty (.-label d))
                                            (str "Camera " (.-deviceId d)))}))
                     vec)))))

(defn request-camera
  "Requests camera access and returns a promise resolving to MediaStream.
   Uses default constraints if none provided. Pass device-id to select specific camera."
  ([]
   (request-camera nil))
  ([device-id]
   (let [constraints (if device-id
                       {:video {:deviceId {:exact device-id}
                                :width    {:ideal 1280}
                                :height   {:ideal 720}}
                        :audio false}
                       default-constraints)]
     (-> js/navigator.mediaDevices
         (.getUserMedia (clj->js constraints))))))

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
