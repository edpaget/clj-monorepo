(ns bashketball-editor-ui.core
  "Application entry point.

  Initializes the React application and mounts it to the DOM."
  (:require
   [bashketball-editor-ui.context.auth :as auth]
   [bashketball-editor-ui.graphql.client :as gql-client]
   [bashketball-editor-ui.router :as router]
   [bashketball-editor-ui.views.home :as home]
   [bashketball-editor-ui.views.layout :as layout]
   [goog.object :as obj]
   [uix.core :refer [$ defui]]
   [uix.dom]))

(extend-type object
  ILookup
  (-lookup ([o k] (obj/get o (name k)))
    ([o k not-found] (obj/get o (name k) not-found))))

(defonce root (atom nil))

(defui app
  "Root application component with routing."
  []
  ($ gql-client/apollo-provider {:client gql-client/client}
     ($ auth/auth-provider
        ($ router/browser-router
           ($ router/routes
              ($ router/route {:path "/" :element ($ layout/layout)}
                 ($ router/route {:index true :element ($ home/home-view)})
                 ($ router/route {:path "sets/:set-slug" :element ($ home/home-view)})))))))

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
