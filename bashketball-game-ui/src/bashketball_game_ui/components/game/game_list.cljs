(ns bashketball-game-ui.components.game.game-list
  "List component for displaying games."
  (:require
   [bashketball-game-ui.components.game.game-card :refer [game-card]]
   [bashketball-ui.components.loading :refer [skeleton]]
   [uix.core :refer [$ defui]]))

(defui game-list-skeleton
  "Loading skeleton for game list."
  []
  ($ :div {:class "space-y-4"}
     (for [i (range 3)]
       ($ :div {:key i :class "bg-white rounded-lg shadow-sm border border-gray-200 p-4"}
          ($ :div {:class "flex justify-between mb-3"}
             ($ skeleton {:class "h-5 w-32"})
             ($ skeleton {:class "h-5 w-20"}))
          ($ skeleton {:class "h-4 w-24 mb-4"})
          ($ :div {:class "flex justify-end"}
             ($ skeleton {:class "h-8 w-20"}))))))

(defui game-list
  "Renders a list of games.

  Props:
  - games: Sequence of game summaries
  - loading: boolean for loading state
  - empty-message: String to show when no games
  - current-user-id: UUID of current user
  - on-join: fn(game) for joining waiting games
  - on-resume: fn(game) for resuming active games
  - on-view: fn(game) for viewing completed games
  - on-cancel: fn(game) for cancelling own waiting games
  - show-join?: boolean to show join buttons"
  [{:keys [games loading empty-message current-user-id
           on-join on-resume on-view on-cancel show-join?]}]
  (cond
    loading
    ($ game-list-skeleton)

    (empty? games)
    ($ :div {:class "bg-white rounded-lg shadow-sm border border-gray-200 p-8 text-center text-gray-500"}
       (or empty-message "No games found."))

    :else
    ($ :div {:class "space-y-4"}
       (for [game games]
         ($ game-card
            {:key (:id game)
             :game game
             :current-user-id current-user-id
             :on-join on-join
             :on-resume on-resume
             :on-view on-view
             :on-cancel on-cancel
             :show-join? show-join?})))))
