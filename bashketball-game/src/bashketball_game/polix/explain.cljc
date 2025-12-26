(ns bashketball-game.polix.explain
  "Human-readable explanations for policy validation residuals.

  Converts polix residuals into UI-friendly messages that explain why
  an action cannot be performed.

  ## Usage

      (require '[bashketball-game.polix.explain :as explain])

      ;; Get explanation for a validation failure
      (explain/explain-residual residual)
      ;=> [{:key :player-exhausted :message \"Player is exhausted and cannot act\"}]

      ;; Get explanation for a specific constraint
      (explain/explain-constraint [:player-exhausted] [:= false] true)
      ;=> {:key :player-exhausted :message \"Player is exhausted and cannot act\"}"
  (:require
   [polix.residual :as res]))

;; =============================================================================
;; Constraint Explanations
;; =============================================================================

(def constraint-explanations
  "Map of constraint patterns to explanation data.

  Keys are `[path [op expected]]` patterns.
  Values are `{:key keyword :message string}` explanation data.

  Patterns use `:_` as a wildcard for any value."
  {;; Movement constraints
   [[:player-on-court] [:= true]]
   {:key :player-off-court
    :message "Player must be on the court"}

   [[:player-exhausted] [:= false]]
   {:key :player-exhausted
    :message "Player is exhausted and cannot act"}

   [[:valid-position] [:= true]]
   {:key :invalid-position
    :message "Target position is not valid"}

   [[:can-move-to] [:= true]]
   {:key :cannot-move
    :message "Player cannot move to that position"}

   ;; Substitution constraints
   [[:on-court-on-court] [:= true]]
   {:key :on-court-not-on-court
    :message "Selected player is not on the court"}

   [[:off-court-on-court] [:= false]]
   {:key :off-court-already-on
    :message "Substitute player is already on the court"}

   [[:on-court-team] [:= :_]]
   {:key :different-teams
    :message "Players must belong to the same team"}

   ;; Card action constraints
   [[:card-in-hand] [:= true]]
   {:key :card-not-in-hand
    :message "Card is not in your hand"}

   [[:actions-remaining] [:> 0]]
   {:key :no-actions-remaining
    :message "No actions remaining this turn"}

   [[:holder-on-court] [:= true]]
   {:key :holder-not-on-court
    :message "Target player must be on the court"}

   [[:target-player-on-court] [:= true]]
   {:key :target-not-on-court
    :message "Target player must be on the court"}

   ;; Active player constraints
   [[:player] [:= :_]]
   {:key :not-active-player
    :message "It is not your turn"}

   ;; Ball possession constraints
   [[:has-ball] [:= true]]
   {:key :no-ball
    :message "Player must have the ball"}

   [[:ball-holder-on-court] [:= true]]
   {:key :ball-holder-off-court
    :message "Ball holder must be on the court"}

   ;; Shooting constraints
   [[:distance-to-basket] [:<= :_]]
   {:key :out-of-range
    :message "Too far from basket to shoot"}

   ;; Pass target constraints
   [[:target-same-team] [:= true]]
   {:key :wrong-team
    :message "Can only pass to teammates"}

   [[:target-is-ball-holder] [:= false]]
   {:key :is-ball-holder
    :message "Cannot pass to the ball holder"}

   [[:target-on-court] [:= true]]
   {:key :target-off-court
    :message "Target player must be on the court"}

   ;; Refresh constraints
   [[:player-exhausted] [:= true]]
   {:key :player-not-exhausted
    :message "Player is not exhausted"}})

(defn- match-constraint?
  "Returns true if constraint matches the pattern.

  Patterns can use `:_` as a wildcard that matches any value."
  [[pattern-path [pattern-op pattern-val]] [path [op val]]]
  (and (= pattern-path path)
       (= pattern-op op)
       (or (= pattern-val :_)
           (= pattern-val val))))

(defn explain-constraint
  "Returns an explanation for a specific constraint.

  Takes a `path` (e.g., `[:player-exhausted]`), an `inner-constraint`
  (e.g., `[:= false]`), and an optional `witness` value.

  Returns `{:key keyword :message string}` or nil if no explanation found."
  ([path inner-constraint]
   (explain-constraint path inner-constraint nil))
  ([path inner-constraint witness]
   (let [constraint-key [path inner-constraint]]
     (some (fn [[pattern explanation]]
             (when (match-constraint? pattern constraint-key)
               (if witness
                 (assoc explanation :witness witness)
                 explanation)))
           constraint-explanations))))

;; =============================================================================
;; Residual Explanation
;; =============================================================================

(defn- extract-conflicts
  "Extracts conflict constraints from a residual.

  Returns a sequence of `{:path [...] :constraint [...] :witness value}` maps."
  [residual]
  (for [[path constraints] residual
        :when              (vector? path)
        constraint         constraints
        :when              (res/conflict? constraint)]
    {:path path
     :constraint (res/conflict-constraint constraint)
     :witness (res/conflict-witness constraint)}))

(defn- extract-open-constraints
  "Extracts open (non-conflict) constraints from a residual.

  Returns a sequence of `{:path [...] :constraint [...]}` maps."
  [residual]
  (for [[path constraints] residual
        :when              (vector? path)
        constraint         constraints
        :when              (not (res/conflict? constraint))]
    {:path path
     :constraint constraint}))

(defn explain-residual
  "Explains all constraints in a residual.

  Returns a sequence of explanation maps. Each map contains:
  - `:key` - keyword identifying the constraint type
  - `:message` - human-readable message
  - `:witness` - (optional) the actual value that caused the conflict

  Conflicts are explained first, then open constraints."
  [residual]
  (when (res/residual? residual)
    (let [conflicts                                             (extract-conflicts residual)
          open-constraints                                      (extract-open-constraints residual)
          conflict-explanations
          (keep (fn [{:keys [path constraint witness]}]
                  (explain-constraint path constraint witness))
                conflicts)
          open-explanations
          (keep (fn [{:keys [path constraint]}]
                  (explain-constraint path constraint))
                open-constraints)]
      (concat conflict-explanations open-explanations))))

(defn explain-first
  "Returns the first explanation from a residual, or nil.

  Useful when you only need one reason for why an action failed."
  [residual]
  (first (explain-residual residual)))

;; =============================================================================
;; Action-Specific Explanations
;; =============================================================================

(defn explain-move-failure
  "Explains why a move action failed.

  Returns a specific message based on the residual constraints."
  [residual]
  (let [explanations (explain-residual residual)]
    (or (first explanations)
        {:key :unknown-move-failure
         :message "Unable to move player"})))

(defn explain-substitute-failure
  "Explains why a substitute action failed."
  [residual]
  (let [explanations (explain-residual residual)]
    (or (first explanations)
        {:key :unknown-substitute-failure
         :message "Unable to substitute player"})))

(defn explain-action-failure
  "Explains why an action failed based on its type.

  Provides action-specific context when available."
  [action-type residual]
  (case action-type
    :bashketball/move-player (explain-move-failure residual)
    :bashketball/substitute (explain-substitute-failure residual)
    (or (explain-first residual)
        {:key :action-unavailable
         :message "Action is not available"})))
