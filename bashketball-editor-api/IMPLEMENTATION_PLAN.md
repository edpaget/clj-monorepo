# bashketball-editor-api Implementation Plan

This document outlines the complete implementation plan for the bashketball-editor-api GraphQL server.

## Phase 1: Implementation Skeleton ✅ COMPLETE

**Status**: Complete

The skeleton implementation provides:
- Project structure and configuration with Aero
- Database migrations for users and sessions
- Integrant system with component lifecycle
- Repository protocol and model skeletons
- Basic GraphQL schema with `me` query
- HTTP server with route structure
- Testing infrastructure with fixtures
- Integration with existing monorepo packages

## Phase 2: Authentication & User Management ✅ COMPLETE

**Status**: Complete

Implemented:
- GitHub OAuth flow via `oidc-github` and `authn` libraries
- User creation/upsert from GitHub profile data
- Session management with cookie-based storage
- GitHub access token storage for Git operations
- Session refresh middleware
- User GraphQL queries (`me`, `user`, `users`)
- Logout handler
- Comprehensive auth tests

**Goal**: Implement complete GitHub OAuth flow and user management

### 2.1 Complete GitHub OAuth Flow

**File**: `src/bashketball_editor_api/handler.clj`

Update `login-handler` to:
1. Exchange GitHub authorization code for access token
2. Fetch GitHub user profile
3. Upsert user in database
4. Create session
5. Set session cookie
6. Redirect to frontend URL

**Implementation**:

```clojure
(defn login-handler
  [authenticator user-repo config]
  (fn [request]
    (let [code (get-in request [:params "code"])
          credentials {:code code}]
      (if-let [session-id (authn/authenticate authenticator credentials)]
        (let [session (authn/get-session authenticator session-id)
              user-id (:user-id session)
              user (user/find-by-id user-repo (parse-uuid user-id))]
          {:status 302
           :headers {"Location" (get-in config [:github :oauth :success-redirect-uri])}
           :session {:authn/session-id session-id}})
        {:status 401
         :headers {"Content-Type" "application/json"}
         :body {:error "Authentication failed"}}))))
```

### 2.2 GitHub User Profile Integration

**File**: `src/bashketball_editor_api/services/auth.clj`

The `GitHubClaimsProvider` already handles user upsert when fetching claims:
1. Receives GitHub user profile data
2. Transforms it using `github-data->user-data`
3. Upserts user via `repo-proto/create!` (uses upsert semantics)
4. Returns claims with user ID

**Key Design Pattern**: The repository's `create!` method uses upsert semantics,
so calling it automatically handles both user creation and updates based on the
`github-login` unique constraint.

### 2.3 Session Refresh Middleware

**File**: `src/bashketball_editor_api/handler.clj`

Add session refresh middleware:

```clojure
(defn wrap-session-refresh
  [handler authenticator]
  (fn [request]
    (when-let [session-id (get-in request [:session :authn/session-id])]
      (authn/refresh-session authenticator session-id))
    (handler request)))
```

Update `create-handler` to include refresh middleware.

### 2.4 User GraphQL Queries

**File**: `src/bashketball_editor_api/graphql/resolvers/query.clj`

Add additional user queries using the flexible `find-by` protocol method:

```clojure
(gql/defresolver :Query :user
  "Fetches a user by ID or GitHub login."
  [:=> [:cat :any [:map [:id {:optional true} :string]
                         [:github-login {:optional true} :string]] :any]
       [:maybe User]]
  [ctx args _value]
  (let [criteria (cond
                   (:id args) {:id (parse-uuid (:id args))}
                   (:github-login args) {:github-login (:github-login args)}
                   :else nil)]
    (when criteria
      (when-let [user (repo/find-by (:user-repo ctx) criteria)]
        (transform-user user)))))

(gql/defresolver :Query :users
  "Lists all users with optional filtering."
  [:=> [:cat :any [:map {:optional true} [:limit :int]] :any] [:vector User]]
  [ctx args _value]
  (let [users (repo/find-all (:user-repo ctx) args)]
    (mapv transform-user users)))
```

**Note**: The flexible `find-by` method allows querying by any field (`:id`, `:github-login`, etc.)
without creating method-specific repository methods.

### 2.5 Testing

**Files**:
- `test/bashketball_editor_api/auth_test.clj`
- `test/bashketball_editor_api/models/user_test.clj`

Tests to implement:
- GitHub OAuth callback flow
- User creation from GitHub data
- User upsert (update existing user)
- Session creation and retrieval
- Session expiration
- Authentication middleware

### 2.6 Configuration Updates

**File**: `resources/config.edn`

Add:
```clojure
:github
{:oauth
 {:success-redirect-uri #or [#env GITHUB_SUCCESS_REDIRECT_URI "http://localhost:3001/"]}}
```

---

## Phase 3: Git Repository Integration (JGit) ✅ COMPLETE

**Status**: Complete

**Goal**: Implement Git-backed storage for cards and sets using JGit with single writer pattern

### Implemented

- `clj-jgit/clj-jgit {:mvn/version "1.0.2"}` dependency added
- `src/bashketball_editor_api/git/repo.clj` - Core Git operations (clone-or-open, read/write/delete-file, commit, fetch, pull, push, status, ahead-behind)
- `src/bashketball_editor_api/git/sync.clj` - Manual sync operations (pull-from-remote, push-to-remote, get-sync-status)
- `src/bashketball_editor_api/git/cards.clj` - Card repository implementing Repository protocol
- `src/bashketball_editor_api/git/sets.clj` - Set repository implementing Repository protocol
- Updated `system.clj` with `::git-repo`, `::card-repo`, `::set-repo` Integrant components
- Updated `handler.clj` to include repositories in GraphQL context
- GraphQL mutations: `pullFromRemote`, `pushToRemote`
- GraphQL query: `syncStatus`
- CORS middleware for cross-origin frontend requests

### Tests

- `test/bashketball_editor_api/git/repo_test.clj` - Git repository operations
- `test/bashketball_editor_api/git/cards_test.clj` - Card repository CRUD
- `test/bashketball_editor_api/git/sets_test.clj` - Set repository CRUD
- `test/bashketball_editor_api/git/sync_test.clj` - Sync operations

All 19 Git tests pass (49 assertions).

### Key Design Decisions

- **Single writer pattern**: One instance designated as writer via `GIT_WRITER=true`
- **User attribution**: Git commits use authenticated user's name/email
- **Per-user credentials**: Push/pull use authenticated user's GitHub OAuth token
- **Thread-safe writes**: Uses locking for file operations
- **EDN file format**: Cards at `cards/{set-id}/{card-id}.edn`, sets at `sets/{set-id}/metadata.edn`
- **Manual sync**: UI-initiated pull/push rather than automatic background sync

See `JGIT_ARCHITECTURE.md` for detailed design documentation.

---

## Phase 4: Card & Set GraphQL API

**Goal**: Implement complete GraphQL schema for Bashketball game cards and sets

### 4.1 Card Type System

The game has multiple card types, each with specific attributes. The schema uses a discriminated union
pattern based on `card-type`.

**File**: `src/bashketball_editor_api/graphql/schema.clj`

#### Enums

```clojure
(def CardType
  "Discriminator for card types."
  [:enum {:graphql/type "CardType"}
   :card-type/PLAYER_CARD
   :card-type/ABILITY_CARD
   :card-type/SPLIT_PLAY_CARD
   :card-type/PLAY_CARD
   :card-type/COACHING_CARD
   :card-type/STANDARD_ACTION_CARD
   :card-type/TEAM_ASSET_CARD])

(def PlayerSize
  [:enum {:graphql/type "PlayerSize"}
   :size/SM
   :size/MD
   :size/LG])

#### Base Card Schema

All cards share these common fields:

```clojure
(def Card
  "Base card schema - all card types extend this."
  [:map {:graphql/interface "Card"}
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]
   [:card-type CardType]
   [:created-at inst?]
   [:updated-at inst?]])
```

**Note**: Cards use `:name` as their primary key within a set. Version history is tracked
by Git - use `git log` or the Git history API to see previous versions of a card.

#### Card Type Schemas

```clojure
(def PlayerCard
  "Player cards represent basketball players with stats."
  [:merge Card
   [:map {:graphql/type "PlayerCard"}
    [:card-type [:= :card-type/PLAYER_CARD]]
    [:deck-size {:default 5} :int]
    [:sht {:default 1} :int]           ;; Shot stat
    [:pss {:default 1} :int]           ;; Pass stat
    [:def {:default 1} :int]           ;; Defense stat
    [:speed {:default 1} :int]
    [:size {:default :size/SM} PlayerSize]
    [:abilities {:default []} [:vector :string]]]])

(def AbilityCard
  "Ability cards provide special powers."
  [:merge Card
   [:map {:graphql/type "AbilityCard"}
    [:card-type [:= :card-type/ABILITY_CARD]]
    [:abilities {:default []} [:vector :string]]]])

(def CardWithFate
  "Cards that have a fate value."
  [:merge Card
   [:map
    [:fate {:default 0} :int]]])

(def SplitPlayCard
  "Split play cards have both offense and defense effects."
  [:merge CardWithFate
   [:map {:graphql/type "SplitPlayCard"}
    [:card-type [:= :card-type/SPLIT_PLAY_CARD]]
    [:offense {:default ""} :string]
    [:defense {:default ""} :string]]])

(def PlayCard
  "Play cards describe a single play action."
  [:merge CardWithFate
   [:map {:graphql/type "PlayCard"}
    [:card-type [:= :card-type/PLAY_CARD]]
    [:play {:default ""} :string]]])

(def CoachingCard
  "Coaching cards provide team-wide effects."
  [:merge CardWithFate
   [:map {:graphql/type "CoachingCard"}
    [:card-type [:= :card-type/COACHING_CARD]]
    [:coaching {:default ""} :string]]])

(def StandardActionCard
  "Standard action cards available to all players."
  [:merge CardWithFate
   [:map {:graphql/type "StandardActionCard"}
    [:card-type [:= :card-type/STANDARD_ACTION_CARD]]
    [:offense {:default ""} :string]
    [:defense {:default ""} :string]]])

(def TeamAssetCard
  "Team asset cards represent persistent team resources."
  [:merge CardWithFate
   [:map {:graphql/type "TeamAssetCard"}
    [:card-type [:= :card-type/TEAM_ASSET_CARD]]
    [:asset-power :string]]])
```

#### Multi-Schema for Card Dispatch

```clojure
(def GameCard
  "Union type for all card types, dispatched by :card-type."
  [:multi {:dispatch :card-type
           :graphql/type "GameCard"}
   [:card-type/PLAYER_CARD PlayerCard]
   [:card-type/ABILITY_CARD AbilityCard]
   [:card-type/SPLIT_PLAY_CARD SplitPlayCard]
   [:card-type/PLAY_CARD PlayCard]
   [:card-type/COACHING_CARD CoachingCard]
   [:card-type/STANDARD_ACTION_CARD StandardActionCard]
   [:card-type/TEAM_ASSET_CARD TeamAssetCard]])
```

#### Game Assets

```clojure
(def GameAsset
  "Image/media assets for cards."
  [:map {:graphql/type "GameAsset"}
   [:id :uuid]
   [:mime-type :string]
   [:img-url :string]
   [:status GameAssetStatus]
   [:error-message [:maybe :string]]
   [:created-at inst?]
   [:updated-at inst?]])
```

#### Card Sets

```clojure
(def CardSet
  "A collection of related cards (e.g., expansion pack)."
  [:map {:graphql/type "CardSet"}
   [:id :uuid]
   [:name :string]
   [:description [:maybe :string]]
   [:created-at inst?]
   [:updated-at inst?]])
```

### 4.2 Input Types for Mutations

```clojure
;; Base input shared across card types
(def CardInputBase
  [:map
   [:name :string]
   [:image-prompt {:optional true} [:maybe :string]]])

(def PlayerCardInput
  [:merge CardInputBase
   [:map
    [:deck-size {:optional true} :int]
    [:sht {:optional true} :int]
    [:pss {:optional true} :int]
    [:def {:optional true} :int]
    [:speed {:optional true} :int]
    [:size {:optional true} PlayerSize]
    [:abilities {:optional true} [:vector :string]]]])

(def AbilityCardInput
  [:merge CardInputBase
   [:map
    [:abilities {:optional true} [:vector :string]]]])

(def FateCardInput
  [:merge CardInputBase
   [:map
    [:fate {:optional true} :int]]])

(def SplitPlayCardInput
  [:merge FateCardInput
   [:map
    [:offense {:optional true} :string]
    [:defense {:optional true} :string]]])

(def PlayCardInput
  [:merge FateCardInput
   [:map
    [:play {:optional true} :string]]])

(def CoachingCardInput
  [:merge FateCardInput
   [:map
    [:coaching {:optional true} :string]]])

(def StandardActionCardInput
  [:merge FateCardInput
   [:map
    [:offense {:optional true} :string]
    [:defense {:optional true} :string]]])

(def TeamAssetCardInput
  [:merge FateCardInput
   [:map
    [:asset-power {:optional true} :string]]])

(def CardSetInput
  [:map
   [:name :string]
   [:description {:optional true} [:maybe :string]]])
```

### 4.3 File Storage Format

Cards are stored as EDN files in the Git repository:

**Path**: `cards/{set-id}/{name}.edn`

```clojure
;; Example: cards/abc123/lightning-strike.edn
{:name "Lightning Strike"
 :card-type :card-type/PLAY_CARD
 :fate 2
 :play "Deal 3 damage to any player in your zone."
 :image-prompt "A dramatic basketball court with lightning bolts striking the hardwood floor"
 :created-at #inst "2025-01-15T10:30:00.000Z"
 :updated-at #inst "2025-01-20T14:45:00.000Z"}

;; Example: cards/abc123/jordan.edn
{:name "Jordan"
 :card-type :card-type/PLAYER_CARD
 :deck-size 5
 :sht 5
 :pss 3
 :def 4
 :speed 4
 :size :size/MD
 :abilities ["Clutch" "Fadeaway"]
 :image-prompt "A silhouette of a basketball player mid-jump with tongue out, dramatic lighting"
 :created-at #inst "2025-01-15T10:30:00.000Z"
 :updated-at #inst "2025-01-20T14:45:00.000Z"}
```

**Version History**: Use Git to view previous versions of a card:
- `git log -- cards/{set-id}/{name}.edn` - view commit history
- `git show {commit}:cards/{set-id}/{name}.edn` - view card at specific commit

### 4.4 Query Resolvers

**File**: `src/bashketball_editor_api/graphql/resolvers/query.clj`

```clojure
(gql/defresolver :Query :card
  "Fetches a single card by name and set ID."
  [:=> [:cat :any [:map [:name :string] [:setId :string]] :any]
       [:maybe GameCard]]
  [ctx {:keys [name setId]} _value]
  (proto/find-by (:card-repo ctx)
                 {:name name
                  :set-id (parse-uuid setId)}))

(gql/defresolver :Query :cards
  "Lists cards, optionally filtered by set ID and/or card type."
  [:=> [:cat :any [:map {:optional true}
                        [:setId {:optional true} :string]
                        [:cardType {:optional true} CardType]] :any]
       [:vector GameCard]]
  [ctx args _value]
  (let [opts (cond-> {}
               (:setId args) (assoc-in [:where :set-id] (parse-uuid (:setId args)))
               (:cardType args) (assoc-in [:where :card-type] (:cardType args)))]
    (proto/find-all (:card-repo ctx) opts)))

(gql/defresolver :Query :cardSet
  "Fetches a single card set by ID."
  [:=> [:cat :any [:map [:id :string]] :any] [:maybe CardSet]]
  [ctx {:keys [id]} _value]
  (proto/find-by (:set-repo ctx) {:id (parse-uuid id)}))

(gql/defresolver :Query :cardSets
  "Lists all card sets."
  [:=> [:cat :any :any :any] [:vector CardSet]]
  [ctx _args _value]
  (proto/find-all (:set-repo ctx) {}))
```

### 4.5 Mutation Resolvers

**File**: `src/bashketball_editor_api/graphql/resolvers/mutation.clj`

Implement card mutations with type-specific inputs:

```clojure
(defn- get-user-context
  "Extracts user context for Git operations."
  [ctx]
  (let [user-id (get-in ctx [:request :authn/user-id])
        user (proto/find-by (:user-repo ctx) {:id (parse-uuid user-id)})]
    {:name (:name user)
     :email (:email user)
     :github-token (:github-token user)}))

(gql/defresolver :Mutation :createPlayerCard
  "Creates a new player card."
  [:=> [:cat :any [:map [:setId :string] [:input PlayerCardInput]] :any] PlayerCard]
  [ctx {:keys [setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :card-type :card-type/PLAYER_CARD)
                      (assoc :_user (get-user-context ctx)))]
    (proto/create! (:card-repo ctx) card-data)))

(gql/defresolver :Mutation :createPlayCard
  "Creates a new play card."
  [:=> [:cat :any [:map [:setId :string] [:input PlayCardInput]] :any] PlayCard]
  [ctx {:keys [setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :card-type :card-type/PLAY_CARD)
                      (assoc :_user (get-user-context ctx)))]
    (proto/create! (:card-repo ctx) card-data)))

;; Similar mutations for other card types:
;; - createAbilityCard
;; - createSplitPlayCard
;; - createCoachingCard
;; - createStandardActionCard
;; - createTeamAssetCard

(gql/defresolver :Mutation :updateCard
  "Updates an existing card. Requires name and setId to identify the card."
  [:=> [:cat :any [:map [:name :string]
                        [:setId :string]
                        [:input :map]] :any]
       GameCard]
  [ctx {:keys [name setId input]} _value]
  (let [card-data (-> input
                      (assoc :set-id (parse-uuid setId))
                      (assoc :_user (get-user-context ctx)))]
    (proto/update! (:card-repo ctx)
                   {:name name}
                   card-data)))

(gql/defresolver :Mutation :deleteCard
  "Deletes a card by name and set ID."
  [:=> [:cat :any [:map [:name :string]
                        [:setId :string]] :any]
       :boolean]
  [ctx {:keys [name setId]} _value]
  (proto/delete! (:card-repo ctx)
                 {:name name
                  :set-id (parse-uuid setId)
                  :_user (get-user-context ctx)}))

(gql/defresolver :Mutation :createCardSet
  "Creates a new card set."
  [:=> [:cat :any [:map [:input CardSetInput]] :any] CardSet]
  [ctx {:keys [input]} _value]
  (proto/create! (:set-repo ctx)
                 (assoc input :_user (get-user-context ctx))))

(gql/defresolver :Mutation :updateCardSet
  "Updates an existing card set."
  [:=> [:cat :any [:map [:id :string] [:input CardSetInput]] :any] CardSet]
  [ctx {:keys [id input]} _value]
  (proto/update! (:set-repo ctx)
                 (parse-uuid id)
                 (assoc input :_user (get-user-context ctx))))

(gql/defresolver :Mutation :deleteCardSet
  "Deletes a card set and all its cards."
  [:=> [:cat :any [:map [:id :string]] :any] :boolean]
  [ctx {:keys [id]} _value]
  (proto/delete! (:set-repo ctx)
                 {:id (parse-uuid id)
                  :_user (get-user-context ctx)}))
```

### 4.7 Nested Resolvers

**File**: `src/bashketball_editor_api/graphql/resolvers/query.clj`

Add resolver for fetching cards within a set:

```clojure
(gql/defresolver :CardSet :cards
  "Fetches cards for a card set."
  [:=> [:cat :any :any CardSet] [:vector GameCard]]
  [ctx _args card-set]
  (proto/find-all (:card-repo ctx) {:where {:set-id (:id card-set)}}))
```

### 4.8 Card Repository Updates

The card repository needs to be updated to use `:name` as the primary key within a set:

**File**: `src/bashketball_editor_api/git/cards.clj`

```clojure
(defn- card-path
  "Returns the path for a card file: cards/{set-id}/{name}.edn"
  [set-id name]
  (let [safe-name (-> name
                      str/lower-case
                      (str/replace #"[^a-z0-9]" "-"))]
    (str "cards/" set-id "/" safe-name ".edn")))

(defrecord CardRepository [git-repo lock]
  proto/Repository
  (find-by [_this criteria]
    ;; Uses name as primary key within a set
    (when-let [name (:name criteria)]
      (when-let [set-id (:set-id criteria)]
        (let [path (card-path set-id name)]
          (when-let [content (git-repo/read-file git-repo path)]
            (edn/read-string content))))))

  (find-all [_this opts]
    (if-let [set-id (get-in opts [:where :set-id])]
      (let [card-files (list-cards-in-set git-repo set-id)
            cards (mapv #(edn/read-string (slurp %)) card-files)]
        ;; Filter by card-type if specified
        (if-let [card-type (get-in opts [:where :card-type])]
          (filterv #(= card-type (:card-type %)) cards)
          cards))
      []))

  ;; ... rest of implementation
)
```

### 4.9 Testing

**Files**:
- `test/bashketball_editor_api/graphql/card_queries_test.clj`
- `test/bashketball_editor_api/graphql/card_mutations_test.clj`
- `test/bashketball_editor_api/graphql/set_queries_test.clj`
- `test/bashketball_editor_api/graphql/set_mutations_test.clj`

Tests to implement:
- Query resolvers for each card type (PlayerCard, PlayCard, etc.)
- Card type filtering in queries
- Mutation resolvers for CRUD operations on each card type
- Name-based lookup within sets
- Authentication enforcement on mutations
- Nested resolver for cards in sets
- Input validation per card type
- Default value population
- Error handling for invalid card types

---

## Phase 5: Business Logic & Services

**Goal**: Implement validation, error handling, and business rules

### 5.1 Card Service Implementation

**File**: `src/bashketball_editor_api/services/card.clj`

Implement business logic methods:

```clojure
(defn validate-card
  "Validates card data against business rules."
  [card]
  (cond
    (str/blank? (:name card))
    {:valid? false :errors [{:field :name :message "Card name is required"}]}

    (> (count (:name card)) 100)
    {:valid? false :errors [{:field :name :message "Card name too long"}]}

    :else
    {:valid? true}))

(defn create-card
  "Creates a card with validation."
  [service set-id input]
  (let [card-data (assoc input :set-id set-id)
        validation (validate-card card-data)]
    (if (:valid? validation)
      {:ok (proto/create! (:card-repo service) card-data)}
      {:error (:errors validation)})))

(defn update-card
  "Updates a card with validation."
  [service id set-id input]
  (if-let [existing (proto/find-by (:card-repo service) {:id id :set-id set-id})]
    (let [updated (merge existing input)
          validation (validate-card updated)]
      (if (:valid? validation)
        {:ok (proto/update! (:card-repo service) id input)}
        {:error (:errors validation)}))
    {:error [{:message "Card not found"}]}))
```

### 5.2 Set Service Implementation

**File**: `src/bashketball_editor_api/services/set.clj`

Implement set business logic:

```clojure
(defn validate-set
  "Validates card set data against business rules."
  [card-set]
  (cond
    (str/blank? (:name card-set))
    {:valid? false :errors [{:field :name :message "Set name is required"}]}

    :else
    {:valid? true}))

(defn delete-set
  "Deletes a set and all its cards."
  [service id]
  (if-let [card-set (proto/find-by (:set-repo service) {:id id})]
    (let [cards (proto/find-all (:card-repo service) {:where {:set-id id}})]
      ;; Delete all cards first
      (doseq [card cards]
        (proto/delete! (:card-repo service) (:id card)))
      ;; Delete the set
      (proto/delete! (:set-repo service) id)
      {:ok true})
    {:error [{:message "Set not found"}]}))
```

### 5.3 Update Resolvers to Use Services

**File**: `src/bashketball_editor_api/graphql/resolvers/mutation.clj`

Update mutation resolvers to use services:

```clojure
(gql/defresolver :Mutation :createCard
  [:=> [:cat :any [:map [:set-id :string] [:input CardInput]] :any]
       [:or Card [:map [:errors [:vector :map]]]]]
  [ctx {:keys [set-id input]} _value]
  (let [result (card-svc/create-card
                (:card-service ctx)
                (parse-uuid set-id)
                input)]
    (if (:ok result)
      (:ok result)
      {:errors (:error result)})))
```

### 5.4 Error Handling Middleware

**File**: `src/bashketball_editor_api/graphql/middleware.clj`

Add error handling middleware:

```clojure
(defn wrap-error-handling
  [resolver]
  (fn [ctx args value]
    (try
      (resolver ctx args value)
      (catch Exception e
        (log/error e "Error in resolver")
        {:errors [{:message "Internal server error"
                   :type :internal-error}]}))))
```

### 5.5 Transaction Support

**File**: `src/bashketball_editor_api/graphql/middleware.clj`

Add transaction middleware for database operations:

```clojure
(defn wrap-transaction
  [resolver]
  (fn [ctx args value]
    (db/with-transaction [tx]
      (resolver ctx args value))))
```

### 5.6 Testing

**Files**:
- `test/bashketball_editor_api/services/card_test.clj`
- `test/bashketball_editor_api/services/set_test.clj`

Tests to implement:
- Card validation rules
- Set validation rules
- Cascade delete for sets
- Error handling
- Transaction rollback

---

## Phase 6: Production Readiness

**Goal**: Prepare application for production deployment

### 6.1 Logging and Monitoring

**File**: `src/bashketball_editor_api/middleware/logging.clj`

Implement request logging:

```clojure
(defn wrap-request-logging
  [handler]
  (fn [request]
    (let [start (System/currentTimeMillis)
          response (handler request)
          duration (- (System/currentTimeMillis) start)]
      (log/info {:event :request
                 :method (:request-method request)
                 :uri (:uri request)
                 :status (:status response)
                 :duration-ms duration})
      response)))
```

Add structured logging for:
- GraphQL query execution
- Authentication events
- GitHub API calls
- Database queries (already supported via `db/*debug*`)

### 6.2 Rate Limiting

**File**: `src/bashketball_editor_api/middleware/rate_limit.clj`

Implement rate limiting per user:

```clojure
(defn create-rate-limiter
  [max-requests window-ms]
  (atom {}))

(defn wrap-rate-limit
  [handler rate-limiter config]
  (fn [request]
    (let [user-id (get-in request [:authn/user-id])
          allowed? (check-rate-limit rate-limiter user-id config)]
      (if allowed?
        (handler request)
        {:status 429
         :headers {"Content-Type" "application/json"}
         :body {:error "Rate limit exceeded"}}))))
```

### 6.3 CORS Configuration

**File**: `src/bashketball_editor_api/handler.clj`

Add CORS middleware:

```clojure
(defn wrap-cors
  [handler allowed-origins]
  (fn [request]
    (let [origin (get-in request [:headers "origin"])
          response (handler request)]
      (if (contains? (set allowed-origins) origin)
        (-> response
            (assoc-in [:headers "Access-Control-Allow-Origin"] origin)
            (assoc-in [:headers "Access-Control-Allow-Methods"] "GET, POST, OPTIONS")
            (assoc-in [:headers "Access-Control-Allow-Headers"] "Content-Type, Authorization")
            (assoc-in [:headers "Access-Control-Allow-Credentials"] "true"))
        response))))
```

Add CORS configuration to `config.edn`.

### 6.4 Security Headers

**File**: `src/bashketball_editor_api/middleware/security.clj`

Add security headers:

```clojure
(defn wrap-security-headers
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "X-Content-Type-Options"] "nosniff")
          (assoc-in [:headers "X-Frame-Options"] "DENY")
          (assoc-in [:headers "X-XSS-Protection"] "1; mode=block")
          (assoc-in [:headers "Strict-Transport-Security"]
                    "max-age=31536000; includeSubDomains")))))
```

### 6.5 Health Checks

**File**: `src/bashketball_editor_api/handler.clj`

Enhance health check endpoint:

```clojure
(defn health-handler
  [db-pool]
  (fn [_request]
    (let [db-healthy? (try
                       (binding [db/*datasource* db-pool]
                         (db/execute-one! ["SELECT 1 as value"]))
                       true
                       (catch Exception _e false))]
      {:status (if db-healthy? 200 503)
       :headers {"Content-Type" "application/json"}
       :body {:status (if db-healthy? "ok" "degraded")
              :checks {:database db-healthy?}
              :timestamp (System/currentTimeMillis)}})))
```

### 6.6 Performance Optimization

**Caching**:
- Add caching for GitHub API responses
- Cache compiled GraphQL schema
- Consider Redis for distributed caching

**Query Optimization**:
- Add database indexes (already in migrations)
- Use connection pooling (already configured)
- Implement DataLoader pattern for GraphQL N+1 queries

**File**: `src/bashketball_editor_api/graphql/dataloader.clj`

### 6.7 Documentation

**Files to create**:
- `DEPLOYMENT.md` - Deployment instructions
- `API.md` - GraphQL API documentation
- `CONTRIBUTING.md` - Development guidelines

### 6.8 Environment Configuration

**File**: `.env.example`

Create example environment file:

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/bashketball_editor_prod

# Server
PORT=3000
HOST=0.0.0.0

# GitHub OAuth
GITHUB_CLIENT_ID=
GITHUB_SECRET=
GITHUB_REDIRECT_URI=https://api.bashketball.com/auth/github/callback
GITHUB_SUCCESS_REDIRECT_URI=https://bashketball.com/

# GitHub Repository
GITHUB_REPO_OWNER=
GITHUB_REPO_NAME=
GITHUB_REPO_BRANCH=main

# Session
SESSION_TTL_MS=86400000

# Rate Limiting
RATE_LIMIT_MAX_REQUESTS=100
RATE_LIMIT_WINDOW_MS=60000

# CORS
CORS_ALLOWED_ORIGINS=https://bashketball.com,http://localhost:3001
```

### 6.9 Docker Support

**File**: `Dockerfile`

```dockerfile
FROM clojure:temurin-21-tools-deps-alpine

WORKDIR /app

COPY deps.edn .
COPY bashketball-editor-api/deps.edn bashketball-editor-api/
COPY db/deps.edn db/
COPY authn/deps.edn authn/
COPY oidc-github/deps.edn oidc-github/
COPY graphql-server/deps.edn graphql-server/

RUN clojure -P -X:bashketball-editor-api

COPY . .

EXPOSE 3000

CMD ["clojure", "-M:bashketball-editor-api", "-m", "bashketball-editor-api.server", "prod"]
```

**File**: `docker-compose.yml`

### 6.10 CI/CD Pipeline

**File**: `.github/workflows/bashketball-editor-api.yml`

GitHub Actions workflow for:
- Running tests on PR
- Linting with clj-kondo
- Building Docker image
- Deploying to production

### 6.11 Testing

**Comprehensive test coverage**:
- Load testing with k6 or similar
- Security testing (OWASP checks)
- End-to-end API tests
- Performance benchmarks

---

## Implementation Order

Recommended implementation sequence:

1. **Phase 2** (1-2 days)
   - Essential for any functionality
   - Unblocks user testing

2. **Phase 3** (2-3 days)
   - Core feature implementation
   - Enables card/set management

3. **Phase 4** (2-3 days)
   - API completeness
   - User-facing functionality

4. **Phase 5** (1-2 days)
   - Code quality and robustness
   - Better error messages

5. **Phase 6** (2-4 days)
   - Production deployment
   - Can be done incrementally

**Total Estimated Time**: 8-14 days for full implementation

---

## Success Criteria

### Phase 2 ✅ COMPLETE
- [x] Users can authenticate via GitHub OAuth
- [x] User profiles are created/updated from GitHub data
- [x] Sessions persist across requests
- [x] `me` query returns authenticated user
- [x] `user` and `users` queries work
- [x] Logout functionality works
- [x] GitHub access token stored for Git operations

### Phase 3 ✅ COMPLETE
- [x] Git repository initialized (clone-or-open)
- [x] Can read/write/delete files from local Git repo
- [x] Card repository operations work (find-by, find-all, create!, update!, delete!)
- [x] Set repository operations work (find-by, find-all, create!, update!, delete!)
- [x] Manual sync operations (pullFromRemote, pushToRemote mutations)
- [x] Sync status query (syncStatus)
- [x] User attribution in Git commits
- [x] Per-user GitHub token for push/pull
- [x] CORS support for cross-origin frontend

### Phase 4 ⏳ PENDING
- [ ] Card type schemas implemented (PlayerCard, PlayCard, AbilityCard, etc.)
- [ ] Card queries work (card, cards with filtering by setId and cardType)
- [ ] CardSet queries work (cardSet, cardSets)
- [ ] Type-specific create mutations work (createPlayerCard, createPlayCard, etc.)
- [ ] Generic updateCard mutation works with name+setId lookup
- [ ] deleteCard mutation works with name+setId lookup
- [ ] CardSet CRUD mutations work
- [ ] Nested resolver works (CardSet.cards)
- [ ] Card repository updated for name-based lookup
- [ ] Authentication required for all mutations
- [ ] Proper error messages for invalid card types

### Phase 5 ⏳ PENDING
- [ ] Input validation works correctly
- [ ] Business rules enforced
- [ ] Error handling is comprehensive
- [ ] Transactions work properly

### Phase 6 ⏳ PENDING
- [ ] Application runs in production
- [ ] Monitoring and logging in place
- [ ] Security headers configured
- [ ] Rate limiting works
- [ ] Documentation complete

---

## Notes

- Each phase builds on the previous phase
- Testing should be written alongside implementation
- Configuration changes should be documented
- Breaking changes should be avoided once in production
- Consider feature flags for gradual rollout
