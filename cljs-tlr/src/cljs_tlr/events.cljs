(ns cljs-tlr.events
  "Low-level event firing utilities.

  These functions fire DOM events directly on elements. For most tests,
  prefer [[cljs-tlr.user-event]] which simulates real user interactions
  more accurately (e.g., firing focus, keydown, keyup, and input events
  for typing, not just a single event).

  Use these functions when you need precise control over individual
  events or when testing event handlers directly."
  (:require
   ["@testing-library/react" :as tlr]))

(defn click
  "Fires a click event on the element."
  [^js element]
  (.click tlr/fireEvent element))

(defn dbl-click
  "Fires a double-click event on the element."
  [^js element]
  (.dblClick tlr/fireEvent element))

(defn change
  "Fires a change event with the given value.

  For input elements, pass the new value and it will be set
  on `event.target.value`."
  [^js element value]
  (.change tlr/fireEvent element #js {:target #js {:value value}}))

(defn input
  "Fires an input event with the given value."
  [^js element value]
  (.input tlr/fireEvent element #js {:target #js {:value value}}))

(defn submit
  "Fires a submit event on a form element."
  [^js element]
  (.submit tlr/fireEvent element))

(defn focus
  "Fires a focus event on the element."
  [^js element]
  (.focus tlr/fireEvent element))

(defn blur
  "Fires a blur event on the element."
  [^js element]
  (.blur tlr/fireEvent element))

(defn key-down
  "Fires a keyDown event on the element.

  The key-opts map should contain properties like `:key`, `:code`,
  `:charCode`, `:keyCode`, etc."
  [^js element key-opts]
  (.keyDown tlr/fireEvent element (clj->js key-opts)))

(defn key-up
  "Fires a keyUp event on the element."
  [^js element key-opts]
  (.keyUp tlr/fireEvent element (clj->js key-opts)))

(defn key-press
  "Fires a keyPress event on the element."
  [^js element key-opts]
  (.keyPress tlr/fireEvent element (clj->js key-opts)))

(defn mouse-over
  "Fires a mouseOver event on the element."
  [^js element]
  (.mouseOver tlr/fireEvent element))

(defn mouse-out
  "Fires a mouseOut event on the element."
  [^js element]
  (.mouseOut tlr/fireEvent element))

(defn mouse-enter
  "Fires a mouseEnter event on the element."
  [^js element]
  (.mouseEnter tlr/fireEvent element))

(defn mouse-leave
  "Fires a mouseLeave event on the element."
  [^js element]
  (.mouseLeave tlr/fireEvent element))

(defn mouse-down
  "Fires a mouseDown event on the element."
  [^js element]
  (.mouseDown tlr/fireEvent element))

(defn mouse-up
  "Fires a mouseUp event on the element."
  [^js element]
  (.mouseUp tlr/fireEvent element))

(defn scroll
  "Fires a scroll event on the element."
  [^js element]
  (.scroll tlr/fireEvent element))

(defn drag
  "Fires a drag event on the element."
  [^js element]
  (.drag tlr/fireEvent element))

(defn fire-drop
  "Fires a drop event on the element."
  [^js element]
  (.drop tlr/fireEvent element))
