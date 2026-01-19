(ns ball-tracking.state
  "Shared application state for ball tracking.")

(defonce app-state
  (atom {:running?           false
         :tracks             {}
         :fps                0
         :frame-count        0
         :last-fps-time      0
         :model-loaded?      false
         :error              nil
         :collisions         []
         :collision-callback nil}))
