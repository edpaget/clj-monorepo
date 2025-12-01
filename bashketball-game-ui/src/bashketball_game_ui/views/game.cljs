(ns bashketball-game-ui.views.game
  "Game page view.

  Displays the active game with board, players, and actions.
  Uses the game context provider for real-time state updates."
  (:require
   [bashketball-game-ui.components.game.player-info :refer [player-info]]
   [bashketball-game-ui.components.game.turn-indicator :refer [turn-indicator]]
   [bashketball-game-ui.context.auth :refer [use-auth]]
   [bashketball-game-ui.context.game :refer [game-provider use-game-context]]
   [bashketball-ui.components.loading :refer [spinner]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui]]))

(defui game-content
  "Inner game content that consumes the game context."
  []
  (let [{:keys [game game-state my-team is-my-turn loading error connected]}
        (use-game-context)

        score                                                                (:score game-state)
        opponent-team                                                        (if (= my-team :home) :away :home)
        my-player                                                            (get-in game-state [:players my-team])
        opponent                                                             (get-in game-state [:players opponent-team])
        active-player                                                        (:active-player game-state)]

    (cond
      loading
      ($ :div {:class "flex flex-col items-center justify-center h-64 gap-4"}
         ($ spinner)
         ($ :p {:class "text-slate-500"} "Loading game..."))

      error
      ($ :div {:class "flex flex-col items-center justify-center h-64 gap-4"}
         ($ :div {:class "text-red-500 text-xl"} "⚠️")
         ($ :p {:class "text-slate-700 font-medium"} "Error loading game")
         ($ :p {:class "text-slate-500 text-sm"} (str error)))

      (nil? game)
      ($ :div {:class "flex flex-col items-center justify-center h-64 gap-4"}
         ($ :p {:class "text-slate-500"} "Game not found"))

      :else
      ($ :div {:class "flex flex-col h-full"}
         ;; Connection status
         (when-not connected
           ($ :div {:class "bg-yellow-50 border-b border-yellow-200 px-4 py-2 text-sm text-yellow-800"}
              "Connecting to game server..."))

         ;; Header with game info
         ($ :div {:class "flex justify-between items-center p-4 border-b bg-white"}
            ($ :div {:class "text-sm text-slate-500"}
               ($ :span "Game: ")
               ($ :span {:class "font-mono"} (subs (:id game) 0 8)))
            ($ turn-indicator {:turn-number (:turn-number game-state)
                               :phase       (:phase game-state)
                               :active-player active-player
                               :is-my-turn  is-my-turn}))

         ;; Main content area
         ($ :div {:class "flex-1 flex flex-col p-4 bg-slate-100"}
            ;; Opponent info
            ($ :div {:class "mb-4"}
               ($ player-info {:player      opponent
                               :team        opponent-team
                               :score       (get score opponent-team 0)
                               :is-active   (= active-player opponent-team)
                               :is-opponent true}))

            ;; Game board placeholder (Phase 5C)
            ($ :div {:class "flex-1 flex items-center justify-center bg-white rounded-lg border-2 border-dashed border-slate-300"}
               ($ :div {:class "text-center text-slate-400"}
                  ($ :p {:class "text-lg font-medium"} "Game Board")
                  ($ :p {:class "text-sm"} "Coming in Phase 5C")))

            ;; My info
            ($ :div {:class "mt-4"}
               ($ player-info {:player     my-player
                               :team       my-team
                               :score      (get score my-team 0)
                               :is-active  (= active-player my-team)})))

         ;; Action bar placeholder (Phase 5D)
         ($ :div {:class "border-t bg-white p-4"}
            ($ :div {:class "flex justify-between items-center"}
               ($ :div {:class "text-sm text-slate-500"}
                  (if is-my-turn
                    "Your turn - choose an action"
                    "Waiting for opponent..."))
               ($ :button {:class    "px-4 py-2 bg-slate-200 text-slate-600 rounded cursor-not-allowed"
                           :disabled true}
                  "End Turn (Phase 5D)")))))))

(defui game-view
  "Game page component.

  Wraps the game content with the game provider for SSE subscription."
  []
  (let [{:keys [id]}   (router/use-params)
        {:keys [user]} (use-auth)]
    (if-not user
      ($ :div {:class "flex justify-center items-center h-64"}
         ($ spinner))
      ($ game-provider {:game-id id :user-id (:id user)}
         ($ game-content)))))
