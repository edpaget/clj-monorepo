(ns bashketball-game-ui.views.games
  "Games history view.

  Shows user's past and active games with filtering options."
  (:require
   [bashketball-game-ui.components.game.game-list :refer [game-list]]
   [bashketball-game-ui.context.auth :refer [use-auth]]
   [bashketball-game-ui.hooks.use-games :refer [use-my-games]]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-state]]))

(def tabs
  "Filter tabs for game list."
  [{:id nil :label "All Games"}
   {:id "ACTIVE" :label "Active"}
   {:id "WAITING" :label "Waiting"}
   {:id "COMPLETED" :label "Completed"}])

(defui games-view
  "Games page component.

  Displays the user's game history with filtering options."
  []
  (let [{:keys [user]}                (use-auth)
        current-user-id               (:id user)
        [active-tab set-active-tab]   (use-state nil)
        {:keys [games loading error]} (use-my-games active-tab)
        navigate                      (router/use-navigate)]

    ($ :div {:class "space-y-6"}
       ($ :h1 {:class "text-2xl font-bold text-gray-900"} "My Games")

       ;; Tab Filter
       ($ :div {:class "flex gap-2 border-b border-gray-200 pb-2"}
          (for [{:keys [id label]} tabs]
            ($ button
               {:key (or id "all")
                :variant (if (= id active-tab) :default :ghost)
                :size :sm
                :on-click #(set-active-tab id)}
               label)))

       ;; Error state
       (when error
         ($ :div {:class "bg-red-50 border border-red-200 rounded-lg p-4 text-red-700"}
            "Failed to load games. Please try again."))

       ;; Games List
       ($ game-list
          {:games games
           :loading loading
           :current-user-id current-user-id
           :empty-message (case active-tab
                            nil "You haven't played any games yet."
                            "ACTIVE" "No active games."
                            "WAITING" "No waiting games."
                            "COMPLETED" "No completed games."
                            "No games found.")
           :on-resume #(navigate (str "/games/" (:id %)))
           :on-view #(navigate (str "/games/" (:id %)))}))))
