# Exclusive Initializer

Allows a resource to be initialized once in situations where we may attempt to start them in parallel.

## Usage

```clojure
(require '[exclusive-initializer.core :as exclusive])

(exclusive/hello "World")
;; => "Hello, World!"
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
