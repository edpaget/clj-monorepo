(ns bashketball-game-ui.views.game
  "Game page view.

  Displays the active game with board, players, and actions.
  Uses the game context provider for real-time state updates.
  Sections consume context directly via [[use-game-context]],
  [[use-game-derived]], and [[use-game-handlers]]."
  (:require
   [bashketball-game-ui.components.game.card-detail-modal :refer [card-detail-modal]]
   [bashketball-game-ui.components.game.fate-reveal-modal :refer [fate-reveal-modal]]
   [bashketball-game-ui.components.game.game-log-modal :refer [game-log-modal]]
   [bashketball-game-ui.components.game.roster-modal :refer [roster-modal]]
   [bashketball-game-ui.components.game.substitute-modal :refer [substitute-modal]]
   [bashketball-game-ui.context.auth :refer [use-auth]]
   [bashketball-game-ui.context.game :refer [game-provider use-game-context]]
   [bashketball-game-ui.hooks.use-game-derived :refer [use-game-derived]]
   [bashketball-game-ui.hooks.use-game-handlers :refer [use-game-handlers]]
   [bashketball-game-ui.views.game.sections :as sections]
   [bashketball-ui.components.loading :refer [spinner]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui use-state]]))

(defui game-content
  "Inner game content that consumes the game context.

  Orchestrates layout and modals. All sections consume context directly
  via hooks, eliminating prop drilling.

  Uses three-column layout on large screens (≥1024px), stacked layout
  on smaller screens."
  []
  (let [{:keys [game game-state loading error detail-modal fate-reveal substitute-mode]}
        (use-game-context)

        {:keys [my-starter-players my-bench-players
                home-players away-players home-starters away-starters]}
        (use-game-derived)

        {:keys [on-substitute]}
        (use-game-handlers)

        on-info-click                                                                    (:show detail-modal)

        ;; Modal state for log and roster
        [log-open? set-log-open]                                                         (use-state false)
        [roster-open? set-roster-open]                                                   (use-state false)]

    (cond
      loading
      ($ sections/loading-state)

      error
      ($ sections/error-state {:error error})

      (nil? game)
      ($ sections/not-found-state)

      :else
      ($ :div {:class "flex flex-col h-screen bg-gray-50"}
         ($ sections/connection-banner)
         ($ sections/game-header {:on-log-click    #(set-log-open true)
                                  :on-roster-click #(set-roster-open true)})

         ;; Main content area - three columns on large screens
         ($ :div {:class "flex-1 flex flex-col lg:flex-row gap-2 p-2 bg-slate-100 min-h-0 overflow-hidden"}
            ;; Left column: Away team (large screens only)
            ($ :div {:class "hidden lg:flex"}
               ($ sections/team-column-section {:team :AWAY :on-info-click on-info-click}))

            ;; Center: Game board
            ($ :div {:class "flex-1 min-h-0"}
               ($ sections/board-section))

            ;; Right column: Home team (large screens only)
            ($ :div {:class "hidden lg:flex"}
               ($ sections/team-column-section {:team :HOME :on-info-click on-info-click})))

         ;; Team panels below board (small screens only)
         ($ :div {:class "flex gap-2 px-2 py-1 lg:hidden"}
            ($ sections/compact-team-panel {:team :AWAY :on-info-click on-info-click})
            ($ sections/compact-team-panel {:team :HOME :on-info-click on-info-click}))

         ;; Bottom bar with hand and actions
         ($ sections/bottom-bar-section)

         ;; Modals
         ($ card-detail-modal {:open?     (:open? detail-modal)
                               :card-slug (:card-slug detail-modal)
                               :on-close  (:close detail-modal)})

         ($ fate-reveal-modal {:open?    (:open? fate-reveal)
                               :fate     (:fate fate-reveal)
                               :on-close (:close fate-reveal)})

         ($ substitute-modal {:open?             (:active substitute-mode)
                              :starters          my-starter-players
                              :bench             my-bench-players
                              :selected-starter  (:starter-id substitute-mode)
                              :on-starter-select (:set-starter substitute-mode)
                              :on-bench-select   on-substitute
                              :on-close          (:cancel substitute-mode)})

         ($ game-log-modal {:open?    log-open?
                            :events   (:events game-state)
                            :on-close #(set-log-open false)})

         ($ roster-modal {:open?         roster-open?
                          :home-players  home-players
                          :home-starters home-starters
                          :away-players  away-players
                          :away-starters away-starters
                          :on-close      #(set-roster-open false)})))))

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
