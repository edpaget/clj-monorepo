(ns bashketball-game-ui.components.game.bottom-bar
  "Bottom bar component with hand cards and action buttons.

  Ensures hand cards are always fully visible with horizontal scroll,
  while actions overflow into a dropdown menu when space is limited."
  (:require
   [bashketball-game-ui.components.game.action-button-with-tooltip :refer [action-button-with-tooltip]]
   [bashketball-game-ui.components.game.player-hand :refer [player-hand]]
   [bashketball-game-ui.components.game.score-controls :as score]
   [bashketball-game-ui.components.ui.dropdown-menu :as dropdown]
   [bashketball-ui.utils :refer [cn]]
   [uix.core :refer [$ defui]]))

;; Action priority groups - primary actions always visible, secondary in overflow
(def ^:private primary-action-ids
  "Actions always shown as buttons."
  #{:end-turn :pass :shoot :play-card :submit-discard :cancel-discard :start-game :draw :discard :start-from-tipoff
    :standard-action :cancel-standard-action :proceed-standard-action})

(def ^:private secondary-action-ids
  "Actions shown in overflow menu."
  #{:shuffle :return-discard :substitute :next-phase :reveal-fate :cancel-pass
    :peek-my-deck :peek-opponent-deck})

(defui action-overflow-menu
  "Dropdown menu for overflow actions.

  Props:
  - actions: vector of {:id :label :on-click :disabled :class}
  - disabled: boolean to disable the trigger"
  [{:keys [actions disabled]}]
  ($ dropdown/dropdown-menu
     ($ dropdown/dropdown-menu-trigger {:asChild true}
        ($ :button {:class    (cn "inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium"
                                  "border border-gray-200 bg-white shadow-sm hover:bg-gray-100 hover:text-gray-900"
                                  "min-h-[44px] h-11 px-4"
                                  "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-gray-950"
                                  "disabled:pointer-events-none disabled:opacity-50")
                    :disabled disabled
                    :type     "button"}
           ($ :span {:class "sr-only"} "More actions")
           "\u2022\u2022\u2022"))
     ($ dropdown/dropdown-menu-content {:align "end" :side "top"}
        (if (empty? actions)
          ($ dropdown/dropdown-menu-item {:disabled true} "No actions available")
          ($ :<>
             (for [{:keys [id label on-click disabled class]} actions]
               ($ dropdown/dropdown-menu-item
                  {:key       id
                   :on-select on-click
                   :disabled  disabled
                   :class     class}
                  label)))))))

(defui action-button
  "Single action button with touch-friendly size and optional tooltip.

  When disabled with an explanation, shows a tooltip on hover explaining
  why the action is unavailable."
  [{:keys [label on-click disabled variant class explanation]}]
  ($ action-button-with-tooltip
     {:label       label
      :on-click    on-click
      :disabled    disabled
      :variant     variant
      :class       class
      :explanation explanation}))

(defui action-bar
  "Action buttons with overflow menu.

  Props:
  - actions: vector of action maps with :id :label :on-click :disabled :class :variant
  - loading: boolean to disable all actions
  - status-text: string to display on the left
  - on-add-score: fn [team points] for manual score adjustment"
  [{:keys [actions loading status-text on-add-score]}]
  (let [primary   (filter #(primary-action-ids (:id %)) actions)
        secondary (filter #(secondary-action-ids (:id %)) actions)]
    ($ :div {:class "flex items-center justify-between gap-4"}
       ;; Left: status text and score controls
       ($ :div {:class "flex items-center gap-4 min-w-0"}
          ($ :div {:class "text-sm text-slate-600 truncate"}
             status-text)
          (when on-add-score
            ($ score/score-controls {:on-add-score on-add-score
                                     :loading      loading})))

       ;; Right: action buttons
       ($ :div {:class "flex items-center gap-2 flex-shrink-0"}
          ;; Primary actions as buttons
          (for [{:keys [id label on-click disabled variant class explanation]} primary]
            ($ action-button {:key         id
                              :label       label
                              :on-click    on-click
                              :disabled    (or disabled loading)
                              :variant     variant
                              :class       class
                              :explanation explanation}))

          ;; Secondary actions in overflow menu
          (when (seq secondary)
            ($ action-overflow-menu {:actions  secondary
                                     :disabled loading}))))))

(defui bottom-bar
  "Bottom bar with hand cards and action buttons.

  Props:
  - hand: vector of cards in player's hand
  - catalog: map of card-slug to full card data (for preview mode)
  - expanded: boolean, true when hand is expanded to show previews
  - on-expand-toggle: fn [] to toggle expanded state
  - selected-card: currently selected card slug or nil
  - discard-mode: boolean
  - discard-cards: set of card instance-ids marked for discard
  - on-card-click: fn [card] when card clicked
  - on-detail-click: fn [card-slug] to show card detail
  - disabled: boolean to disable hand interaction
  - actions: vector of action maps for action bar
  - loading: boolean
  - status-text: string for status display
  - on-add-score: fn [team points] for manual score adjustment"
  [{:keys [hand catalog expanded on-expand-toggle selected-card discard-mode discard-cards
           on-card-click on-detail-click disabled
           actions loading status-text on-add-score]}]
  ($ :div {:class "border-t bg-white"}
     ;; Hand section - takes available space
     ($ :div {:class "px-3 py-2 border-b"}
        ;; Header with toggle
        ($ :div {:class "flex items-center justify-between mb-1"}
           ($ :div {:class "text-xs font-medium text-slate-500"}
              (if discard-mode
                (str "Select cards to discard (" (count discard-cards) " selected)")
                "Your Hand"))
           (when (seq hand)
             ($ :button
                {:class    "text-slate-400 hover:text-slate-600 text-xs"
                 :on-click on-expand-toggle}
                (if expanded "Collapse" "Expand"))))
        ($ player-hand {:hand            hand
                        :catalog         catalog
                        :display-mode    (if expanded :preview :compact)
                        :selected-card   selected-card
                        :discard-mode    discard-mode
                        :discard-cards   discard-cards
                        :on-card-click   on-card-click
                        :on-detail-click on-detail-click
                        :disabled        disabled}))

     ;; Actions section
     ($ :div {:class "px-3 py-2"}
        ($ action-bar {:actions      actions
                       :loading      loading
                       :status-text  status-text
                       :on-add-score on-add-score}))))
