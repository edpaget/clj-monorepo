(ns bashketball-game-ui.dev.render-tracking
  "Development tools for tracking component re-renders.

  Provides hooks and utilities to measure re-render reduction
  from context splitting. Use during development to verify
  performance improvements."
  (:require [uix.core :refer [use-ref use-effect]]))

(def ^:private render-counts
  "Atom tracking render counts by component name."
  (atom {}))

(defn use-render-counter
  "Hook that counts renders for a component.

  Call at the top of a component to track how many times it re-renders.
  In development, logs render count on each render.

  Example:
    (defui my-component []
      (use-render-counter \"my-component\")
      ...)"
  [component-name]
  (let [count-ref (use-ref 0)]
    (use-effect
     (fn []
       (swap! render-counts update component-name (fnil inc 0))
       (set! (.-current count-ref) (inc (.-current count-ref)))
       (js/console.log (str "[render-tracking] " component-name " rendered: " (.-current count-ref) " times"))
       js/undefined))
    (.-current count-ref)))

(defn get-render-stats
  "Returns current render statistics for all tracked components.

  Returns a map of `{component-name -> render-count}`."
  []
  @render-counts)

(defn reset-render-stats!
  "Resets all render statistics to zero."
  []
  (reset! render-counts {}))

(defn print-render-stats
  "Prints a formatted summary of render statistics to console."
  []
  (let [stats @render-counts]
    (js/console.group "[render-tracking] Render Statistics")
    (doseq [[name count] (sort-by val > stats)]
      (js/console.log (str "  " name ": " count " renders")))
    (js/console.groupEnd)))
