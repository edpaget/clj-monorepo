(ns bashketball-game-ui.context.game-config
  "Static game configuration context.

  Contains data that rarely changes after initial game load:
  - game record (player IDs, timestamps)
  - my-team determination
  - catalog (card slug to card data map)
  - actions dispatch functions"
  (:require [uix.core :refer [create-context use-context]]))

(def game-config-context
  "React context for static game configuration."
  (create-context nil))

(defn use-game-config
  "Returns the game config context value.

  Contains `:game`, `:my-team`, `:catalog`, and `:actions`.
  Throws if used outside of game-provider."
  []
  (let [ctx (use-context game-config-context)]
    (when-not ctx
      (throw (js/Error. "use-game-config must be used within game-provider")))
    ctx))
