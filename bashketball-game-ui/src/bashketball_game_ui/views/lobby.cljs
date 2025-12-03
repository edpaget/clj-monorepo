(ns bashketball-game-ui.views.lobby
  "Game lobby view.

  Shows available games to join and allows creating new games."
  (:require
   ["lucide-react" :refer [Plus RefreshCw]]
   [bashketball-game-ui.components.game.game-list :refer [game-list]]
   [bashketball-game-ui.components.game.join-dialog :refer [join-dialog]]
   [bashketball-game-ui.context.auth :refer [use-auth]]
   [bashketball-game-ui.hooks.use-games :refer [use-available-games-live use-my-games]]
   [bashketball-game-ui.hooks.use-lobby :refer [use-create-game use-join-game use-leave-game]]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-state]]))

(defui lobby-view
  "Lobby page component.

  Displays available games to join and active games to resume."
  []
  (let [{:keys [user]}                                    (use-auth)
        current-user-id                                   (:id user)
        {available-games :games :keys [loading refetch]}  (use-available-games-live)
        {:keys [games active-games]}                      (use-my-games "ACTIVE")
        [create-game {:keys [loading] :as create-result}] (use-create-game)
        create-loading                                    loading
        [join-game {:keys [loading] :as join-result}]     (use-join-game)
        join-loading                                      loading
        [leave-game _]                                    (use-leave-game)
        navigate                                          (router/use-navigate)

        [show-create? set-show-create]                    (use-state false)
        [game-to-join set-game-to-join]                   (use-state nil)]

    ($ :div {:class "space-y-8"}
       ;; Header
       ($ :div {:class "flex justify-between items-center"}
          ($ :h1 {:class "text-2xl font-bold text-gray-900"} "Game Lobby")
          ($ :div {:class "flex gap-2"}
             ($ button {:variant :outline
                        :size :sm
                        :on-click #(refetch)}
                ($ RefreshCw {:className "w-4 h-4"}))
             ($ button {:on-click #(set-show-create true)}
                ($ Plus {:className "w-4 h-4 mr-2"})
                "Create Game")))

       ;; Active Games Section
       (when (seq active-games)
         ($ :section
            ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-4"}
               "Your Active Games")
            ($ game-list
               {:games active-games
                :current-user-id current-user-id
                :on-resume #(navigate (str "/games/" (:id %)))})))

       ;; Available Games Section
       ($ :section
          ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-4"}
             "Available Games")
          ($ game-list
             {:games available-games
              :loading loading
              :current-user-id current-user-id
              :empty-message "No games available. Create a game to get started!"
              :show-join? true
              :on-join #(set-game-to-join %)}))

       ;; Create Game Dialog
       ($ join-dialog
          {:open? show-create?
           :title "Create New Game"
           :submit-label "Create Game"
           :on-close #(set-show-create false)
           :submitting? create-loading
           :on-submit (fn [deck-id]
                        (-> (create-game deck-id)
                            (.then (fn [result]
                                     (set-show-create false)
                                     (when-let [game-id (some-> result :data :create-game :id)]
                                       (navigate (str "/games/" game-id)))))))})

       ;; Join Game Dialog
       ($ join-dialog
          {:open? (some? game-to-join)
           :game game-to-join
           :title "Join Game"
           :submit-label "Join Game"
           :on-close #(set-game-to-join nil)
           :submitting? join-loading
           :on-submit (fn [deck-id]
                        (-> (join-game {:game-id (str (:id game-to-join))
                                        :deck-id deck-id})
                            (.then (fn [result]
                                     (set-game-to-join nil)
                                     (when-let [game-id (some-> result :data :join-game :id)]
                                       (navigate (str "/games/" game-id)))))))}))))
