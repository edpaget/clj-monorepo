(ns bashketball-game-ui.views.home
  "Home page view.

  Landing page for the Bashketball game application."
  (:require
   [bashketball-game-ui.config :as config]
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui]]))

(defui home-view
  "Home page component."
  []
  ($ :div {:class "min-h-screen bg-gray-50"}
     ($ :div {:class "container mx-auto px-4 py-16"}
        ($ :div {:class "text-center"}
           ($ :h1 {:class "text-4xl font-bold text-gray-900 mb-4"}
              config/app-name)
           ($ :p {:class "text-lg text-gray-600 mb-8"}
              "Build decks. Play games. Compete.")
           ($ :div {:class "flex justify-center gap-4"}
              ($ :a {:href config/google-login-url}
                 ($ button {:variant :default :size :lg}
                    "Sign in with Google"))
              ($ button {:variant :outline :size :lg}
                 "Learn More"))))))
