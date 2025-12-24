(ns polix.triggers.processing
  "Event processing and trigger firing.

  Provides the core event processing loop that evaluates trigger conditions,
  applies effects, and handles timing (before/instead/after). The main entry
  point is [[fire-event]] which processes all matching triggers for an event.

  Conditions are evaluated using [[polix.unify/unify]] against a document
  built from the event and trigger context. This integrates triggers with
  the unified constraint-based policy system."
  (:require [polix.residual :as res]
            [polix.triggers.effects :as effects]
            [polix.triggers.registry :as registry]
            [polix.unify :as unify]))

(defn- build-trigger-document
  "Builds a document for condition evaluation from trigger context and event.

  The document contains all event fields plus trigger-specific bindings as
  top-level keys. This allows conditions to access both event data and trigger
  context using `:doc/` accessors.

  The document contains:
  - `:self`, `:owner`, `:source` - trigger bindings
  - `:event-type` - the event's `:type` value
  - All other event fields (without the `:type` key)

  Example:
  ```clojure
  (build-trigger-document
    {:self \"entity-1\" :owner \"player-1\" :source \"ability-1\"}
    {:type :entity/damaged :target-id \"entity-1\" :amount 5})
  ;; => {:self \"entity-1\"
  ;;     :owner \"player-1\"
  ;;     :source \"ability-1\"
  ;;     :event-type :entity/damaged
  ;;     :target-id \"entity-1\"
  ;;     :amount 5}
  ```"
  [trigger event]
  (merge
   {:self       (:self trigger)
    :owner      (:owner trigger)
    :source     (:source trigger)
    :event-type (:type event)}
   (dissoc event :type)))

(defn- evaluate-condition
  "Evaluates a trigger's condition using [[polix.unify/unify]].

  Takes a trigger and event, builds a document containing both, and unifies
  the trigger's condition against it.

  Returns one of:
  - `:satisfied` - condition met, trigger should fire
  - `:conflict` - condition violated, trigger should not fire
  - `:open` - condition has unresolved constraints (conservative: don't fire)

  If the trigger has no condition, returns `:satisfied` (always fires)."
  [trigger event]
  (if-let [condition (:condition trigger)]
    (let [document (build-trigger-document trigger event)
          result   (unify/unify condition document {:event event})]
      (cond
        (res/satisfied? result)     :satisfied
        (res/has-conflicts? result) :conflict
        (res/residual? result)      :open
        :else                       :conflict))
    :satisfied))

(defn- process-trigger
  "Processes a single trigger against an event.

  Evaluates the trigger's condition and, if satisfied, applies its effect.
  Returns a result map with processing details and updated state/registry."
  [state registry trigger event]
  (let [condition-result (evaluate-condition trigger event)]
    (if (= :satisfied condition-result)
      (let [ctx           {:state  state
                           :event  event
                           :trigger trigger
                           :self   (:self trigger)
                           :owner  (:owner trigger)
                           :source (:source trigger)}
            effect-result (effects/apply-effect state (:effect trigger) ctx)
            new-registry  (if (:once? trigger)
                            (registry/unregister-trigger registry (:id trigger))
                            registry)]
        {:state    (:state effect-result)
         :registry new-registry
         :result   {:trigger-id       (:id trigger)
                    :fired?           true
                    :condition-result condition-result
                    :effect-result    effect-result
                    :removed?         (boolean (:once? trigger))}})
      {:state    state
       :registry registry
       :result   {:trigger-id       (:id trigger)
                  :fired?           false
                  :condition-result condition-result
                  :removed?         false}})))

(defn- process-trigger-group
  "Processes a group of triggers in order.

  Returns accumulated results with updated state and registry. If any trigger
  sets `:prevented?` in its effect result, processing continues but the
  prevented flag is set."
  [state registry triggers event]
  (reduce
   (fn [{:keys [state registry results prevented?]} trigger]
     (let [{:keys [state registry result]} (process-trigger state registry trigger event)
           new-prevented?                  (or prevented?
                                               (get-in result [:effect-result :prevented?]))]
       {:state      state
        :registry   registry
        :results    (conj results result)
        :prevented? new-prevented?}))
   {:state state :registry registry :results [] :prevented? false}
   triggers))

(defn- partition-by-timing
  "Partitions triggers into groups by timing."
  [triggers]
  (let [grouped (group-by :timing triggers)]
    {:before  (get grouped :polix.triggers.timing/before [])
     :instead (get grouped :polix.triggers.timing/instead [])
     :after   (get grouped :polix.triggers.timing/after [])
     :at      (get grouped :polix.triggers.timing/at [])}))

(defn fire-event
  "Fires an event and processes all matching triggers.

  Takes a context map containing `:registry` and `:state`, plus the event to
  fire. Returns a [[FireEventResult]] with updated state, registry, and
  processing results.

  Processing order:
  1. Before triggers (can set `:prevented?`)
  2. Instead triggers (first match only, if not prevented)
  3. After triggers (if not prevented)
  4. At triggers (always processed)

  Example:
  ```clojure
  (fire-event {:state {} :registry reg}
              {:type :entity/damaged :target-id \"e-1\" :amount 5})
  ;; => {:state {...}
  ;;     :registry {...}
  ;;     :event {:type :entity/damaged ...}
  ;;     :results [...]
  ;;     :prevented? false}
  ```"
  [{:keys [state registry]} event]
  (let [triggers                          (registry/get-triggers-for-event registry (:type event))
        {:keys [before instead after at]} (partition-by-timing triggers)
        before-result                     (process-trigger-group state registry before event)
        prevented?                        (:prevented? before-result)]
    (if prevented?
      {:state      (:state before-result)
       :registry   (:registry before-result)
       :event      event
       :results    (:results before-result)
       :prevented? true}
      (let [instead-result (if (seq instead)
                             (process-trigger-group
                              (:state before-result)
                              (:registry before-result)
                              [(first instead)]
                              event)
                             {:state      (:state before-result)
                              :registry   (:registry before-result)
                              :results    []
                              :prevented? false})
            after-result   (process-trigger-group
                            (:state instead-result)
                            (:registry instead-result)
                            after
                            event)
            at-result      (process-trigger-group
                            (:state after-result)
                            (:registry after-result)
                            at
                            event)]
        {:state      (:state at-result)
         :registry   (:registry at-result)
         :event      event
         :results    (vec (concat (:results before-result)
                                  (:results instead-result)
                                  (:results after-result)
                                  (:results at-result)))
         :prevented? false}))))
