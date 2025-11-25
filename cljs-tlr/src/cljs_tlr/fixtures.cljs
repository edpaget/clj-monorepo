(ns cljs-tlr.fixtures
  "Test fixtures for React Testing Library.

  Provides fixtures for managing component lifecycle in tests, ensuring
  proper cleanup between test runs to prevent state leakage."
  (:require
   ["@testing-library/react" :as tlr]))

(def cleanup-fixture
  "Fixture that cleans up rendered components after each test.

  React Testing Library accumulates rendered components in the DOM. This
  fixture ensures each test starts with a clean slate by calling cleanup
  after every test.

  Usage:

      (use-fixtures :each fixtures/cleanup-fixture)"
  {:after (fn [] (tlr/cleanup))})

(defn configure!
  "Configures testing-library options globally.

  Accepts a map of options that configure testing-library behavior:

  - `:async-util-timeout` - Default timeout for async utilities (ms)
  - `:computed-style-supports-pseudo-elements` - Enable pseudo-element support
  - `:default-hidden` - Include hidden elements in queries
  - `:show-original-stack-trace` - Show original stack traces
  - `:throw-suggestions` - Throw errors with query suggestions

  Call this once at test suite initialization."
  [opts]
  (tlr/configure (clj->js opts)))
