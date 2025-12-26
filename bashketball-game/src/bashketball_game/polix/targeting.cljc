(ns bashketball-game.polix.targeting
  "Target categorization for UI affordances.

  Provides residual-based target validation for move, pass, and shoot actions.
  Functions return categorized targets with status and reason information
  that the UI can use to show valid/invalid targets with appropriate styling.

  ## Usage

      (require '[bashketball-game.polix.targeting :as targeting])

      ;; Get categorized move targets
      (targeting/categorize-move-targets game-state \"player-1\")
      ;=> {:blocked false, :valid-positions #{[2 3] [2 4] ...}}

      ;; Get categorized pass targets
      (targeting/categorize-pass-targets game-state :team/HOME \"ball-holder-id\")
      ;=> {\"player-2\" {:status :valid}
      ;    \"player-3\" {:status :invalid :reason :off-court}}"
  (:require
   [bashketball-game.movement :as movement]
   [bashketball-game.state :as state]
   [bashketball-game.polix.validation :as validation]
   [bashketball-game.polix.explain :as explain]))

;; =============================================================================
;; Move Target Categorization
;; =============================================================================

(defn categorize-move-targets
  "Categorizes hex positions for move targeting.

  Returns a map describing move availability:
  - `:blocked` - true if player cannot move at all
  - `:reason` - keyword explaining why blocked (when blocked)
  - `:valid-positions` - set of valid move positions (when not blocked)

  Uses BFS reachability from [[bashketball-game.movement/valid-move-positions]]
  for efficiency rather than evaluating each position individually."
  ([game-state player-id]
   (categorize-move-targets game-state player-id nil))
  ([game-state player-id catalog]
   (let [player (state/get-basketball-player game-state player-id)]
     (cond
       (nil? player)
       {:blocked true :reason :player-not-found}

       (nil? (:position player))
       {:blocked true :reason :player-off-court}

       (:exhausted player)
       {:blocked true :reason :player-exhausted}

       :else
       (let [valid-positions (movement/valid-move-positions game-state player-id catalog)]
         (if (empty? valid-positions)
           {:blocked true :reason :no-valid-moves}
           {:blocked false :valid-positions valid-positions}))))))

;; =============================================================================
;; Pass Target Categorization
;; =============================================================================

(defn categorize-pass-targets
  "Categorizes players as valid/invalid pass recipients.

  Returns a map of `{player-id {:status :valid/:invalid, :reason keyword}}`.
  Only considers teammates (same team as ball holder).

  Invalid reasons:
  - `:is-ball-holder` - cannot pass to self
  - `:off-court` - player is not on the court"
  [game-state team ball-holder-id]
  (let [team-players (state/get-all-players game-state team)]
    (into {}
          (for [[player-id player] team-players]
            [player-id
             (cond
               (= player-id ball-holder-id)
               {:status :invalid :reason :is-ball-holder}

               (nil? (:position player))
               {:status :invalid :reason :off-court}

               :else
               {:status :valid})]))))

(defn get-valid-pass-targets
  "Returns a set of player IDs that are valid pass targets.

  Convenience function that extracts only valid targets from
  [[categorize-pass-targets]]."
  [game-state team ball-holder-id]
  (->> (categorize-pass-targets game-state team ball-holder-id)
       (filter (fn [[_ v]] (= :valid (:status v))))
       (map first)
       set))

(defn get-invalid-pass-targets
  "Returns a map of invalid player IDs to their reasons.

  Convenience function for UI to show why targets are invalid."
  [game-state team ball-holder-id]
  (->> (categorize-pass-targets game-state team ball-holder-id)
       (filter (fn [[_ v]] (= :invalid (:status v))))
       (into {})))

;; =============================================================================
;; Action Explanations
;; =============================================================================

(defn get-action-explanation
  "Returns explanation for why an action is unavailable.

  Combines [[validation/validate-action]] with [[explain/explain-residual]]
  to produce UI-friendly explanation messages.

  Returns a vector of `{:key keyword :message string}` maps, or nil if
  the action is available."
  [game-state action]
  (let [residual (validation/validate-action game-state action)]
    (when-not (empty? residual)
      (vec (explain/explain-residual residual)))))

(defn get-action-status
  "Returns the status and explanation for an action.

  Returns a map with:
  - `:available` - true if action can be performed
  - `:explanation` - vector of explanations (when not available)

  Convenience wrapper around [[validation/action-requirements]] that
  adds formatted explanations."
  [game-state action]
  (let [{:keys [status conflicts requirements]} (validation/action-requirements game-state action)]
    (if (= :available status)
      {:available true}
      {:available false
       :explanation (vec (explain/explain-residual (or conflicts requirements)))})))

;; =============================================================================
;; Batch Action Status
;; =============================================================================

(defn get-all-action-statuses
  "Returns status and explanations for multiple action types.

  Takes a game state, team, and optional action-specific parameters.
  Returns a map of `{action-type {:available bool :explanation [...]}}`.

  This is optimized for UI use where multiple action states are needed
  at once."
  [game-state team]
  (let [base-action {:player team}
        action-types [:bashketball/move-player
                      :bashketball/substitute
                      :bashketball/play-card
                      :bashketball/stage-card
                      :bashketball/draw-cards
                      :bashketball/attach-ability]]
    (into {}
          (for [action-type action-types]
            [action-type
             (get-action-status game-state (assoc base-action :type action-type))]))))
