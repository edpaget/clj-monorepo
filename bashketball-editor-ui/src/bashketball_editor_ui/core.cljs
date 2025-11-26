(ns bashketball-editor-ui.core
  "Application entry point.

  Initializes the React application and mounts it to the DOM."
  (:require
   [bashketball-editor-ui.views.home :as home]
   [uix.core :refer [$ defui]]
   [uix.dom]))

(defonce root (atom nil))

(defui app
  "Root application component."
  []
  ($ home/home-view))

(defn render!
  "Renders the application to the root."
  []
  (uix.dom/render-root ($ app) @root))

(defn ^:export init!
  "Initializes and mounts the application.

  Called by shadow-cljs on page load."
  []
  (reset! root (uix.dom/create-root (js/document.getElementById "app")))
  (render!))

(defn ^:dev/after-load reload!
  "Re-renders after hot reload."
  []
  (render!))
