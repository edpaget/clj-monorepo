(ns bashketball-editor-ui.components.protected-route
  "Protected route wrapper that requires authentication.

  Wraps routes that should only be accessible to authenticated users.
  Shows a loading state while checking auth, then either renders children
  or redirects to the home page with a login prompt."
  (:require
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.config :as config]
   [bashketball-editor-ui.context.auth :refer [use-auth]]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui]]))

(defui protected-route
  "Wrapper component that requires authentication.

  If the user is authenticated, renders children via Outlet.
  If loading, shows a loading spinner.
  If not authenticated, redirects to home or shows login prompt."
  [{:keys [redirect-to]
    :or {redirect-to "/"}}]
  (let [{:keys [loading? logged-in?]} (use-auth)]
    (cond
      loading?
      ($ :div {:class "flex items-center justify-center min-h-[50vh]"}
         ($ :div {:class "text-gray-500"} "Loading..."))

      logged-in?
      ($ router/outlet)

      :else
      ($ router/navigate {:to redirect-to :replace true}))))

(defui require-auth
  "Shows login prompt instead of redirecting.

  Useful when you want to show the page structure but prompt for login
  rather than redirecting away entirely."
  [{:keys [children]}]
  (let [{:keys [loading? logged-in?]} (use-auth)]
    (cond
      loading?
      ($ :div {:class "flex items-center justify-center min-h-[50vh]"}
         ($ :div {:class "text-gray-500"} "Loading..."))

      logged-in?
      children

      :else
      ($ :div {:class "flex flex-col items-center justify-center min-h-[50vh] gap-4"}
         ($ :p {:class "text-gray-600"} "Please log in to access this feature.")
         ($ :a {:href config/github-login-url}
            ($ button {:variant :default} "Login with GitHub"))))))
