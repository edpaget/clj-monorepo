(ns bashketball-editor-ui.context.auth
  "Authentication context for the editor app.

  Wraps the configurable auth context from bashketball-ui with
  app-specific configuration."
  (:require
   [bashketball-editor-ui.config :as config]
   [bashketball-editor-ui.hooks.use-me :refer [use-me]]
   [bashketball-ui.context.auth :as auth]))

(def auth-provider
  "Auth provider configured for the editor app."
  (auth/create-auth-provider {:use-user-hook use-me}))

(def use-auth
  "Re-export use-auth from shared library."
  auth/use-auth)

(def logout!
  "Logout function configured for the editor app."
  (auth/create-logout-fn {:logout-url (str config/api-base-url "/auth/logout")}))
