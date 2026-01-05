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

  Uses `:bashketball-fn/draw-count` to compute the actual draw count,
  allowing card abilities to modify the base count."
  {:id "rule/draw-cards"
   :event-types #{:bashketball/draw-cards.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-draw-cards
            :player [:ctx :event :player]
            :count [:bashketball-fn/draw-count
                    [:ctx :event :player]
                    [:ctx :event :count]]}})

(def move-step-rule
  "Default rule for move-step: produces terminal do-move-step effect.

  Computes movement cost declaratively using `:bashketball-fn/step-cost`,
  which factors in base cost and ZoC penalties based on game state."
  {:id "rule/move-step"
   :event-types #{:bashketball/player-entering-hex.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-move-step
            :player-id [:ctx :event :player-id]
            :to-position [:ctx :event :to-position]
            :cost [:bashketball-fn/step-cost
                   [:ctx :event :player-id]
                   [:ctx :event :to-position]]}})

;;; ---------------------------------------------------------------------------
;;; Phase Transition Rules
;;; ---------------------------------------------------------------------------

(def phase-starting-rule
  "Default rule for phase starting: sets the game phase.

  Fires after `:bashketball/phase-starting.request` event."
  {:id "rule/phase-starting"
   :event-types #{:bashketball/phase-starting.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-set-phase
            :phase [:ctx :event :to-phase]}})

;;; ---------------------------------------------------------------------------
;;; Turn Transition Rules
;;; ---------------------------------------------------------------------------

(def turn-ending-rule
  "Default rule for turn ending: advances the turn counter and swaps active player.

  Fires after `:bashketball/turn-ending.request` event."
  {:id "rule/turn-ending"
   :event-types #{:bashketball/turn-ending.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-advance-turn}})

(def turn-starting-rule
  "Default rule for turn starting: transitions to UPKEEP phase.

  Fires after `:bashketball/turn-starting.request` event.
  Auto-sequences into UPKEEP phase for the new turn."
  {:id "rule/turn-starting"
   :event-types #{:bashketball/turn-starting.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/transition-phase
            :to-phase :phase/UPKEEP}})

;;; ---------------------------------------------------------------------------
;;; Choice Continuation Rules
;;; ---------------------------------------------------------------------------

(def choice-submitted-rule
  "Default rule for choice submission: executes continuation if present.

  Fires after `:bashketball/choice.submitted` event. Binds `:choice/selected`
  in context before executing the continuation effect."
  {:id "rule/choice-submitted"
   :event-types #{:bashketball/choice.submitted}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/execute-choice-continuation}})

;;; ---------------------------------------------------------------------------
;;; Play Card Rules
;;; ---------------------------------------------------------------------------

(def fuel-discarded-rule
  "Default rule for card-discarded-as-fuel: fires signal effect if present.

  When a coaching card with a signal is discarded as fuel, this rule
  applies the signal effect. The signal effect and context are provided
  in the event by the play-card orchestration."
  {:id "rule/fuel-discarded"
   :event-types #{:bashketball/card-discarded-as-fuel.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-fire-signal
            :signal-effect [:ctx :event :signal-effect]
            :signal-context [:ctx :event :signal-context]}})

;;; ---------------------------------------------------------------------------
;;; Rule Registry
;;; ---------------------------------------------------------------------------

(def default-rules
  "All default game rules. Add new rules here as more actions are migrated."
  [draw-cards-rule
   move-step-rule
   phase-starting-rule
   turn-ending-rule
   turn-starting-rule
   choice-submitted-rule
   fuel-discarded-rule])

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
