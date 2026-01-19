(ns ball-tracking.components.app
  "Main UIx application component for ball tracking."
  (:require [ball-tracking.camera :as camera]
            [ball-tracking.controller :as controller]
            [ball-tracking.state :refer [app-state]]
            [ball-tracking.yolo :as yolo]
            [uix.core :refer [defui $ use-state use-effect use-ref]]))

(defui loading-spinner []
  ($ :div {:class "flex flex-col items-center gap-4"}
     ($ :div {:class "animate-spin w-12 h-12 border-4 border-cyan-400 border-t-transparent rounded-full"})
     ($ :p {:class "text-gray-400"} "Loading YOLO model (~20MB)...")))

(defui error-message [{:keys [message]}]
  ($ :div {:class "bg-red-900/50 border border-red-500 rounded-lg p-4 max-w-md"}
     ($ :p {:class "text-red-400"} message)))

(defui stats-overlay [{:keys [fps tracks]}]
  ($ :div {:class "absolute top-4 left-4 bg-black/70 text-white px-3 py-2 rounded-lg font-mono text-sm"}
     ($ :div "FPS: " ($ :span {:class "text-cyan-400"} fps))
     ($ :div "Tracks: " ($ :span {:class "text-green-400"} (count tracks)))))

(defui camera-selector [{:keys [cameras selected on-change disabled?]}]
  ($ :div {:class "flex items-center gap-3"}
     ($ :label {:class "text-gray-400 text-sm"} "Camera:")
     ($ :select
        {:class    "bg-gray-800 border border-gray-600 text-white rounded-lg px-3 py-2 text-sm focus:border-cyan-400 focus:outline-none disabled:opacity-50"
         :value    (or selected "")
         :disabled disabled?
         :on-change (fn [e] (on-change (.-value (.-target e))))}
        ($ :option {:value ""} "Default camera")
        (for [{:keys [device-id label]} cameras]
          ($ :option {:key device-id :value device-id} label)))))

(defui control-button [{:keys [running? on-start on-stop]}]
  (if running?
    ($ :button {:class "px-6 py-3 bg-red-600 hover:bg-red-700 text-white rounded-lg font-semibold transition-colors flex items-center gap-2"
                :on-click on-stop}
       ($ :span {:class "w-4 h-4 bg-white rounded-sm"})
       "Stop")
    ($ :button {:class "px-6 py-3 bg-green-600 hover:bg-green-700 text-white rounded-lg font-semibold transition-colors flex items-center gap-2"
                :on-click on-start}
       ($ :span {:class "w-0 h-0 border-l-[16px] border-l-white border-y-[8px] border-y-transparent"})
       "Start Tracking")))

(defui video-canvas []
  (let [video-ref                       (use-ref nil)
        canvas-ref                      (use-ref nil)
        [running? set-running!]         (use-state false)
        [cameras set-cameras!]          (use-state [])
        [selected-camera set-selected!] (use-state nil)
        [state set-state!]              (use-state {:fps 0 :tracks {} :error nil})]

    ;; Enumerate available cameras on mount
    (use-effect
     (fn []
       (-> (camera/enumerate-video-devices)
           (.then (fn [devices]
                    (set-cameras! devices)
                    (swap! app-state assoc :available-cameras devices)))
           (.catch (fn [err]
                     (js/console.warn "Could not enumerate cameras:" err))))
       js/undefined)
     [])

    ;; Sync app-state to local state
    (use-effect
     (fn []
       (let [update-state (fn []
                            (set-state! {:fps    (:fps @app-state)
                                         :tracks (:tracks @app-state)
                                         :error  (:error @app-state)}))]
         (add-watch app-state :ui-sync (fn [_ _ _ _] (update-state)))
         #(remove-watch app-state :ui-sync)))
     [])

    ($ :div {:class "flex flex-col items-center gap-6"}
       ($ :div {:class "relative w-full max-w-4xl rounded-lg overflow-hidden shadow-2xl border border-gray-700"}
          ($ :video {:ref     video-ref
                     :class   "w-full bg-gray-800"
                     :autoPlay true
                     :playsInline true
                     :muted   true})
          ($ :canvas {:ref   canvas-ref
                      :class "absolute top-0 left-0 w-full h-full pointer-events-none"})
          (when running?
            ($ stats-overlay {:fps    (:fps state)
                              :tracks (:tracks state)}))
          (when-not running?
            ($ :div {:class "absolute inset-0 flex items-center justify-center bg-gray-800/80"}
               ($ :p {:class "text-gray-400 text-lg"} "Click \"Start Tracking\" to begin"))))

       (when-let [error (:error state)]
         ($ error-message {:message error}))

       ($ :div {:class "flex flex-wrap items-center justify-center gap-4"}
          (when (seq cameras)
            ($ camera-selector
               {:cameras   cameras
                :selected  selected-camera
                :disabled? running?
                :on-change (fn [device-id]
                             (set-selected! (when (seq device-id) device-id))
                             (swap! app-state assoc :selected-camera
                                    (when (seq device-id) device-id)))}))
          ($ control-button
             {:running?  running?
              :on-start  (fn []
                           (controller/start-tracking @video-ref @canvas-ref selected-camera)
                           (set-running! true))
              :on-stop   (fn []
                           (controller/stop-tracking @video-ref)
                           (set-running! false))})))))

(defui app []
  (let [[model-ready? set-model-ready!] (use-state false)
        [load-error set-load-error!]    (use-state nil)]

    (use-effect
     (fn []
       (-> (yolo/load-yolov8 "/model/model.json")
           (.then (fn [_]
                    (swap! app-state assoc :model-loaded? true)
                    (set-model-ready! true)))
           (.catch (fn [err]
                     (set-load-error! (str "Failed to load model: " (.-message err))))))
       js/undefined)
     [])

    ($ :div {:class "min-h-screen bg-gray-900 text-white p-8"}
       ($ :div {:class "max-w-5xl mx-auto"}
          ($ :h1 {:class "text-4xl font-bold text-center mb-2"}
             "Ball Tracking")
          ($ :p {:class "text-gray-400 text-center mb-8"}
             "Real-time object detection using YOLO")

          ($ :div {:class "flex justify-center"}
             (cond
               load-error
               ($ error-message {:message load-error})

               (not model-ready?)
               ($ loading-spinner)

               :else
               ($ video-canvas)))

          ($ :div {:class "mt-8 text-center text-gray-500 text-sm"}
             ($ :p "Point your camera at a ball (soccer, basketball, tennis, etc.)")
             ($ :p "YOLO detects 80 object classes including sports balls"))))))
