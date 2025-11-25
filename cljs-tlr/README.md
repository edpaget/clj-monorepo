# cljs-tlr

ClojureScript wrapper for [@testing-library/react](https://testing-library.com/docs/react-testing-library/intro/).

Provides Clojure-idiomatic functions for testing React components in a Node.js environment with JSDom.

## Installation

Add to your project's `deps.edn`:

```clojure
{:deps {local/cljs-tlr {:local/root "../cljs-tlr"}}}
```

Install npm dependencies:

```bash
npm install react react-dom @testing-library/react @testing-library/dom @testing-library/user-event jsdom global-jsdom
```

## Configuration

### shadow-cljs.edn

The key to making this work is the `:prepend-js` option which sets up JSDom before any ClojureScript code runs:

```clojure
{:source-paths ["src" "test"]
 :dependencies []
 :builds
 {:test
  {:target :node-test
   :output-to "out/node-tests.js"
   :prepend-js "require('global-jsdom/register');"  ; <-- This line is required
   :autorun true
   :ns-regexp "my-app\\..*-test$"}}}
```

### package.json

```json
{
  "scripts": {
    "test": "shadow-cljs compile test",
    "test:watch": "shadow-cljs watch test"
  }
}
```

## Usage

### Basic Example

```clojure
(ns my-app.button-test
  (:require
   [cljs.test :as t :include-macros true]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [uix.core :refer [$ defui]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(defui button [{:keys [on-click children]}]
  ($ :button {:on-click on-click} children))

(t/deftest button-renders-test
  (tlr/render ($ button {} "Click me"))
  (t/is (some? (tlr/get-by-role "button" {:name "Click me"}))))
```

### User Interactions

```clojure
(t/deftest button-click-test
  (t/async done
    (let [clicked (atom false)
          _ (tlr/render ($ button {:on-click #(reset! clicked true)} "Click"))
          user (tlr/setup)]
      (-> (tlr/click user (tlr/get-by-role "button"))
          (.then (fn []
                   (t/is @clicked)
                   (done)))))))
```

### Async Waiting

```clojure
(t/deftest async-content-test
  (t/async done
    (tlr/render ($ async-component))
    (-> (tlr/wait-for #(tlr/get-by-text "Loaded!"))
        (.then (fn [element]
                 (t/is (some? element))
                 (done))))))
```

## Namespaces

| Namespace | Description |
|-----------|-------------|
| `cljs-tlr.core` | Re-exports common functions for convenience |
| `cljs-tlr.render` | `render`, `render-hook`, `rerender`, `unmount` |
| `cljs-tlr.screen` | Query functions: `get-by-*`, `query-by-*`, `find-by-*` |
| `cljs-tlr.events` | Low-level `fireEvent` wrappers |
| `cljs-tlr.user-event` | High-level user simulation (recommended) |
| `cljs-tlr.async` | `wait-for`, `wait-for-element-to-be-removed`, `act` |
| `cljs-tlr.fixtures` | `cleanup-fixture`, `configure!` |
| `cljs-tlr.uix` | UIx-specific utilities |

## Query Priority

Following [testing-library best practices](https://testing-library.com/docs/queries/about#priority), prefer queries in this order:

1. **`get-by-role`** - Best for accessibility
2. **`get-by-label-text`** - Good for form fields
3. **`get-by-placeholder-text`** - Fallback for inputs
4. **`get-by-text`** - For non-interactive elements
5. **`get-by-display-value`** - For filled form elements
6. **`get-by-test-id`** - Last resort

## Query Variants

| Variant | No Match | 1 Match | 1+ Match | Await? |
|---------|----------|---------|----------|--------|
| `get-by-*` | throw | return | throw | No |
| `query-by-*` | null | return | throw | No |
| `find-by-*` | throw | return | throw | Yes |
| `get-all-by-*` | throw | array | array | No |
| `query-all-by-*` | [] | array | array | No |
| `find-all-by-*` | throw | array | array | Yes |

## Running Tests

```bash
# One-time compilation and run
npm test

# Watch mode for development
npm run test:watch
```

## Development

```bash
cd cljs-tlr
npm install
npm run test:watch
```
