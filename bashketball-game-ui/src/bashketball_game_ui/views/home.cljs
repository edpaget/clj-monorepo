(ns bashketball-game-ui.views.home
  "Home page view.

  Landing page for the Bashketball game application."
  (:require
   [bashketball-game-ui.config :as config]
   [bashketball-game-ui.context.auth :as auth]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui]]))

(defui guest-home
  "Home content for unauthenticated users."
  []
  ($ :div {:class "text-center py-16"}
     ($ :h2 {:class "text-4xl font-bold text-gray-900 mb-4"}
        "Welcome to " config/app-name)
     ($ :p {:class "text-lg text-gray-600 mb-8 max-w-2xl mx-auto"}
        "Build decks, challenge opponents, and compete in the ultimate trading card basketball game.")
     ($ :div {:class "flex justify-center gap-4"}
        ($ :a {:href config/google-login-url}
           ($ button {:variant :default :size :lg}
              "Sign in with Google"))
        ($ button {:variant :outline :size :lg}
           "Learn More"))))

(defui authenticated-home
  "Home content for authenticated users."
  [{:keys [user]}]
  ($ :div {:class "space-y-8"}
     ($ :div {:class "text-center py-8"}
        ($ :h2 {:class "text-3xl font-bold text-gray-900 mb-2"}
           "Welcome back, " (or (:name user) (:email user)) "!")
        ($ :p {:class "text-gray-600"}
           "Ready to play some Bashketball?"))
     ($ :div {:class "grid grid-cols-1 md:grid-cols-3 gap-6"}
        ($ :div {:class "bg-white rounded-lg shadow p-6 text-center"}
           ($ :h3 {:class "text-xl font-semibold text-gray-900 mb-2"} "Find a Game")
           ($ :p {:class "text-gray-600 mb-4"} "Join an existing game or create a new one.")
           ($ router/link {:to "/lobby"}
              ($ button {:variant :default :class "w-full"} "Go to Lobby")))
        ($ :div {:class "bg-white rounded-lg shadow p-6 text-center"}
           ($ :h3 {:class "text-xl font-semibold text-gray-900 mb-2"} "Build Decks")
           ($ :p {:class "text-gray-600 mb-4"} "Create and customize your card decks.")
           ($ router/link {:to "/decks"}
              ($ button {:variant :outline :class "w-full"} "My Decks")))
        ($ :div {:class "bg-white rounded-lg shadow p-6 text-center"}
           ($ :h3 {:class "text-xl font-semibold text-gray-900 mb-2"} "Game History")
           ($ :p {:class "text-gray-600 mb-4"} "View your past games and statistics.")
           ($ router/link {:to "/games"}
              ($ button {:variant :outline :class "w-full"} "My Games"))))))

(defui home-view
  "Home page component.

  Shows different content based on authentication state."
  []
  (let [{:keys [user loading? logged-in?]} (auth/use-auth)]
    (cond
      loading?
      ($ :div {:class "flex items-center justify-center min-h-[50vh]"}
         ($ :div {:class "text-gray-500"} "Loading..."))

      logged-in?
      ($ authenticated-home {:user user})

      :else
      ($ guest-home))))
