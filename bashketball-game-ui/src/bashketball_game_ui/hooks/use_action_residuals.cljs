(ns bashketball-game-ui.hooks.use-action-residuals
  "Hook for computing action residuals with memoization.

  Provides residual-based action availability and targeting info for
  the UI to show valid/invalid targets and action explanations."
  (:require
   [bashketball-game-ui.hooks.selectors :as s]
   [bashketball-game.polix.targeting :as targeting]
   [uix.core :refer [use-memo]]))

(defn use-action-residuals
  "Returns residual-based action information.

  Consumes game state from context and computes:
  - `:action-statuses` - Map of action-type to {:available bool :explanation [...]}
  - `:move-targets` - Move target categorization {:blocked bool :valid-positions #{}}
  - `:pass-targets` - Pass target categorization {player-id {:status :reason}}
  - `:valid-pass-target-ids` - Set of valid pass target player IDs
  - `:invalid-pass-target-ids` - Set of invalid pass target player IDs

  All computations are memoized based on their dependencies."
  []
  (let [game-state                                                                 (s/use-game-state)
        my-team                                                                    (s/use-my-team)
        {:keys [mode data]}                                                        (s/use-selection)

        selected-player-id                                                         (:player-id data)
        ball-holder-id                                                             (get-in game-state [:ball :holder-id])
        pass-active                                                                (= mode :targeting-pass)

        ;; Memoized action statuses for all action types
        action-statuses
        (use-memo
         #(when game-state
            (targeting/get-all-action-statuses game-state my-team))
         [game-state my-team])

        ;; Memoized move target categorization
        move-targets
        (use-memo
         #(when (and game-state selected-player-id)
            (targeting/categorize-move-targets game-state selected-player-id))
         [game-state selected-player-id])

        ;; Memoized pass target categorization
        pass-targets
        (use-memo
         #(when (and game-state pass-active my-team ball-holder-id)
            (targeting/categorize-pass-targets game-state my-team ball-holder-id))
         [game-state pass-active my-team ball-holder-id])

        ;; Convenience sets for valid/invalid pass targets
        valid-pass-target-ids
        (use-memo
         #(when pass-targets
            (->> pass-targets
                 (filter (fn [[_ v]] (= :valid (:status v))))
                 (map first)
                 set))
         [pass-targets])

        invalid-pass-target-ids
        (use-memo
         #(when pass-targets
            (->> pass-targets
                 (filter (fn [[_ v]] (= :invalid (:status v))))
                 (map first)
                 set))
         [pass-targets])]

    {:action-statuses       action-statuses
     :move-targets          move-targets
     :pass-targets          pass-targets
     :valid-pass-target-ids valid-pass-target-ids
     :invalid-pass-target-ids invalid-pass-target-ids}))

(defn use-action-explanation
  "Returns explanation for a specific action type.

  Convenience hook for getting explanation for a single action.
  Returns nil if action is available, or vector of explanations if not."
  [action-type]
  (let [{:keys [action-statuses]} (use-action-residuals)]
    (when-let [status (get action-statuses action-type)]
      (when-not (:available status)
        (:explanation status)))))

(defn use-move-blocked?
  "Returns true if the selected player cannot move, with reason.

  Returns nil if no player selected, or {:blocked bool :reason keyword}."
  []
  (let [{:keys [move-targets]} (use-action-residuals)]
    move-targets))

(defn use-skill-test-state
  "Returns the current skill test and choice state for UI rendering.

  Returns nil when no test/choice is active, or a map with:
  - `:test` - the pending skill test (if any)
  - `:phase` - :awaiting-fate, :fate-revealed, or :awaiting-choice
  - `:choice` - the pending choice (if any)
  - `:waiting-for` - team that needs to respond to choice"
  []
  (let [game-state (s/use-game-state)]
    (use-memo
     #(when game-state
        (let [test   (:pending-skill-test game-state)
              choice (:pending-choice game-state)]
          (when (or test choice)
            {:test        test
             :phase       (cond
                            choice :awaiting-choice
                            (and test (:fate test)) :fate-revealed
                            test :awaiting-fate
                            :else nil)
             :choice      choice
             :waiting-for (:waiting-for choice)})))
     [game-state])))

(defn use-skill-test-active?
  "Returns true if there's a skill test in progress."
  []
  (let [skill-test-state (use-skill-test-state)]
    (some? (:test skill-test-state))))

(defn use-choice-pending?
  "Returns true if there's a choice awaiting player input."
  []
  (let [skill-test-state (use-skill-test-state)]
    (some? (:choice skill-test-state))))
