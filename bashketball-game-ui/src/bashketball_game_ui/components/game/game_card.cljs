(ns bashketball-game-ui.components.game.game-card
  "Game summary card for displaying in lists."
  (:require
   ["lucide-react" :refer [Clock Play Users]]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

(defn- format-time-ago
  "Formats a date string as relative time (e.g., '5m ago')."
  [date-str]
  (when date-str
    (let [date     (js/Date. date-str)
          now      (js/Date.)
          diff-ms  (- now date)
          diff-min (/ diff-ms 60000)]
      (cond
        (< diff-min 1)    "just now"
        (< diff-min 60)   (str (int diff-min) "m ago")
        (< diff-min 1440) (str (int (/ diff-min 60)) "h ago")
        :else             (str (int (/ diff-min 1440)) "d ago")))))

(defui status-badge
  "Renders a status badge with appropriate styling."
  [{:keys [status]}]
  (let [[label color] (case status
                        :game-status/waiting   ["Waiting" "bg-yellow-100 text-yellow-800"]
                        :game-status/active    ["In Progress" "bg-green-100 text-green-800"]
                        :game-status/completed ["Completed" "bg-gray-100 text-gray-800"]
                        :game-status/abandoned ["Abandoned" "bg-red-100 text-red-800"]
                        ["Unknown" "bg-gray-100 text-gray-500"])]
    ($ :span {:class (cn "px-2 py-1 text-xs font-medium rounded-full" color)}
       label)))

(defui game-card
  "Renders a game summary card.

  Props:
  - game: Game summary map with :id, :player-1-id, :player-2-id, :status, :created-at
  - current-user-id: UUID of the current user (to determine if they created the game)
  - on-join: fn called when Join button clicked (for waiting games)
  - on-resume: fn called when Resume button clicked (for active games)
  - on-view: fn called when View button clicked (for completed games)
  - on-cancel: fn called when Cancel button clicked (for own waiting games)
  - show-join?: boolean to show join button (default false)"
  [{:keys [game current-user-id on-join on-resume on-view on-cancel show-join?]}]
  (let [{:keys [id player-1-id player-2-id status created-at]} game
        is-owner?                                              (= current-user-id player-1-id)]
    ($ :div {:class "bg-white rounded-lg shadow-sm border border-gray-200 p-4"}
       ($ :div {:class "flex justify-between items-start mb-3"}
          ($ :div {:class "flex items-center gap-2"}
             ($ Users {:className "w-4 h-4 text-gray-400"})
             ($ :span {:class "font-medium text-gray-900"}
                (if is-owner? "You" "Player 1"))
             (when player-2-id
               ($ :<>
                  ($ :span {:class "text-gray-400"} "vs")
                  ($ :span {:class "font-medium text-gray-900"}
                     (if (= current-user-id player-2-id) "You" "Player 2")))))
          ($ status-badge {:status status}))

       ($ :div {:class "flex items-center gap-2 text-sm text-gray-500 mb-4"}
          ($ Clock {:className "w-3 h-3"})
          ($ :span (format-time-ago created-at)))

       ($ :div {:class "flex justify-end gap-2"}
          (case status
            :game-status/waiting
            (if (and is-owner? on-cancel)
              ($ button {:size :sm :variant :outline :on-click #(on-cancel game)}
                 "Cancel")
              (when (and show-join? (not is-owner?) on-join)
                ($ button {:size :sm :on-click #(on-join game)}
                   "Join Game")))

            :game-status/active
            (when on-resume
              ($ button {:size :sm :on-click #(on-resume game)}
                 ($ Play {:className "w-3 h-3 mr-1"})
                 "Resume"))

            :game-status/completed
            (when on-view
              ($ button {:size :sm :variant :outline :on-click #(on-view game)}
                 "View"))

            nil)))))
