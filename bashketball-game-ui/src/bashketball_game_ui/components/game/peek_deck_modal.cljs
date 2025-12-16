(ns bashketball-game-ui.components.game.peek-deck-modal
  "Modal component for peeking at deck cards.

  Two-phase flow:
  1. Select count (1-5) and click Peek
  2. Assign each card to TOP/BOTTOM/DISCARD and confirm"
  (:require
   [bashketball-ui.cards.card-preview :refer [card-preview]]
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui use-state use-effect]]))

(defui count-selector
  "Number selector for choosing how many cards to peek."
  [{:keys [value on-change]}]
  ($ :div {:class "flex items-center gap-2"}
     (for [n (range 1 6)]
       ($ :button
          {:key      n
           :class    (str "w-10 h-10 rounded-lg border-2 font-medium transition-colors "
                          (if (= n value)
                            "border-amber-500 bg-amber-50 text-amber-700"
                            "border-slate-200 hover:border-slate-300 text-slate-600"))
           :on-click #(on-change n)}
          n))))

(defui card-button
  "Card button for selection in placement phase."
  [{:keys [card catalog selected on-click placement]}]
  (let [card-data (get catalog (:card-slug card))]
    ($ :button
       {:class    (str "relative rounded-lg border-2 p-1 transition-all "
                       (cond
                         selected "border-amber-500 ring-2 ring-amber-200"
                         placement "border-green-500 bg-green-50"
                         :else "border-slate-200 hover:border-slate-300"))
        :on-click on-click}
       ($ :div {:class "w-20 h-28 overflow-hidden"}
          (when card-data
            ($ :div {:class "transform scale-[0.25] origin-top-left"}
               ($ card-preview {:card card-data}))))
       (when placement
         ($ :div {:class "absolute bottom-0 left-0 right-0 bg-green-600 text-white text-xs py-0.5 text-center font-medium"}
            placement)))))

(defui destination-button
  "Button for selecting card destination."
  [{:keys [destination label disabled on-click]}]
  ($ button
     {:variant  (if disabled :outline :default)
      :size     :sm
      :disabled disabled
      :on-click #(on-click destination)
      :class    (case destination
                  "TOP" "bg-blue-600 hover:bg-blue-700"
                  "BOTTOM" "bg-purple-600 hover:bg-purple-700"
                  "DISCARD" "bg-red-600 hover:bg-red-700"
                  "")}
     label))

(defui peek-deck-modal
  "Modal for peeking at deck cards.

  Props:
  - open?: boolean, whether modal is open
  - target-team: team keyword (:team/HOME or :team/AWAY)
  - count: number of cards to examine
  - phase: :select-count or :place-cards
  - examined-cards: vector of card instances from examined zone
  - catalog: card catalog map {slug -> card}
  - on-count-change: fn [n] to update count
  - on-peek: fn [] to submit examine-cards action
  - on-resolve: fn [placements] to submit resolve action
  - on-close: fn [] to close modal"
  [{:keys [open? target-team count phase examined-cards catalog
           on-count-change on-peek on-resolve on-close]}]
  (let [[selected-id set-selected-id] (use-state nil)
        [placements set-placements]   (use-state {})

        all-placed?                   (= (clojure.core/count placements)
                                         (clojure.core/count examined-cards))

        handle-destination-click      (fn [destination]
                                        (when selected-id
                                          (set-placements #(assoc % selected-id destination))
                                          (set-selected-id nil)))

        handle-confirm                (fn []
                                        (let [placement-vec (mapv (fn [card]
                                                                    {:instance-id (:instance-id card)
                                                                     :destination (get placements (:instance-id card))})
                                                                  examined-cards)]
                                          (on-resolve placement-vec)))]

    (use-effect
     (fn []
       (when (not open?)
         (set-selected-id nil)
         (set-placements {}))
       js/undefined)
     [open?])

    (when open?
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ($ :div {:class    "fixed inset-0 bg-black/50"
                  :on-click on-close})
         ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-lg mx-4"}
            ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-4"}
               (str "Peek Deck - " (if (= target-team :team/HOME) "HOME" "AWAY")))

            (if (= phase :select-count)
              ($ :<>
                 ($ :p {:class "text-sm text-slate-600 mb-4"}
                    "How many cards do you want to peek at?")
                 ($ :div {:class "mb-6"}
                    ($ count-selector {:value     count
                                       :on-change on-count-change}))
                 ($ :div {:class "flex justify-end gap-2"}
                    ($ button {:variant  :outline
                               :on-click on-close}
                       "Cancel")
                    ($ button {:on-click on-peek}
                       "Peek")))

              ($ :<>
                 ($ :p {:class "text-sm text-slate-600 mb-2"}
                    "Select each card and choose where to place it:")

                 ($ :div {:class "flex flex-wrap gap-2 mb-4 justify-center"}
                    (for [card examined-cards]
                      ($ card-button {:key       (:instance-id card)
                                      :card      card
                                      :catalog   catalog
                                      :selected  (= (:instance-id card) selected-id)
                                      :placement (get placements (:instance-id card))
                                      :on-click  #(set-selected-id (:instance-id card))})))

                 (when selected-id
                   ($ :div {:class "mb-4 p-3 bg-slate-50 rounded-lg"}
                      ($ :p {:class "text-sm text-slate-600 mb-2"}
                         "Place selected card:")
                      ($ :div {:class "flex gap-2 justify-center"}
                         ($ destination-button {:destination "TOP"
                                                :label       "Top of Deck"
                                                :on-click    handle-destination-click})
                         ($ destination-button {:destination "BOTTOM"
                                                :label       "Bottom of Deck"
                                                :on-click    handle-destination-click})
                         ($ destination-button {:destination "DISCARD"
                                                :label       "Discard"
                                                :on-click    handle-destination-click}))))

                 (when (seq placements)
                   ($ :div {:class "mb-4 text-sm text-slate-600"}
                      ($ :p {:class "font-medium mb-1"} "Placements:")
                      (for [card  examined-cards
                            :let  [placement (get placements (:instance-id card))]
                            :when placement]
                        ($ :div {:key   (:instance-id card)
                                 :class "ml-2"}
                           (str (or (:card-slug card) "Card") " → " placement)))))

                 ($ :div {:class "flex justify-end gap-2"}
                    ($ button {:variant  :outline
                               :on-click on-close}
                       "Cancel")
                    ($ button {:disabled (not all-placed?)
                               :on-click handle-confirm}
                       "Confirm")))))))))
