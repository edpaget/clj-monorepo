# Exclusive Initializer

Provides macros for test fixtures that ensure initialization code runs only once across tests running in parallel.

## Usage

The `initialize!` macro ensures that initialization code runs only once per lock name, even when tests run in parallel. The `deinitialize!` macro resets the lock state, allowing re-initialization.

```clojure
(require '[exclusive-initializer.core :as exclusive])

;; Basic fixture example that prints "Good Job" only once
(defn printer-fixture [f]
  (exclusive/initialize! ::print-lock
    (prn "Good Job"))
  (f))

;; Database setup fixture with cleanup
(defn db-fixture [f]
  (exclusive/initialize! ::db-lock
    ;; Setup database schema/seed data
    (setup-test-db!))
  (f)
  (exclusive/deinitialize! ::db-lock
    (teardown-test-db!)))
```

### Available Macros

- `initialize!` - Run initialization code only once per lock name
- `deinitialize!` - Reset the lock state and optionally run cleanup code

### Thread Safety

Both macros use `locking` internally to ensure thread-safe access to shared lock state. Each lock is identified by a unique lock name (typically a namespaced keyword).

### Utility Functions

```clojure
;; Reset all locks (useful for test cleanup)
(exclusive/reset-locks!)
```

## Development

```bash
# Run tests
clojure -X:test

# Start REPL
clojure -M:repl

# Lint code
clojure -M:lint
```
