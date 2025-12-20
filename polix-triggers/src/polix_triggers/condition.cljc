(ns polix-triggers.condition
  "Condition evaluation using polix.

  Integrates polix policy parsing and evaluation for trigger conditions.
  Conditions are polix policy expressions that access trigger context and event
  data via the `:doc/` prefix."
  (:require [cats.core :as m]
            [cats.monad.either :as either]
            [polix.ast :as ast]
            [polix.parser :as parser]))

(def ^:private operators
  "Operators available in condition expressions."
  {:= =
   :!= not=
   :> >
   :>= >=
   :< <
   :<= <=
   :in (fn [v coll] (contains? (set coll) v))
   :not-in (fn [v coll] (not (contains? (set coll) v)))})

(declare eval-ast)

(defn- eval-and
  "Evaluates an :and expression with short-circuit semantics."
  [children document]
  (loop [remaining children]
    (if (empty? remaining)
      true
      (let [result (eval-ast (first remaining) document)]
        (cond
          (false? result) false
          (map? result) result
          :else (recur (rest remaining)))))))

(defn- eval-or
  "Evaluates an :or expression with short-circuit semantics."
  [children document]
  (loop [remaining children]
    (if (empty? remaining)
      false
      (let [result (eval-ast (first remaining) document)]
        (cond
          (true? result) true
          (map? result) result
          :else (recur (rest remaining)))))))

(defn- eval-not
  "Evaluates a :not expression."
  [children document]
  (let [result (eval-ast (first children) document)]
    (cond
      (boolean? result) (not result)
      (map? result) result)))

(defn- eval-function-call
  "Evaluates a function call AST node."
  [node document]
  (let [op-name (:value node)
        children (:children node)]
    (case op-name
      :and (eval-and children document)
      :or (eval-or children document)
      :not (eval-not children document)
      (if-let [op (get operators op-name)]
        (let [args (mapv #(eval-ast % document) children)]
          (if (some map? args)
            {:residual {op-name args}}
            (apply op args)))
        (throw (ex-info "Unknown operator" {:operator op-name}))))))

(defn- eval-ast
  "Recursively evaluates an AST node against a document."
  [node document]
  (case (:type node)
    ::ast/literal
    (:value node)

    ::ast/doc-accessor
    (let [key (:value node)]
      (if (contains? document key)
        (get document key)
        {:residual {key :missing}}))

    ::ast/function-call
    (eval-function-call node document)

    (throw (ex-info "Unsupported AST node type" {:type (:type node)}))))

(defn compile-condition
  "Compiles a condition expression into an evaluation function.

  Takes a polix policy expression and returns a function that evaluates
  against a document. The condition should use `:doc/` prefixed keywords
  to access document values (e.g., `[:= :doc/event-type :test/damaged]`).

  Returns a function `(fn [document])` that returns `true`, `false`, or
  `{:residual {...}}` for missing keys."
  [condition]
  (let [parse-result (parser/parse-policy condition)]
    (if (either/left? parse-result)
      (throw (ex-info "Failed to parse condition"
                      {:condition condition
                       :error (m/extract parse-result)}))
      (let [ast (m/extract parse-result)]
        (fn [document]
          (eval-ast ast document))))))

(defn build-trigger-document
  "Builds a document for condition evaluation from trigger context and event.

  The document contains:
  - `:self`, `:owner`, `:source` - trigger bindings
  - `:event-type` - the event's `:type` value
  - All other event fields (without prefix)

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
   {:self (:self trigger)
    :owner (:owner trigger)
    :source (:source trigger)
    :event-type (:type event)}
   (dissoc event :type)))

(defn evaluate-condition
  "Evaluates a trigger's condition against an event.

  Takes a trigger (with compiled `:condition-fn` or raw `:condition`) and
  an event. Returns `true`, `false`, or `{:residual {...}}`.

  If the trigger has no condition, returns `true` (always fires)."
  [trigger event]
  (if-let [condition-fn (or (:condition-fn trigger)
                            (when-let [cond (:condition trigger)]
                              (compile-condition cond)))]
    (let [doc (build-trigger-document trigger event)]
      (condition-fn doc))
    true))
