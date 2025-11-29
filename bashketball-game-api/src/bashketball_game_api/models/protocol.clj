(ns bashketball-game-api.models.protocol
  "Repository protocol for data access abstraction.

  Provides a common interface for CRUD operations across different storage
  backends.")

(defprotocol Repository
  "Protocol for repository operations.

  Implementations provide data access for specific entity types. Supports
  common CRUD operations with flexible querying."

  (find-by [this criteria]
    "Retrieves a single entity matching the criteria.

    The `criteria` map specifies field-value pairs to match, such as
    `{:id uuid}` or `{:google-id \"12345\"}`. Returns the first
    matching entity map if found, nil otherwise.")

  (find-all [this opts]
    "Retrieves all entities matching the given options.

    The `opts` map can include:
    - `:where` - Criteria map for filtering
    - `:limit` - Maximum number of results
    - `:offset` - Number of results to skip
    - `:order-by` - Sorting specification

    Returns a vector of entity maps.")

  (create! [this data]
    "Creates or updates an entity with the provided data.

    Validates the data, assigns an ID if not present, and persists the entity.
    Uses upsert semantics - if a unique constraint conflict occurs, updates
    the existing entity instead of failing.

    Returns the created or updated entity map with its ID.")

  (update! [this id data]
    "Updates an existing entity with the provided data.

    Merges the data with the existing entity and persists the changes.
    Returns the updated entity map if successful, nil if not found.")

  (delete! [this id]
    "Deletes an entity by its unique identifier.

    Returns true if the entity was deleted, false if not found."))
