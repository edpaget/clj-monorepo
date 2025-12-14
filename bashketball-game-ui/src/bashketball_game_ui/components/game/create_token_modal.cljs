(ns bashketball-game-ui.components.game.create-token-modal
  "Modal component for creating tokens.

  Allows entering a token name and choosing placement (asset or attach to player)."
  (:require
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui use-state]]))

(defui placement-button
  "Button for selecting token placement."
  [{:keys [label selected on-click]}]
  ($ :button
     {:class    (str "px-3 py-2 rounded-lg border-2 text-sm font-medium transition-colors "
                     (if selected
                       "border-amber-500 bg-amber-50 text-amber-800"
                       "border-slate-200 hover:border-slate-300 hover:bg-slate-50 text-slate-600"))
      :on-click on-click}
     label))

(defui player-button
  "Button for selecting a target player."
  [{:keys [player selected on-click]}]
  ($ :button
     {:class    (str "flex flex-col items-center p-2 rounded-lg border-2 transition-colors "
                     (if selected
                       "border-amber-500 bg-amber-50"
                       "border-slate-200 hover:border-slate-300 hover:bg-slate-50"))
      :on-click on-click}
     ($ :span {:class "font-medium text-sm truncate max-w-[80px]"}
        (:name player))))

(defui create-token-modal
  "Modal for creating a new token.

  Props:
  - open?: boolean, whether modal is open
  - players: vector of player maps (players on court for current team)
  - on-create: fn [{:keys [name placement target-player-id]}] to create the token
  - on-close: fn [] to close modal"
  [{:keys [open? players on-create on-close]}]
  (let [[token-name set-token-name] (use-state "")
        [placement set-placement]   (use-state :placement/ASSET)
        [target-player set-target]  (use-state nil)
        can-create?                 (and (seq token-name)
                                         (or (= placement :placement/ASSET)
                                             (some? target-player)))
        handle-create               (fn []
                                      (when can-create?
                                        (on-create {:name             token-name
                                                    :placement        placement
                                                    :target-player-id target-player})
                                        (set-token-name "")
                                        (set-placement :placement/ASSET)
                                        (set-target nil)))
        handle-close                (fn []
                                      (set-token-name "")
                                      (set-placement :placement/ASSET)
                                      (set-target nil)
                                      (on-close))]
    (when open?
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ($ :div {:class    "fixed inset-0 bg-black/50"
                  :on-click handle-close})
         ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-md mx-4"}
            ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-4"}
               "Create Token")

            ($ :div {:class "mb-4"}
               ($ :label {:class "block text-sm text-slate-600 mb-1"}
                  "Token Name")
               ($ :input {:type        "text"
                          :class       "w-full px-3 py-2 border border-slate-300 rounded-lg
                                       focus:outline-none focus:ring-2 focus:ring-amber-500 focus:border-amber-500"
                          :placeholder "Enter token name..."
                          :value       token-name
                          :on-change   #(set-token-name (.. % -target -value))}))

            ($ :div {:class "mb-4"}
               ($ :p {:class "text-sm text-slate-600 mb-2"}
                  "Placement:")
               ($ :div {:class "flex gap-2"}
                  ($ placement-button {:label    "Asset"
                                       :selected (= placement :placement/ASSET)
                                       :on-click #(do (set-placement :placement/ASSET)
                                                      (set-target nil))})
                  ($ placement-button {:label    "Attach to Player"
                                       :selected (= placement :placement/ATTACH)
                                       :on-click #(set-placement :placement/ATTACH)})))

            (when (= placement :placement/ATTACH)
              ($ :div {:class "mb-4"}
                 ($ :p {:class "text-sm text-slate-600 mb-2"}
                    "Select player:")
                 (if (seq players)
                   ($ :div {:class "flex flex-wrap gap-2"}
                      (for [player players]
                        ($ player-button {:key      (:id player)
                                          :player   player
                                          :selected (= (:id player) target-player)
                                          :on-click #(set-target (:id player))})))
                   ($ :div {:class "text-sm text-slate-400"}
                      "No players available"))))

            ($ :div {:class "flex justify-end gap-2"}
               ($ button {:variant  :outline
                          :on-click handle-close}
                  "Cancel")
               ($ button {:on-click handle-create
                          :disabled (not can-create?)}
                  "Create")))))))
