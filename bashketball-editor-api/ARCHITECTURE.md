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

## Git-Backed Storage Architecture

### Design Decision: JGit with Single Writer Pattern

Cards and card sets are stored in a Git repository and accessed via JGit rather than the GitHub API.

**Key Benefits**:
- **Performance**: Local file access (1-10ms) vs HTTP API calls (100-500ms)
- **No rate limits**: Unlimited operations vs 5,000 requests/hour
- **Atomic commits**: Multiple card changes in single commit
- **Full Git features**: Branches, history, diffs, blame
- **Platform independence**: Works with GitHub, GitLab, self-hosted Git
- **Bulk operations**: Import/export hundreds of cards efficiently

### Single Writer Pattern

To avoid coordination complexity in multi-instance deployments:

```
┌─────────────────────────────────────────────┐
│ Writer Instance (GIT_WRITER=true)          │
│                                             │
│  ┌──────────┐     ┌─────────────────────┐  │
│  │ GraphQL  │────▶│ Card Repository     │  │
│  │ Mutations│     │ (Read/Write)        │  │
│  └──────────┘     └──────────┬──────────┘  │
│                              │              │
│  ┌──────────┐     ┌──────────▼──────────┐  │
│  │ UI Sync  │────▶│ Local Git Clone     │  │
│  │ (Manual) │     │ (Working Tree)      │  │
│  └──────────┘     └──────────┬──────────┘  │
│                              │              │
└──────────────────────────────┼──────────────┘
                               │ manual push/pull
       ┌───────────────────────┼───────────────────────┐
       │                       │                       │
       ▼                       ▼                       ▼
┌─────────────┐         ┌─────────────┐        ┌─────────────┐
│ Reader      │         │ Reader      │        │ Remote Git  │
│ Instance    │         │ Instance    │        │ (GitHub)    │
│             │         │             │        └─────────────┘
│ Read-Only   │         │ Read-Only   │
│ Queries     │         │ Queries     │
└─────────────┘         └─────────────┘
```

**Implementation**:
- One instance designated as writer via environment variable `GIT_WRITER=true`
- Writer instance handles all mutations
- Additional instances can be read-only (query-only)
- Sync with remote is explicit via UI (pull/push buttons)
- All instances clone repository on startup for local reads

**File**: `src/bashketball_editor_api/git/repo.clj`

```clojure
(ns bashketball-editor-api.git.repo
  (:require [clj-jgit.porcelain :as git]))

(defn clone-or-open [config]
  (let [repo-dir (io/file (:repo-path config))]
    (if (.exists repo-dir)
      (git/load-repo (:repo-path config))
      (git/git-clone (:remote-url config)
                     :dir (:repo-path config)
                     :branch (:branch config)))))

(defn commit [repo message author-name author-email]
  (when (writer-instance?)
    (git/git-add repo ".")
    (git/git-commit repo message
                    :name author-name
                    :email author-email)))

(defn push [repo github-token]
  (when (writer-instance?)
    (git/git-push repo
                  :credentials {:username "token"
                                :password github-token})))
```

**Configuration** (`resources/config.edn`):

```clojure
{:git
 {:repo-path "/data/bashketball-cards"
  :remote-url #env GIT_REMOTE_URL
  :branch "main"
  :writer? #profile {:dev true
                     :prod #or [#env GIT_WRITER "false"]}}}
```

**Deployment**:
```bash
# Writer instance
GIT_WRITER=true GIT_REMOTE_URL=git@github.com:org/cards.git ./start.sh

# Read-only instances (optional, for scaling queries)
GIT_WRITER=false GIT_REMOTE_URL=git@github.com:org/cards.git ./start.sh
```

### Git Repository Structure

```
bashketball-cards/
├── cards/
│   ├── <set-uuid-1>/
│   │   ├── <card-uuid-1>.edn
│   │   ├── <card-uuid-2>.edn
│   │   └── <card-uuid-3>.edn
│   └── <set-uuid-2>/
│       └── <card-uuid-4>.edn
└── sets/
    ├── <set-uuid-1>/
    │   └── metadata.edn
    └── <set-uuid-2>/
        └── metadata.edn
```

**Card File Example** (`cards/<set-uuid>/<card-uuid>.edn`):

```clojure
{:id #uuid "123e4567-e89b-12d3-a456-426614174000"
 :set-id #uuid "987fcdeb-51a2-43d7-8c9e-123456789abc"
 :name "Lightning Strike"
 :description "Deal 3 damage to target player"
 :attributes {:type "instant"
              :cost {:mana 2}
              :rarity "common"}
 :created-at #inst "2025-01-15T10:30:00.000Z"
 :updated-at #inst "2025-01-20T14:45:00.000Z"}
```

### Card Repository Implementation

**File**: `src/bashketball_editor_api/git/cards.clj`

```clojure
(defrecord CardRepository [git-repo lock]
  proto/Repository
  (find-by [_this criteria]
    ;; Read from local working tree (fast)
    (when-let [id (:id criteria)]
      (when-let [set-id (:set-id criteria)]
        (let [path (card-path set-id id)
              content (git-repo/read-file git-repo path)]
          (when content
            (edn/read-string content))))))

  (create! [_this data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      ;; Extract user context from data (passed by GraphQL resolver)
      (let [user-ctx (:_user data)
            _ (when-not user-ctx
                (throw (ex-info "User context required for Git operations" {})))
            ;; Remove user context before storing
            card (-> data
                     (dissoc :_user)
                     (assoc :id (or (:id data) (random-uuid))
                            :created-at (or (:created-at data) (now))
                            :updated-at (now)))
            path (card-path (:set-id card) (:id card))]
        ;; Write to working tree
        (git-repo/write-file git-repo path (pr-str card))
        ;; Commit with user's credentials
        (git-repo/commit git-repo
                        (str "Create card: " (:name card) " [" (:id card) "]")
                        (:name user-ctx)
                        (:email user-ctx))
        ;; Push with user's GitHub token
        (git-repo/push git-repo (:github-token user-ctx))
        card)))
  ;; ... other methods
)
```

**User Context Pattern**: GraphQL mutation resolvers add a `:_user` key to the data map containing the authenticated user's name, email, and GitHub token. The repository extracts this for Git operations (commit author and push credentials) and removes it before storing the card data. This ensures each Git commit is properly attributed to the user who made the change.

### Manual Sync Operations

The writer instance exposes GraphQL mutations for manual sync:

**File**: `src/bashketball_editor_api/git/sync.clj`

```clojure
(defn pull-from-remote
  "Fetches and merges changes from remote repository.

  Uses the authenticated user's GitHub token for credentials.
  Returns status with any conflicts detected."
  [git-repo github-token]
  (try
    (git-repo/fetch git-repo github-token)
    (git-repo/pull git-repo github-token)
    {:status :success
     :message "Successfully pulled changes from remote"}
    (catch org.eclipse.jgit.api.errors.CheckoutConflictException e
      {:status :conflict
       :message "Merge conflicts detected"
       :error (ex-message e)})
    (catch Exception e
      {:status :error
       :message "Failed to pull from remote"
       :error (ex-message e)})))

(defn push-to-remote
  "Pushes local commits to remote repository.

  Uses the authenticated user's GitHub token for credentials."
  [git-repo github-token]
  (try
    (git-repo/push git-repo github-token)
    {:status :success
     :message "Successfully pushed changes to remote"}
    (catch Exception e
      {:status :error
       :message "Failed to push to remote"
       :error (ex-message e)})))

(defn get-sync-status
  "Returns current sync status (ahead/behind remote)."
  [git-repo]
  (let [status (git-repo/status git-repo)]
    {:ahead (:commits-ahead status)
     :behind (:commits-behind status)
     :uncommitted-changes (:uncommitted-changes status)
     :clean? (and (zero? (:commits-ahead status))
                  (zero? (:commits-behind status))
                  (empty? (:uncommitted-changes status)))}))
```

### Trade-offs

#### Git-Backed Repositories

**Challenge**: File-based storage requires different query patterns than SQL.

**Solution**:
- Accept constraints in protocol implementation
- Use `:where` map for filtering
- Require compound keys where needed (e.g., `{:id :set-id}` for cards)
- List directory contents for `find-all` operations
- All instances have local clone for fast reads

**Challenge**: Multi-instance coordination for writes.

**Solution**: Single writer pattern
- One designated writer instance handles all mutations
- Other instances are read-only (can scale queries independently)
- Manual sync via UI (explicit pull/push operations)
- Simple deployment model, no distributed locking needed
- Users control when changes are synced with remote

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
