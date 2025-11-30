(ns bashketball-game-ui.core
  "Application entry point.

  Initializes the React application and mounts it to the DOM."
  (:require
   [bashketball-game-ui.context.auth :as auth]
   [bashketball-game-ui.graphql.client :as gql-client]
   [bashketball-game-ui.views.deck-editor :as deck-editor]
   [bashketball-game-ui.views.decks :as decks]
   [bashketball-game-ui.views.games :as games]
   [bashketball-game-ui.views.home :as home]
   [bashketball-game-ui.views.layout :as layout]
   [bashketball-game-ui.views.lobby :as lobby]
   [bashketball-game-ui.views.login-callback :as login-callback]
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
                 ($ router/route {:path "auth/callback" :element ($ login-callback/login-callback-view)})
                 ;; Protected routes - require authentication
                 ($ router/route {:element ($ protected-route)}
                    ($ router/route {:path "lobby" :element ($ lobby/lobby-view)})
                    ($ router/route {:path "decks" :element ($ decks/decks-view)})
                    ($ router/route {:path "decks/:id" :element ($ deck-editor/deck-editor-view)})
                    ($ router/route {:path "games" :element ($ games/games-view)}))))))))

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
