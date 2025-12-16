(ns bashketball-game-ui.components.game.peek-deck-modal
  "Modal component for peeking at deck cards.

  Uses the peek state machine for state management. The machine handles:
  - Count selection (1-5)
  - Card selection for placement
  - Placement assignment
  - Finish guard (all cards must be placed)

  Two-phase flow:
  1. Select count (1-5) and click Peek
  2. Assign each card to TOP/BOTTOM/DISCARD and confirm"
  (:require
   [bashketball-ui.cards.card-preview :refer [card-preview]]
   [bashketball-ui.components.button :refer [button]]
   [uix.core :refer [$ defui use-effect use-callback]]))

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

  Uses the peek state machine for state management. The machine handles all
  state transitions including card selection and placement tracking.

  Props:
  - machine-state: peek machine state `{:state :data}`
  - send: fn to send events to the peek machine
  - examined-cards: vector of card instances from examined zone (from game state)
  - catalog: card catalog map {slug -> card}
  - on-peek: fn [team count] to submit examine-cards action (called before machine proceeds)"
  [{:keys [machine-state send examined-cards catalog on-peek]}]
  (let [state          (:state machine-state)
        data           (:data machine-state)
        open?          (not= state :closed)
        target-team    (:target-team data)
        peek-count     (:count data)
        selected-id    (:selected-id data)
        placements     (or (:placements data) {})
        cards          (or (:cards data) [])

        in-place-phase (= state :place-cards)

        all-placed?    (and (seq cards)
                            (= (clojure.core/count placements)
                               (clojure.core/count cards)))

        handle-peek    (use-callback
                        (fn []
                          (on-peek target-team peek-count))
                        [on-peek target-team peek-count])]

    ;; When examined-cards arrive and we're in select-count state,
    ;; transition to place-cards with the cards
    (use-effect
     (fn []
       (when (and (= state :select-count)
                  (seq examined-cards))
         (send {:type :proceed
                :data {:cards (mapv :instance-id examined-cards)}}))
       js/undefined)
     [state examined-cards send])

    (when open?
      ($ :div {:class "fixed inset-0 z-50 flex items-center justify-center"}
         ($ :div {:class    "fixed inset-0 bg-black/50"
                  :on-click #(send {:type :cancel})})
         ($ :div {:class "relative bg-white rounded-lg shadow-xl p-6 w-full max-w-lg mx-4"}
            ($ :h2 {:class "text-lg font-semibold text-gray-900 mb-4"}
               (str "Peek Deck - " (if (= target-team :team/HOME) "HOME" "AWAY")))

            (if (not in-place-phase)
              ;; Phase 1: Select count
              ($ :<>
                 ($ :p {:class "text-sm text-slate-600 mb-4"}
                    "How many cards do you want to peek at?")
                 ($ :div {:class "mb-6"}
                    ($ count-selector {:value     peek-count
                                       :on-change #(send {:type :set-count :data {:count %}})}))
                 ($ :div {:class "flex justify-end gap-2"}
                    ($ button {:variant  :outline
                               :on-click #(send {:type :cancel})}
                       "Cancel")
                    ($ button {:on-click handle-peek}
                       "Peek")))

              ;; Phase 2: Place cards
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
                                      :on-click  #(send {:type :select-card
                                                         :data {:instance-id (:instance-id card)}})})))

                 (when selected-id
                   ($ :div {:class "mb-4 p-3 bg-slate-50 rounded-lg"}
                      ($ :p {:class "text-sm text-slate-600 mb-2"}
                         "Place selected card:")
                      ($ :div {:class "flex gap-2 justify-center"}
                         ($ destination-button {:destination "TOP"
                                                :label       "Top of Deck"
                                                :on-click    #(send {:type :place-card
                                                                     :data {:destination %}})})
                         ($ destination-button {:destination "BOTTOM"
                                                :label       "Bottom of Deck"
                                                :on-click    #(send {:type :place-card
                                                                     :data {:destination %}})})
                         ($ destination-button {:destination "DISCARD"
                                                :label       "Discard"
                                                :on-click    #(send {:type :place-card
                                                                     :data {:destination %}})}))))

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
                               :on-click #(send {:type :cancel})}
                       "Cancel")
                    ($ button {:disabled (not all-placed?)
                               :on-click #(send {:type :finish})}
                       "Confirm")))))))))
