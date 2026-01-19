(ns ball-tracking.components.app
  "Main UIx application component for ball tracking."
  (:require [ball-tracking.camera :as camera]
            [ball-tracking.controller :as controller]
            [ball-tracking.recorder :as recorder]
            [ball-tracking.state :refer [app-state default-config]]
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

(defui settings-button [{:keys [on-click]}]
  ($ :button {:class "px-4 py-3 bg-gray-700 hover:bg-gray-600 text-white rounded-lg font-semibold transition-colors"
              :on-click on-click}
     "Settings"))

(defui record-button [{:keys [recording? disabled? on-start on-stop]}]
  (if recording?
    ($ :button {:class "px-4 py-3 bg-red-600 hover:bg-red-700 text-white rounded-lg font-semibold transition-colors flex items-center gap-2 animate-pulse"
                :on-click on-stop}
       ($ :span {:class "w-3 h-3 bg-white rounded-full"})
       "Stop Rec")
    ($ :button {:class    "px-4 py-3 bg-gray-700 hover:bg-gray-600 text-white rounded-lg font-semibold transition-colors flex items-center gap-2 disabled:opacity-50"
                :disabled disabled?
                :on-click on-start}
       ($ :span {:class "w-3 h-3 bg-red-500 rounded-full"})
       "Record")))

(defn- format-timestamp [ts]
  (let [date (js/Date. ts)]
    (str (.toLocaleDateString date) " " (.toLocaleTimeString date))))

(defui clip-card [{:keys [clip on-delete]}]
  ($ :div {:class "bg-gray-800 rounded-lg overflow-hidden border border-gray-700"}
     ($ :video {:class    "w-full h-32 object-cover bg-black"
                :src      (:url clip)
                :controls true
                :preload  "metadata"})
     ($ :div {:class "p-2 flex justify-between items-center"}
        ($ :span {:class "text-xs text-gray-400"}
           (format-timestamp (:timestamp clip)))
        ($ :div {:class "flex gap-2"}
           ($ :button {:class    "text-cyan-400 hover:text-cyan-300 text-sm"
                       :on-click #(recorder/download-clip clip)}
              "Download")
           ($ :button {:class    "text-red-400 hover:text-red-300 text-sm"
                       :on-click #(on-delete (:id clip))}
              "Delete")))))

(defui clips-gallery [{:keys [clips on-delete on-clear]}]
  (when (seq clips)
    ($ :div {:class "mt-6 w-full max-w-4xl"}
       ($ :div {:class "flex justify-between items-center mb-3"}
          ($ :h3 {:class "text-lg font-semibold text-white"}
             (str "Saved Clips (" (count clips) ")"))
          ($ :button {:class    "text-sm text-red-400 hover:text-red-300"
                      :on-click on-clear}
             "Clear All"))
       ($ :div {:class "grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3"}
          (for [clip (reverse clips)]
            ($ clip-card {:key       (:id clip)
                          :clip      clip
                          :on-delete on-delete}))))))

(defui range-slider [{:keys [label value min max step on-change format-fn]}]
  ($ :div {:class "flex flex-col gap-1"}
     ($ :div {:class "flex justify-between text-sm"}
        ($ :span {:class "text-gray-400"} label)
        ($ :span {:class "text-cyan-400 font-mono"} ((or format-fn str) value)))
     ($ :input {:type      "range"
                :class     "w-full accent-cyan-400"
                :value     value
                :min       min
                :max       max
                :step      step
                :on-change (fn [e] (on-change (js/parseFloat (.-value (.-target e)))))})))

(defui class-checkbox [{:keys [label checked? on-change]}]
  ($ :label {:class "flex items-center gap-2 cursor-pointer hover:bg-gray-700/50 px-2 py-1 rounded"}
     ($ :input {:type      "checkbox"
                :class     "accent-cyan-400 w-4 h-4"
                :checked   checked?
                :on-change (fn [_] (on-change))})
     ($ :span {:class "text-sm text-gray-300"} label)))

(def class-categories
  "Object classes grouped by category for easier selection."
  {"Sports & Toys"   ["sports ball" "frisbee" "kite" "baseball bat" "baseball glove"
                      "skateboard" "surfboard" "tennis racket" "skis" "snowboard"]
   "Food"            ["banana" "apple" "sandwich" "orange" "broccoli" "carrot"
                      "hot dog" "pizza" "donut" "cake"]
   "Kitchen"         ["bottle" "wine glass" "cup" "fork" "knife" "spoon" "bowl"]
   "Electronics"     ["tv" "laptop" "mouse" "remote" "keyboard" "cell phone"]
   "Personal Items"  ["backpack" "umbrella" "handbag" "tie" "suitcase"]
   "Household"       ["chair" "couch" "potted plant" "bed" "dining table" "toilet"
                      "book" "clock" "vase" "scissors" "teddy bear" "hair drier" "toothbrush"]
   "Appliances"      ["microwave" "oven" "toaster" "sink" "refrigerator"]
   "Vehicles"        ["bicycle" "car" "motorcycle" "airplane" "bus" "train" "truck" "boat"]
   "Animals"         ["bird" "cat" "dog" "horse" "sheep" "cow" "elephant" "bear" "zebra" "giraffe"]
   "Street"          ["traffic light" "fire hydrant" "stop sign" "parking meter" "bench"]
   "People"          ["person"]})

(defui settings-panel [{:keys [config on-change on-close]}]
  (let [[expanded-cat set-expanded!] (use-state #{"Sports & Toys"})]
    ($ :div {:class "fixed inset-0 bg-black/70 flex items-center justify-center z-50"
             :on-click (fn [e]
                         (when (= (.-target e) (.-currentTarget e))
                           (on-close)))}
       ($ :div {:class "bg-gray-800 rounded-xl p-6 max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto"}
          ($ :div {:class "flex justify-between items-center mb-6"}
             ($ :h2 {:class "text-xl font-bold text-white"} "Tracking Settings")
             ($ :button {:class "text-gray-400 hover:text-white text-2xl"
                         :on-click on-close}
                "×"))

          ;; Numeric sliders
          ($ :div {:class "space-y-4 mb-6"}
             ($ :h3 {:class "text-sm font-semibold text-gray-500 uppercase tracking-wide"} "Detection")
             ($ range-slider
                {:label     "Confidence Threshold"
                 :value     (:score-threshold config)
                 :min       0.1
                 :max       0.9
                 :step      0.05
                 :format-fn #(.toFixed % 2)
                 :on-change #(on-change :score-threshold %)})

             ($ :h3 {:class "text-sm font-semibold text-gray-500 uppercase tracking-wide mt-6"} "Tracking")
             ($ range-slider
                {:label     "Max Match Distance (px)"
                 :value     (:max-distance config)
                 :min       20
                 :max       300
                 :step      10
                 :on-change #(on-change :max-distance %)})
             ($ range-slider
                {:label     "Track Timeout (ms)"
                 :value     (:track-timeout config)
                 :min       100
                 :max       2000
                 :step      100
                 :on-change #(on-change :track-timeout %)})

             ($ :h3 {:class "text-sm font-semibold text-gray-500 uppercase tracking-wide mt-6"} "Collision")
             ($ range-slider
                {:label     "Velocity Threshold (px/s)"
                 :value     (:velocity-threshold config)
                 :min       5
                 :max       100
                 :step      5
                 :format-fn #(str (int %) " px/s")
                 :on-change #(on-change :velocity-threshold %)}))

          ;; Class selection
          ($ :div {:class "mb-6"}
             ($ :div {:class "flex justify-between items-center mb-3"}
                ($ :h3 {:class "text-sm font-semibold text-gray-500 uppercase tracking-wide"}
                   "Object Classes")
                ($ :div {:class "flex gap-2"}
                   ($ :button {:class "text-xs text-cyan-400 hover:text-cyan-300"
                               :on-click #(on-change :enabled-classes (set yolo/coco-labels))}
                      "Select All")
                   ($ :button {:class "text-xs text-cyan-400 hover:text-cyan-300"
                               :on-click #(on-change :enabled-classes #{})}
                      "Clear All")))

             ($ :div {:class "space-y-2"}
                (for [[category classes] (sort-by first class-categories)]
                  ($ :div {:key category :class "border border-gray-700 rounded-lg overflow-hidden"}
                     ($ :button
                        {:class    "w-full px-3 py-2 bg-gray-700/50 flex justify-between items-center hover:bg-gray-700"
                         :on-click #(set-expanded! (if (contains? expanded-cat category)
                                                     (disj expanded-cat category)
                                                     (conj expanded-cat category)))}
                        ($ :span {:class "text-sm font-medium text-gray-300"} category)
                        ($ :span {:class "text-gray-500"}
                           (str (count (filter #(contains? (:enabled-classes config) %) classes))
                                "/" (count classes))
                           (if (contains? expanded-cat category) " ▼" " ▶")))
                     (when (contains? expanded-cat category)
                       ($ :div {:class "p-2 grid grid-cols-2 sm:grid-cols-3 gap-1 bg-gray-800"}
                          (for [cls (sort classes)]
                            ($ class-checkbox
                               {:key       cls
                                :label     cls
                                :checked?  (contains? (:enabled-classes config) cls)
                                :on-change #(on-change :enabled-classes
                                                       (if (contains? (:enabled-classes config) cls)
                                                         (disj (:enabled-classes config) cls)
                                                         (conj (:enabled-classes config) cls)))}))))))))

          ;; Reset button
          ($ :div {:class "flex justify-end"}
             ($ :button {:class    "px-4 py-2 text-sm text-gray-400 hover:text-white"
                         :on-click #(on-change :reset default-config)}
                "Reset to Defaults"))))))

(defui video-canvas []
  (let [video-ref                           (use-ref nil)
        canvas-ref                          (use-ref nil)
        [running? set-running!]             (use-state false)
        [recording? set-recording!]         (use-state false)
        [clips set-clips!]                  (use-state [])
        [cameras set-cameras!]              (use-state [])
        [selected-camera set-selected!]     (use-state nil)
        [show-settings? set-show-settings!] (use-state false)
        [config set-config!]                (use-state (:config @app-state))]

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
                            (set-recording! (:recording? @app-state))
                            (set-clips! (:clips @app-state)))]
         (add-watch app-state :recording-sync (fn [_ _ _ _] (update-state)))
         #(remove-watch app-state :recording-sync)))
     [])

    ($ :<>
       ($ :div {:class "flex flex-col items-center gap-6"}
          ($ :div {:class (str "relative w-full max-w-4xl rounded-lg overflow-hidden shadow-2xl border "
                               (if recording? "border-red-500 border-2" "border-gray-700"))}
             ($ :video {:ref       video-ref
                        :class     "w-full bg-gray-800"
                        :autoPlay  true
                        :playsInline true
                        :muted     true})
             ($ :canvas {:ref   canvas-ref
                         :class "absolute top-0 left-0 w-full h-full pointer-events-none"})
             (when running?
               ($ :div {:class "absolute top-4 left-4 bg-black/70 text-white px-3 py-2 rounded-lg font-mono text-sm"}
                  ($ :div "FPS: " ($ :span {:class "text-cyan-400"} (:fps @app-state)))
                  ($ :div "Tracks: " ($ :span {:class "text-green-400"} (count (:tracks @app-state))))))
             (when recording?
               ($ :div {:class "absolute top-4 right-4 bg-red-600 text-white px-3 py-1 rounded-full text-sm font-semibold flex items-center gap-2"}
                  ($ :span {:class "w-2 h-2 bg-white rounded-full animate-pulse"})
                  "REC"))
             (when-not running?
               ($ :div {:class "absolute inset-0 flex items-center justify-center bg-gray-800/80"}
                  ($ :p {:class "text-gray-400 text-lg"} "Click \"Start Tracking\" to begin"))))

          (when-let [error (:error @app-state)]
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
                              (when recording?
                                (recorder/stop-recording)
                                (set-recording! false))
                              (controller/stop-tracking @video-ref)
                              (set-running! false))})
             ($ record-button
                {:recording? recording?
                 :disabled?  (not running?)
                 :on-start   #(recorder/start-recording @video-ref @canvas-ref)
                 :on-stop    #(recorder/stop-recording)})
             ($ settings-button {:on-click #(set-show-settings! true)}))

          ($ clips-gallery
             {:clips     clips
              :on-delete recorder/delete-clip
              :on-clear  recorder/clear-all-clips}))

       (when show-settings?
         ($ settings-panel
            {:config    config
             :on-close  #(set-show-settings! false)
             :on-change (fn [key value]
                          (if (= key :reset)
                            (do (set-config! value)
                                (swap! app-state assoc :config value))
                            (let [new-config (assoc config key value)]
                              (set-config! new-config)
                              (swap! app-state assoc :config new-config))))})))))

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
