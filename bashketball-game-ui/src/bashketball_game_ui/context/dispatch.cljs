(ns bashketball-game-ui.context.dispatch
  "Centralized action dispatch context.

  Provides a dispatch function that routes action maps to game actions.
  Components use [[use-dispatch]] to get the dispatch function and send
  actions without needing direct access to game action handlers.

  This pattern:
  - Reduces component dependencies to a single function
  - Makes action payloads inspectable and testable
  - Enables action logging/replay for debugging"
  (:require [uix.core :as uix]))

(def dispatch-context
  "React context for the dispatch function."
  (uix/create-context nil))

(defn use-dispatch
  "Returns the dispatch function from context.

  The dispatch function accepts action maps with `:type` and optional data.
  Throws if used outside of [[dispatch-provider]]."
  []
  (let [dispatch (uix/use-context dispatch-context)]
    (when-not dispatch
      (throw (js/Error. "use-dispatch must be used within dispatch-provider")))
    dispatch))

(uix/defui dispatch-provider
  "Provider component that supplies the dispatch function.

  Wraps children with the dispatch context, making the dispatch function
  available to all descendants via [[use-dispatch]]."
  [{:keys [dispatch children]}]
  (uix/$ (.-Provider dispatch-context) {:value dispatch}
         children))
