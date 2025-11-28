(ns bashketball-editor-ui.views.layout
  "Application layout with header and navigation."
  (:require
   [bashketball-editor-ui.components.git-status :refer [git-status]]
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [bashketball-editor-ui.config :as config]
   [bashketball-editor-ui.context.auth :as auth]
   [bashketball-editor-ui.router :as router]
   [uix.core :refer [$ defui]]))

(defui user-menu
  "User menu shown when logged in."
  [{:keys [user on-logout]}]
  ($ :div {:class "flex items-center gap-4"}
     ;; Git status with commit/push/pull workflow
     ($ git-status)
     ;; Separator
     ($ :div {:class "h-6 w-px bg-gray-300"})
     ;; User avatar and logout
     ($ :div {:class "flex items-center gap-2"}
        (when-let [avatar-url (:avatar-url user)]
          ($ :img {:src avatar-url
                   :alt (or (:name user) (:github-login user))
                   :class "w-8 h-8 rounded-full"}))
        ($ button {:variant :outline
                   :on-click on-logout}
           "Logout"))))

(defui login-button
  "Login button shown when not logged in."
  []
  ($ :a {:href config/github-login-url}
     ($ button {:variant :outline} "Login with GitHub")))

(defui header
  "Application header with navigation and login."
  []
  (let [{:keys [user loading? logged-in? refetch]} (auth/use-auth)]
    ($ :header {:class "bg-white shadow"}
       ($ :div {:class "max-w-7xl mx-auto py-6 px-4 flex justify-between items-center"}
          ($ router/link {:to "/"}
             ($ :h1 {:class "text-3xl font-bold text-gray-900"}
                config/app-name))
          (cond
            loading?
            ($ :span {:class "text-sm text-gray-500"} "Loading...")

            logged-in?
            ($ user-menu {:user user
                          :on-logout #(auth/logout! refetch)})

            :else
            ($ login-button))))))

(defui layout
  "Root layout with header and outlet for child routes."
  []
  ($ :div {:class "min-h-screen bg-gray-50"}
     ($ header)
     ($ :main {:class "max-w-7xl mx-auto py-6 px-4"}
        ($ router/outlet))))
