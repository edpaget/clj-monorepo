(ns bashketball-editor-ui.core
  "Application entry point.

  Initializes the React application and mounts it to the DOM."
  (:require
   [bashketball-editor-ui.views.home :as home]
   [uix.core :refer [$ defui]]
   [uix.dom]))

(defui app
  "Root application component."
  []
  ($ home/home-view))

(defn ^:export init!
  "Initializes and mounts the application.

  Called by shadow-cljs on page load."
  []
  (let [root (uix.dom/create-root (js/document.getElementById "app"))]
    (uix.dom/render-root ($ app) root)))
