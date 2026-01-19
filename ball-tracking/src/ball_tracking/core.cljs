(ns ball-tracking.core
  "Entry point for ball tracking application."
  (:require [ball-tracking.components.app :as app]
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
