(ns bashketball-game-ui.hooks.use-game-ui
  "Custom hooks for game UI state management.

  Groups related state into reusable hooks that encapsulate state
  and state transitions for common UI patterns."
  (:require [uix.core :refer [use-state use-callback use-memo]]))

(defn use-selection
  "Hook for managing player and card selection state.

  Returns a map with:
  - :selected-player - currently selected player ID or nil
  - :selected-card - currently selected card instance-id or nil
  - :set-selected-player - setter function
  - :set-selected-card - setter function
  - :toggle-player - toggles player selection (select if different, deselect if same)
  - :toggle-card - toggles card selection (by instance-id)
  - :clear - clears both selections"
  []
  (let [[selected-player set-selected-player] (use-state nil)
        [selected-card set-selected-card]     (use-state nil)

        toggle-player                         (use-callback
                                               (fn [id]
                                                 (set-selected-player #(if (= % id) nil id)))
                                               [])

        toggle-card                           (use-callback
                                               (fn [instance-id]
                                                 (set-selected-card #(if (= % instance-id) nil instance-id)))
                                               [])

        clear                                 (use-callback
                                               (fn []
                                                 (set-selected-player nil)
                                                 (set-selected-card nil))
                                               [])]
    (use-memo
     (fn []
       {:selected-player     selected-player
        :selected-card       selected-card
        :set-selected-player set-selected-player
        :set-selected-card   set-selected-card
        :toggle-player       toggle-player
        :toggle-card         toggle-card
        :clear               clear})
     [selected-player selected-card toggle-player toggle-card clear])))

(defn use-pass-mode
  "Hook for managing pass mode state.

  Returns a map with:
  - :active - boolean, true if pass mode is active
  - :start - function to enter pass mode
  - :cancel - function to exit pass mode"
  []
  (let [[active set-active] (use-state false)
        start               (use-callback #(set-active true) [])
        cancel              (use-callback #(set-active false) [])]
    (use-memo
     (fn []
       {:active active
        :start  start
        :cancel cancel})
     [active start cancel])))

(defn use-discard-mode
  "Hook for managing discard mode and selected cards.

  Returns a map with:
  - :active - boolean, true if discard mode is active
  - :cards - set of selected card instance-ids for discard
  - :count - number of cards selected
  - :toggle-card - function to toggle a card in the discard set (by instance-id)
  - :enter - function to enter discard mode (clears previous selections)
  - :cancel - function to exit discard mode and clear selections
  - :get-cards-and-exit - function that returns selected instance-ids and exits mode"
  []
  (let [[active set-active] (use-state false)
        [cards set-cards]   (use-state #{})
        toggle-card         (use-callback
                             (fn [instance-id]
                               (set-cards #(if (contains? % instance-id)
                                             (disj % instance-id)
                                             (conj % instance-id))))
                             [])
        enter               (use-callback
                             (fn []
                               (set-active true)
                               (set-cards #{}))
                             [])
        cancel              (use-callback
                             (fn []
                               (set-active false)
                               (set-cards #{}))
                             [])
        get-cards-and-exit  (use-callback
                             (fn []
                               (let [result cards]
                                 (set-active false)
                                 (set-cards #{})
                                 result))
                             [cards])]
    (use-memo
     (fn []
       {:active             active
        :cards              cards
        :count              (count cards)
        :toggle-card        toggle-card
        :enter              enter
        :cancel             cancel
        :get-cards-and-exit get-cards-and-exit})
     [active cards toggle-card enter cancel get-cards-and-exit])))

(defn use-detail-modal
  "Hook for managing card detail modal state.

  Returns a map with:
  - :card-slug - currently displayed card slug or nil
  - :open? - boolean, true if modal is open
  - :show - function to show modal for a card slug
  - :close - function to close modal"
  []
  (let [[card-slug set-card-slug] (use-state nil)
        show                      (use-callback #(set-card-slug %) [])
        close                     (use-callback #(set-card-slug nil) [])]
    (use-memo
     (fn []
       {:card-slug card-slug
        :open?     (some? card-slug)
        :show      show
        :close     close})
     [card-slug show close])))

(defn use-ball-mode
  "Hook for managing ball selection/movement mode.

  Returns a map with:
  - :active - boolean, true when ball is selected for movement
  - :select - function to enter ball mode (ball selected)
  - :cancel - function to exit ball mode"
  []
  (let [[active set-active] (use-state false)
        select              (use-callback #(set-active true) [])
        cancel              (use-callback #(set-active false) [])]
    (use-memo
     (fn []
       {:active active
        :select select
        :cancel cancel})
     [active select cancel])))

(defn use-fate-reveal
  "Hook for managing fate reveal modal state.

  Returns a map with:
  - :fate - the revealed fate value, or nil
  - :open? - boolean, true if modal should be shown
  - :show - fn [fate] to show the modal with a fate value
  - :close - fn [] to close the modal"
  []
  (let [[fate set-fate] (use-state nil)
        show            (use-callback #(set-fate %) [])
        close           (use-callback #(set-fate nil) [])]
    (use-memo
     (fn []
       {:fate  fate
        :open? (some? fate)
        :show  show
        :close close})
     [fate show close])))

(defn use-substitute-mode
  "Hook for managing substitution mode state.

  Returns a map with:
  - :active - boolean, true when in substitution selection mode
  - :on-court-id - selected on-court player to substitute out, or nil
  - :set-on-court - fn [id] to select an on-court player
  - :enter - function to enter substitution mode
  - :cancel - function to exit substitution mode and clear selection"
  []
  (let [[active set-active]           (use-state false)
        [on-court-id set-on-court-id] (use-state nil)
        set-on-court                  (use-callback #(set-on-court-id %) [])
        enter                         (use-callback
                                       (fn []
                                         (set-active true)
                                         (set-on-court-id nil))
                                       [])
        cancel                        (use-callback
                                       (fn []
                                         (set-active false)
                                         (set-on-court-id nil))
                                       [])]
    (use-memo
     (fn []
       {:active       active
        :on-court-id  on-court-id
        :set-on-court set-on-court
        :enter        enter
        :cancel       cancel})
     [active on-court-id set-on-court enter cancel])))

(defn use-side-panel-mode
  "Hook for managing side panel view mode.

  Returns a map with:
  - :mode - current mode (:log or :players)
  - :show-log - fn to switch to log view
  - :show-players - fn to switch to players view
  - :toggle - fn to toggle between modes"
  []
  (let [[mode set-mode] (use-state :log)
        show-log        (use-callback #(set-mode :log) [])
        show-players    (use-callback #(set-mode :players) [])
        toggle          (use-callback #(set-mode (fn [m] (if (= m :log) :players :log))) [])]
    (use-memo
     (fn []
       {:mode         mode
        :show-log     show-log
        :show-players show-players
        :toggle       toggle})
     [mode show-log show-players toggle])))

(defn use-create-token-modal
  "Hook for managing create token modal state.

  Returns a map with:
  - :open? - boolean, true if modal is open
  - :show - fn [] to open the modal
  - :close - fn [] to close the modal"
  []
  (let [[open? set-open] (use-state false)
        show             (use-callback #(set-open true) [])
        close            (use-callback #(set-open false) [])]
    (use-memo
     (fn []
       {:open? open?
        :show  show
        :close close})
     [open? show close])))

(defn use-attach-ability-modal
  "Hook for managing attach ability modal state.

  Returns a map with:
  - :open? - boolean, true if modal is open
  - :instance-id - the card instance-id to attach
  - :card-slug - the card slug for the ability card
  - :played-by - the team that played the card
  - :show - fn [instance-id card-slug played-by] to open the modal
  - :close - fn [] to close the modal"
  []
  (let [[card-data set-card-data] (use-state nil)
        show                      (use-callback
                                   (fn [instance-id card-slug played-by]
                                     (set-card-data {:instance-id instance-id
                                                     :card-slug   card-slug
                                                     :played-by   played-by}))
                                   [])
        close                     (use-callback #(set-card-data nil) [])]
    (use-memo
     (fn []
       {:open?       (some? card-data)
        :instance-id (:instance-id card-data)
        :card-slug   (:card-slug card-data)
        :played-by   (:played-by card-data)
        :show        show
        :close       close})
     [card-data show close])))

(defn use-standard-action-mode
  "Hook for managing standard action mode state.

  Standard action mode is a two-step flow:
  1. Select 2 cards from hand to discard (:step = :select-cards)
  2. Select which standard action to play (:step = :select-action)

  Returns a map with:
  - :active - boolean, true if standard action mode is active
  - :step - keyword, :select-cards or :select-action
  - :cards - set of selected card instance-ids for discard
  - :count - number of cards selected
  - :toggle-card - fn [instance-id] to toggle card selection
  - :enter - fn [] to enter standard action mode
  - :proceed - fn [] to proceed to action selection (requires exactly 2 cards)
  - :cancel - fn [] to exit mode and clear state
  - :get-cards-and-exit - fn [] returns selected instance-ids and exits mode"
  []
  (let [[active set-active] (use-state false)
        [step set-step]     (use-state :select-cards)
        [cards set-cards]   (use-state #{})

        toggle-card         (use-callback
                             (fn [instance-id]
                               (set-cards #(if (contains? % instance-id)
                                             (disj % instance-id)
                                             (conj % instance-id))))
                             [])

        enter               (use-callback
                             (fn []
                               (set-active true)
                               (set-step :select-cards)
                               (set-cards #{}))
                             [])

        proceed             (use-callback
                             (fn []
                               (set-step :select-action))
                             [])

        cancel              (use-callback
                             (fn []
                               (set-active false)
                               (set-step :select-cards)
                               (set-cards #{}))
                             [])

        get-cards-and-exit  (use-callback
                             (fn []
                               (let [result cards]
                                 (set-active false)
                                 (set-step :select-cards)
                                 (set-cards #{})
                                 result))
                             [cards])]
    (use-memo
     (fn []
       {:active             active
        :step               step
        :cards              cards
        :count              (count cards)
        :toggle-card        toggle-card
        :enter              enter
        :proceed            proceed
        :cancel             cancel
        :get-cards-and-exit get-cards-and-exit})
     [active step cards toggle-card enter proceed cancel get-cards-and-exit])))

(defn use-peek-deck-modal
  "Hook for managing peek deck modal state.

  Returns a map with:
  - :open? - boolean, true if modal is open
  - :target-team - team whose deck to peek (:team/HOME or :team/AWAY)
  - :count - number of cards to examine (1-5)
  - :phase - :select-count or :place-cards
  - :show - fn [team] to open the modal for a team
  - :close - fn [] to close the modal and reset state
  - :set-count - fn [n] to update the count
  - :set-phase - fn [phase] to change the phase"
  []
  (let [[modal-data set-modal-data] (use-state nil)

        show                        (use-callback
                                     (fn [team]
                                       (set-modal-data {:target-team team
                                                        :count       3
                                                        :phase       :select-count}))
                                     [])

        close                       (use-callback
                                     #(set-modal-data nil)
                                     [])

        set-count                   (use-callback
                                     (fn [n]
                                       (set-modal-data #(assoc % :count n)))
                                     [])

        set-phase                   (use-callback
                                     (fn [phase]
                                       (set-modal-data #(assoc % :phase phase)))
                                     [])]
    (use-memo
     (fn []
       {:open?       (some? modal-data)
        :target-team (:target-team modal-data)
        :count       (:count modal-data)
        :phase       (:phase modal-data)
        :show        show
        :close       close
        :set-count   set-count
        :set-phase   set-phase})
     [modal-data show close set-count set-phase])))
