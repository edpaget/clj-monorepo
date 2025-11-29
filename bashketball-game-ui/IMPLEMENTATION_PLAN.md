# bashketball-game-ui Implementation Plan

A ClojureScript SPA for playing the Bashketball trading card game, built with UIx and Apollo GraphQL.

## Overview

This package provides the player-facing game UI, allowing users to:
- Sign in via Google OAuth (handled by bashketball-game-api)
- Build and manage card decks
- Play games against other players
- View game history and statistics

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    bashketball-game-ui                       │
│                   (ClojureScript SPA)                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ GraphQL
┌─────────────────────────────────────────────────────────────┐
│                   bashketball-game-api                       │
│              (GraphQL API + Google OAuth)                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     bashketball-game                         │
│           (Game State Engine - Pure Clojure)                 │
└─────────────────────────────────────────────────────────────┘
```

## Directory Structure

```
bashketball-game-ui/
├── src/bashketball_game_ui/
│   ├── core.cljs                    # App entry, root component, init!
│   ├── config.cljs                  # API URLs, app constants
│   ├── router.cljs                  # React Router re-exports
│   │
│   ├── components/
│   │   ├── ui/                      # Reusable UI primitives
│   │   │   ├── button.cljs
│   │   │   ├── input.cljs
│   │   │   ├── select.cljs
│   │   │   ├── card.cljs            # Card display component
│   │   │   ├── dialog.cljs
│   │   │   ├── loading.cljs
│   │   │   └── avatar.cljs
│   │   ├── protected-route.cljs     # Auth guard
│   │   ├── deck/                    # Deck-related components
│   │   │   ├── deck-list.cljs
│   │   │   ├── deck-builder.cljs
│   │   │   ├── deck-card.cljs
│   │   │   └── card-selector.cljs
│   │   └── game/                    # Game-related components
│   │       ├── game-board.cljs      # Main game board layout
│   │       ├── player-hand.cljs     # Cards in player's hand
│   │       ├── player-field.cljs    # Active cards on field
│   │       ├── action-bar.cljs      # Available actions
│   │       ├── game-log.cljs        # Action history
│   │       ├── turn-indicator.cljs  # Whose turn / phase
│   │       └── card-detail.cljs     # Expanded card view
│   │
│   ├── views/                       # Page components (routes)
│   │   ├── layout.cljs              # Master layout with nav
│   │   ├── home.cljs                # Landing page
│   │   ├── login-callback.cljs      # OAuth callback handler
│   │   ├── decks.cljs               # Deck list view
│   │   ├── deck-editor.cljs         # Single deck edit view
│   │   ├── lobby.cljs               # Game matchmaking lobby
│   │   ├── game.cljs                # Active game view
│   │   ├── game-history.cljs        # Past games list
│   │   └── profile.cljs             # User profile/stats
│   │
│   ├── context/
│   │   ├── auth.cljs                # Auth context + use-auth hook
│   │   └── game.cljs                # Active game context
│   │
│   ├── graphql/
│   │   ├── client.cljs              # Apollo client setup with SSE link
│   │   ├── sse-link.cljs            # Custom Apollo link for SSE subscriptions
│   │   ├── queries.cljs             # GraphQL queries
│   │   ├── mutations.cljs           # GraphQL mutations
│   │   └── subscriptions.cljs       # GraphQL subscriptions (via SSE)
│   │
│   ├── hooks/
│   │   ├── use-me.cljs              # Current user query
│   │   ├── use-decks.cljs           # Deck CRUD operations
│   │   ├── use-cards.cljs           # Card collection queries
│   │   ├── use-game.cljs            # Game state and actions
│   │   ├── use-lobby.cljs           # Matchmaking hooks
│   │   └── form.cljs                # Form state management
│   │
│   ├── game/                        # Client-side game logic
│   │   ├── state.cljs               # Local game state management
│   │   ├── actions.cljs             # Action dispatch helpers
│   │   └── validation.cljs          # Client-side move validation
│   │
│   └── schemas/
│       ├── deck.cljs                # Deck validation schemas
│       ├── card.cljs                # Card type schemas
│       └── game.cljs                # Game state schemas
│
├── test/bashketball_game_ui/
│   ├── components/
│   ├── views/
│   └── hooks/
│
├── public/
│   ├── index.html
│   ├── css/output.css               # Generated Tailwind
│   └── js/                          # Generated JS
│
├── resources/public/css/
│   └── input.css                    # Tailwind directives
│
├── src/js/
│   └── index.js                     # npm dependency exports
│
├── shadow-cljs.edn
├── deps.edn
├── package.json
├── tailwind.config.js
├── postcss.config.js
└── README.md
```

## Implementation Phases

### Phase 1: Project Scaffolding

1. **Create project structure**
   - Initialize deps.edn with UIx, Malli, shadow-cljs dependencies
   - Set up package.json with React, Apollo, Tailwind, Radix UI
   - Configure shadow-cljs.edn for :app and :test builds
   - Set up Tailwind CSS configuration

2. **Core infrastructure**
   - `core.cljs` - App entry point with ILookup extension for JS objects
   - `config.cljs` - API URL configuration
   - `router.cljs` - React Router re-exports
   - Basic `index.html` with app mount point

3. **UI component library**
   - Copy/adapt button, input, select, dialog from bashketball-editor-ui
   - Add game-specific components (card display, avatar)
   - Set up `cn` utility for Tailwind class merging

### Phase 2: Authentication

1. **GraphQL client setup**
   - Apollo client with credentials: "include"
   - InMemoryCache configuration
   - Custom SSE link for subscriptions (see Phase 5)

2. **Auth context implementation**
   ```clojure
   ;; context/auth.cljs
   (defui auth-provider [{:keys [children]}]
     (let [{:keys [user loading?]} (use-me)]
       ($ (.-Provider auth-context)
          {:value {:user user
                   :loading? loading?
                   :logged-in? (some? user)}}
          children)))
   ```

3. **Google OAuth flow**
   - Login button redirects to `{api-base-url}/auth/google/login`
   - OAuth callback view handles redirect from API
   - API sets session cookie, UI refetches user

4. **Protected routes**
   - Wrap authenticated routes with guard component
   - Redirect to home if not logged in

### Phase 3: Deck Management

1. **GraphQL queries/mutations** (aligned with bashketball-game-api)
   ```graphql
   query MyDecks {
     myDecks {
       id name cardSlugs isValid validationErrors
     }
   }

   query Deck($id: ID!) {
     deck(id: $id) {
       id name cardSlugs
       cards { slug name setSlug cardType sht pss def speed size abilities }
       isValid validationErrors
     }
   }

   # Card catalog queries
   query Cards($setId: ID) {
     cards(setId: $setId) {
       slug name setSlug cardType
       sht pss def speed size abilities deckSize  # Player card fields
       fate offense defense coaching assetPower   # Action card fields
     }
   }

   query Sets {
     sets { slug name description cards { slug name cardType } }
   }

   query Card($id: ID!) {
     card(id: $id) { slug name setSlug cardType ... }
   }

   mutation CreateDeck($name: String!) {
     createDeck(name: $name) { id name cardSlugs isValid }
   }

   mutation UpdateDeck($id: ID!, $name: String, $cardSlugs: [String!]) {
     updateDeck(id: $id, name: $name, cardSlugs: $cardSlugs) {
       id name cardSlugs isValid validationErrors
     }
   }

   mutation DeleteDeck($id: ID!) {
     deleteDeck(id: $id)
   }

   mutation ValidateDeck($id: ID!) {
     validateDeck(id: $id) { id isValid validationErrors }
   }
   ```

2. **Deck list view**
   - Display user's decks with validation status
   - Create new deck button
   - Delete deck with confirmation
   - Show invalid deck warnings

3. **Deck builder view**
   - Two-panel layout: available cards | deck contents
   - Card filtering by set, type, name search
   - Click to add/remove cards (store as `cardSlugs`)
   - Real-time validation feedback
   - Save triggers `validateDeck` mutation

4. **Deck validation rules** (from API)
   - 3-5 player cards required
   - 30-50 action/coaching cards required
   - Max 4 copies of same card
   - All card slugs must exist in catalog

5. **Malli schemas** (using bashketball-schemas)
   ```clojure
   (ns bashketball-game-ui.schemas.deck
     "Deck schemas for the UI."
     (:require [bashketball-schemas.core :as schemas]
               [bashketball-schemas.card :as card]))

   ;; Card schema imported from shared library
   ;; Use schemas/Card, schemas/PlayerCard, etc.

   ;; Deck schema (UI-specific, includes GraphQL response fields)
   (def Deck
     [:map
      [:id :uuid]
      [:name [:string {:min 1 :max 255}]]
      [:cardSlugs [:vector card/Slug]]
      [:cards {:optional true} [:vector schemas/Card]]
      [:isValid :boolean]
      [:validationErrors {:optional true} [:vector :string]]])

   ;; Enums available from shared library:
   ;; schemas/CardType, schemas/Size, schemas/GameStatus,
   ;; schemas/Team, schemas/GamePhase, schemas/BallStatus
   ```

### Phase 4: Game Lobby & Matchmaking

1. **GraphQL queries/mutations** (aligned with bashketball-game-api)
   ```graphql
   # My games with optional status filter
   query MyGames($status: GameStatus) {
     myGames(status: $status) {
       id
       homePlayer { id email name avatarUrl }
       awayPlayer { id email name avatarUrl }
       status
       gameState { turnNumber phase activePlayer }
       winner { id name }
       createdAt startedAt
     }
   }

   # Games waiting for opponent
   query AvailableGames {
     availableGames {
       id
       homePlayer { id name avatarUrl }
       status createdAt
     }
   }

   # Single game with full state
   query Game($id: ID!) {
     game(id: $id) {
       id
       homePlayer { id email name avatarUrl }
       awayPlayer { id email name avatarUrl }
       status
       gameState {
         gameId phase turnNumber activePlayer
         score { home away }
         board { width height occupants { position { q r } type id } }
         ball { status holderId position { q r } }
         homePlayers { id actionsRemaining hand drawPileCount discardCount
           team { starters bench players {
             id cardSlug name position { q r } exhausted
             stats { size speed shooting passing dribbling defense }
             abilities modifiers { id stat amount source expiresAt }
           }}}
         awayPlayers { ... }
         stack { id type source data }
         events { id playerId eventType eventData sequenceNum createdAt }
       }
       winner { id name }
       createdAt startedAt
     }
   }

   mutation CreateGame($deckId: ID!) {
     createGame(deckId: $deckId) {
       id status homePlayer { id name }
     }
   }

   mutation JoinGame($gameId: ID!, $deckId: ID!) {
     joinGame(gameId: $gameId, deckId: $deckId) {
       id status awayPlayer { id name }
     }
   }

   mutation LeaveGame($gameId: ID!) {
     leaveGame(gameId: $gameId)
   }

   mutation ForfeitGame($gameId: ID!) {
     forfeitGame(gameId: $gameId) { id status winner { id name } }
   }

   # Real-time game updates (via SSE)
   subscription GameUpdated($gameId: ID!) {
     gameUpdated(gameId: $gameId) {
       type  # STATE_CHANGED | PLAYER_JOINED | GAME_STARTED | GAME_ENDED | PLAYER_LEFT
       game { id status gameState { ... } }
       event { id playerId eventType eventData sequenceNum createdAt }
     }
   }

   # Lobby updates for new available games
   subscription LobbyUpdated {
     lobbyUpdated { ... }
   }
   ```

2. **Game status enum**
   - `WAITING` - Created, waiting for opponent
   - `ACTIVE` - Game in progress
   - `COMPLETED` - Game finished with winner
   - `ABANDONED` - Game cancelled

3. **Lobby view**
   - List available games (status=WAITING) to join
   - Create new game (select validated deck only)
   - List my active games (status=ACTIVE) to resume
   - Recent completed games

4. **Matchmaking flow**
   - Select deck → Create game → Status=WAITING → Wait for opponent
   - Or: Browse available games → Select deck → Join → Status=ACTIVE

### Phase 5: Game Interface

1. **Game state context**
   ```clojure
   ;; context/game.cljs
   (def game-context (create-context nil))

   (defui game-provider [{:keys [game-id children]}]
     (let [game-state (use-game-subscription game-id)
           dispatch (use-game-dispatch game-id)]
       ($ (.-Provider game-context)
          {:value {:state game-state :dispatch dispatch}}
          children)))
   ```

2. **Game board layout**
   ```
   ┌──────────────────────────────────────────────┐
   │  Opponent Info  │  Turn/Phase  │  Game Log   │
   ├─────────────────┴──────────────┴─────────────┤
   │              Opponent Field                   │
   │  [Card] [Card] [Card] [Card] [Card]          │
   ├──────────────────────────────────────────────┤
   │              Your Field                       │
   │  [Card] [Card] [Card] [Card] [Card]          │
   ├──────────────────────────────────────────────┤
   │              Your Hand                        │
   │  [Card] [Card] [Card] [Card] [Card] [Card]   │
   ├──────────────────────────────────────────────┤
   │  [Action 1]  [Action 2]  [End Turn]          │
   └──────────────────────────────────────────────┘
   ```

3. **Core game components**
   - `game-board.cljs` - Main layout orchestrator
   - `player-hand.cljs` - Draggable/clickable hand cards
   - `player-field.cljs` - Active cards with targeting
   - `action-bar.cljs` - Context-sensitive action buttons
   - `game-log.cljs` - Scrollable action history
   - `card-detail.cljs` - Modal/popover for card details

4. **Game actions** (aligned with bashketball-game-api)

   The API uses `submitAction` mutation with `GameActionInput`. Actions are namespaced
   keywords from the bashketball-game library (e.g., `"bashketball/move-player"`).

   ```graphql
   mutation SubmitAction($gameId: ID!, $action: GameActionInput!) {
     submitAction(gameId: $gameId, action: $action) {
       success
       game { id gameState { ... } }
       error
     }
   }

   # GameActionInput fields vary by action type
   input GameActionInput {
     type: String!           # e.g., "bashketball/move-player"
     playerId: String        # For player-specific actions
     player: Team            # HOME or AWAY
     position: HexPositionInput
     count: Int              # For draw-cards
     amount: Int             # For set-actions, add-score
     cardSlugs: [String!]    # For discard-cards
     phase: GamePhase        # For set-phase
     holderId: String        # For ball possession
     origin: HexPositionInput
     target: HexPositionInput
     actionType: BallActionType  # SHOT or PASS
     points: Int
     team: Team
   }
   ```

   **Key action types from bashketball-game**:
   ```clojure
   ;; Game flow
   {:type "bashketball/set-phase" :phase "ACTIONS"}
   {:type "bashketball/advance-turn"}
   {:type "bashketball/set-active-player" :player "HOME"}

   ;; Player resources
   {:type "bashketball/draw-cards" :player "HOME" :count 5}
   {:type "bashketball/discard-cards" :player "HOME" :cardSlugs ["basic-shot"]}
   {:type "bashketball/set-actions" :player "HOME" :amount 3}

   ;; Basketball player movement
   {:type "bashketball/move-player" :playerId "home-mj-0" :position {:q 3 :r 5}}
   {:type "bashketball/exhaust-player" :playerId "home-mj-0"}
   {:type "bashketball/refresh-player" :playerId "home-mj-0"}
   {:type "bashketball/substitute" :team "HOME" :starterId "..." :benchId "..."}

   ;; Ball control
   {:type "bashketball/set-ball-possessed" :holderId "home-mj-0"}
   {:type "bashketball/set-ball-loose" :position {:q 2 :r 7}}
   {:type "bashketball/set-ball-in-air" :origin {:q 3 :r 5} :target {:q 2 :r 13} :actionType "SHOT"}

   ;; Scoring
   {:type "bashketball/add-score" :team "HOME" :points 2}
   ```

   **Hook implementation**:
   ```clojure
   ;; hooks/use-game.cljs
   (defn use-game [game-id]
     (let [{:keys [data]} (use-subscription GAME_UPDATED_SUBSCRIPTION
                                            {:variables {:gameId game-id}})
           [submit-action] (use-mutation SUBMIT_ACTION)]
       {:state (transform-game-state (:game data))
        :submit (fn [action]
                  (submit-action {:variables {:gameId game-id :action action}}))
        ;; Convenience wrappers
        :move-player (fn [player-id q r]
                       (submit-action {:variables
                                       {:gameId game-id
                                        :action {:type "bashketball/move-player"
                                                 :playerId player-id
                                                 :position {:q q :r r}}}}))
        :pass-ball (fn [origin-q origin-r target-q target-r]
                     (submit-action {:variables
                                     {:gameId game-id
                                      :action {:type "bashketball/set-ball-in-air"
                                               :origin {:q origin-q :r origin-r}
                                               :target {:q target-q :r target-r}
                                               :actionType "PASS"}}}))
        :shoot-ball (fn [origin-q origin-r target-q target-r]
                      (submit-action {:variables
                                      {:gameId game-id
                                       :action {:type "bashketball/set-ball-in-air"
                                                :origin {:q origin-q :r origin-r}
                                                :target {:q target-q :r target-r}
                                                :actionType "SHOT"}}}))
        :end-turn (fn []
                    (submit-action {:variables
                                    {:gameId game-id
                                     :action {:type "bashketball/advance-turn"}}}))}))
   ```

5. **Real-time updates via SSE**

   The backend uses Server-Sent Events (SSE) for GraphQL subscriptions instead of WebSockets.
   This requires a custom Apollo link implementation.

   **SSE Link Implementation** (`graphql/sse-link.cljs`):

   The API serves SSE at `/subscriptions/game/:game-id` (REST-style endpoint, not GraphQL).

   ```clojure
   (ns bashketball-game-ui.graphql.sse-link
     "Custom Apollo link for game subscriptions over SSE.

      The bashketball-game-api uses REST-style SSE endpoints rather than
      GraphQL-over-SSE. This link intercepts subscription operations and
      connects to the appropriate SSE endpoint based on the subscription name."
     (:require [bashketball-game-ui.config :as config]
               ["@apollo/client" :as apollo]
               ["zen-observable" :as Observable]))

   (defn game-subscription-url
     "Returns SSE endpoint URL for game subscriptions."
     [game-id]
     (str config/api-base-url "/subscriptions/game/" game-id))

   (defn create-sse-link
     "Creates an Apollo link that handles subscriptions via SSE.

      Intercepts `gameUpdated` subscriptions and connects to the API's
      SSE endpoint at `/subscriptions/game/:game-id`."
     []
     (apollo/ApolloLink.
      (fn [operation forward]
        (let [definition (-> operation .-query .-definitions first)
              is-subscription? (= "subscription" (.-operation definition))]
          (if-not is-subscription?
            ;; Forward non-subscription operations to next link
            (forward operation)
            ;; Handle subscriptions via SSE
            (Observable.
             (fn [observer]
               (let [variables (js->clj (.-variables operation) :keywordize-keys true)
                     game-id (:gameId variables)
                     url (game-subscription-url game-id)
                     event-source (js/EventSource. url #js {:withCredentials true})]

                 (set! (.-onopen event-source)
                       (fn [_]
                         (js/console.log "SSE connected to" url)))

                 (set! (.-onmessage event-source)
                       (fn [event]
                         (let [data (js/JSON.parse (.-data event))]
                           ;; Transform API response to match GraphQL subscription shape
                           (.next observer #js {:data #js {:gameUpdated data}}))))

                 (set! (.-onerror event-source)
                       (fn [error]
                         (js/console.error "SSE error" error)
                         (when (= (.-readyState event-source) 2) ; CLOSED
                           (.error observer error))))

                 ;; Return cleanup function
                 (fn []
                   (js/console.log "SSE disconnecting from" url)
                   (.close event-source))))))))))
   ```

   **Apollo Client Setup** (`graphql/client.cljs`):
   ```clojure
   (ns bashketball-game-ui.graphql.client
     (:require ["@apollo/client" :as apollo]
               [bashketball-game-ui.graphql.sse-link :as sse]
               [bashketball-game-ui.config :as config]))

   (def http-link
     (apollo/createHttpLink
      #js {:uri config/api-url
           :credentials "include"}))

   (def sse-link (sse/create-sse-link))

   ;; Split link: SSE for subscriptions, HTTP for queries/mutations
   (def split-link
     (apollo/split
      (fn [op]
        (let [definition (-> op .-query .-definitions first)]
          (and (= "OperationDefinition" (.-kind definition))
               (= "subscription" (.-operation definition)))))
      sse-link
      http-link))

   (def client
     (apollo/ApolloClient.
      #js {:link split-link
           :cache (apollo/InMemoryCache.)}))
   ```

   **Benefits of SSE over WebSockets**:
   - Simpler server implementation (standard HTTP)
   - Automatic reconnection built into EventSource API
   - Works through HTTP/2 proxies and load balancers
   - Session cookies work without extra configuration
   - Unidirectional (server→client) which matches subscription pattern

   **Connection status handling**:
   - Track SSE connection state in React context
   - Show reconnecting indicator when connection drops
   - EventSource auto-reconnects with exponential backoff

### Phase 6: Polish & UX

1. **Loading states**
   - Skeleton loaders for cards
   - Spinner overlays for actions
   - Optimistic updates where safe

2. **Error handling**
   - Toast notifications for errors
   - Retry mechanisms for failed actions
   - Graceful degradation

3. **Animations**
   - Card play animations
   - Attack animations
   - Turn transition effects
   - Victory/defeat screens

4. **Responsive design**
   - Mobile-friendly game board
   - Touch-friendly card interactions
   - Orientation handling

5. **Accessibility**
   - Keyboard navigation
   - Screen reader support
   - High contrast mode

## Dependencies

### deps.edn
```clojure
{:deps
 {org.clojure/clojure {:mvn/version "1.12.2"}
  org.clojure/clojurescript {:mvn/version "1.11.132"}
  thheller/shadow-cljs {:mvn/version "2.28.21"}
  com.pitch/uix.core {:mvn/version "1.4.4"}
  com.pitch/uix.dom {:mvn/version "1.4.4"}
  metosin/malli {:mvn/version "0.19.1"}
  camel-snake-kebab/camel-snake-kebab {:mvn/version "0.4.3"}
  binaryage/devtools {:mvn/version "1.0.7"}

  ;; Shared schemas (CLJC)
  bashketball-schemas {:local/root "../bashketball-schemas"}

  ;; Game logic (CLJC - shared with API)
  bashketball-game {:local/root "../bashketball-game"}}
 :aliases
 {:test {:extra-paths ["test"]
         :extra-deps {local/cljs-tlr {:local/root "../cljs-tlr"}}}}}
```

### package.json
```json
{
  "dependencies": {
    "@apollo/client": "^3.11.0",
    "@radix-ui/react-dialog": "^1.1.0",
    "@radix-ui/react-dropdown-menu": "^2.1.0",
    "@radix-ui/react-label": "^2.1.0",
    "@radix-ui/react-select": "^2.1.0",
    "@radix-ui/react-slot": "^1.1.0",
    "@radix-ui/react-tabs": "^1.1.0",
    "@radix-ui/react-tooltip": "^1.1.0",
    "class-variance-authority": "^0.7.0",
    "clsx": "^2.1.0",
    "graphql": "^16.9.0",
    "lucide-react": "^0.460.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "react-router-dom": "^7.9.6",
    "tailwind-merge": "^2.5.0",
    "zen-observable": "^0.10.0"
  },
  "devDependencies": {
    "autoprefixer": "^10.4.0",
    "esbuild": "^0.27.0",
    "global-jsdom": "^26.0.0",
    "postcss": "^8.4.0",
    "shadow-cljs": "^2.28.21",
    "tailwindcss": "^3.4.0"
  }
}
```

Note: SSE uses the browser's native `EventSource` API. `zen-observable` provides
the Observable implementation required by Apollo's custom link API.

## API Alignment

This UI plan is aligned with `BASHKETBALL_GAME_API_PLAN.md`. Key integration points:

### GraphQL Endpoints (from API plan)

**Queries**:
- `me` - Current authenticated user (User type)
- `myDecks` - User's deck collection
- `deck(id: ID!)` - Single deck with resolved cards
- `cards(setId: ID)` - Card catalog (from cards JAR)
- `card(id: ID!)` - Single card
- `sets` - Available card sets
- `myGames(status: GameStatus)` - User's games with optional filter
- `game(id: ID!)` - Full game state
- `availableGames` - Games waiting for opponent

**Mutations**:
- `createDeck(name: String!)` / `updateDeck(id, name, cardSlugs)` / `deleteDeck(id)`
- `validateDeck(id: ID!)` - Server-side validation
- `createGame(deckId)` / `joinGame(gameId, deckId)` / `leaveGame(gameId)` / `forfeitGame(gameId)`
- `submitAction(gameId: ID!, action: GameActionInput!)` - Submit game action

**Subscriptions (via SSE)**:
- `gameUpdated(gameId: ID!)` - Real-time game state changes
- `lobbyUpdated` - New available games

### SSE Endpoint
The API serves subscriptions at REST-style endpoints:
- `/subscriptions/game/:game-id` - Game state updates

### Authentication
- Google OIDC via `oidc-google` library
- Session-based auth via `authn` middleware
- Login: `GET /auth/google/login` (redirect to Google)
- Callback handled by API, sets session cookie
- Logout: `POST /auth/logout`

### Card Data
Cards loaded from `io.github.bashketball/cards` JAR:
- Sets: `base/`, `demo-set/`
- Card types: PLAYER_CARD, STANDARD_ACTION_CARD, SPLIT_PLAY_CARD, COACHING_CARD, TEAM_ASSET_CARD
- Player cards have: slug, name, sht, pss, def, speed, size, abilities
- Action cards have: slug, name, fate, offense, defense

## Integration with bashketball-game

The `bashketball-game` package (CLJC library) provides pure game logic:

### Key Namespaces (from API plan)
- `bashketball-game.state` - `create-game`, state accessors
- `bashketball-game.actions` - `apply-action`, action handlers
- `bashketball-game.schema` - Malli schemas, `valid-action?` validation
- `bashketball-game.board` - 5x14 hex grid utilities
- `bashketball-game.event-log` - Event queries, replay support

### UI Integration Points

1. **Client-side action validation**
   ```clojure
   (require '[bashketball-game.schema :as schema])
   ;; Disable invalid action buttons
   (when (schema/valid-action? current-state proposed-action)
     (enable-action-button!))
   ```

2. **Optimistic updates**
   ```clojure
   (require '[bashketball-game.actions :as actions])
   ;; Apply action locally before server confirms
   (let [optimistic-state (actions/apply-action current-state action)]
     (set-local-state! optimistic-state)
     (submit-action-to-server! action))
   ```

3. **Board rendering helpers**
   ```clojure
   (require '[bashketball-game.board :as board])
   ;; Calculate valid move positions
   (board/valid-moves current-state player-id)
   ;; Convert hex coords to screen coords
   (board/hex->pixel {:q 3 :r 5})
   ```

4. **Game state transformation**
   - Transform server GameState to UI-friendly format
   - Derive computed properties (valid moves, targeting options)
   - Filter opponent's hidden information (hand cards)

## Testing Strategy

1. **Unit tests** - Individual components with cljs-tlr
2. **Hook tests** - Custom hooks with mock Apollo client
3. **Integration tests** - Full user flows with mock API
4. **E2E tests** - Playwright for critical paths (optional)

## Implementation Order

1. Project scaffolding and build setup
2. Core infrastructure (router, config, UI components)
3. Authentication flow
4. Basic deck list view
5. Deck builder
6. Game lobby
7. Game board (read-only)
8. Game actions
9. Real-time subscriptions
10. Polish and animations
