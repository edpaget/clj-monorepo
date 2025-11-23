# bashketball-editor-api Architecture

## Repository Pattern

The application uses a flexible repository pattern for data access abstraction, allowing the same interface to work across different storage backends (PostgreSQL, GitHub).

### Core Protocol

```clojure
(defprotocol Repository
  (find-by [this criteria])
  (find-all [this opts])
  (create! [this data])
  (update! [this id data])
  (delete! [this id]))
```

### Key Design Principles

#### 1. Flexible Querying with `find-by`

Instead of method-specific lookups like `find-by-id` or `find-by-github-login`, we use a single `find-by` method that accepts a criteria map:

```clojure
;; Find by ID
(repo/find-by user-repo {:id uuid})

;; Find by GitHub login
(repo/find-by user-repo {:github-login "octocat"})

;; Find by multiple criteria
(repo/find-by card-repo {:id card-id :set-id set-id})
```

**Benefits**:
- No need for domain-specific repository methods
- Consistent API across all entity types
- Easy to add new query patterns without protocol changes
- Natural fit for building dynamic queries

#### 2. Upsert Semantics in `create!`

The `create!` method uses upsert semantics - it creates a new entity if it doesn't exist, or updates it if a unique constraint conflict occurs:

```clojure
;; PostgreSQL implementation
(create! [_this data]
  (db/execute-one!
   {:insert-into :users
    :values [user-data]
    :on-conflict :github-login
    :do-update-set (assoc user-data :updated-at now)
    :returning [:*]}))
```

**Benefits**:
- Idempotent operations - safe to retry
- No need for separate upsert methods
- Aligns with real-world use cases (e.g., OAuth user creation)
- Simpler API surface

#### 3. Options-Based `find-all`

The `find-all` method accepts an options map for filtering, pagination, and sorting:

```clojure
;; Simple listing
(repo/find-all user-repo {})

;; With filtering
(repo/find-all card-repo {:where {:set-id set-id}})

;; With pagination
(repo/find-all user-repo {:limit 10 :offset 20})

;; With custom ordering
(repo/find-all user-repo {:order-by [[:created-at :desc]]})
```

**Benefits**:
- Flexible without being overly complex
- Supports common patterns (pagination, filtering, sorting)
- Easy to extend with new options
- Consistent across implementations

### Storage Backend Implementations

#### PostgreSQL (User Repository)

The user repository demonstrates database-backed storage:

```clojure
(defrecord UserRepository []
  proto/Repository
  (find-by [_this criteria]
    (when-let [where-clause (build-where-clause criteria)]
      (db/execute-one!
       {:select [:*]
        :from [:users]
        :where where-clause})))

  (create! [_this data]
    ;; Upsert on github-login unique constraint
    (db/execute-one!
     {:insert-into :users
      :values [user-data]
      :on-conflict :github-login
      :do-update-set (assoc user-data :updated-at now)
      :returning [:*]}))
  ;; ... other methods
)
```

**Features**:
- Automatic timestamp management
- Upsert on unique constraints
- Dynamic where clause building
- Flexible querying by any field

#### GitHub (Card/Set Repositories)

Card and set repositories use GitHub as a file-based storage backend:

```clojure
(defrecord CardRepository [github-client]
  proto/Repository
  (find-by [this criteria]
    ;; GitHub requires file path, which depends on set-id
    (when-let [id (:id criteria)]
      (when-let [set-id (:set-id criteria)]
        (client/get-file github-client (card-path set-id id)))))

  (create! [this data]
    ;; GitHub client handles upsert via create-or-update-file
    (client/create-or-update-file
     github-client
     (card-path set-id id)
     card-data
     commit-message))
  ;; ... other methods
)
```

**Constraints**:
- File paths depend on entity relationships (cards need set-id)
- No native querying - must list directory and filter
- Upsert is natural (file create-or-update)
- Eventual consistency via Git commits

## Service Layer

The service layer sits between repositories and GraphQL resolvers, providing:

### 1. Data Transformation

Transforms external data formats (GitHub API) to internal data models:

```clojure
(defn github-data->user-data
  "Transforms GitHub API user data to user repository data format."
  [github-data]
  {:github-login (:login github-data)
   :email (:email github-data)
   :avatar-url (:avatar_url github-data)
   :name (:name github-data)})
```

**Principle**: Keep repositories focused on persistence, not data mapping.

### 2. Business Logic

Implements validation and business rules:

```clojure
(defn validate-card [card]
  (cond
    (str/blank? (:name card))
    {:valid? false :errors [{:field :name :message "Card name is required"}]}

    (> (count (:name card)) 100)
    {:valid? false :errors [{:field :name :message "Card name too long"}]}

    :else
    {:valid? true}))
```

### 3. Orchestration

Coordinates multiple repository operations:

```clojure
(defn delete-set [service id]
  ;; Find the set
  (when-let [card-set (repo/find-by (:set-repo service) {:id id})]
    ;; Find all cards in the set
    (let [cards (repo/find-all (:card-repo service) {:where {:set-id id}})]
      ;; Delete all cards first
      (doseq [card cards]
        (repo/delete! (:card-repo service) (:id card)))
      ;; Then delete the set
      (repo/delete! (:set-repo service) id))))
```

## GraphQL Layer

GraphQL resolvers use the repository protocol for data access:

```clojure
(gql/defresolver :Query :user
  "Fetches a user by ID or GitHub login."
  [:=> [:cat :any [:map [:id {:optional true} :string]
                         [:github-login {:optional true} :string]] :any]
       [:maybe User]]
  [ctx args _value]
  (let [criteria (cond
                   (:id args) {:id (parse-uuid (:id args))}
                   (:github-login args) {:github-login (:github-login args)})]
    (when criteria
      (repo/find-by (:user-repo ctx) criteria))))
```

**Pattern**: Convert GraphQL arguments to repository criteria/options.

## Authentication Flow

The authentication service demonstrates clean separation of concerns:

1. **GitHub OAuth Validation** - Handled by `oidc-github` package
2. **User Upsert** - Service transforms GitHub data and calls `repo/create!`
3. **Session Creation** - Handled by `authn` package with database-backed storage

```clojure
(defrecord GitHubClaimsProvider [github-claims-provider user-repo]
  proto/ClaimsProvider
  (get-claims [_this user-id scope]
    ;; 1. Get GitHub claims
    (let [github-claims (proto/get-claims github-claims-provider user-id scope)
          ;; 2. Transform to user data
          user-data (github-data->user-data github-claims)
          ;; 3. Upsert user (creates or updates)
          user (repo/create! user-repo user-data)]
      ;; 4. Return enriched claims
      (assoc github-claims
             :user-id (str (:id user))
             :sub (str (:id user))))))
```

## Component Architecture (Integrant)

Components are wired together with clear dependencies:

```
config
  ├── db-pool
  │     └── migrate
  │     └── user-repo
  │           └── auth-service
  │                 └── authenticator
  ├── github-client
  │     ├── card-repo
  │     └── set-repo
  └── graphql-schema
        └── handler
              └── server
```

**Key Principles**:
- Each component has a single responsibility
- Dependencies are explicit via Integrant refs
- Stateful resources (DB, HTTP server) have proper lifecycle
- Easy to test individual components in isolation

## Benefits of This Architecture

### 1. Testability
- Mock repositories by implementing the protocol
- Test services without database or GitHub
- Test resolvers with fake repositories

### 2. Flexibility
- Swap storage backends without changing services
- Add new query patterns without protocol changes
- Support multiple storage strategies simultaneously

### 3. Consistency
- Same API for all entity types
- Predictable patterns across the codebase
- Easy to understand and navigate

### 4. Maintainability
- Clear separation of concerns
- Easy to extend without modification
- Self-documenting through protocols
- Business logic centralized in services

## Trade-offs

### GitHub-Backed Repositories

**Challenge**: File-based storage requires different query patterns than SQL.

**Solution**:
- Accept constraints in protocol implementation
- Use `:where` map for filtering
- Require compound keys where needed (e.g., `{:id :set-id}` for cards)
- Consider metadata index files for complex queries

### Upsert Semantics

**Challenge**: Not all create operations should upsert.

**Solution**:
- Make upsert the default (most common case)
- Add explicit `insert!` method if needed
- Use unique constraints to control upsert behavior
- Document upsert behavior clearly

### Generic `find-by`

**Challenge**: Less type-safe than specific methods.

**Solution**:
- Use Malli schemas for criteria validation
- Document expected criteria in docstrings
- Fail fast with clear error messages
- Provide helper functions for common patterns

## Future Enhancements

1. **Caching Layer**: Add caching middleware for repositories
2. **Batch Operations**: Add `find-by-ids` for efficient bulk lookups
3. **Transactions**: Wrap multi-repository operations in transactions
4. **Query DSL**: More sophisticated query building for complex filters
5. **Async/Reactive**: Support asynchronous repository operations
6. **Audit Logging**: Track all mutations for compliance
