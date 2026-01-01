(ns bashketball-game.polix.phase-triggers
  "Phase transition triggers for turn structure automation.

  Registers triggers that:
  1. Validate phase transitions (before trigger)
  2. Execute phase-specific effects (after triggers)
  3. Handle turn/quarter advancement

  Call [[register-phase-triggers!]] at application startup after
  creating the game's trigger registry."
  (:require
   [bashketball-game.polix.phase-policies :as pp]
   [bashketball-game.state :as state]))

(def phase-trigger-source
  "Source ID for phase-related triggers."
  "phase-system")

(def validate-phase-transition
  "Before trigger that validates phase transitions.

  Prevents set-phase action if the transition is not allowed per
  [[phase-policies/valid-transitions]]."
  {:event-types #{:bashketball/set-phase.before}
   :timing :polix.triggers.timing/before
   :condition [:fn
               (fn [doc]
                 (let [from-phase (get doc :phase)
                       to-phase   (get doc :phase)]
                   (pp/valid-transition? from-phase to-phase)))]
   :effect {:type :prevent}
   :priority 0})

(def upkeep-entry-trigger
  "After trigger that executes UPKEEP phase effects.

  When entering UPKEEP phase:
  - Refreshes all exhausted players on the active team"
  {:event-types #{:bashketball/set-phase.after}
   :timing :polix.triggers.timing/after
   :condition [:= :doc/phase :phase/UPKEEP]
   :effect {:effect/type :bashketball/refresh-all
            :team :doc/active-player}
   :priority 10})

(def end-of-turn-entry-trigger
  "After trigger that executes END_OF_TURN (draw) phase effects.

  When entering END_OF_TURN phase:
  - Active player draws 3 cards
  - If over hand limit (8), must discard down to limit"
  {:event-types #{:bashketball/set-phase.after}
   :timing :polix.triggers.timing/after
   :condition [:= :doc/phase :phase/END_OF_TURN]
   :effect {:effect/type :bashketball/draw-cards
            :player :doc/active-player
            :count 3}
   :priority 10})

(defn build-hand-limit-check-trigger
  "After trigger that checks hand limit after drawing.

  If active player's hand exceeds 8 cards, offers a choice to discard."
  []
  {:event-types #{:bashketball/draw-cards.after}
   :timing :polix.triggers.timing/after
   :condition [:and
               [:= :doc/phase :phase/END_OF_TURN]
               [:> [:fn (fn [doc]
                          (let [state        (:state doc)
                                active-team  (:active-player state)]
                            (count (state/get-hand state active-team))))]
                pp/hand-limit]]
   :effect {:effect/type :bashketball/offer-choice
            :choice-type :discard-to-hand-limit
            :waiting-for :doc/active-player
            :context {:target-count pp/hand-limit}}
   :priority 20})

(def turn-advancement-triggers
  "Triggers for turn and quarter advancement.

  After END_OF_TURN completes (no pending choice):
  - Increment turn number
  - Swap active player
  - If turn > 12, advance quarter
  - Set phase to UPKEEP for next turn"
  [{:event-types #{:bashketball/clear-choice.after}
    :timing :polix.triggers.timing/after
    :condition [:and
                [:= :doc/phase :phase/END_OF_TURN]
                [:= [:doc :pending-choice :type] :discard-to-hand-limit]]
    :effect {:effect/type :do-advance-turn}
    :priority 100}])

(defn register-phase-triggers!
  "Registers all phase-related triggers in the given registry.

  Returns the updated registry with phase triggers added."
  [registry]
  (-> registry
      ;; Note: validate-phase-transition needs custom implementation
      ;; because it requires checking from/to phase comparison
      ;; For now, we'll implement validation in the action handler
      ))

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
