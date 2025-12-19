(ns bashketball-game-ui.hooks.use-selection-machine
  "React hook wrapper for the selection state machine.

  Provides a React-friendly interface to the pure selection state machine,
  handling state updates and action dispatch."
  (:require
   [bashketball-game-ui.context.dispatch :refer [use-dispatch]]
   [bashketball-game-ui.game.selection-machine :as sm]
   [uix.core :as uix]))

(defn use-selection-machine
  "Hook providing selection state and event dispatch.

  Returns a map with:
  - `:mode` - Current selection mode keyword (e.g., :idle, :player-selected)
  - `:data` - Selection data (selected player, cards, etc.)
  - `:send` - Function to send events to the state machine
  - `:can?` - Predicate to check if an event type is valid in current state

  Events are maps with `:type` and optional `:data` keys.
  When a transition triggers an action, it's automatically dispatched."
  []
  (let [[state set-state] (uix/use-state (sm/init))
        dispatch          (use-dispatch)

        send              (uix/use-callback
                           (fn [event]
                             (set-state
                              (fn [current]
                                (let [result (sm/transition current event)]
                                  (when-let [action (:action result)]
                                    (dispatch action))
                                  (select-keys result [:state :data])))))
                           [dispatch])

        can?              (uix/use-callback
                           (fn [event-type]
                             (contains? (sm/valid-events (:state state)) event-type))
                           [state])]

    {:mode (:state state)
     :data (:data state)
     :send send
     :can? can?}))
