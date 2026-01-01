(ns bashketball-game.polix.event-context
  "Event counters and causation tracking for event-driven architecture.

  This module provides the infrastructure for the event-driven game state
  architecture where actions produce events, triggers respond with effects,
  and only terminal effects modify state.

  Key concepts:

  **Event Counters**: Track how many times each event type has occurred,
  scoped by turn, event type, and team. Enables triggers like
  \"the second time you draw this turn\".

  **Causation Chain**: Each event carries a vector of trigger IDs that
  caused it. Triggers automatically skip if they're in their own causation
  chain, preventing infinite recursion.

  **Execution Lock**: Triggers are locked while executing to prevent
  reentrancy issues.")

(def ^:private max-event-depth
  "Maximum recursion depth for event processing. Safety net against runaway loops."
  100)

;;; ---------------------------------------------------------------------------
;;; Event Counters
;;; ---------------------------------------------------------------------------

(defn counter-key
  "Builds a scoped counter key for an event.

  Keys are scoped by `[turn-number event-type team]` so counters reset
  each turn automatically. The team is taken from the event's `:team` field."
  [state event]
  [(:turn-number state) (:event-type event) (:team event)])

(defn increment-counter
  "Increments the event counter and returns `[updated-ctx count]`.

  The count represents how many times this event type has occurred for
  this team during this turn, including the current occurrence."
  [ctx event]
  (let [key (counter-key (:state ctx) event)
        ctx' (update-in ctx [:event-counters key] (fnil inc 0))
        cnt (get-in ctx' [:event-counters key])]
    [ctx' cnt]))

(defn get-counter
  "Returns the current count for an event type, or 0 if not yet occurred."
  [ctx state event]
  (let [key (counter-key state event)]
    (get-in ctx [:event-counters key] 0)))

(defn reset-turn-counters
  "Removes all counters for a specific turn.

  Call when advancing to a new turn to clean up old counter entries."
  [ctx turn-number]
  (update ctx :event-counters
          (fn [counters]
            (into {}
                  (remove (fn [[[turn & _] _]] (= turn turn-number)))
                  counters))))

;;; ---------------------------------------------------------------------------
;;; Causation Chain
;;; ---------------------------------------------------------------------------

(defn in-causation?
  "Returns true if the trigger ID is in the event's causation chain.

  Used to prevent a trigger from firing on events it caused, avoiding
  infinite recursion."
  [event trigger-id]
  (boolean (some #{trigger-id} (:causation event))))

(defn add-to-causation
  "Adds a trigger ID to the causation chain.

  Returns a new causation vector with the trigger ID appended."
  [causation trigger-id]
  (conj (or causation []) trigger-id))

(defn get-causation
  "Extracts the causation chain from an event, defaulting to empty vector."
  [event]
  (or (:causation event) []))

;;; ---------------------------------------------------------------------------
;;; Execution Lock
;;; ---------------------------------------------------------------------------

(defn trigger-locked?
  "Returns true if the trigger is currently executing.

  Used as a safety net to prevent reentrancy even if causation check fails."
  [ctx trigger-id]
  (contains? (:executing-triggers ctx) trigger-id))

(defn lock-trigger
  "Adds trigger to the executing set."
  [ctx trigger-id]
  (update ctx :executing-triggers (fnil conj #{}) trigger-id))

(defn unlock-trigger
  "Removes trigger from the executing set."
  [ctx trigger-id]
  (update ctx :executing-triggers disj trigger-id))

;;; ---------------------------------------------------------------------------
;;; Depth Tracking
;;; ---------------------------------------------------------------------------

(defn get-depth
  "Returns the current event processing depth."
  [ctx]
  (get ctx :event-depth 0))

(defn increment-depth
  "Increments depth and throws if max depth exceeded."
  [ctx]
  (let [depth (inc (get-depth ctx))]
    (when (> depth max-event-depth)
      (throw (ex-info "Event recursion limit exceeded"
                      {:depth depth
                       :max-depth max-event-depth})))
    (assoc ctx :event-depth depth)))

(defn decrement-depth
  "Decrements depth after event processing completes."
  [ctx]
  (update ctx :event-depth (fnil dec 1)))

;;; ---------------------------------------------------------------------------
;;; Trigger Eligibility
;;; ---------------------------------------------------------------------------

(defn can-trigger-fire?
  "Checks if a trigger is eligible to fire for this event.

  A trigger can fire if:
  1. It's not in the event's causation chain (prevents self-triggering)
  2. It's not currently executing (prevents reentrancy)"
  [ctx trigger event]
  (let [trigger-id (:id trigger)]
    (and (not (in-causation? event trigger-id))
         (not (trigger-locked? ctx trigger-id)))))
