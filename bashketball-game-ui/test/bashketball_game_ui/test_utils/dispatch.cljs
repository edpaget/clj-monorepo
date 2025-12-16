(ns bashketball-game-ui.test-utils.dispatch
  "Test utilities for dispatch-based component testing.

  Provides helpers for capturing and asserting dispatched actions in tests.
  This simplifies component testing by replacing complex mock setups with
  a simple capture mechanism."
  (:require
   [bashketball-game-ui.context.dispatch :refer [dispatch-provider]]
   [uix.core :refer [$]]))

(defn capture-dispatch
  "Returns a map with a dispatch fn that captures all dispatched actions.

  Returns map with:
  - `:dispatch` - The dispatch function to pass to dispatch-provider
  - `:actions` - Atom containing vector of dispatched actions
  - `:reset!` - Function to clear captured actions
  - `:last-action` - Function to get most recently dispatched action"
  []
  (let [actions (atom [])]
    {:dispatch    (fn [action] (swap! actions conj action))
     :actions     actions
     :reset!      #(reset! actions [])
     :last-action #(last @actions)}))

(defn with-dispatch-provider
  "Wraps a component in dispatch-provider with capture for testing.

  Returns a map with:
  - `:element` - The wrapped component ready for rendering
  - `:actions` - Atom containing captured actions
  - `:last-action` - Function to get most recently dispatched action
  - `:reset!` - Function to clear captured actions"
  [component]
  (let [{:keys [dispatch actions last-action reset!]} (capture-dispatch)]
    {:element     ($ dispatch-provider {:dispatch dispatch} component)
     :actions     actions
     :last-action last-action
     :reset!      reset!}))

(defn dispatched?
  "Returns true if an action of given type was dispatched.

  Takes the actions atom from capture-dispatch and an action type keyword."
  [actions-atom action-type]
  (some #(= action-type (:type %)) @actions-atom))

(defn dispatched-actions
  "Returns all dispatched actions of the given type."
  [actions-atom action-type]
  (filter #(= action-type (:type %)) @actions-atom))

(defn action-count
  "Returns the number of actions dispatched of the given type."
  [actions-atom action-type]
  (count (dispatched-actions actions-atom action-type)))

(defn clear-actions!
  "Clears all captured actions from the atom."
  [actions-atom]
  (reset! actions-atom []))

(defn assert-dispatched
  "Asserts that an action of the given type was dispatched.

  Returns the first matching action for further assertions."
  [actions-atom action-type]
  (let [matches (dispatched-actions actions-atom action-type)]
    (when (empty? matches)
      (throw (js/Error. (str "Expected action " action-type " to be dispatched, but it was not. "
                             "Dispatched: " (pr-str (map :type @actions-atom))))))
    (first matches)))

(defn assert-not-dispatched
  "Asserts that no action of the given type was dispatched."
  [actions-atom action-type]
  (when (dispatched? actions-atom action-type)
    (throw (js/Error. (str "Expected action " action-type " NOT to be dispatched, but it was.")))))

(defn assert-action-payload
  "Asserts that an action was dispatched with the expected payload.

  Takes the actions atom, action type, and expected payload map.
  The payload is matched partially - the action can have additional keys."
  [actions-atom action-type expected-payload]
  (let [action (assert-dispatched actions-atom action-type)]
    (doseq [[k v] expected-payload]
      (when (not= v (get action k))
        (throw (js/Error. (str "Expected action " action-type " to have " k " = " v
                               ", but got " (get action k))))))))
