(ns bashketball-game-ui.hooks.use-game-ui
  "Custom hooks for game UI state management.

  Groups related state into reusable hooks that encapsulate state
  and state transitions for common UI patterns.

  Note: Selection, discard, substitute, and peek interactions have been migrated
  to pure state machines. See:
  - [[bashketball-game-ui.game.selection-machine]]
  - [[bashketball-game-ui.game.discard-machine]]
  - [[bashketball-game-ui.game.substitute-machine]]
  - [[bashketball-game-ui.game.peek-machine]]"
  (:require [uix.core :refer [use-state use-callback use-memo]]))

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
