(ns bashketball-game-ui.context.game-state
  "Dynamic game state context.

  Contains data that changes on every game mutation:
  - game-state (core engine state)
  - is-my-turn computation
  - connection status (loading, error, connected)"
  (:require [uix.core :refer [create-context use-context]]))

(def game-state-context
  "React context for dynamic game state."
  (create-context nil))

(defn use-game-state-ctx
  "Returns the game state context value.

  Contains `:game-state`, `:is-my-turn`, `:loading`, `:error`, and `:connected`.
  Throws if used outside of game-provider."
  []
  (let [ctx (use-context game-state-context)]
    (when-not ctx
      (throw (js/Error. "use-game-state-ctx must be used within game-provider")))
    ctx))
