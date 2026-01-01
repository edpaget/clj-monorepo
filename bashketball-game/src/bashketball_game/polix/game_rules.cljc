(ns bashketball-game.polix.game-rules
  "Default game rules as catchall triggers.

  In the event-driven architecture, actions produce request events that flow
  through the trigger system. Card abilities can intercept and modify these
  requests. Catchall rules provide the default behavior when no ability
  intervenes.

  Rules have priority 1000 (low) so card abilities with lower priority
  numbers fire first. The catchall rule produces a terminal `do-*` effect
  that actually modifies game state.

  Example flow:
  ```
  :bashketball/draw-cards effect
    → fires :bashketball/draw-cards.request event
      → card ability triggers (priority 100) may add effects
      → catchall rule (priority 1000) produces :bashketball/do-draw-cards
        → terminal effect modifies state
  ```"
  (:require [polix.triggers.core :as triggers]))

;;; ---------------------------------------------------------------------------
;;; Catchall Rules
;;; ---------------------------------------------------------------------------

(def draw-cards-rule
  "Default rule for draw-cards: produces terminal do-draw-cards effect.

  Resolves `:player` and `:count` from the event data."
  {:id "rule/draw-cards"
   :event-types #{:bashketball/draw-cards.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-draw-cards
            :player [:ctx :event :player]
            :count [:ctx :event :count]}})

;;; ---------------------------------------------------------------------------
;;; Rule Registry
;;; ---------------------------------------------------------------------------

(def default-rules
  "All default game rules. Add new rules here as more actions are migrated."
  [draw-cards-rule])

(defn register-game-rules!
  "Registers all default game rules in the trigger registry.

  Call once during game initialization after creating the registry.
  Returns the updated registry with all rules registered."
  [registry]
  (reduce
   (fn [reg rule]
     (triggers/register-trigger reg rule "game-rules" nil nil))
   registry
   default-rules))

(defn get-rule-ids
  "Returns a set of all rule IDs for testing/debugging."
  []
  (set (map :id default-rules)))
