(ns bashketball-game-ui.views.layout
  "Application layout with header and navigation."
  (:require
   [bashketball-game-ui.config :as config]
   [bashketball-game-ui.context.auth :as auth]
   [bashketball-ui.components.avatar :refer [avatar]]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui]]))

(defui user-menu
  "User menu shown when logged in."
  [{:keys [user on-logout]}]
  ($ :div {:class "flex items-center gap-4"}
     ($ avatar {:src (config/resolve-api-url (:avatar-url user))
                :name (:name user)
                :email (:email user)
                :size :md})
     ($ :span {:class "text-sm text-gray-700"}
        (or (:name user) (:email user)))
     ($ button {:variant :outline
                :size :sm
                :on-click on-logout}
        "Logout")))

(defui login-button
  "Login button shown when not logged in."
  []
  ($ :a {:href config/google-login-url}
     ($ button {:variant :default} "Sign in with Google")))

(defn- nav-link-class
  "Returns class string based on active state."
  [^js props]
  (if (.-isActive props)
    "text-blue-600 font-medium"
    "text-gray-600 hover:text-gray-900"))

(defui nav-links
  "Navigation links for authenticated users."
  []
  ($ :nav {:class "flex items-center gap-4"}
     ($ router/nav-link {:to "/lobby" :class nav-link-class} "Lobby")
     ($ router/nav-link {:to "/decks" :class nav-link-class} "My Decks")
     ($ router/nav-link {:to "/games" :class nav-link-class} "My Games")
     ($ router/nav-link {:to "/rules/introduction" :class nav-link-class} "Rules")))

(defui header
  "Application header with navigation and login."
  []
  (let [{:keys [user loading? logged-in? refetch]} (auth/use-auth)]
    ($ :header {:class "bg-white shadow"}
       ($ :div {:class "max-w-7xl mx-auto py-4 px-4 flex justify-between items-center"}
          ($ :div {:class "flex items-center gap-8"}
             ($ router/link {:to "/"}
                ($ :h1 {:class "text-2xl font-bold text-gray-900"}
                   config/app-name))
             (when logged-in?
               ($ nav-links)))
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
