(ns bashketball-game.actions
  "Data-driven action application with multimethod dispatch.

  Provides `apply-action` which validates actions against the schema,
  dispatches to the appropriate handler, fires triggers, and logs the event."
  (:require [bashketball-game.board :as board]
            [bashketball-game.polix.card-effects :as card-effects]
            [bashketball-game.polix.triggers :as triggers]
            [bashketball-game.schema :as schema]
            [bashketball-game.state :as state]
            [clojure.set :as set]
            [clojure.string :as str]
            [malli.core :as m]))

(defn- now
  "Returns the current timestamp as an ISO-8601 string."
  []
  #?(:clj (.toString (java.time.Instant/now))
     :cljs (.toISOString (js/Date.))))

(defn- generate-id
  "Generates a random UUID string."
  []
  #?(:clj (str (java.util.UUID/randomUUID))
     :cljs (str (random-uuid))))

(defmulti -apply-action
  "Multimethod dispatching on :type. Implementations receive [game-state action]
   and return the modified game-state. Should NOT handle event logging - that's
   done by the wrapper function."
  (fn [_game-state action] (:type action)))

(defn- apply-action-impl
  "Core action application logic.

  Validates, dispatches to -apply-action multimethod, validates board invariants,
  and logs the event. Returns the new state."
  [game-state action]
  (when-not (m/validate schema/Action action)
    (throw (ex-info "Invalid action"
                    {:action action
                     :explanation (m/explain schema/Action action)})))
  (let [new-state  (-apply-action game-state action)
        event-data (:event-data new-state)
        event      (cond-> (assoc action :timestamp (now))
                     event-data (merge event-data))]
    (when-let [invariant-error (board/check-occupant-invariants (:board new-state))]
      (throw (ex-info "Board invariant violation: duplicate occupant IDs"
                      {:action action
                       :error invariant-error})))
    (-> new-state
        (dissoc :event-data)
        (update :events conj event))))

(def ^:private lifecycle-actions
  "Actions that affect trigger lifecycle and require registry updates.
   NOTE: Most lifecycle actions have been migrated to effects.cljc.
   Remaining actions here use the action->effect bridge via triggers."
  #{})

(defn apply-action
  "Applies a validated action to game state with trigger processing.

  Takes a context map and an action. Returns
  `{:state new-state :registry new-registry :prevented? bool}`.

  Context map keys:
  - `:state` - current game state (required)
  - `:registry` - trigger registry (optional, nil disables triggers)
  - `:catalog` - effect catalog for ability lookups (optional, needed for
                 lifecycle actions like substitute, attach, detach, play-card, move-asset)

  Processing order:
  1. Validates action against schema
  2. Fires before triggers (can prevent action)
  3. If not prevented, applies action via multimethod
  4. Updates registry for lifecycle actions (substitute, attach, detach, play-card, move-asset)
  5. Validates board invariants, logs event
  6. Fires after triggers

  See also [[do-action]] for a simpler API when triggers aren't needed."
  [{:keys [state registry catalog]} action]
  (if-not registry
    {:state (apply-action-impl state action)
     :registry nil
     :prevented? false}
    (let [events        (triggers/action->events state action)
          before-result (triggers/fire-bashketball-event
                         {:state state :registry registry}
                         (:before events))]
      (if (:prevented? before-result)
        {:state state
         :registry (:registry before-result)
         :prevented? true}
        (let [old-state    (:state before-result)
              new-state    (apply-action-impl old-state action)
              ;; Update registry for lifecycle actions
              updated-reg  (if (and catalog (lifecycle-actions (:type action)))
                             (card-effects/update-registry-for-action
                              (:registry before-result) catalog old-state new-state action)
                             (:registry before-result))
              after-result (triggers/fire-bashketball-event
                            {:state new-state :registry updated-reg}
                            (:after events))]
          {:state (:state after-result)
           :registry (:registry after-result)
           :prevented? false})))))

(defn do-action
  "Applies an action to game state without trigger processing.

  Simple API for when triggers aren't needed. Takes game-state and action,
  returns new game-state.

  For trigger support, use [[apply-action]] with a context map."
  [game-state action]
  (apply-action-impl game-state action))

;; -----------------------------------------------------------------------------
;; Game Flow Actions
;; NOTE: do-set-phase and do-advance-turn migrated to effects.cljc

;; NOTE: set-active-player migrated to effects.cljc (do-set-active-player)
;; NOTE: start-from-tipoff migrated to effects.cljc

;; -----------------------------------------------------------------------------
;; Player Resource Actions
;; NOTE: draw-cards and discard-cards migrated to effects.cljc

;; NOTE: set-actions, remove-cards, shuffle-deck, return-discard migrated to effects.cljc

;; -----------------------------------------------------------------------------
;; Basketball Player Actions
;; NOTE: move-player, begin-movement, do-move-step, end-movement,
;;       exhaust-player, refresh-player migrated to effects.cljc

;; NOTE: refresh-all migrated to effects.cljc

;; NOTE: add-modifier, remove-modifier, clear-modifiers migrated to effects.cljc

;; NOTE: substitute migrated to effects.cljc (do-substitute)

;; -----------------------------------------------------------------------------
;; Ball Actions
;; NOTE: set-ball-possessed, set-ball-loose, set-ball-in-air migrated to effects.cljc

;; -----------------------------------------------------------------------------
;; Scoring Actions
;; NOTE: add-score migrated to effects.cljc

;; -----------------------------------------------------------------------------
;; Stack Actions
;; NOTE: push-stack, pop-stack, clear-stack migrated to effects.cljc

;; -----------------------------------------------------------------------------
;; Fate/Skill Test Actions
;; NOTE: reveal-fate, record-skill-test migrated to effects.cljc
;; NOTE: initiate-skill-test, modify-skill-test, set-skill-test-fate,
;;       resolve-skill-test, clear-skill-test migrated to effects.cljc

;; -----------------------------------------------------------------------------
;; Choice Actions
;; NOTE: offer-choice, submit-choice, clear-choice migrated to effects.cljc

;; NOTE: play-card action removed (legacy, test-only). Use :bashketball/play-card effect instead.

;; NOTE: stage-card migrated to effects.cljc (do-stage-card)

;; NOTE: resolve-card migrated to effects.cljc (do-resolve-card)

;; NOTE: move-asset migrated to effects.cljc (do-move-asset)

;; -----------------------------------------------------------------------------
;; Ability Attachment Actions
;; NOTE: attach-ability and detach-ability migrated to effects.cljc

;; -----------------------------------------------------------------------------
;; Token Actions
;; NOTE: create-token migrated to effects.cljc (do-create-token)

;; -----------------------------------------------------------------------------
;; Virtual Standard Action
;; NOTE: stage-virtual-standard-action migrated to effects.cljc (do-stage-virtual-standard-action)

;; -----------------------------------------------------------------------------
;; Examine Cards Actions
;; NOTE: examine-cards and resolve-examined-cards migrated to effects.cljc
