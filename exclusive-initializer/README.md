# Exclusive Initializer

Provides a wrapper for test fixtures that ensures a given fixture is run once across tests running in parallel.

## Usage

The `wrap` macro provides thread-safe initialization functions for use in test fixtures. It creates a lock with the given name and provides functions to coordinate exclusive access to shared resources.

```clojure
(require '[exclusive-initializer.core :as exclusive])

;; Basic fixture example that prints "Good Job" only once
(defn printer-fixture [f]
  (exclusive/wrap [{:keys [lock unlock initialize! initialized?]} ::print-lock]
    (lock)
    (when-not (initialized?)
      (prn "Good Job")
      (initialize!))
    (unlock)
    (f)))

;; Database setup fixture
(defn db-fixture [f]
  (exclusive/wrap [{:keys [lock unlock initialize! deinitialize! initialized?]} ::db-lock]
    (lock)
    (try
      (when-not (initialized?)
        ;; Setup database schema/seed data
        (setup-test-db!)
        (initialize!))
      (f)
      (finally
        (unlock)))))
```

### Available Functions

The `wrap` macro provides these functions through destructuring:

- `lock` - Acquire the exclusive lock
- `unlock` - Release the exclusive lock
- `initialize!` - Mark the resource as initialized
- `deinitialize!` - Mark the resource as uninitialized
- `initialized?` - Check if the resource has been initialized

### Thread Safety

The macro automatically wraps the entire body in a try...finally block to ensure locks are released even if exceptions occur. Each lock is identified by a unique lock name (typically a namespaced keyword) and uses `ReentrantLock` internally.

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
