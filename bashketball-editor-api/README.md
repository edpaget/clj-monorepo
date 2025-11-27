# bashketball-editor-api

GraphQL API for the Bashketball Trading Card Game editor.

## Overview

A GraphQL API server that manages users, sessions, and trading cards for Bashketball. Users authenticate via GitHub OAuth, and cards/sets are stored in a GitHub repository while user data and sessions are stored in PostgreSQL.

## Architecture

- **Database**: PostgreSQL for users and sessions
- **Authentication**: GitHub OAuth via oidc-github
- **API**: GraphQL via Lacinia
- **Storage**: Hybrid approach
  - User data and sessions in PostgreSQL
  - Cards and sets in Git repository via JGit (EDN files)
- **Git Integration**: JGit with single writer pattern
  - Local clone for fast reads (1-10ms)
  - Background sync with remote (GitHub, GitLab, etc.)
  - Single writer instance for mutations
- **Configuration**: Aero for environment-specific config
- **Component Management**: Integrant for lifecycle management
- **Validation**: Malli schemas throughout

## Project Structure

```
bashketball-editor-api/
├── src/bashketball_editor_api/
│   ├── config.clj              # Configuration loading
│   ├── system.clj              # Integrant system definition
│   ├── server.clj              # Server entry point
│   ├── handler.clj             # Ring handler, routes, and CORS middleware
│   ├── auth/
│   │   └── github.clj          # GitHub OAuth integration
│   ├── models/
│   │   ├── protocol.clj        # Repository protocol
│   │   └── user.clj            # User model (PostgreSQL)
│   ├── git/
│   │   ├── repo.clj            # Git repository operations (JGit)
│   │   ├── sync.clj            # Manual sync operations (pull/push)
│   │   ├── cards.clj           # Card repository (Git-backed)
│   │   └── sets.clj            # Set repository (Git-backed)
│   ├── services/
│   │   └── auth.clj            # Authentication service
│   └── graphql/
│       ├── schema.clj          # GraphQL schema
│       ├── middleware.clj      # GraphQL middleware (auth)
│       └── resolvers/
│           ├── query.clj       # Query resolvers (me, user, users, syncStatus)
│           └── mutation.clj    # Mutation resolvers (pullFromRemote, pushToRemote)
├── resources/
│   ├── config.edn              # Aero configuration
│   └── migrations/             # Database migrations
│       ├── 001-create-users.edn
│       └── 002-create-sessions.edn
└── test/
    └── bashketball_editor_api/
        ├── test_utils.clj      # Test fixtures and helpers
        ├── auth_test.clj       # Authentication tests
        ├── handler_test.clj    # Handler tests
        ├── system_test.clj     # Integration tests
        └── git/
            ├── repo_test.clj   # Git repository tests
            ├── cards_test.clj  # Card repository tests
            ├── sets_test.clj   # Set repository tests
            └── sync_test.clj   # Sync operations tests
```

## Getting Started

### Prerequisites

- Clojure 1.12+
- PostgreSQL 14+
- GitHub OAuth Application
- GitHub repository for storing cards

### Environment Variables

#### Using direnv (Recommended)

This project includes a `.envrc` template for use with [direnv](https://direnv.net/):

1. Install direnv: `brew install direnv` (macOS) or see [direnv installation](https://direnv.net/docs/installation.html)
2. Copy the template: The `.envrc` file is already in the project
3. Edit `.envrc` and replace placeholder values with your actual credentials
4. Allow direnv: `direnv allow`

The environment variables will be automatically loaded when you enter the project directory.

#### Required Environment Variables

```bash
# GitHub OAuth (create app at https://github.com/settings/developers)
export GITHUB_CLIENT_ID="your-github-oauth-client-id"
export GITHUB_SECRET="your-github-oauth-client-secret"

# GitHub Repository for Card Storage
export GITHUB_REPO_OWNER="your-github-username"
export GITHUB_REPO_NAME="bashketball-cards"

# Git Remote URL
export GIT_REMOTE_URL="https://github.com/your-github-username/bashketball-cards.git"
```

#### Optional Environment Variables (with defaults)

```bash
# Server (default: 3000)
export PORT=3000

# Database (default: jdbc:postgresql://localhost:5432/bashketball_editor_dev?user=postgres&password=postgres)
export DATABASE_URL="jdbc:postgresql://localhost:5432/bashketball_editor_dev?user=postgres&password=postgres"

# OAuth Redirects
export GITHUB_REDIRECT_URI="http://localhost:3000/auth/github/callback"
export GITHUB_SUCCESS_REDIRECT_URI="http://localhost:3001/"

# Repository Configuration
export GITHUB_REPO_BRANCH="main"
export GIT_REPO_PATH="/data/bashketball-cards"
export GIT_BRANCH="main"

# Session Configuration (24 hours in milliseconds)
export SESSION_TTL_MS=86400000
```

### Database Setup

Create the development and test databases:

```bash
createdb bashketball_editor_dev
createdb bashketball_editor_test
```

Migrations will run automatically on system startup.

### Running the Server

#### From REPL

Start a REPL in the project directory:

```bash
clojure -M:repl/rebel
```

The `user` namespace is automatically loaded with development utilities:

```clojure
;; Start the system with dev profile
(start)

;; Stop the system
(stop)

;; Restart the system
(restart)

;; Access the running system
(system)

;; Access specific components
(::system/config (system))
(::system/db-pool (system))
(::system/handler (system))
```

#### From Command Line

```bash
# Development
clojure -M:bashketball-editor-api -m bashketball-editor-api.server

# Production
clojure -M:bashketball-editor-api -m bashketball-editor-api.server prod
```

### Running Tests

```bash
# Run package tests
clojure -X:bashketball-editor-api-test

# Run all tests
clojure -X:test-all
```

## Deployment

### Single Writer Pattern

For production deployments with multiple instances:

#### Writer Instance (handles mutations)
```bash
# Set environment variable to designate as writer
export GIT_WRITER=true
export GIT_REMOTE_URL=git@github.com:org/bashketball-cards.git
export GIT_REPO_PATH=/data/bashketball-cards

# Start the application
clojure -M:bashketball-editor-api -m bashketball-editor-api.server prod
```

#### Reader Instances (optional, for scaling queries)
```bash
# Defaults to read-only when GIT_WRITER is not set or false
export GIT_WRITER=false
export GIT_REMOTE_URL=git@github.com:org/bashketball-cards.git
export GIT_REPO_PATH=/data/bashketball-cards

# Start the application
clojure -M:bashketball-editor-api -m bashketball-editor-api.server prod
```

**Key Points**:
- Only ONE instance should have `GIT_WRITER=true`
- Writer instance handles all GraphQL mutations
- Reader instances can handle queries (optional, for load balancing)
- All instances clone the repository locally for fast reads
- Background sync job runs only on writer instance (every 30s)
- Writer instance pushes commits to remote Git repository

### Git Repository Setup

Initialize the card repository:

```bash
# Create a new repository
git init bashketball-cards
cd bashketball-cards

# Create directory structure
mkdir -p cards sets
touch cards/.gitkeep sets/.gitkeep

# Initial commit
git add .
git commit -m "Initial commit"

# Push to remote
git remote add origin git@github.com:your-org/bashketball-cards.git
git push -u origin main
```

## API Endpoints

### Health Check

```
GET /health
```

Returns system health status.

### Authentication

```
GET /auth/github/callback?code=...
```

GitHub OAuth callback that creates/updates user and establishes session.

```
POST /auth/logout
```

Destroys the current session.

### GraphQL

```
POST /graphql
GET /graphql  # GraphiQL interface (dev only)
```

GraphQL endpoint. Requires authentication for most operations.

## GraphQL Schema

### Current Implementation (Phase 3)

```graphql
type User {
  id: ID!
  githubLogin: String!
  email: String
  avatarUrl: String
  name: String
}

type SyncStatus {
  ahead: Int!
  behind: Int!
  uncommittedChanges: Int!
  isClean: Boolean!
}

type SyncResult {
  status: String!
  message: String!
  error: String
  conflicts: [String!]
}

type Query {
  # Returns current authenticated user
  me: User

  # Find user by ID or GitHub login
  user(id: ID, githubLogin: String): User

  # List all users with optional limit
  users(limit: Int): [User!]!

  # Git sync status
  syncStatus: SyncStatus!
}

type Mutation {
  # Pull changes from remote Git repository
  pullFromRemote: SyncResult!

  # Push local changes to remote Git repository
  pushToRemote: SyncResult!
}
```

### Planned Schema (Phase 4)

```graphql
# Enums
enum CardType {
  PLAYER_CARD
  ABILITY_CARD
  SPLIT_PLAY_CARD
  PLAY_CARD
  COACHING_CARD
  STANDARD_ACTION_CARD
  TEAM_ASSET_CARD
}

enum PlayerSize { SM MD LG }

# Base Card interface (all card types implement this)
# Note: Version history is tracked by Git, not in the schema
interface Card {
  name: String!
  cardType: CardType!
  imagePrompt: String       # Description for AI image generation
  createdAt: String!
  updatedAt: String!
}

# Card type implementations
type PlayerCard implements Card {
  name: String!
  cardType: CardType!
  imagePrompt: String
  deckSize: Int!
  sht: Int!      # Shot stat
  pss: Int!      # Pass stat
  def: Int!      # Defense stat
  speed: Int!
  size: PlayerSize!
  abilities: [String!]!
  createdAt: String!
  updatedAt: String!
}

type PlayCard implements Card {
  name: String!
  cardType: CardType!
  imagePrompt: String
  fate: Int!
  play: String!
  createdAt: String!
  updatedAt: String!
}

# ... similar types for AbilityCard, SplitPlayCard, CoachingCard,
#     StandardActionCard, TeamAssetCard

type CardSet {
  id: ID!
  name: String!
  description: String
  cards: [Card!]!
  createdAt: String!
  updatedAt: String!
}

type Query {
  # ... existing queries
  card(name: String!, setId: ID!): Card
  cards(setId: ID, cardType: CardType): [Card!]!
  cardSet(id: ID!): CardSet
  cardSets: [CardSet!]!
}

type Mutation {
  # ... existing mutations

  # Type-specific card creation
  createPlayerCard(setId: ID!, input: PlayerCardInput!): PlayerCard!
  createPlayCard(setId: ID!, input: PlayCardInput!): PlayCard!
  createAbilityCard(setId: ID!, input: AbilityCardInput!): AbilityCard!
  # ... similar for other card types

  # Generic card update/delete (uses name as key within a set)
  # Version history is tracked by Git
  updateCard(name: String!, setId: ID!, input: CardInput!): Card!
  deleteCard(name: String!, setId: ID!): Boolean!

  createCardSet(input: CardSetInput!): CardSet!
  updateCardSet(id: ID!, input: CardSetInput!): CardSet!
  deleteCardSet(id: ID!): Boolean!
}
```

## Development Roadmap

### Phase 1: Implementation Skeleton ✅

- [x] Project structure and configuration
- [x] Database migrations
- [x] Integrant system setup
- [x] Basic authentication flow
- [x] Minimal GraphQL schema (me query)
- [x] Testing infrastructure

### Phase 2: Authentication & User Management ✅

- [x] Complete GitHub OAuth flow
- [x] User upsert from GitHub data
- [x] Session management
- [x] User GraphQL queries (me, user, users)
- [x] Authentication middleware for resolvers
- [x] GitHub access token storage for Git operations

### Phase 3: Git Repository Integration (JGit) ✅

- [x] JGit dependency integration (clj-jgit)
- [x] Git repository component (clone-or-open, read, write, delete, commit, push, pull)
- [x] Manual sync operations (pull-from-remote, push-to-remote)
- [x] Card repository (Git-backed with local clone)
- [x] Set repository (Git-backed with local clone)
- [x] Single writer pattern implementation
- [x] EDN file format for cards/sets
- [x] Read-only repository enforcement
- [x] User attribution in Git commits
- [x] Per-user GitHub token for push/pull credentials
- [x] Sync status query (syncStatus)
- [x] CORS middleware for cross-origin frontend requests

### Phase 4: Card & Set GraphQL API

- [ ] Complete Card and CardSet types
- [ ] Query resolvers (cards, cardSets)
- [ ] Mutation resolvers (create, update, delete)
- [ ] Input validation with Malli
- [ ] Authorization checks

### Phase 5: Business Logic & Services

- [ ] Card validation logic
- [ ] Set validation logic
- [ ] Transaction handling
- [ ] Error handling

### Phase 6: Production Readiness

- [ ] Comprehensive error handling
- [ ] Request logging
- [ ] Rate limiting
- [ ] Security hardening
- [ ] Performance optimization
- [ ] Deployment guide

## Configuration

Configuration is managed through `resources/config.edn` using Aero. It supports:

- Environment variable substitution with `#env`
- Profile-specific configuration with `#profile`
- Default values with `#or`

See `config.edn` for all available options.

## Testing

Tests use the `:test` profile which uses a separate test database. The test infrastructure provides:

- `with-system` fixture for system lifecycle
- `with-clean-db` fixture for database cleanup
- `create-test-user` helper for user creation
- `with-db` macro for database access

## License

See the root repository for license information.
