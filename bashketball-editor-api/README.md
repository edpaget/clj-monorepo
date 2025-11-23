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
  - Cards and sets in GitHub repository (EDN files)
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
│   ├── handler.clj             # Ring handler and routes
│   ├── models/
│   │   ├── protocol.clj        # Repository protocol
│   │   ├── user.clj            # User model (PostgreSQL)
│   │   └── session.clj         # Session model (PostgreSQL)
│   ├── github/
│   │   ├── client.clj          # GitHub API client
│   │   ├── cards.clj           # Card repository (GitHub)
│   │   └── sets.clj            # Set repository (GitHub)
│   ├── services/
│   │   ├── auth.clj            # Authentication service
│   │   ├── card.clj            # Card business logic
│   │   └── set.clj             # Set business logic
│   └── graphql/
│       ├── schema.clj          # GraphQL schema
│       ├── middleware.clj      # GraphQL middleware
│       └── resolvers/
│           ├── query.clj       # Query resolvers
│           └── mutation.clj    # Mutation resolvers
├── resources/
│   ├── config.edn              # Aero configuration
│   └── migrations/             # Database migrations
│       ├── 001-create-users.edn
│       └── 002-create-sessions.edn
└── test/
    └── bashketball_editor_api/
        ├── test_utils.clj      # Test fixtures and helpers
        └── system_test.clj     # Integration tests
```

## Getting Started

### Prerequisites

- Clojure 1.12+
- PostgreSQL 14+
- GitHub OAuth Application
- GitHub repository for storing cards

### Environment Variables

Create a `.env` file or set the following environment variables:

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/bashketball_editor_dev

# Server
PORT=3000

# GitHub OAuth
GITHUB_CLIENT_ID=your-client-id
GITHUB_SECRET=your-client-secret
GITHUB_REDIRECT_URI=http://localhost:3000/auth/github/callback

# GitHub Repository for card storage
GITHUB_REPO_OWNER=your-username
GITHUB_REPO_NAME=bashketball-cards

# Optional
GITHUB_REPO_BRANCH=main
SESSION_TTL_MS=86400000  # 24 hours
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

```clojure
(require '[bashketball-editor-api.server :as server])

;; Start with dev profile
(server/start!)

;; Stop
(server/stop!)

;; Restart
(server/restart!)
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

### Current Implementation (Phase 1)

```graphql
type User {
  id: ID!
  githubLogin: String!
  email: String
  avatarUrl: String
  name: String
}

type Query {
  me: User  # Returns current authenticated user
}
```

### Planned Schema (Future Phases)

```graphql
type Card {
  id: ID!
  setId: ID!
  name: String!
  description: String
  attributes: JSON
  createdAt: String
  updatedAt: String
}

type CardSet {
  id: ID!
  name: String!
  description: String
  cards: [Card!]!
  createdAt: String
  updatedAt: String
}

type Query {
  me: User
  card(id: ID!): Card
  cards(setId: ID): [Card!]!
  cardSet(id: ID!): CardSet
  cardSets: [CardSet!]!
}

type Mutation {
  createCard(setId: ID!, input: CardInput!): Card!
  updateCard(id: ID!, input: CardInput!): Card!
  deleteCard(id: ID!): Boolean!

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

### Phase 2: Authentication & User Management

- [ ] Complete GitHub OAuth flow
- [ ] User upsert from GitHub data
- [ ] Session management
- [ ] Authentication middleware for resolvers

### Phase 3: GitHub Repository Integration

- [ ] GitHub API client implementation
- [ ] Card repository (GitHub-backed)
- [ ] Set repository (GitHub-backed)
- [ ] EDN file format for cards/sets
- [ ] Conflict detection

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
