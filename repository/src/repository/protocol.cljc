(ns repository.protocol
  "Core protocols for storage-agnostic data access.

  Provides a minimal interface that all backends can implement. The protocol
  uses simple key/value maps for basic queries, with complex queries handled
  by named scopes that backends interpret in their native format.

  ## Query Structure

  Queries are maps that may contain:
  - `:where` - Key/value map for equality matching
  - `:scope` - Named scope keyword for complex queries
  - `:scope-params` - Parameters for the scope
  - `:order-by` - Vector of `[field direction]` pairs
  - `:limit` - Maximum results
  - `:cursor` - Cursor for pagination

  ## Example Usage

      (require '[repository.protocol :as repo])

      ;; Basic lookup
      (repo/find-one user-repo {:where {:email \"test@example.com\"}})

      ;; With scope
      (repo/find-many game-repo {:scope :by-player
                                  :scope-params {:player-id user-id}
                                  :limit 10})

      ;; Save entity
      (repo/save! user-repo {:name \"Alice\" :email \"alice@example.com\"})

      ;; Delete by query
      (repo/delete! session-repo {:where {:user-id user-id}})")

(defprotocol Repository
  "Base protocol for data repositories.

  All repositories must implement these operations. The semantics are
  intentionally loose to accommodate different storage backends (databases,
  files, in-memory, etc.)."

  (find-one [this query]
    "Finds a single entity matching the query.

    The query map may contain:
    - `:where` - Key/value map for equality matching
    - `:scope` - Named scope for complex queries
    - `:scope-params` - Parameters for the scope

    Returns the entity map if found, nil otherwise.")

  (find-many [this query]
    "Finds all entities matching the query.

    The query map may contain:
    - `:where` - Key/value map for equality matching
    - `:scope` - Named scope for complex queries
    - `:scope-params` - Parameters for the scope
    - `:order-by` - Vector of `[field direction]` pairs
    - `:limit` - Maximum results
    - `:cursor` - Cursor for pagination

    Returns a map with:
    - `:data` - Vector of entity maps
    - `:page-info` - Pagination info with `:has-next-page` and `:end-cursor`")

  (save! [this entity]
    "Saves an entity, creating or updating as appropriate.

    The decision to insert vs update depends on:
    - Presence of an ID field (backend-specific)
    - Backend's conflict resolution strategy

    Returns the saved entity with any generated fields (ID, timestamps, etc.).")

  (delete! [this query]
    "Deletes entities matching the query.

    Returns the count of deleted entities."))

(defprotocol Countable
  "Optional protocol for repositories that support counting."

  (count-matching [this query]
    "Returns count of entities matching the query.

    Supports the same query options as [[find-many]]."))

(defprotocol Transactional
  "Optional protocol for repositories supporting transactions."

  (with-transaction [this f]
    "Executes function `f` within a transaction.

    The function receives no arguments. If `f` throws an exception,
    the transaction is rolled back. Returns the result of `f`."))
