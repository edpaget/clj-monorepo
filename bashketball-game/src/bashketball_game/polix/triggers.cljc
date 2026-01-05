(ns bashketball-game.polix.triggers
  "Trigger integration layer for bashketball-game.

  Connects bashketball actions to the polix.triggers system, enabling card
  abilities to react to game events. Provides:

  - [[action->events]] to generate before/after events from actions
  - [[fire-bashketball-event]] to fire events with game-specific context
  - [[fire-request-event]] for the new event-driven architecture
  - Registry management functions delegating to [[polix.triggers.core]]

  The trigger registry is passed externally (not stored in game-state) since
  it's a derived runtime structure rebuilt from cards at game initialization.

  ## Event-Driven Architecture

  The new [[fire-request-event]] function implements an architecture where:
  - Actions produce request events (e.g., `:bashketball/draw-cards.request`)
  - Triggers respond to events with effects
  - Only terminal `do-*` effects modify state
  - Causation chains prevent self-triggering
  - Event counters enable \"Nth occurrence\" conditions"
  (:require
   [bashketball-game.polix.event-context :as event-ctx]
   [polix.effects.core :as fx]
   [polix.residual :as res]
   [polix.triggers.core :as triggers]
   [polix.triggers.registry :as registry]
   [polix.unify :as unify]))

;; Forward declaration for choice-event used in action->events
(declare choice-event)

(defn- event-type-with-suffix
  "Appends a suffix to an action type keyword.

  Example: `:bashketball/move-player` + `.before` → `:bashketball/move-player.before`"
  [action-type suffix]
  (keyword (namespace action-type)
           (str (name action-type) suffix)))

(defn action->events
  "Generates before and after events from an action.

  Returns `{:before event :after event}` where each event contains:
  - `:type` - action type with `.before` or `.after` suffix
  - All action fields (player-id, position, etc.)
  - `:turn-number`, `:active-player`, `:phase` from game state

  Special handling for `:bashketball/submit-choice` to use [[choice-event]]
  for proper choice field inclusion in the event."
  [game-state action]
  (let [action-type (:type action)
        base-fields (-> action
                        (dissoc :type)
                        (assoc :turn-number (:turn-number game-state)
                               :active-player (:active-player game-state)
                               :phase (:phase game-state)))]
    (if (= action-type :bashketball/submit-choice)
      ;; submit-choice uses choice-event to include pending choice fields
      {:before nil
       :after  (choice-event game-state "submitted")}
      ;; default: generate before/after events from action type
      {:before (assoc base-fields :type (event-type-with-suffix action-type ".before"))
       :after  (assoc base-fields :type (event-type-with-suffix action-type ".after"))})))

(defn- enrich-event
  "Adds game state context to an event for trigger processing.

  The polix.triggers.processing module builds trigger documents from event
  fields. By enriching the event with game state, triggers can access game
  context via `:doc/state`, `:doc/turn-number`, etc."
  [game-state event]
  (assoc event
         :turn-number (:turn-number game-state)
         :active-player (:active-player game-state)
         :phase (:phase game-state)
         :state game-state))

(defn fire-bashketball-event
  "Fires an event with bashketball-specific context.

  Enriches the event with game state fields before delegating to
  [[polix.triggers.core/fire-event]]. Returns the result map containing
  `:state`, `:registry`, `:prevented?`, and `:results`."
  [{:keys [state registry]} event]
  (let [enriched-event (enrich-event state event)]
    (triggers/fire-event {:state state :registry registry} enriched-event)))

;;; ---------------------------------------------------------------------------
;;; Registry Management (delegates to polix.triggers.core)
;;; ---------------------------------------------------------------------------

(defn create-registry
  "Creates an empty trigger registry."
  []
  (triggers/create-registry))

(defn register-trigger
  "Registers a trigger definition in the registry.

  Takes a trigger definition map and binding context (source, owner, self).
  Returns the updated registry.

  The trigger definition should contain:
  - `:event-types` - set of event type keywords (e.g., `#{:bashketball/move-player.after}`)
  - `:timing` - when to fire (`:polix.triggers.timing/before`, etc.)
  - `:condition` - optional polix policy expression
  - `:effect` - effect to apply when condition is satisfied
  - `:once?` - optional, remove after firing (default false)
  - `:priority` - optional, lower values fire first (default 0)"
  [registry trigger-def source owner self]
  (triggers/register-trigger registry trigger-def source owner self))

(defn unregister-trigger
  "Removes a trigger by ID from the registry."
  [registry trigger-id]
  (triggers/unregister-trigger registry trigger-id))

(defn unregister-triggers-by-source
  "Removes all triggers registered by a source.

  Useful when a card ability is removed and all its triggers should be
  cleaned up."
  [registry source-id]
  (triggers/unregister-triggers-by-source registry source-id))

(defn get-triggers
  "Returns all registered triggers as a sequence."
  [registry]
  (triggers/get-triggers registry))

(defn get-trigger
  "Returns a trigger by ID, or nil if not found."
  [registry trigger-id]
  (triggers/get-trigger registry trigger-id))

;;; ---------------------------------------------------------------------------
;;; Skill Test Events
;;; ---------------------------------------------------------------------------

(defn skill-test-event
  "Creates a skill test event.

  Event types:
  - `:bashketball/skill-test.before` - Before fate is revealed, modifiers can be added
  - `:bashketball/skill-test.fate-revealed` - After fate drawn, more modifiers possible
  - `:bashketball/skill-test.after` - After resolution, result is final

  The event contains all pending skill test fields plus game context."
  [game-state event-suffix]
  (when-let [test (:pending-skill-test game-state)]
    (merge test
           {:type (keyword "bashketball" (str "skill-test." event-suffix))
            :turn-number (:turn-number game-state)
            :active-player (:active-player game-state)
            :phase (:phase game-state)})))

(defn fire-skill-test-event
  "Fires a skill test event with full game context.

  Returns `{:state :registry :prevented? :results}` from trigger processing.
  If no pending skill test exists, returns state unchanged with no triggers fired."
  [{:keys [state registry]} event-suffix]
  (if-let [event (skill-test-event state event-suffix)]
    (fire-bashketball-event {:state state :registry registry} event)
    {:state state :registry registry :prevented? false :results []}))

(defn choice-event
  "Creates a choice event.

  Event types:
  - `:bashketball/choice.offered` - A choice was presented to a player
  - `:bashketball/choice.submitted` - A player submitted their choice

  The event contains pending choice fields plus game context."
  [game-state event-suffix]
  (when-let [choice (:pending-choice game-state)]
    (merge choice
           {:type (keyword "bashketball" (str "choice." event-suffix))
            :turn-number (:turn-number game-state)
            :active-player (:active-player game-state)
            :phase (:phase game-state)})))

;;; ---------------------------------------------------------------------------
;;; Event-Driven Architecture
;;; ---------------------------------------------------------------------------

(defn- build-trigger-document
  "Builds a document for condition evaluation from trigger context and event.

  The document contains:
  - `:self`, `:owner`, `:source` - trigger bindings
  - `:event-type` - the event's type
  - `:occurrence-this-turn` - how many times this event has occurred
  - All other event fields"
  [trigger event]
  (merge
   {:self       (:self trigger)
    :owner      (:owner trigger)
    :source     (:source trigger)
    :event-type (:event-type event)
    :self-team  (or (:owner trigger) (:team event))}
   (dissoc event :event-type)))

(defn- evaluate-condition
  "Evaluates a trigger's condition against the event.

  Returns `:satisfied`, `:conflict`, or `:open`."
  [trigger event]
  (if-let [condition (:condition trigger)]
    (let [document (build-trigger-document trigger event)
          result (unify/unify condition document {:event event})]
      (cond
        (res/satisfied? result) :satisfied
        (res/has-conflicts? result) :conflict
        (res/residual? result) :open
        :else :conflict))
    :satisfied))

(defn- apply-trigger-effect
  "Applies a trigger's effect with causation tracking.

  Propagates the causation chain so child events know their ancestry."
  [ctx trigger event]
  (let [causation (event-ctx/add-to-causation (:causation event) (:id trigger))
        effect-ctx {:state (:state ctx)
                    :event event
                    :trigger trigger
                    :self (:self trigger)
                    :owner (:owner trigger)
                    :source (:source trigger)}
        opts {:registry (:registry ctx)
              :event-counters (:event-counters ctx)
              :executing-triggers (:executing-triggers ctx)
              :event-depth (:event-depth ctx)
              :causation causation}]
    (fx/apply-effect (:state ctx) (:effect trigger) effect-ctx opts)))

(defn- process-single-trigger
  "Processes a single trigger, returning updated context and result."
  [ctx trigger event]
  (let [condition-result (evaluate-condition trigger event)]
    (if (= :satisfied condition-result)
      (let [ctx-locked (event-ctx/lock-trigger ctx (:id trigger))
            effect-result (apply-trigger-effect ctx-locked trigger event)
            ctx-unlocked (event-ctx/unlock-trigger
                          (assoc ctx-locked :state (:state effect-result))
                          (:id trigger))
            ;; Merge any context updates from effect (counters, registry)
            ctx-merged (merge ctx-unlocked
                              (select-keys effect-result [:event-counters :registry]))
            new-registry (if (:once? trigger)
                           (registry/unregister-trigger
                            (:registry ctx-merged) (:id trigger))
                           (:registry ctx-merged))]
        {:ctx (assoc ctx-merged :registry new-registry)
         :result {:trigger-id (:id trigger)
                  :fired? true
                  :condition-result condition-result
                  :effect-result effect-result
                  :removed? (boolean (:once? trigger))}})
      {:ctx ctx
       :result {:trigger-id (:id trigger)
                :fired? false
                :condition-result condition-result
                :removed? false}})))

(defn- process-triggers-with-causation
  "Processes eligible triggers in priority order with causation tracking."
  [ctx event triggers]
  (reduce
   (fn [{:keys [ctx results]} trigger]
     (if (event-ctx/can-trigger-fire? ctx trigger event)
       (let [{:keys [ctx result]} (process-single-trigger ctx trigger event)]
         {:ctx ctx
          :results (conj results result)})
       {:ctx ctx
        :results (conj results {:trigger-id (:id trigger)
                                :fired? false
                                :condition-result :skipped-causation
                                :removed? false})}))
   {:ctx ctx :results []}
   (sort-by :priority triggers)))

(defn fire-request-event
  "Fires a request event with counter tracking and causation support.

  This is the main entry point for the new event-driven architecture.
  Unlike [[fire-bashketball-event]], this function:

  1. Increments the event counter before processing
  2. Adds `:occurrence-this-turn` to the event
  3. Filters triggers by causation chain
  4. Processes triggers with causation propagation
  5. Tracks event depth for recursion protection

  Takes a context map with:
  - `:state` - current game state
  - `:registry` - trigger registry
  - `:event-counters` - current counter map (optional, defaults to {})
  - `:executing-triggers` - currently executing trigger IDs (optional)

  Returns updated context with:
  - `:state` - updated game state
  - `:registry` - updated registry
  - `:event-counters` - updated counters
  - `:results` - trigger processing results
  - `:prevented?` - whether action was prevented"
  [{:keys [state registry] :as initial-ctx} event]
  (let [;; Increment depth for recursion protection
        ctx' (event-ctx/increment-depth initial-ctx)
        ;; Increment counter before processing
        [ctx'' occurrence] (event-ctx/increment-counter ctx' event)
        ;; Add occurrence and ensure event-type is set
        event' (-> event
                   (assoc :occurrence-this-turn occurrence)
                   (cond-> (not (:event-type event))
                     (assoc :event-type (:type event))))
        ;; Get all triggers for this event type
        all-triggers (registry/get-triggers-for-event
                      (:registry ctx'')
                      (:event-type event'))
        ;; Process triggers with causation filtering
        {:keys [ctx results]} (process-triggers-with-causation ctx'' event' all-triggers)
        ;; Check if any trigger prevented the action
        prevented? (some #(get-in % [:effect-result :prevented?]) results)
        ;; Decrement depth on the final context
        ctx-final (event-ctx/decrement-depth ctx)]
    {:state (:state ctx-final)
     :registry (:registry ctx-final)
     :event-counters (:event-counters ctx-final)
     :executing-triggers (:executing-triggers ctx-final)
     :event event'
     :results results
     :prevented? (boolean prevented?)}))
