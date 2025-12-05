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
  - :starter-id - selected starter to substitute out, or nil
  - :set-starter - fn [id] to select a starter
  - :enter - function to enter substitution mode
  - :cancel - function to exit substitution mode and clear selection"
  []
  (let [[active set-active]         (use-state false)
        [starter-id set-starter-id] (use-state nil)
        set-starter                 (use-callback #(set-starter-id %) [])
        enter                       (use-callback
                                     (fn []
                                       (set-active true)
                                       (set-starter-id nil))
                                     [])
        cancel                      (use-callback
                                     (fn []
                                       (set-active false)
                                       (set-starter-id nil))
                                     [])]
    (use-memo
     (fn []
       {:active     active
        :starter-id starter-id
        :set-starter set-starter
        :enter      enter
        :cancel     cancel})
     [active starter-id set-starter enter cancel])))
