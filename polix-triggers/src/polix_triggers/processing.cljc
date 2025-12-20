(ns polix-triggers.processing
  "Event processing and trigger firing.

  Provides the core event processing loop that evaluates trigger conditions,
  applies effects, and handles timing (before/instead/after). The main entry
  point is [[fire-event]] which processes all matching triggers for an event."
  (:require [polix-triggers.condition :as condition]
            [polix-triggers.effects :as effects]
            [polix-triggers.registry :as registry]))

(defn- process-trigger
  "Processes a single trigger against an event.

  Evaluates the trigger's condition and, if satisfied, applies its effect.
  Returns a result map with processing details and updated state/registry."
  [state registry trigger event]
  (let [condition-result (condition/evaluate-condition trigger event)]
    (if (true? condition-result)
      (let [ctx {:state state
                 :event event
                 :trigger trigger
                 :self (:self trigger)
                 :owner (:owner trigger)
                 :source (:source trigger)}
            effect-result (effects/apply-effect state (:effect trigger) ctx)
            new-registry (if (:once? trigger)
                           (registry/unregister-trigger registry (:id trigger))
                           registry)]
        {:state (:state effect-result)
         :registry new-registry
         :result {:trigger-id (:id trigger)
                  :fired? true
                  :condition-result condition-result
                  :effect-result effect-result
                  :removed? (:once? trigger)}})
      {:state state
       :registry registry
       :result {:trigger-id (:id trigger)
                :fired? false
                :condition-result condition-result
                :removed? false}})))

(defn- process-trigger-group
  "Processes a group of triggers in order.

  Returns accumulated results with updated state and registry. If any trigger
  sets `:prevented?` in its effect result, processing continues but the
  prevented flag is set."
  [state registry triggers event]
  (reduce
   (fn [{:keys [state registry results prevented?]} trigger]
     (let [{:keys [state registry result]} (process-trigger state registry trigger event)
           new-prevented? (or prevented?
                              (get-in result [:effect-result :prevented?]))]
       {:state state
        :registry registry
        :results (conj results result)
        :prevented? new-prevented?}))
   {:state state :registry registry :results [] :prevented? false}
   triggers))

(defn- partition-by-timing
  "Partitions triggers into groups by timing."
  [triggers]
  (let [grouped (group-by :timing triggers)]
    {:before  (get grouped :polix-triggers.timing/before [])
     :instead (get grouped :polix-triggers.timing/instead [])
     :after   (get grouped :polix-triggers.timing/after [])
     :at      (get grouped :polix-triggers.timing/at [])}))

(defn fire-event
  "Fires an event and processes all matching triggers.

  Takes a context map containing `:registry` and `:state`, plus the event to
  fire. Returns a [[FireEventResult]] with updated state, registry, and
  processing results.

  Processing order:
  1. Before triggers (can set `:prevented?`)
  2. Instead triggers (first match only, if not prevented)
  3. After triggers (if not prevented)

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
  (let [triggers (registry/get-triggers-for-event registry (:type event))
        {:keys [before instead after at]} (partition-by-timing triggers)

        ;; Process before triggers
        before-result (process-trigger-group state registry before event)

        ;; If prevented, skip instead and after
        prevented? (:prevented? before-result)]
    (if prevented?
      {:state (:state before-result)
       :registry (:registry before-result)
       :event event
       :results (:results before-result)
       :prevented? true}

      ;; Process instead triggers (first match only)
      (let [instead-result (if (seq instead)
                             (process-trigger-group
                              (:state before-result)
                              (:registry before-result)
                              [(first instead)]
                              event)
                             {:state (:state before-result)
                              :registry (:registry before-result)
                              :results []
                              :prevented? false})

            ;; Process after triggers
            after-result (process-trigger-group
                          (:state instead-result)
                          (:registry instead-result)
                          after
                          event)

            ;; Process at triggers (same as after for now)
            at-result (process-trigger-group
                       (:state after-result)
                       (:registry after-result)
                       at
                       event)]
        {:state (:state at-result)
         :registry (:registry at-result)
         :event event
         :results (concat (:results before-result)
                          (:results instead-result)
                          (:results after-result)
                          (:results at-result))
         :prevented? false}))))
