# Db

DB is a catchall of utils for using next.jdbc ergonomically in projects driven by integrant.

It provides a set of functions and macros for querying a database using next.jdbc and using implicitly managed connection pools via dynamic vars. It also provides utility functions for migrating a database using ragtime. 

Finally, it provides mapper functions for working with postgresql types converting enums to and from namespaced clojure keywords and json(b) to and from clojure datastructures. 

## Usage

```clojure
(require '[db.core :as db])

(db/hello "World")
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
