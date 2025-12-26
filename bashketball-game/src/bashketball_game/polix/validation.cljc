(ns bashketball-game.polix.validation
  "Action validation using polix policies with residuals.

  Provides functions to validate actions against their precondition policies,
  check availability, and explain validation failures in UI-friendly terms.

  ## Key Functions

  - [[validate-action]] - Validates an action, returns a residual
  - [[action-available?]] - Returns true if action can be performed
  - [[action-requirements]] - Returns validation status and requirements
  - [[available-actions]] - Returns set of available action types

  ## Usage

      (require '[bashketball-game.polix.validation :as validation])

      ;; Check if a specific action is valid
      (validation/action-available?
        game-state
        {:type :bashketball/move-player :player-id \"p1\" :position [2 4]})

      ;; Get detailed requirements
      (validation/action-requirements game-state action)
      ;=> {:status :available}
      ;=> {:status :impossible :conflicts {...}}
      ;=> {:status :partial :requirements {...}}"
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.movement :as movement]
   [bashketball-game.polix.policies :as policies]
   [bashketball-game.state :as state]
   [polix.core :as polix]))

;; =============================================================================
;; Document Building
;; =============================================================================

(defn- compute-player-context
  "Computes pre-evaluated values for a player-based action.

  Pre-computes operator results to avoid passing game-state maps through
  polix evaluation (which confuses maps with residuals)."
  [game-state player-id]
  (when player-id
    (let [player (state/get-basketball-player game-state player-id)]
      {:player-on-court (some? (:position player))
       :player-exhausted (boolean (:exhausted player))
       :player-team (state/get-basketball-player-team game-state player-id)})))

(defn- compute-target-player-context
  "Computes pre-evaluated values for target player."
  [game-state target-player-id]
  (when target-player-id
    (let [player (state/get-basketball-player game-state target-player-id)]
      {:target-player-on-court (some? (:position player))
       :target-player-exhausted (boolean (:exhausted player))
       :target-player-team (state/get-basketball-player-team game-state target-player-id)})))

(defn- compute-substitute-context
  "Computes pre-evaluated values for substitute action."
  [game-state on-court-id off-court-id]
  (when (and on-court-id off-court-id)
    (let [on-court-player  (state/get-basketball-player game-state on-court-id)
          off-court-player (state/get-basketball-player game-state off-court-id)]
      {:on-court-on-court (some? (:position on-court-player))
       :off-court-on-court (some? (:position off-court-player))
       :on-court-team (state/get-basketball-player-team game-state on-court-id)
       :off-court-team (state/get-basketball-player-team game-state off-court-id)})))

(defn- compute-move-context
  "Computes pre-evaluated values for move action."
  [game-state player-id position]
  (when (and player-id position)
    {:valid-position (board/valid-position? position)
     :can-move-to (movement/can-move-to? game-state player-id position)}))

(defn- compute-card-context
  "Computes pre-evaluated values for card actions."
  [game-state team instance-id]
  (when (and team instance-id)
    {:card-in-hand (boolean
                    (some #(= (:instance-id %) instance-id)
                          (state/get-hand game-state team)))
     :actions-remaining (get-in game-state [:players team :actions-remaining] 0)}))

(defn- build-validation-document
  "Builds a document for policy validation from game state and action.

  Creates a flat document with pre-computed boolean/value results instead
  of passing raw game-state through polix (which confuses maps with residuals).

  Document keys depend on action type but typically include:
  - Pre-computed operator results (player-on-court, player-exhausted, etc.)
  - Action parameters (position, instance-id, etc.)
  - `:active-player` - the currently active team"
  [game-state action]
  (let [action-type (:type action)
        base        {:active-player (:active-player game-state)}]
    (merge
     base
     (dissoc action :type)
     (case action-type
       :bashketball/move-player
       (merge
        (compute-player-context game-state (:player-id action))
        (compute-move-context game-state (:player-id action) (:position action)))

       :bashketball/substitute
       (compute-substitute-context game-state (:on-court-id action) (:off-court-id action))

       :bashketball/exhaust-player
       (compute-player-context game-state (:player-id action))

       :bashketball/refresh-player
       (compute-player-context game-state (:player-id action))

       (:bashketball/play-card :bashketball/stage-card :bashketball/attach-ability)
       (merge
        (compute-card-context game-state (:player action) (:instance-id action))
        (when (:target-player-id action)
          (compute-target-player-context game-state (:target-player-id action))))

       :bashketball/draw-cards
       {}

       :bashketball/set-ball-possessed
       (let [holder-id (:holder-id action)
             player    (when holder-id (state/get-basketball-player game-state holder-id))]
         {:holder-on-court (some? (:position player))})

       ;; Default: no pre-computation
       {}))))

;; =============================================================================
;; Core Validation API
;; =============================================================================

(defn validate-action
  "Validates an action against its precondition policy.

  Returns a residual:
  - `{}` if action is valid (all constraints satisfied)
  - `{:path [constraints]}` if validation fails or requirements missing

  Actions without policies return `{}` (no semantic validation).

  Example:

      (validate-action game-state {:type :bashketball/move-player
                                   :player-id \"p1\"
                                   :position [2 4]})
      ;=> {} ; valid
      ;=> {[:exhausted] [[:conflict [:= false] true]]} ; player exhausted"
  [game-state action]
  (if-let [policy (policies/get-policy (:type action))]
    (let [document (build-validation-document game-state action)]
      (polix/unify policy document))
    {}))

(defn action-available?
  "Returns true if the action can be performed.

  Shorthand for `(polix/satisfied? (validate-action state action))`.

  Example:

      (action-available? game-state {:type :bashketball/move-player
                                     :player-id \"p1\"
                                     :position [2 4]})
      ;=> true or false"
  [game-state action]
  (polix/satisfied? (validate-action game-state action)))

(defn action-requirements
  "Returns the validation status and any requirements for an action.

  Returns a map with:
  - `:status` - one of `:available`, `:partial`, or `:impossible`
  - `:conflicts` - conflict residual (when `:impossible`)
  - `:requirements` - open residual (when `:partial`)

  Status meanings:
  - `:available` - action can be performed now
  - `:impossible` - action cannot be performed (constraint violated)
  - `:partial` - action may be possible if more data provided

  Example:

      (action-requirements game-state {:type :bashketball/move-player
                                       :player-id \"p1\"
                                       :position [2 4]})
      ;=> {:status :available}
      ;=> {:status :impossible
      ;    :conflicts {[:exhausted] [[:conflict [:= false] true]]}}"
  [game-state action]
  (let [residual (validate-action game-state action)]
    (cond
      (polix/satisfied? residual)
      {:status :available}

      (polix/has-conflicts? residual)
      {:status :impossible :conflicts residual}

      :else
      {:status :partial :requirements residual})))

;; =============================================================================
;; Action Enumeration
;; =============================================================================

(defn available-actions
  "Returns a set of action types that are currently available.

  Takes game state and the acting team. For each registered policy, creates
  a minimal action document and checks if the policy is satisfied.

  Note: This provides a rough indication of availability. Some actions may
  require additional parameters (like target position) to fully validate.

  Example:

      (available-actions game-state :team/HOME)
      ;=> #{:bashketball/draw-cards :bashketball/stage-card ...}"
  [game-state team]
  (let [base-doc {:active-player (:active-player game-state)
                  :player team}]
    (into #{}
          (for [[action-type policy] policies/action-policies
                :let                 [residual (polix/unify policy base-doc)]
                :when                (or (polix/satisfied? residual)
                                         (polix/open-residual? residual))]
            action-type))))

(defn unavailable-actions
  "Returns a map of action types to their blocking conflicts.

  Useful for UI to show why actions are disabled.

  Example:

      (unavailable-actions game-state :team/HOME)
      ;=> {:bashketball/play-card {[:instance-id] [...]}, ...}"
  [game-state team]
  (let [base-doc {:active-player (:active-player game-state)
                  :player team}]
    (into {}
          (for [[action-type policy] policies/action-policies
                :let                 [residual (polix/unify policy base-doc)]
                :when                (polix/has-conflicts? residual)]
            [action-type residual]))))
