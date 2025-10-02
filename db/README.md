# Db

DB is a catchall of utils for using next.jdbc ergonomically in projects driven by integrant.

It provides a set of functions and macros for querying a database using next.jdbc and using implicitly managed connection pools via dynamic vars. It also provides utility functions for migrating a database using ragtime.

Finally, it provides mapper functions for working with postgresql types converting enums to and from namespaced clojure keywords and json(b) to and from clojure datastructures.

## Usage

### Connection Management

Use Integrant to manage the connection pool lifecycle:

```clojure
(require '[db.core :as db]
         '[integrant.core :as ig])

;; Integrant configuration
(def config
  {::db/pool {:config {:database-url "jdbc:postgresql://localhost/mydb"
                       :c3p0-opts {}}}})

;; Start the system
(def system (ig/init config))

;; Bind the datasource for query execution
(binding [db/*datasource* (::db/pool system)]
  (db/execute-one! {:select [:*] :from [:users] :where [:= :id 1]}))

;; Shutdown
(ig/halt! system)
```

### Query Functions

Execute queries using HoneySQL maps or SQL vectors:

```clojure
;; Execute a query that returns one row
(db/execute-one! {:select [:*] :from [:users] :where [:= :id 1]})
;; => {:id 1 :name "Alice" :email "alice@example.com"}

;; Execute a query that returns multiple rows
(db/execute! {:select [:*] :from [:users]})
;; => [{:id 1 :name "Alice"} {:id 2 :name "Bob"}]

;; Use a reducible query for large result sets
(into []
      (comp (filter #(> (:age %) 18))
            (map :name))
      (db/plan {:select [:*] :from [:users]}))

;; Use raw SQL vectors
(db/execute! ["SELECT * FROM users WHERE id = ?" 1])
```

### Transactions

Use `with-transaction` to execute queries within a transaction:

```clojure
(db/with-transaction [tx]
  (db/execute-one! {:insert-into :users
                    :values [{:name "Charlie" :email "charlie@example.com"}]})
  (db/execute-one! {:update :accounts
                    :set {:balance 100}
                    :where [:= :user-id 1]}))
```

### Explicit Connections

Pass a datasource or connection explicitly:

```clojure
(let [ds (db.connection-pool/create-pool "jdbc:postgresql://localhost/mydb")]
  (db/execute-one! ds {:select [:*] :from [:users] :where [:= :id 1]}))
```

### Database Migrations

Use Ragtime for database migrations:

```clojure
(require '[db.migrate :as migrate])

;; Migrations should be in resources/migrations/
;; Apply all pending migrations
(binding [db/*datasource* datasource]
  (migrate/migrate))

;; Rollback the last migration
(binding [db/*datasource* datasource]
  (migrate/rollback))
```

### PostgreSQL Type Extensions

The library automatically handles PostgreSQL types:

```clojure
;; JSON/JSONB - Clojure maps/vectors convert automatically
(db/execute-one! {:insert-into :documents
                  :values [{:data {:foo "bar" :baz [1 2 3]}}]})
;; The :data map is automatically converted to JSONB

;; Enums - Database enums convert to namespaced keywords
(db/execute-one! {:select [:status] :from [:orders] :where [:= :id 1]})
;; => {:status :order-status/pending}
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
