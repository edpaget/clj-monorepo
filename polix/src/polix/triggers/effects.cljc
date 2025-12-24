(ns polix.triggers.effects
  "Effect application stub for trigger system.

  This namespace provides a minimal effect application interface that will
  be fully integrated with the polix effects system in Phase 7. For now,
  it implements basic effect types directly to allow trigger testing.

  Full effect integration with polix-effects will be added in Phase 7.")

(defn- apply-assoc-in
  "Applies an assoc-in effect."
  [state {:keys [path value]}]
  (assoc-in state path value))

(defn- apply-update-in
  "Applies an update-in effect."
  [state {:keys [path f args]}]
  (let [update-fn (cond
                    (fn? f)      f
                    (= f :inc)   inc
                    (= f :dec)   dec
                    (= f :conj)  conj
                    :else        identity)]
    (if (seq args)
      (apply update-in state path update-fn args)
      (update-in state path update-fn))))

(defn- apply-single-effect
  "Applies a single effect to state. Returns updated state or throws."
  [state effect _ctx]
  (case (:type effect)
    :polix-effects/noop      state
    :polix-effects/assoc-in  (apply-assoc-in state effect)
    :polix-effects/update-in (apply-update-in state effect)
    :polix-effects/sequence  (reduce #(apply-single-effect %1 %2 _ctx)
                                      state
                                      (:effects effect))
    ;; For unknown effect types, just return state unchanged
    ;; Full effect handling will be added in Phase 7
    state))

(defn apply-effect
  "Applies an effect to state within context.

  Takes state, effect definition, and context. Returns a result map with:

  - `:state` - The new state after applying the effect
  - `:applied` - Vector of effects that were successfully applied
  - `:failed` - Vector of failure info for effects that failed
  - `:pending` - Nil, or pending choice info

  Note: This is a minimal implementation for Phase 6 testing.
  Full integration with polix-effects will be added in Phase 7."
  [state effect ctx]
  (try
    (let [new-state (apply-single-effect state effect ctx)]
      {:state   new-state
       :applied [effect]
       :failed  []
       :pending nil})
    (catch #?(:clj Exception :cljs :default) e
      {:state   state
       :applied []
       :failed  [{:effect  effect
                  :error   :effect-failed
                  :message #?(:clj (.getMessage e)
                              :cljs (.-message e))}]
       :pending nil})))
