(ns hooks.db.core
  (:require [clj-kondo.hooks-api :as api]))

(defn with-connection
  "Hook for db.core/with-connection macro.

  Transforms (db/with-connection [conn] body...)
  into (let [conn nil] body...)
  so that clj-kondo recognizes the binding and doesn't warn about unused symbols."
  [{:keys [node]}]
  (let [[_with-connection bindings & body] (:children node)
        ;; Extract the binding symbol from [conn]
        binding-vec (:children bindings)
        conn-symbol (first binding-vec)
        ;; Create a let binding with nil value: [conn nil]
        new-bindings (api/vector-node [conn-symbol (api/token-node 'nil)])
        ;; Transform into a let binding
        new-node (api/list-node
                  (list*
                   (api/token-node 'let)
                   new-bindings
                   body))]
    {:node new-node}))
