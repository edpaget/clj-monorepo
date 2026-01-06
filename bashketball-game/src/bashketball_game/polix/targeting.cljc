(ns bashketball-game.polix.targeting
  "Target categorization for UI affordances.

  Provides residual-based target validation for move, pass, shoot, and
  standard action targets. Functions return categorized targets with status
  and reason information that the UI can use to show valid/invalid targets
  with appropriate styling.

  ## Usage

      (require '[bashketball-game.polix.targeting :as targeting])

      ;; Get categorized move targets
      (targeting/categorize-move-targets game-state \"player-1\")
      ;=> {:blocked false, :valid-positions #{[2 3] [2 4] ...}}

      ;; Get categorized pass targets
      (targeting/categorize-pass-targets game-state :team/HOME \"ball-holder-id\")
      ;=> {\"player-2\" {:status :valid}
      ;    \"player-3\" {:status :invalid :reason :off-court}}

      ;; Check shoot availability
      (targeting/categorize-shoot-availability game-state \"player-1\")
      ;=> {:available true}

      ;; Get block targets
      (targeting/categorize-block-targets game-state \"player-1\")
      ;=> {\"opponent-1\" {:status :valid}
      ;    \"opponent-2\" {:status :invalid :reason :not-adjacent}}"
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.movement :as movement]
   [bashketball-game.polix.explain :as explain]
   [bashketball-game.polix.validation :as validation]
   [bashketball-game.state :as state]))

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
  for efficiency rather than evaluating each position individually.

  Requires context with `:state` and `:registry`."
  [{:keys [state] :as ctx} player-id]
  {:pre [(some? state) (some? (:registry ctx))]}
  (let [player (state/get-basketball-player state player-id)]
    (cond
      (nil? player)
      {:blocked true :reason :player-not-found}

      (nil? (:position player))
      {:blocked true :reason :player-off-court}

      (:exhausted player)
      {:blocked true :reason :player-exhausted}

      :else
      (let [valid-positions (movement/valid-move-positions ctx player-id)]
        (if (empty? valid-positions)
          {:blocked true :reason :no-valid-moves}
          {:blocked false :valid-positions valid-positions})))))

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
  (let [base-action  {:player team}
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

;; =============================================================================
;; Standard Action Targeting
;; =============================================================================

(defn- get-target-basket-position
  "Returns the basket position that a team shoots at.

  HOME team shoots at [2 13], AWAY team shoots at [2 0]."
  [team]
  (if (= team :team/HOME)
    [2 13]
    [2 0]))

(defn categorize-shoot-availability
  "Categorizes whether a player can shoot.

  Returns a map with:
  - `:available` - true if player can shoot
  - `:reason` - keyword explaining why unavailable (when not available)

  Checks:
  - Player has the ball
  - Player is within 7 hexes of basket
  - Player is not exhausted"
  [game-state player-id]
  (let [player (state/get-basketball-player game-state player-id)
        ball   (state/get-ball game-state)
        team   (state/get-basketball-player-team game-state player-id)]
    (cond
      (nil? player)
      {:available false :reason :player-not-found}

      (nil? (:position player))
      {:available false :reason :player-off-court}

      (or (not= (:status ball) :ball-status/POSSESSED)
          (not= (:holder-id ball) player-id))
      {:available false :reason :not-ball-carrier}

      (:exhausted player)
      {:available false :reason :exhausted}

      (nil? team)
      {:available false :reason :unknown-team}

      :else
      (let [basket-pos (get-target-basket-position team)
            distance   (board/hex-distance (:position player) basket-pos)]
        (if (> distance 7)
          {:available false :reason :out-of-range}
          {:available true})))))

(defn categorize-block-targets
  "Categorizes opponents as valid/invalid block targets.

  Block requires:
  - Adjacent to target (distance 1)
  - Target has the ball
  - Target is within 4 hexes of their basket

  Returns a map of `{player-id {:status :valid/:invalid, :reason keyword}}`."
  [game-state actor-id]
  (let [actor      (state/get-basketball-player game-state actor-id)
        actor-team (state/get-basketball-player-team game-state actor-id)
        ball       (state/get-ball game-state)
        opp-team   (if (= actor-team :team/HOME) :team/AWAY :team/HOME)
        opponents  (state/get-all-players game-state opp-team)]
    (if (nil? (:position actor))
      {}
      (into {}
            (for [[pid player] opponents]
              [pid
               (let [holder?   (and (= (:status ball) :ball-status/POSSESSED)
                                    (= (:holder-id ball) pid))
                     on-court? (some? (:position player))
                     adjacent? (and on-court?
                                    (= 1 (board/hex-distance (:position actor)
                                                             (:position player))))
                     basket    (get-target-basket-position opp-team)
                     in-range? (and on-court?
                                    (<= (board/hex-distance (:position player) basket) 4))]
                 (cond
                   (not on-court?)
                   {:status :invalid :reason :off-court}

                   (not holder?)
                   {:status :invalid :reason :not-ball-carrier}

                   (not adjacent?)
                   {:status :invalid :reason :not-adjacent}

                   (not in-range?)
                   {:status :invalid :reason :out-of-basket-range}

                   :else
                   {:status :valid}))])))))

(defn categorize-steal-targets
  "Categorizes opponents as valid/invalid steal targets.

  Steal requires:
  - Adjacent to target (distance 1)
  - Target has the ball
  - Target is on opposing team

  Returns a map of `{player-id {:status :valid/:invalid, :reason keyword}}`."
  [game-state actor-id]
  (let [actor      (state/get-basketball-player game-state actor-id)
        actor-team (state/get-basketball-player-team game-state actor-id)
        ball       (state/get-ball game-state)
        opp-team   (if (= actor-team :team/HOME) :team/AWAY :team/HOME)
        opponents  (state/get-all-players game-state opp-team)]
    (if (nil? (:position actor))
      {}
      (into {}
            (for [[pid player] opponents]
              [pid
               (let [holder?   (and (= (:status ball) :ball-status/POSSESSED)
                                    (= (:holder-id ball) pid))
                     on-court? (some? (:position player))
                     adjacent? (and on-court?
                                    (= 1 (board/hex-distance (:position actor)
                                                             (:position player))))]
                 (cond
                   (not on-court?)
                   {:status :invalid :reason :off-court}

                   (not holder?)
                   {:status :invalid :reason :not-ball-carrier}

                   (not adjacent?)
                   {:status :invalid :reason :not-adjacent}

                   :else
                   {:status :valid}))])))))

(defn categorize-screen-targets
  "Categorizes opponents as valid/invalid screen targets.

  Screen requires:
  - Adjacent to target (distance 1)
  - Target is on opposing team
  - Actor is not exhausted

  Returns a map of `{player-id {:status :valid/:invalid, :reason keyword}}`."
  [game-state actor-id]
  (let [actor      (state/get-basketball-player game-state actor-id)
        actor-team (state/get-basketball-player-team game-state actor-id)
        opp-team   (if (= actor-team :team/HOME) :team/AWAY :team/HOME)
        opponents  (state/get-all-players game-state opp-team)]
    (cond
      (nil? (:position actor))
      {}

      (:exhausted actor)
      (into {}
            (for [[pid _] opponents]
              [pid {:status :invalid :reason :actor-exhausted}]))

      :else
      (into {}
            (for [[pid player] opponents]
              [pid
               (let [on-court? (some? (:position player))
                     adjacent? (and on-court?
                                    (= 1 (board/hex-distance (:position actor)
                                                             (:position player))))]
                 (cond
                   (not on-court?)
                   {:status :invalid :reason :off-court}

                   (not adjacent?)
                   {:status :invalid :reason :not-adjacent}

                   :else
                   {:status :valid}))])))))

(defn categorize-check-targets
  "Categorizes opponents as valid/invalid check targets.

  Check requires:
  - Adjacent to target (distance 1)
  - Target is on opposing team
  - Actor is not exhausted

  Returns a map of `{player-id {:status :valid/:invalid, :reason keyword}}`."
  [game-state actor-id]
  ;; Check has same requirements as screen
  (categorize-screen-targets game-state actor-id))

(defn get-valid-standard-action-targets
  "Returns valid targets for a standard action mode.

  Takes the action mode (`:shoot`, `:block`, `:pass`, `:steal`, `:screen`, `:check`)
  and returns a set of valid target IDs, or `:self` for self-targeting actions."
  [game-state actor-id action-mode]
  (case action-mode
    :shoot
    (let [{:keys [available]} (categorize-shoot-availability game-state actor-id)]
      (if available #{:self} #{}))

    :block
    (->> (categorize-block-targets game-state actor-id)
         (filter (fn [[_ v]] (= :valid (:status v))))
         (map first)
         set)

    :pass
    (let [team (state/get-basketball-player-team game-state actor-id)]
      (get-valid-pass-targets game-state team actor-id))

    :steal
    (->> (categorize-steal-targets game-state actor-id)
         (filter (fn [[_ v]] (= :valid (:status v))))
         (map first)
         set)

    :screen
    (->> (categorize-screen-targets game-state actor-id)
         (filter (fn [[_ v]] (= :valid (:status v))))
         (map first)
         set)

    :check
    (->> (categorize-check-targets game-state actor-id)
         (filter (fn [[_ v]] (= :valid (:status v))))
         (map first)
         set)

    ;; Default - unknown action
    #{}))
