# bashketball-schemas

Shared Malli schemas for the Bashketball trading card game ecosystem.

## Overview

This library provides domain schemas used across Bashketball applications:
- **bashketball-editor-api** - Card editor GraphQL API
- **bashketball-editor-ui** - Card editor web UI
- **bashketball-game-api** - Game server API
- **bashketball-game-ui** - Game client UI
- **bashketball-game** - Core game logic

All schemas include `:graphql/type` metadata for seamless GraphQL integration with the `graphql-server` library.

## Installation

Add to your `deps.edn`:

```clojure
{:deps {local/bashketball-schemas {:local/root "../bashketball-schemas"}}}
```

## Usage

```clojure
(require '[bashketball-schemas.card :as card])
(require '[bashketball-schemas.enums :as enums])
(require '[malli.core :as m])

;; Validate a card
(m/validate card/PlayerCard
  {:slug "jordan"
   :name "Michael Jordan"
   :set-slug "legends"
   :card-type :card-type/PLAYER_CARD
   :sht 9 :pss 7 :def 8 :speed 9
   :size :size/MD
   :abilities ["Clutch" "Fadeaway"]})

;; Get enum values for UI dropdowns
(enums/enum-values enums/CardType)
;; => [:card-type/PLAYER_CARD :card-type/ABILITY_CARD ...]

;; Multi-schema validates any card type
(m/validate card/Card some-card)
```

## Schemas

### Enums (`bashketball-schemas.enums`)

| Schema | GraphQL Type | Values |
|--------|--------------|--------|
| `CardType` | `CardType` | `PLAYER_CARD`, `ABILITY_CARD`, `PLAY_CARD`, `STANDARD_ACTION_CARD`, `SPLIT_PLAY_CARD`, `COACHING_CARD`, `TEAM_ASSET_CARD` |
| `Size` | `PlayerSize` | `SM`, `MD`, `LG` |
| `GameStatus` | `GameStatus` | `WAITING`, `ACTIVE`, `COMPLETED`, `ABANDONED` |
| `Team` | `Team` | `HOME`, `AWAY` |
| `GamePhase` | `GamePhase` | `SETUP`, `UPKEEP`, `ACTIONS`, `RESOLUTION`, `END_OF_TURN`, `GAME_OVER` |
| `BallStatus` | `BallStatus` | `POSSESSED`, `LOOSE`, `IN_AIR` |
| `BallActionType` | `BallActionType` | `SHOT`, `PASS` |

Utility functions:
- `(enum-values schema)` - Extract keyword values from an enum
- `(enum->options schema)` - Convert to `[{:value :label}]` for UI selects

### Cards (`bashketball-schemas.card`)

| Schema | GraphQL Type | Description |
|--------|--------------|-------------|
| `BaseCard` | `Card` (interface) | Common fields for all cards |
| `PlayerCard` | `PlayerCard` | Basketball player with stats |
| `AbilityCard` | `AbilityCard` | Special ability card |
| `PlayCard` | `PlayCard` | Single play action |
| `StandardActionCard` | `StandardActionCard` | Actions available to all players |
| `SplitPlayCard` | `SplitPlayCard` | Offense/defense split card |
| `CoachingCard` | `CoachingCard` | Team-wide effect |
| `TeamAssetCard` | `TeamAssetCard` | Persistent team resource |
| `Card` | `GameCard` (union) | Multi-schema dispatched on `:card-type` |
| `CardSet` | `CardSet` | Card set metadata |

### Users (`bashketball-schemas.user`)

| Schema | Description |
|--------|-------------|
| `Email` | Email address string |
| `GoogleId` | Google OAuth subject ID |
| `GitHubId` | GitHub user ID (integer) |
| `User` | User with optional OAuth identifiers |

## GraphQL Integration

All schemas include `:graphql/type` or `:graphql/interface` metadata. When used with `graphql-server`, schemas automatically generate the corresponding GraphQL types:

```clojure
;; Enum with GraphQL type
[:enum {:graphql/type :CardType}
 :card-type/PLAYER_CARD
 :card-type/ABILITY_CARD
 ...]

;; Map with GraphQL type
[:map {:graphql/type :PlayerCard}
 [:slug ...]
 [:name ...]
 ...]
```

## Running Tests

```bash
clojure -X:test
```

## License

Proprietary - Bashketball
