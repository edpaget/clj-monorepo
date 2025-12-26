(ns bashketball-game.polix.policies
  "Action precondition policies for bashketball game validation.

  Defines policies that validate whether actions can be performed given
  the current game state. Each action type has a corresponding policy that
  returns a residual indicating satisfaction, missing requirements, or
  conflicts.

  Policies are defined as pure data vectors using the polix DSL. They use
  operators registered in [[bashketball-game.polix.operators]].

  ## Document Structure

  Policies expect a document with the following fields:
  - `:doc/state` - the full game state
  - `:doc/player-id` - the acting player ID (for player actions)
  - `:doc/position` - target position (for movement actions)
  - Other action-specific fields prefixed with `:doc/`

  ## Usage

      (require '[bashketball-game.polix.policies :as policies]
               '[polix.core :as polix])

      ;; Check if move is valid
      (polix/unify policies/move-player-policy
                   {:state game-state
                    :player-id \"HOME-player-0\"
                    :position [2 4]})")

;; =============================================================================
;; Move Player Policy
;; =============================================================================

(def move-player-policy
  "Policy for `:bashketball/move-player` action.

  Validates:
  - Player is on court (has a position)
  - Player is not exhausted
  - Target position is valid on the board
  - Player can legally move to the position (path is clear, within range)

  Document expects pre-computed values:
  - `:player-on-court` - boolean
  - `:player-exhausted` - boolean
  - `:valid-position` - boolean
  - `:can-move-to` - boolean"
  [:and
   [:= :doc/player-on-court true]
   [:= :doc/player-exhausted false]
   [:= :doc/valid-position true]
   [:= :doc/can-move-to true]])

;; =============================================================================
;; Substitute Policy
;; =============================================================================

(def substitute-policy
  "Policy for `:bashketball/substitute` action.

  Validates:
  - On-court player is actually on court
  - Off-court player is not on court
  - Both players belong to the same team

  Document expects pre-computed values:
  - `:on-court-on-court` - boolean (is on-court player on court?)
  - `:off-court-on-court` - boolean (is off-court player on court?)
  - `:on-court-team` - team keyword
  - `:off-court-team` - team keyword"
  [:and
   [:= :doc/on-court-on-court true]
   [:= :doc/off-court-on-court false]
   [:= :doc/on-court-team :doc/off-court-team]])

;; =============================================================================
;; Play Card Policy
;; =============================================================================

(def play-card-policy
  "Policy for `:bashketball/play-card` action.

  Validates:
  - The team is the active player
  - Team has actions remaining
  - Card is in the team's hand

  Document expects pre-computed values:
  - `:actions-remaining` - number
  - `:card-in-hand` - boolean"
  [:and
   [:= :doc/player :doc/active-player]
   [:> :doc/actions-remaining 0]
   [:= :doc/card-in-hand true]])

;; =============================================================================
;; Attach Ability Policy
;; =============================================================================

(def attach-ability-policy
  "Policy for `:bashketball/attach-ability` action.

  Validates:
  - Team has actions remaining
  - Card is in the team's hand
  - Target player is on court
  - Card is an ability card (validated separately, requires catalog)

  Document expects pre-computed values:
  - `:actions-remaining` - number
  - `:card-in-hand` - boolean
  - `:target-player-on-court` - boolean"
  [:and
   [:> :doc/actions-remaining 0]
   [:= :doc/card-in-hand true]
   [:= :doc/target-player-on-court true]])

;; =============================================================================
;; Stage Card Policy
;; =============================================================================

(def stage-card-policy
  "Policy for `:bashketball/stage-card` action.

  Validates:
  - The team is the active player
  - Card is in the team's hand

  Document expects pre-computed values:
  - `:card-in-hand` - boolean"
  [:and
   [:= :doc/player :doc/active-player]
   [:= :doc/card-in-hand true]])

;; =============================================================================
;; Draw Cards Policy
;; =============================================================================

(def draw-cards-policy
  "Policy for `:bashketball/draw-cards` action.

  Validates:
  - The team is the active player

  Note: Drawing from an empty deck may be allowed (could trigger reshuffle),
  so we don't validate deck size here."
  [:= :doc/player :doc/active-player])

;; =============================================================================
;; Give Ball Policy
;; =============================================================================

(def give-ball-policy
  "Policy for `:bashketball/set-ball-possessed` action.

  Validates:
  - Target player is on court

  Document expects pre-computed values:
  - `:holder-on-court` - boolean"
  [:= :doc/holder-on-court true])

;; =============================================================================
;; Exhaust Player Policy
;; =============================================================================

(def exhaust-player-policy
  "Policy for `:bashketball/exhaust-player` action.

  Validates:
  - Player is on court
  - Player is not already exhausted

  Document expects pre-computed values:
  - `:player-on-court` - boolean
  - `:player-exhausted` - boolean"
  [:and
   [:= :doc/player-on-court true]
   [:= :doc/player-exhausted false]])

;; =============================================================================
;; Refresh Player Policy
;; =============================================================================

(def refresh-player-policy
  "Policy for `:bashketball/refresh-player` action.

  Validates:
  - Player is exhausted (otherwise refresh is no-op)

  Document expects pre-computed values:
  - `:player-exhausted` - boolean"
  [:= :doc/player-exhausted true])

;; =============================================================================
;; Policy Registry
;; =============================================================================

(def action-policies
  "Map of action type to validation policy.

  Actions without policies have no semantic validation (only schema validation).
  Game flow actions like `:bashketball/set-phase` are intentionally omitted
  as they are typically controlled by game logic, not player actions."
  {:bashketball/move-player       move-player-policy
   :bashketball/substitute        substitute-policy
   :bashketball/play-card         play-card-policy
   :bashketball/attach-ability    attach-ability-policy
   :bashketball/stage-card        stage-card-policy
   :bashketball/draw-cards        draw-cards-policy
   :bashketball/set-ball-possessed give-ball-policy
   :bashketball/exhaust-player    exhaust-player-policy
   :bashketball/refresh-player    refresh-player-policy})

(defn get-policy
  "Returns the validation policy for an action type, or nil if none defined."
  [action-type]
  (get action-policies action-type))
