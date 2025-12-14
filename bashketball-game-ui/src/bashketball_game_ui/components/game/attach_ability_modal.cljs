(ns bashketball-game-ui.components.game.attach-ability-modal
  "Modal for resolving ability cards from the play area.

  Shows options to either discard the card or attach it to a player."
  (:require
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui use-state]]))

(defui player-button
  "Button for selecting a target player to attach ability to."
  [{:keys [player selected on-click]}]
  ($ :button
     {:class    (str "flex flex-col items-center p-2 rounded-lg border-2 transition-colors "
                     (if selected
                       "border-purple-500 bg-purple-50"
                       "border-slate-200 hover:border-slate-300 hover:bg-slate-50"))
      :on-click on-click}
     ($ :span {:class "font-medium text-sm truncate max-w-[80px]"}
        (:name player))))

(defui attach-ability-modal
  "Modal for resolving an ability card from the play area.

  Allows the user to either discard the card or attach it to a player.

  Props:
  - open?: boolean, whether modal is open
  - card-slug: slug of the ability card being resolved
  - players: vector of player maps (players on court for the owning team)
  - catalog: card slug -> card data map
  - on-resolve: fn [target-player-id] to resolve (nil = discard, id = attach)
  - on-close: fn [] to close modal"
  [{:keys [open? card-slug players catalog on-resolve on-close]}]
  (let [[target-player set-target] (use-state nil)
        card-data                  (get catalog card-slug)
        card-name                  (or (:name card-data) card-slug)
        handle-discard             (fn []
                                     (on-resolve nil)
                                     (set-target nil))
        handle-attach              (fn []
                                     (when target-player
                                       (on-resolve target-player)
                                       (set-target nil)))
        handle-close               (fn []
                                     (set-target nil)
                                     (on-close))]
    (when open?
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ($ :div {:class    "fixed inset-0 bg-black/50"
                  :on-click handle-close})
         ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"}
            ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-2"}
               "Resolve Ability")
            ($ :p {:class "text-sm text-slate-600 mb-4"}
               (str "How do you want to resolve \"" card-name "\"?"))

            ($ :div {:class "mb-4"}
               ($ :p {:class "text-sm font-medium text-slate-700 mb-2"}
                  "Attach to player:")
               (if (seq players)
                 ($ :div {:class "flex flex-wrap gap-2"}
                    (for [player players]
                      ($ player-button {:key      (:id player)
                                        :player   player
                                        :selected (= (:id player) target-player)
                                        :on-click #(set-target (:id player))})))
                 ($ :div {:class "text-sm text-slate-400"}
                    "No players available")))

            ($ :div {:class "flex justify-end gap-2"}
               ($ button {:variant  :outline
                          :on-click handle-close}
                  "Cancel")
               ($ button {:variant  :outline
                          :on-click handle-discard}
                  "Discard")
               ($ button {:on-click handle-attach
                          :disabled (nil? target-player)}
                  "Attach")))))))
