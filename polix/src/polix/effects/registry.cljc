(ns polix.effects.registry
  "Multimethod dispatch for effect handlers.

  Provides the core dispatch mechanism for applying effects. Built-in effects
  are registered here, and domain-specific effects can be added via
  [[register-effect!]]."
  (:require [polix.core :as polix]
            [polix.effects.resolution :as res]))

;;; ---------------------------------------------------------------------------
;;; Result Constructors
;;; ---------------------------------------------------------------------------

(defn success
  "Creates a successful result with the new state and applied effects."
  [state applied]
  {:state state
   :applied (if (sequential? applied) (vec applied) [applied])
   :failed []
   :pending nil})

(defn failure
  "Creates a failed result with the original state and error information.

  Optionally accepts a details map for additional context (e.g., conflict info)."
  ([state effect error message]
   {:state state
    :applied []
    :failed [{:effect effect :error error :message message}]
    :pending nil})
  ([state effect error message details]
   {:state state
    :applied []
    :failed [{:effect effect :error error :message message :details details}]
    :pending nil}))

(defn pending
  "Creates a pending result for deferred effects.

  Used when a conditional effect has an open residual and `:on-residual :defer`
  strategy. The caller can re-evaluate when more data becomes available."
  [state effect residual]
  {:state state
   :applied []
   :failed []
   :pending {:effect effect :residual residual :type :deferred}})

(defn merge-results
  "Merges two results, combining applied, failed, and speculative vectors."
  [r1 r2]
  {:state (:state r2)
   :applied (into (:applied r1) (:applied r2))
   :failed (into (:failed r1) (:failed r2))
   :pending (or (:pending r2) (:pending r1))
   :speculative-conditions (into (or (:speculative-conditions r1) [])
                                 (or (:speculative-conditions r2) []))})

;;; ---------------------------------------------------------------------------
;;; Multimethod Dispatch
;;; ---------------------------------------------------------------------------

(defmulti -apply-effect
  "Internal multimethod for effect application. Dispatches on `:type`.

  Handlers receive `[state effect ctx opts]` and return a result map with
  `:state`, `:applied`, `:failed`, and `:pending` keys.

  Use [[register-effect!]] to add custom effect handlers."
  (fn [_state effect _ctx _opts] (:type effect)))

(defmethod -apply-effect :default
  [state effect _ctx _opts]
  (failure state effect :unknown-effect-type
           (str "Unknown effect type: " (:type effect))))

;;; ---------------------------------------------------------------------------
;;; Registration
;;; ---------------------------------------------------------------------------

(defn register-effect!
  "Registers a custom effect handler for the given effect type.

  The handler function receives `[state effect ctx opts]` and must return
  a result map with `:state`, `:applied`, `:failed`, and `:pending` keys.
  Use [[success]] and [[failure]] helpers to construct results."
  [effect-type handler-fn]
  (defmethod -apply-effect effect-type
    [state effect ctx opts]
    (handler-fn state effect ctx opts)))

(defn effect-types
  "Returns the set of all registered effect types."
  []
  (set (keys (methods -apply-effect))))

;;; ---------------------------------------------------------------------------
;;; Built-in Handlers
;;; ---------------------------------------------------------------------------

(defmethod -apply-effect :polix.effects/noop
  [state _effect _ctx _opts]
  (success state []))

(defmethod -apply-effect :polix.effects/assoc-in
  [state {:keys [path value] :as effect} ctx opts]
  (let [resolver       (or (:resolver opts) res/default-resolver)
        resolved-path  (res/resolve-path resolver path ctx)
        resolved-value (res/resolve-ref resolver value ctx)
        new-state      (assoc-in state resolved-path resolved-value)]
    (success new-state [effect])))

(defmethod -apply-effect :polix.effects/sequence
  [state {:keys [effects]} ctx opts]
  (reduce
   (fn [{:keys [state applied failed pending] :as acc} effect]
     (if pending
       acc
       (let [result (-apply-effect state effect ctx opts)]
         {:state (:state result)
          :applied (into applied (:applied result))
          :failed (into failed (:failed result))
          :pending (:pending result)})))
   {:state state :applied [] :failed [] :pending nil}
   effects))

(defmethod -apply-effect :polix.effects/update-in
  [state {:keys [path f args] :as effect} ctx opts]
  (let [resolver      (or (:resolver opts) res/default-resolver)
        resolved-path (res/resolve-path resolver path ctx)
        resolved-fn   (res/resolve-fn f)
        resolved-args (mapv #(res/resolve-ref resolver % ctx) (or args []))]
    (if resolved-fn
      (success (apply update-in state resolved-path resolved-fn resolved-args) [effect])
      (failure state effect :unknown-function (str "Unknown function: " f)))))

(defmethod -apply-effect :polix.effects/dissoc-in
  [state {:keys [path] :as effect} ctx opts]
  (let [resolver      (or (:resolver opts) res/default-resolver)
        resolved-path (res/resolve-path resolver path ctx)
        parent-path   (vec (butlast resolved-path))
        k             (last resolved-path)]
    (if (empty? parent-path)
      (success (dissoc state k) [effect])
      (success (update-in state parent-path dissoc k) [effect]))))

(defmethod -apply-effect :polix.effects/merge-in
  [state {:keys [path value] :as effect} ctx opts]
  (let [resolver       (or (:resolver opts) res/default-resolver)
        resolved-path  (res/resolve-path resolver path ctx)
        resolved-value (res/resolve-ref resolver value ctx)]
    (success (update-in state resolved-path merge resolved-value) [effect])))

;;; ---------------------------------------------------------------------------
;;; Collection Handlers
;;; ---------------------------------------------------------------------------

(defmethod -apply-effect :polix.effects/conj-in
  [state {:keys [path value] :as effect} ctx opts]
  (let [resolver       (or (:resolver opts) res/default-resolver)
        resolved-path  (res/resolve-path resolver path ctx)
        resolved-value (res/resolve-ref resolver value ctx)]
    (success (update-in state resolved-path conj resolved-value) [effect])))

(defmethod -apply-effect :polix.effects/remove-in
  [state {:keys [path predicate] :as effect} ctx opts]
  (let [resolver      (or (:resolver opts) res/default-resolver)
        resolved-path (res/resolve-path resolver path ctx)
        pred-fn       (res/resolve-predicate predicate)]
    (success (update-in state resolved-path #(vec (remove pred-fn %))) [effect])))

(defmethod -apply-effect :polix.effects/move
  [state {:keys [from-path to-path predicate] :as effect} ctx opts]
  (let [resolver           (or (:resolver opts) res/default-resolver)
        resolved-from-path (res/resolve-path resolver from-path ctx)
        resolved-to-path   (res/resolve-path resolver to-path ctx)
        pred-fn            (res/resolve-predicate predicate)
        source-coll        (get-in state resolved-from-path)
        items-to-move      (filter pred-fn source-coll)
        remaining          (vec (remove pred-fn source-coll))
        new-state          (-> state
                               (assoc-in resolved-from-path remaining)
                               (update-in resolved-to-path into items-to-move))]
    (success new-state [effect])))

;;; ---------------------------------------------------------------------------
;;; Composite Handlers
;;; ---------------------------------------------------------------------------

(defn- validate-speculations
  "Checks if any speculative conditions have become conflicts.

  Returns nil if all speculations are still valid, or the first conflicting
  speculation info if any have become conflicts."
  [state speculative-conditions]
  (first
   (for [{:keys [condition]} speculative-conditions
         :let                [new-result (polix/unify condition state)]
         :when               (polix/has-conflicts? new-result)]
     {:condition condition :conflict-residual new-result})))

(defmethod -apply-effect :polix.effects/transaction
  [state {:keys [effects on-failure] :as tx-effect} ctx opts]
  (let [failure-strategy (or on-failure :rollback)]
    (loop [remaining     effects
           current-state state
           applied       []
           speculative   []]
      (if (empty? remaining)
        ;; Done - validate speculations
        (if-let [conflict (validate-speculations current-state speculative)]
          (failure state tx-effect :speculation-conflict
                   "Speculative condition became conflict" conflict)
          {:state current-state :applied applied :failed [] :pending nil})

        (let [effect        (first remaining)
              effect-result (-apply-effect current-state effect ctx opts)]
          (cond
            ;; Pending - propagate up, rollback
            (:pending effect-result)
            {:state state :applied [] :failed []
             :pending (:pending effect-result)}

            ;; Failed
            (seq (:failed effect-result))
            (case failure-strategy
              :rollback {:state state :applied [] :pending nil
                         :failed (:failed effect-result)}
              :partial  {:state current-state :applied applied :pending nil
                         :failed (:failed effect-result)})

            ;; Success - check speculations against new state
            :else
            (let [new-specs (into speculative
                                  (or (:speculative-conditions effect-result) []))]
              (if-let [conflict (validate-speculations (:state effect-result) new-specs)]
                (failure state tx-effect :speculation-conflict
                         "Speculation invalidated by effect" conflict)
                (recur (rest remaining)
                       (:state effect-result)
                       (into applied (:applied effect-result))
                       new-specs)))))))))

(defmethod -apply-effect :polix.effects/let
  [state {:keys [bindings effect]} ctx opts]
  (let [resolver     (or (:resolver opts) res/default-resolver)
        new-bindings (reduce
                      (fn [acc [k v]]
                        (assoc acc k (res/resolve-ref resolver v (assoc ctx :bindings acc))))
                      (or (:bindings ctx) {})
                      (partition 2 bindings))
        new-ctx      (assoc ctx :bindings new-bindings)]
    (-apply-effect state effect new-ctx opts)))

(defmethod -apply-effect :polix.effects/conditional
  [state {:keys [condition then else on-residual] :as effect} ctx opts]
  (let [result   (polix/unify condition state)
        strategy (or on-residual :block)]
    (cond
      ;; Satisfied - apply then branch
      (polix/satisfied? result)
      (if then
        (-apply-effect state then ctx opts)
        (success state [effect]))

      ;; Conflict - apply else branch
      (polix/has-conflicts? result)
      (if else
        (-apply-effect state else ctx opts)
        (success state [effect]))

      ;; Open residual - handle by strategy
      :else
      (case strategy
        :block
        (if else
          (-apply-effect state else ctx opts)
          (success state [effect]))

        :defer
        (pending state effect result)

        :proceed
        (if then
          (let [r (-apply-effect state then ctx opts)]
            (update r :applied
                    (fn [apps] (mapv #(assoc % :condition-residual result) apps))))
          (success state [effect]))

        :speculate
        (if then
          (let [r (-apply-effect state then ctx opts)]
            (-> r
                (update :applied
                        (fn [apps]
                          (mapv #(assoc % :speculative? true
                                        :speculation-condition condition
                                        :condition-residual result) apps)))
                (update :speculative-conditions (fnil conj [])
                        {:condition condition :residual result})))
          (success state [effect]))))))
