(ns bashketball-game-ui.context.ui-state
  "UI interaction state context.

  Contains all machine states and modal hooks:
  - Selection machine (mode, data, send, can-send?)
  - Discard machine
  - Substitute machine
  - Peek machine
  - Modal hooks (detail, fate-reveal, create-token, attach-ability)"
  (:require [uix.core :refer [create-context use-context]]))

(def ui-state-context
  "React context for UI interaction state."
  (create-context nil))

(defn use-ui-state
  "Returns the UI state context value.

  Contains selection machine, discard machine, substitute machine,
  peek machine, and modal hook states.
  Throws if used outside of game-provider."
  []
  (let [ctx (use-context ui-state-context)]
    (when-not ctx
      (throw (js/Error. "use-ui-state must be used within game-provider")))
    ctx))
