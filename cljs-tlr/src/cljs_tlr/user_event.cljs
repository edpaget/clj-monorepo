(ns cljs-tlr.user-event
  "High-level user interaction simulation.

  User events simulate real user behavior more accurately than
  [[cljs-tlr.events]]. For example, clicking an element will:

  1. Move pointer to element
  2. Fire mouseOver/mouseEnter events
  3. Fire mouseDown event
  4. Fire focus event (if focusable)
  5. Fire mouseUp event
  6. Fire click event

  All functions return Promises and should be used with async testing
  patterns like `(t/async done ...)` or the [[cljs-tlr.async/async-test]]
  macro."
  (:require
   ["@testing-library/user-event" :as ue]))

(def ^:private user-event (.-default ue))

(defn setup
  "Creates a new user-event instance with optional configuration.

  Options map supports:

  - `:delay` - Delay between actions in ms (default: 0)
  - `:pointer-events-check` - Validate pointer-events CSS (default: true)
  - `:skip-hover` - Skip hover events before click (default: false)
  - `:skip-click` - Skip click events for type/select (default: false)

  Returns a user object to pass to interaction functions.

  Example:

      (let [user (setup {:delay 100})]
        (-> (click user button)
            (.then #(type user input \"hello\"))))"
  (^js []
   (.setup user-event))
  (^js [opts]
   (.setup user-event (clj->js opts))))

(defn click
  "Clicks an element. Returns a Promise.

  Simulates the full click sequence: pointer move, mousedown,
  focus (if applicable), mouseup, and click."
  [^js user ^js element]
  (.click user element))

(defn dbl-click
  "Double-clicks an element. Returns a Promise."
  [^js user ^js element]
  (.dblClick user element))

(defn triple-click
  "Triple-clicks an element. Returns a Promise.

  Useful for selecting entire paragraphs of text."
  [^js user ^js element]
  (.tripleClick user element))

(defn type-text
  "Types text into an element. Returns a Promise.

  Simulates typing character by character, firing keydown, keypress,
  input, and keyup events for each character.

  Example:

      (-> (type-text user input \"hello@example.com\")
          (.then #(is (= \"hello@example.com\" (.-value input)))))"
  [^js user ^js element text]
  (.type user element text))

(defn clear
  "Clears an input or textarea element. Returns a Promise.

  Selects all text and deletes it, simulating Ctrl+A followed by Delete."
  [^js user ^js element]
  (.clear user element))

(defn select-options
  "Selects options in a select element. Returns a Promise.

  Values can be a single value or array of values for multi-select."
  [^js user ^js element values]
  (.selectOptions user element (clj->js values)))

(defn deselect-options
  "Deselects options in a multi-select element. Returns a Promise."
  [^js user ^js element values]
  (.deselectOptions user element (clj->js values)))

(defn tab
  "Presses Tab to move focus. Returns a Promise.

  Options:

  - `:shift` - Hold shift key (move backwards)"
  ([^js user]
   (.tab user))
  ([^js user opts]
   (.tab user (clj->js opts))))

(defn keyboard
  "Types using keyboard shortcuts and special keys. Returns a Promise.

  Supports special key syntax:

  - `{Enter}` - Press Enter key
  - `{Tab}` - Press Tab key
  - `{Escape}` - Press Escape key
  - `{Backspace}` - Press Backspace
  - `{Delete}` - Press Delete
  - `{ArrowLeft}` - Press left arrow
  - `{Shift>}` - Hold Shift (release with `{/Shift}`)
  - `{Control>}` - Hold Control
  - `{Alt>}` - Hold Alt

  Example:

      (keyboard user \"{Shift>}hello{/Shift}\") ; types HELLO"
  [^js user text]
  (.keyboard user text))

(defn hover
  "Hovers pointer over an element. Returns a Promise."
  [^js user ^js element]
  (.hover user element))

(defn unhover
  "Moves pointer away from element. Returns a Promise."
  [^js user ^js element]
  (.unhover user element))

(defn pointer
  "Performs pointer actions. Returns a Promise.

  Low-level API for complex pointer interactions. Accepts an
  action map or array of action maps with keys like `:target`,
  `:keys`, `:offset`, etc."
  [^js user actions]
  (.pointer user (clj->js actions)))

(defn upload
  "Uploads files to a file input. Returns a Promise.

  Files should be File objects or an array of File objects."
  [^js user ^js element files]
  (.upload user element files))

(defn copy
  "Copies selected text to clipboard. Returns a Promise."
  [^js user]
  (.copy user))

(defn cut
  "Cuts selected text to clipboard. Returns a Promise."
  [^js user]
  (.cut user))

(defn paste
  "Pastes from clipboard or provided text. Returns a Promise."
  ([^js user]
   (.paste user))
  ([^js user text]
   (.paste user text)))
