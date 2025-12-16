(ns bashketball-game-api.models.transaction
  "Transaction abstraction for data integrity.

  Provides a protocol-based abstraction over database transactions, allowing
  services to ensure atomicity without depending on database-specific code.
  Use [[in-transaction]] for convenient syntax when calling [[run-in-tx]]."
  (:require
   [db.core :as db]))

(defprotocol Transactable
  "Protocol for executing operations within a transactional context.

  Implementations ensure that all operations performed within the transaction
  either succeed together or fail together, maintaining data integrity."

  (run-in-tx [this f]
    "Executes the zero-argument function `f` within a transaction.

    All database operations performed by `f` will be atomic - they either
    all succeed or all roll back on failure. Returns the result of `f`.
    Throws if `f` throws, rolling back the transaction."))

(defmacro in-transaction
  "Executes body within a transaction managed by the given transactable.

  Wraps the body forms in an anonymous function and passes it to [[run-in-tx]].
  All database operations within body will be atomic.

  Example:

      (in-transaction tx-manager
        (proto/update! game-repo game-id {:game-state new-state})
        (proto/create! event-repo event-data)
        result)"
  [transactable & body]
  `(run-in-tx ~transactable (fn [] ~@body)))

(defrecord DbTransactionManager []
  Transactable
  (run-in-tx [_ f]
    (db/with-transaction [_]
      (f))))

(defn create-transaction-manager
  "Creates a new database transaction manager."
  []
  (->DbTransactionManager))
