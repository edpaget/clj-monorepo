(ns ball-tracking.state
  "Shared application state for ball tracking.")

(def default-config
  "Default configuration values for tracking parameters."
  {:score-threshold    0.25
   :max-distance       100
   :track-timeout      500
   :velocity-threshold 15
   :enabled-classes    #{"sports ball" "frisbee" "bottle" "cup" "cell phone"
                         "remote" "scissors" "toothbrush" "apple" "orange"
                         "banana" "teddy bear" "clock" "vase" "book"}})

(defonce app-state
  (atom {:running?           false
         :tracks             {}
         :fps                0
         :frame-count        0
         :last-fps-time      0
         :model-loaded?      false
         :error              nil
         :collisions         []
         :collision-callback nil
         :available-cameras  []
         :selected-camera    nil
         :config             default-config
         :show-settings?     false}))
