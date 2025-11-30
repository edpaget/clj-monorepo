(ns bashketball-game-ui.context.auth
  "Authentication context for the game app.

  Wraps the configurable auth context from bashketball-ui with
  app-specific configuration for Google OAuth."
  (:require
   [bashketball-game-ui.config :as config]
   [bashketball-game-ui.hooks.use-me :refer [use-me]]
   [bashketball-ui.context.auth :as auth]
   [uix.core :refer [$ defui]]))

(defui auth-provider
  "Auth provider configured for the game app."
  [{:keys [children]}]
  ($ auth/auth-provider {:use-user-hook use-me}
     children))

(def use-auth
  "Re-export use-auth from shared library."
  auth/use-auth)

(def logout!
  "Logout function configured for the game app."
  (auth/create-logout-fn {:logout-url (str config/api-base-url "/auth/logout")}))
