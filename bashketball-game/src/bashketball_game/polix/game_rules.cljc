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
;;; Player State Rules
;;; ---------------------------------------------------------------------------

(def exhaust-player-rule
  "Default rule for exhaust-player: marks player as exhausted."
  {:id "rule/exhaust-player"
   :event-types #{:bashketball/exhaust-player.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-exhaust-player
            :player-id [:ctx :event :player-id]}})

(def refresh-player-rule
  "Default rule for refresh-player: removes exhaustion from player."
  {:id "rule/refresh-player"
   :event-types #{:bashketball/refresh-player.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-refresh-player
            :player-id [:ctx :event :player-id]}})

;;; ---------------------------------------------------------------------------
;;; Ball State Rules
;;; ---------------------------------------------------------------------------

(def set-ball-possessed-rule
  "Default rule for set-ball-possessed: gives ball to a player."
  {:id "rule/set-ball-possessed"
   :event-types #{:bashketball/set-ball-possessed.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-set-ball-possessed
            :holder-id [:ctx :event :holder-id]}})

(def set-ball-loose-rule
  "Default rule for set-ball-loose: sets ball loose at a position."
  {:id "rule/set-ball-loose"
   :event-types #{:bashketball/set-ball-loose.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-set-ball-loose
            :position [:ctx :event :position]}})

;;; ---------------------------------------------------------------------------
;;; Card & Score Rules
;;; ---------------------------------------------------------------------------

(def discard-cards-rule
  "Default rule for discard-cards: moves cards from hand to discard."
  {:id "rule/discard-cards"
   :event-types #{:bashketball/discard-cards.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-discard-cards
            :player [:ctx :event :player]
            :instance-ids [:ctx :event :instance-ids]}})

(def add-score-rule
  "Default rule for add-score: adds points to a team's score."
  {:id "rule/add-score"
   :event-types #{:bashketball/add-score.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-add-score
            :team [:ctx :event :team]
            :points [:ctx :event :points]}})

;;; ---------------------------------------------------------------------------
;;; Modifier Rules
;;; ---------------------------------------------------------------------------

(def add-modifier-rule
  "Default rule for add-modifier: adds a stat modifier to a player."
  {:id "rule/add-modifier"
   :event-types #{:bashketball/add-modifier.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-add-modifier
            :player-id [:ctx :event :player-id]
            :stat [:ctx :event :stat]
            :amount [:ctx :event :amount]
            :source [:ctx :event :source]
            :expires-at [:ctx :event :expires-at]}})

(def remove-modifier-rule
  "Default rule for remove-modifier: removes a modifier from a player."
  {:id "rule/remove-modifier"
   :event-types #{:bashketball/remove-modifier.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-remove-modifier
            :player-id [:ctx :event :player-id]
            :modifier-id [:ctx :event :modifier-id]}})

(def clear-modifiers-rule
  "Default rule for clear-modifiers: clears all modifiers from a player."
  {:id "rule/clear-modifiers"
   :event-types #{:bashketball/clear-modifiers.request}
   :timing :polix.triggers.timing/after
   :priority 1000
   :condition nil
   :effect {:type :bashketball/do-clear-modifiers
            :player-id [:ctx :event :player-id]}})

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
  [;; Core game flow
   draw-cards-rule
   move-step-rule
   phase-starting-rule
   turn-ending-rule
   turn-starting-rule
   choice-submitted-rule
   ;; Player state
   exhaust-player-rule
   refresh-player-rule
   ;; Ball state
   set-ball-possessed-rule
   set-ball-loose-rule
   ;; Cards & scoring
   discard-cards-rule
   add-score-rule
   ;; Modifiers
   add-modifier-rule
   remove-modifier-rule
   clear-modifiers-rule
   ;; Play card
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
