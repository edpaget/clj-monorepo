(ns polix-effects.registry
  "Multimethod dispatch for effect handlers.

  Provides the core dispatch mechanism for applying effects. Built-in effects
  are registered here, and domain-specific effects can be added via
  [[register-effect!]]."
  (:require [polix-effects.resolution :as res]))

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
  "Creates a failed result with the original state and error information."
  [state effect error message]
  {:state state
   :applied []
   :failed [{:effect effect :error error :message message}]
   :pending nil})

(defn merge-results
  "Merges two results, combining applied and failed vectors."
  [r1 r2]
  {:state (:state r2)
   :applied (into (:applied r1) (:applied r2))
   :failed (into (:failed r1) (:failed r2))
   :pending (or (:pending r2) (:pending r1))})

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

(defmethod -apply-effect :polix-effects/noop
  [state _effect _ctx _opts]
  (success state []))

(defmethod -apply-effect :polix-effects/assoc-in
  [state {:keys [path value] :as effect} ctx opts]
  (let [resolver       (or (:resolver opts) res/default-resolver)
        resolved-path  (res/resolve-path resolver path ctx)
        resolved-value (res/resolve-ref resolver value ctx)
        new-state      (assoc-in state resolved-path resolved-value)]
    (success new-state [effect])))

(defmethod -apply-effect :polix-effects/sequence
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

(defmethod -apply-effect :polix-effects/update-in
  [state {:keys [path f args] :as effect} ctx opts]
  (let [resolver      (or (:resolver opts) res/default-resolver)
        resolved-path (res/resolve-path resolver path ctx)
        resolved-fn   (res/resolve-fn f)
        resolved-args (mapv #(res/resolve-ref resolver % ctx) (or args []))]
    (if resolved-fn
      (success (apply update-in state resolved-path resolved-fn resolved-args) [effect])
      (failure state effect :unknown-function (str "Unknown function: " f)))))

(defmethod -apply-effect :polix-effects/dissoc-in
  [state {:keys [path] :as effect} ctx opts]
  (let [resolver      (or (:resolver opts) res/default-resolver)
        resolved-path (res/resolve-path resolver path ctx)
        parent-path   (vec (butlast resolved-path))
        k             (last resolved-path)]
    (if (empty? parent-path)
      (success (dissoc state k) [effect])
      (success (update-in state parent-path dissoc k) [effect]))))
