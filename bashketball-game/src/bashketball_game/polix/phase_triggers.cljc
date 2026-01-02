(ns bashketball-game.polix.phase-triggers
  "Phase transition triggers for turn structure automation.

  Registers triggers that respond to phase transition events in the
  event-driven architecture:
  1. Validate phase transitions (on phase-starting.request)
  2. Execute phase-specific effects (on phase-starting.request)
  3. Handle hand limit checks (on draw-cards.request)

  Call [[register-phase-triggers!]] at application startup after
  creating the game's trigger registry."
  (:require
   [bashketball-game.polix.phase-policies :as pp]
   [bashketball-game.state :as state]))

(def phase-trigger-source
  "Source ID for phase-related triggers."
  "phase-system")

(def validate-phase-transition
  "Trigger that validates phase transitions.

  Fires on `:bashketball/phase-starting.request` events and prevents invalid
  transitions based on [[phase-policies/valid-transitions]]. Uses priority 0
  to fire before any other phase triggers."
  {:event-types #{:bashketball/phase-starting.request}
   :timing :polix.triggers.timing/before
   :condition [:fn
               (fn [doc]
                 (let [from-phase (:phase doc)
                       to-phase   (get-in doc [:event :to-phase])]
                   (not (pp/valid-transition? from-phase to-phase))))]
   :effect {:type :prevent}
   :priority 0})

(def upkeep-entry-trigger
  "Trigger that executes UPKEEP phase effects.

  Fires on `:bashketball/phase-starting.request` when entering UPKEEP:
  - Refreshes all exhausted players on the active team

  Uses priority 100 to fire before the catchall rule (priority 1000)."
  {:event-types #{:bashketball/phase-starting.request}
   :timing :polix.triggers.timing/after
   :condition [:= [:ctx :event :to-phase] :phase/UPKEEP]
   :effect {:type :bashketball/refresh-all
            :team [:ctx :event :team]}
   :priority 100})

(def end-of-turn-entry-trigger
  "Trigger that executes END_OF_TURN (draw) phase effects.

  Fires on `:bashketball/phase-starting.request` when entering END_OF_TURN:
  - Active player draws 3 cards

  Uses priority 100 to fire before the catchall rule (priority 1000).
  Hand limit checking is handled by [[build-hand-limit-check-trigger]]."
  {:event-types #{:bashketball/phase-starting.request}
   :timing :polix.triggers.timing/after
   :condition [:= [:ctx :event :to-phase] :phase/END_OF_TURN]
   :effect {:type :bashketball/draw-cards
            :player [:ctx :event :team]
            :count 3}
   :priority 100})

(def hand-limit-check-trigger
  "Trigger that checks hand limit after drawing cards.

  Fires on `:bashketball/draw-cards.request` events. If the active player's
  hand exceeds 8 cards, fires the hand-limit check effect which may
  offer a discard choice.

  Uses `:bashketball-fn/cards-to-discard` to compute excess cards."
  {:event-types #{:bashketball/draw-cards.request}
   :timing :polix.triggers.timing/after
   :condition [:> [:bashketball-fn/cards-to-discard [:ctx :event :player]] 0]
   :effect {:type :bashketball/check-hand-limit
            :team [:ctx :event :player]}
   :priority 200})

(def phase-triggers
  "All phase-related triggers for registration."
  [validate-phase-transition
   upkeep-entry-trigger
   end-of-turn-entry-trigger
   hand-limit-check-trigger])

(defn register-phase-triggers!
  "Registers all phase-related triggers in the given registry.

  Returns the updated registry with phase triggers added."
  [registry register-fn]
  (reduce
   (fn [reg trigger]
     (register-fn reg trigger phase-trigger-source nil nil))
   registry
   phase-triggers))

(defn check-hand-limit
  "Checks if player needs to discard cards due to hand limit.

  Returns an effect to offer discard choice if over limit, nil otherwise."
  [game-state team]
  (let [hand-size (count (state/get-hand game-state team))]
    (when (> hand-size pp/hand-limit)
      {:effect/type :bashketball/offer-choice
       :choice-type :discard-to-hand-limit
       :waiting-for team
       :options (mapv (fn [card]
                        {:id (:instance-id card)
                         :label (:card-slug card)})
                      (state/get-hand game-state team))
       :context {:discard-count (- hand-size pp/hand-limit)
                 :target-count pp/hand-limit}})))

(defn should-advance-quarter?
  "Returns true if the current turn exceeds turns-per-quarter."
  [game-state]
  (> (:turn-number game-state) pp/turns-per-quarter))

(defn should-end-game?
  "Returns true if all quarters have been played."
  [game-state]
  (and (should-advance-quarter? game-state)
       (>= (:quarter game-state 1) pp/quarters-per-game)))
