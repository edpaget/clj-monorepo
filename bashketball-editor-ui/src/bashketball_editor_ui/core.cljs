(ns bashketball-editor-ui.core
  "Application entry point.

  Initializes the React application and mounts it to the DOM."
  (:require
   [bashketball-editor-ui.context.auth :as auth]
   [bashketball-editor-ui.graphql.client :as gql-client]
   [bashketball-editor-ui.views.card-editor :as card-editor]
   [bashketball-editor-ui.views.card-view :as card-view]
   [bashketball-editor-ui.views.commit :as commit]
   [bashketball-editor-ui.views.home :as home]
   [bashketball-editor-ui.views.layout :as layout]
   [bashketball-editor-ui.views.set-editor :as set-editor]
   [bashketball-ui.components.protected-route :refer [protected-route]]
   [bashketball-ui.core]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui]]
   [uix.dom]))

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
                 ($ router/route {:path "cards/:setSlug/:slug" :element ($ card-view/card-view)})
                 ;; Protected routes - require authentication
                 ($ router/route {:element ($ protected-route)}
                    ($ router/route {:path "commit" :element ($ commit/commit-view)})
                    ($ router/route {:path "sets/new" :element ($ set-editor/set-editor-view)})
                    ($ router/route {:path "cards/new" :element ($ card-editor/card-editor-view)})
                    ($ router/route {:path "cards/:setSlug/:slug/edit" :element ($ card-editor/card-editor-view)}))))))))

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
