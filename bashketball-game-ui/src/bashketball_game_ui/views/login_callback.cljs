(ns bashketball-game-ui.views.login-callback
  "OAuth callback handler.

  Handles redirect from Google OAuth flow. The API sets the session cookie
  before redirecting here, so we just need to trigger a refetch and redirect
  to the dashboard."
  (:require
   [bashketball-game-ui.context.auth :as auth]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-effect]]))

(defui login-callback-view
  "OAuth callback component.

  When mounted, refetches the auth state and redirects to the lobby."
  []
  (let [{:keys [loading? logged-in? refetch]} (auth/use-auth)
        navigate                              (router/use-navigate)]
    (use-effect
     (fn []
       (refetch)
       js/undefined)
     [refetch])
    (use-effect
     (fn []
       (when (and (not loading?) logged-in?)
         (navigate "/lobby" #js {:replace true}))
       (when (and (not loading?) (not logged-in?))
         (navigate "/" #js {:replace true}))
       js/undefined)
     [loading? logged-in? navigate])
    ($ :div {:class "flex items-center justify-center min-h-screen"}
       ($ :div {:class "text-gray-500"} "Signing in..."))))
