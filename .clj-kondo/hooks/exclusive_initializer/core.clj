(ns hooks.exclusive-initializer.core
  (:require [clj-kondo.hooks-api :as api]))

(defn wrap
  "Kondo hook for the wrap macro."
  [{:keys [node]}]
  (let [[header & body] (rest (:children node))
        [binding-form lock-name] (:children header)
        new-node (api/list-node
                  (list*
                   (api/token-node 'let)
                   (api/vector-node [binding-form (api/map-node {})])
                   lock-name
                   body))]
    {:node new-node}))
