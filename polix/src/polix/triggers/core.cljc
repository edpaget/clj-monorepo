(ns polix.triggers.core
  "Domain-agnostic reactive event-driven trigger system.

  polix.triggers connects events to effects via conditions evaluated by
  [[polix.unify/unify]]. The library provides mechanics for trigger registration,
  event processing, timing, and stacking—consumers define their own event types,
  entities, and domain logic.

  ## Quick Start

  ```clojure
  (require '[polix.triggers.core :as triggers])

  ;; Create a registry
  (def reg (triggers/create-registry))

  ;; Register a trigger
  (def reg' (triggers/register-trigger
              reg
              {:event-types #{:entity/damaged}
               :timing :polix.triggers.timing/after
               :condition [:= :doc/target-id :doc/trigger.self]
               :effect {:type :heal :amount 1}}
              \"ability-123\"  ; source
              \"player-1\"     ; owner
              \"entity-1\"))   ; self

  ;; Fire an event
  (triggers/fire-event
    {:state {} :registry reg'}
    {:type :entity/damaged :target-id \"entity-1\" :amount 5})
  ```

  ## Core Concepts

  - **Event**: A map with `:type` keyword and domain-specific fields
  - **Trigger**: A registered listener that responds to events
  - **Condition**: A polix policy expression evaluated via [[polix.unify/unify]]
  - **Timing**: When the trigger fires (`:before`, `:instead`, `:after`, `:at`)

  ## Condition Evaluation

  Trigger conditions are evaluated using [[polix.unify/unify]] against a document
  built from the event and trigger context. The document contains:

  - All event fields (e.g., `:target-id`, `:amount`)
  - `:self` - the entity the trigger is attached to
  - `:owner` - the owner of the trigger source
  - `:source` - the source that registered the trigger
  - `:event-type` - the event's `:type` value

  Use `:doc/` prefixed accessors in conditions (e.g., `:doc/self`, `:doc/target-id`).

  Conditions that return a residual (missing data) are treated conservatively:
  the trigger does NOT fire if its condition cannot be fully evaluated.

  See [[polix.triggers.schema]] for data structure definitions."
  (:require [polix.triggers.processing :as processing]
            [polix.triggers.registry :as registry]))

(defn create-registry
  "Creates an empty trigger registry.

  The registry maintains triggers indexed by ID and by event type for
  efficient lookup during event processing."
  []
  (registry/create-registry))

(defn register-trigger
  "Registers a trigger definition in the registry.

  Takes a trigger definition map and binding context (source, owner, self).
  Returns the updated registry with the new trigger added.

  The trigger definition should contain:

  - `:event-types` - set of event type keywords to listen for
  - `:timing` - when to fire (`:polix.triggers.timing/before`, etc.)
  - `:condition` - optional polix policy expression
  - `:effect` - effect to apply when condition is satisfied
  - `:once?` - optional, remove after firing (default false)
  - `:priority` - optional, lower values fire first (default 0)

  The condition is a polix policy expression evaluated via [[polix.unify/unify]]
  when the trigger is processed. It can reference:

  - Event fields (e.g., `:doc/target-id`, `:doc/amount`)
  - Trigger bindings (`:doc/self`, `:doc/owner`, `:doc/source`)
  - Event type via `:doc/event-type`

  Example:
  ```clojure
  (register-trigger registry
    {:event-types #{:entity/damaged}
     :timing :polix.triggers.timing/after
     :condition [:= :doc/target-id :doc/self]
     :effect {:type :counter-attack}}
    \"ability-123\"   ; source that registered this
    \"player-1\"      ; owner of the source
    \"entity-1\")     ; entity the source is attached to
  ```"
  [registry trigger-def source-id owner self]
  (registry/register-trigger registry trigger-def source-id owner self))

(defn unregister-trigger
  "Removes a trigger by ID from the registry.

  Returns the updated registry. If the trigger ID does not exist,
  returns the registry unchanged."
  [registry trigger-id]
  (registry/unregister-trigger registry trigger-id))

(defn unregister-triggers-by-source
  "Removes all triggers registered by a source.

  Useful when an ability or effect is removed and all its triggers
  should be cleaned up. Returns the updated registry."
  [registry source-id]
  (registry/unregister-triggers-by-source registry source-id))

(defn fire-event
  "Fires an event and processes all matching triggers.

  Takes a context map containing `:registry` and `:state`, plus the event
  to fire. Returns a result map with:

  - `:state` - updated state after applying effects
  - `:registry` - updated registry (triggers may be removed if `:once?`)
  - `:event` - the original event
  - `:results` - vector of trigger results
  - `:prevented?` - true if a before trigger prevented the action

  Processing order:
  1. Before triggers (sorted by priority, can set `:prevented?`)
  2. Instead triggers (first matching only, skipped if prevented)
  3. After triggers (sorted by priority, skipped if prevented)
  4. At triggers (always processed)

  Example:
  ```clojure
  (fire-event {:state {:hp 10} :registry reg}
              {:type :entity/damaged :target-id \"e-1\" :amount 5})
  ```"
  [ctx event]
  (processing/fire-event ctx event))

(defn get-triggers
  "Returns all registered triggers as a sequence."
  [registry]
  (registry/get-triggers registry))

(defn get-trigger
  "Returns a trigger by ID, or nil if not found."
  [registry trigger-id]
  (registry/get-trigger registry trigger-id))

(defn get-triggers-for-event
  "Returns triggers that listen for the given event type.

  Triggers are sorted by priority (lower values fire first)."
  [registry event-type]
  (registry/get-triggers-for-event registry event-type))
