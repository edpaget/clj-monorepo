(ns ball-tracking.core
  "Entry point for ball tracking application."
  (:require [ball-tracking.components.app :as app]
            [ball-tracking.state :refer [app-state]]
            [uix.core :refer [$]]
            [uix.dom]))

(defonce root (atom nil))

(defn render!
  "Renders the application to the root."
  []
  (uix.dom/render-root ($ app/app) @root))

(defn ^:export init!
  "Initializes and mounts the application.
   Called from index.html on page load."
  []
  (reset! root (uix.dom/create-root (js/document.getElementById "root")))
  (render!))

(defn ^:dev/after-load reload!
  "Re-renders after hot reload."
  []
  (render!))

(defn ^:export on-collision
  "Registers a callback for collision events.
   Callback receives collision object: {moving, stationary, point, timestamp}"
  [callback]
  (swap! app-state assoc :collision-callback callback))

(defn ^:export remove-collision-callback
  "Removes the collision callback."
  []
  (swap! app-state dissoc :collision-callback))
