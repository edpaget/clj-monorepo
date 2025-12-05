(ns bashketball-game-ui.views.game
  "Game page view.

  Displays the active game with board, players, and actions.
  Uses the game context provider for real-time state updates.
  Sections consume context directly via [[use-game-context]],
  [[use-game-derived]], and [[use-game-handlers]]."
  (:require
   [bashketball-game-ui.components.game.card-detail-modal :refer [card-detail-modal]]
   [bashketball-game-ui.components.game.fate-reveal-modal :refer [fate-reveal-modal]]
   [bashketball-game-ui.components.game.substitute-modal :refer [substitute-modal]]
   [bashketball-game-ui.context.auth :refer [use-auth]]
   [bashketball-game-ui.context.game :refer [game-provider use-game-context]]
   [bashketball-game-ui.hooks.use-game-derived :refer [use-game-derived]]
   [bashketball-game-ui.hooks.use-game-handlers :refer [use-game-handlers]]
   [bashketball-game-ui.hooks.use-game-ui :as ui]
   [bashketball-game-ui.views.game.sections :as sections]
   [bashketball-ui.components.loading :refer [spinner]]
   [bashketball-ui.router :as router]
   [uix.core :refer [$ defui]]))

(defui game-content
  "Inner game content that consumes the game context.

  Orchestrates layout and modals. All sections consume context directly
  via hooks, eliminating prop drilling."
  []
  (let [{:keys [game loading error detail-modal fate-reveal substitute-mode]}
        (use-game-context)

        {:keys [my-starter-players my-bench-players]}
        (use-game-derived)

        {:keys [on-substitute]}
        (use-game-handlers)

        side-panel-mode
        (ui/use-side-panel-mode)]

    (cond
      loading
      ($ sections/loading-state)

      error
      ($ sections/error-state {:error error})

      (nil? game)
      ($ sections/not-found-state)

      :else
      ($ :div {:class "flex flex-col h-full"}
         ($ sections/connection-banner)
         ($ sections/game-header)

         ($ :div {:class "flex-1 flex p-4 bg-slate-100 gap-4 min-h-0 overflow-hidden"}
            ($ sections/board-section)
            ($ sections/side-panel {:side-panel-mode side-panel-mode
                                    :on-info-click   (:show detail-modal)}))

         ($ :div {:class "border-t bg-white"}
            ($ sections/hand-section)
            ($ sections/action-section))

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
                              :on-close          (:cancel substitute-mode)})))))

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
