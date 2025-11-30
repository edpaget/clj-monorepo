(ns bashketball-game-ui.components.game.join-dialog
  "Dialog for selecting a deck to join or create a game."
  (:require
   [bashketball-game-ui.hooks.use-decks :refer [use-my-decks]]
   [bashketball-ui.components.button :refer [button]]
   [bashketball-ui.components.loading :refer [spinner button-spinner]]
   [bashketball-ui.components.select :refer [select]]
   [uix.core :refer [$ defui use-state use-effect]]))

(defui join-dialog
  "Dialog for selecting a deck to join/create a game.

  Props:
  - open?: boolean indicating if dialog is open
  - game: Game being joined (nil for create)
  - title: Dialog title
  - submit-label: Submit button text
  - on-close: fn called to close dialog
  - on-submit: fn(deck-id) called on submit
  - submitting?: boolean loading state"
  [{:keys [open? game title submit-label on-close on-submit submitting?]}]
  (let [{:keys [decks loading]} (use-my-decks)
        valid-decks             (filter :is-valid decks)
        [selected set-selected] (use-state nil)
        deck-options            (mapv (fn [d] {:value (str (:id d))
                                               :label (:name d)})
                                      valid-decks)]

    ;; Reset selection when dialog opens
    (use-effect
     (fn []
       (when open?
         (set-selected nil))
       js/undefined)
     [open?])

    (when open?
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ($ :div {:class "fixed inset-0 bg-black/50"
                  :on-click on-close})
         ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"}
            ($ :h2 {:class "text-xl font-semibold text-gray-900 mb-2"}
               (or title "Select Deck"))

            (when game
              ($ :p {:class "text-gray-600 mb-4"}
                 "Join game and start playing"))

            (cond
              loading
              ($ :div {:class "flex justify-center py-8"}
                 ($ spinner))

              (empty? valid-decks)
              ($ :div {:class "text-center py-4"}
                 ($ :p {:class "text-gray-600 mb-2"}
                    "You need a valid deck to play.")
                 ($ :p {:class "text-sm text-gray-500"}
                    "Create and validate a deck with 3-5 players and 30-50 action cards.")
                 ($ :div {:class "flex justify-end pt-4"}
                    ($ button {:variant :outline :on-click on-close}
                       "Close")))

              :else
              ($ :div {:class "space-y-4"}
                 ($ :div
                    ($ :label {:class "block text-sm font-medium text-gray-700 mb-2"}
                       "Choose your deck")
                    ($ select
                       {:value selected
                        :on-value-change set-selected
                        :placeholder "Select a deck"
                        :options deck-options}))

                 ($ :div {:class "flex justify-end gap-3 pt-4"}
                    ($ button {:variant :outline
                               :on-click on-close
                               :disabled submitting?}
                       "Cancel")
                    ($ button {:disabled (or submitting? (nil? selected))
                               :on-click #(on-submit selected)}
                       (when submitting? ($ button-spinner))
                       (or submit-label "Confirm"))))))))))
