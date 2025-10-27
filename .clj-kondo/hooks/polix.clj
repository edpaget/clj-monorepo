(ns hooks.polix
  (:require [clj-kondo.hooks-api :as api]))

(defn defpolicy
  "Hook for polix.core/defpolicy macro.

  Transforms (defpolicy MyPolicy \"doc\" expr) into (def MyPolicy \"doc\" expr)
  so that clj-kondo recognizes the var definition."
  [{:keys [node]}]
  (let [[_defpolicy name-node & args] (:children node)
        ;; Check if first arg is a docstring
        docstring? (and (seq args)
                        (api/string-node? (first args)))
        ;; Build def form
        new-node (api/list-node
                  (list
                   (api/token-node 'def)
                   name-node
                   (when docstring?
                     (first args))))]
    {:node new-node}))
