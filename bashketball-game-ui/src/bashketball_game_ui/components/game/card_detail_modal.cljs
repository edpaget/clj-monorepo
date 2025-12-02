(ns bashketball-game-ui.components.game.card-detail-modal
  "Modal dialog for viewing card details.

  Displays a full card preview when the user clicks the info icon on a card
  in their hand."
  (:require
   [bashketball-game-ui.hooks.use-cards :refer [use-card]]
   [bashketball-ui.cards.card-preview :refer [card-preview]]
   [bashketball-ui.components.loading :refer [spinner]]
   [uix.core :refer [$ defui]]))

(defui card-detail-modal
  "Modal showing full card details.

  Props:
  - open?: boolean indicating if modal is open
  - card-slug: slug of the card to display
  - on-close: fn called to close the modal"
  [{:keys [open? card-slug on-close]}]
  (let [{:keys [card loading error]} (use-card card-slug)]
    (when open?
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ;; Backdrop
         ($ :div {:class    "fixed inset-0 bg-black/50"
                  :on-click on-close})
         ;; Modal content
         ($ :div {:class "relative flex flex-col items-center"}
            ;; Close button
            ($ :button {:class    "absolute -top-2 -right-2 z-10 w-8 h-8 bg-white rounded-full shadow-lg flex items-center justify-center text-slate-500 hover:text-slate-700 hover:bg-slate-50"
                        :on-click on-close}
               "×")
            (cond
              loading
              ($ :div {:class "bg-white rounded-lg p-8 shadow-xl"}
                 ($ spinner))

              error
              ($ :div {:class "bg-white rounded-lg p-6 shadow-xl text-center"}
                 ($ :p {:class "text-red-500 font-medium"} "Error loading card")
                 ($ :p {:class "text-slate-500 text-sm mt-1"} (str error)))

              (nil? card)
              ($ :div {:class "bg-white rounded-lg p-6 shadow-xl text-center"}
                 ($ :p {:class "text-slate-500"} "Card not found"))

              :else
              ($ card-preview {:card card})))))))
