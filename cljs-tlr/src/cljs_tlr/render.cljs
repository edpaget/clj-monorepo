(ns cljs-tlr.render
  "Component rendering utilities for React Testing Library.

  Provides functions to render React components into a test container,
  returning query utilities for interacting with the rendered output."
  (:require
   ["@testing-library/react" :as tlr]))

(defn render
  "Renders a React element and returns an object with query utilities.

  The returned object provides methods like `getByText`, `queryByRole`, etc.
  for querying the rendered DOM. These are bound to the container where
  the component was rendered.

  The optional options map supports:

  - `:container` - DOM element to render into (default: appends to body)
  - `:base-element` - Base element for queries (default: container)
  - `:wrapper` - Wrapper component for context providers

  Example:

      (let [result (render ($ my-component {:name \"test\"}))]
        (.getByText result \"Hello\"))"
  (^js [element]
   (tlr/render element))
  (^js [element opts]
   (tlr/render element (clj->js opts))))

(defn render-hook
  "Renders a React hook for testing in isolation.

  Takes a function that calls the hook and returns an object with:

  - `result.current` - The current return value of the hook
  - `rerender` - Function to re-render with new props
  - `unmount` - Function to unmount the hook

  Example:

      (let [result (render-hook #(use-state 0))]
        (is (= 0 (.-current (.-result result)))))"
  (^js [hook-fn]
   (tlr/renderHook hook-fn))
  (^js [hook-fn opts]
   (tlr/renderHook hook-fn (clj->js opts))))

(defn rerender
  "Re-renders the component with new props.

  Takes the result object from [[render]] and a new React element.
  Useful for testing how components respond to prop changes."
  [^js render-result element]
  (.rerender render-result element))

(defn unmount
  "Unmounts the rendered component.

  Takes the result object from [[render]]. After unmounting, the component
  is removed from the DOM and any effects are cleaned up."
  [^js render-result]
  (.unmount render-result))
