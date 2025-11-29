# bashketball-game

A CLJC library for tracking and manipulating Bashketball game state. Bashketball is a trading card game simulating 3x3 basketball with fantasy creatures.

This library provides low-level state mutations without rule enforcement - consumers are responsible for validating game rules and persisting state.

## Features

- **Pure functions** - All state mutations return new state, no side effects
- **Data-driven actions** - Single `apply-action` function, actions are maps with `:type`
- **Schema-validated** - All actions validated via Malli multi-schema before application
- **Event log** - All mutations append to an event history for replay
- **Cross-platform** - Works in both Clojure (JVM) and ClojureScript (Node.js)

## Usage

```clojure
(require '[bashketball-game.state :as state]
         '[bashketball-game.actions :as actions])

;; Create initial game
(def game (state/create-game
           {:home {:deck ["drive-rebound" "shoot-check" "pass-steal"]
                   :players [{:card-slug "orc-center"
                              :name "Grukk"
                              :stats {:size :big :speed 2 :shooting 2
                                      :passing 1 :dribbling 1 :defense 4}}
                             {:card-slug "elf-point-guard"
                              :name "Lyria"
                              :stats {:size :small :speed 5 :shooting 3
                                      :passing 4 :dribbling 3 :defense 2}}
                             {:card-slug "dwarf-power-forward"
                              :name "Thorin"
                              :stats {:size :mid :speed 2 :shooting 3
                                      :passing 2 :dribbling 2 :defense 4}}]}
            :away {:deck ["drive-rebound" "shoot-check" "pass-steal"]
                   :players [{:card-slug "troll-center"
                              :name "Grok"
                              :stats {:size :big :speed 1 :shooting 1
                                      :passing 1 :dribbling 1 :defense 5}}
                             {:card-slug "goblin-shooting-guard"
                              :name "Sneek"
                              :stats {:size :small :speed 4 :shooting 4
                                      :passing 3 :dribbling 3 :defense 1}}
                             {:card-slug "human-small-forward"
                              :name "John"
                              :stats {:size :mid :speed 3 :shooting 3
                                      :passing 3 :dribbling 3 :defense 3}}]}}))

;; Apply actions via data maps
(-> game
    (actions/apply-action {:type :bashketball/set-phase :phase :actions})
    (actions/apply-action {:type :bashketball/draw-cards :player :home :count 5})
    (actions/apply-action {:type :bashketball/move-player
                           :player-id "home-orc-center-0"
                           :position [2 3]})
    (actions/apply-action {:type :bashketball/set-ball-possessed
                           :holder-id "home-orc-center-0"})
    (actions/apply-action {:type :bashketball/add-score :team :home :points 2}))

;; Events are logged automatically
(:events game)
;; => [{:type :bashketball/set-phase :phase :actions :timestamp "..."}
;;     {:type :bashketball/draw-cards :player :home :count 5 :timestamp "..."}
;;     ...]
```

## Action Types

| Type | Description |
|------|-------------|
| `:bashketball/set-phase` | Change game phase |
| `:bashketball/advance-turn` | Increment turn, switch active player |
| `:bashketball/set-active-player` | Switch priority |
| `:bashketball/set-actions` | Set action points |
| `:bashketball/draw-cards` | Draw from deck |
| `:bashketball/discard-cards` | Discard from hand |
| `:bashketball/shuffle-deck` | Randomize draw pile |
| `:bashketball/move-player` | Move basketball player |
| `:bashketball/exhaust-player` | Mark exhausted |
| `:bashketball/refresh-player` | Clear exhausted |
| `:bashketball/add-modifier` | Add stat modifier |
| `:bashketball/remove-modifier` | Remove modifier |
| `:bashketball/substitute` | Swap starter/bench |
| `:bashketball/set-ball-possessed` | Ball to player |
| `:bashketball/set-ball-loose` | Ball to position |
| `:bashketball/set-ball-in-air` | Ball mid-flight |
| `:bashketball/add-score` | Add points |
| `:bashketball/push-stack` | Add to stack |
| `:bashketball/pop-stack` | Remove from stack |
| `:bashketball/reveal-fate` | Reveal top card |
| `:bashketball/record-skill-test` | Log skill test |

## Board

The game uses a 5x14 hex grid with axial coordinates `[q r]`:
- `q`: 0-4 (width)
- `r`: 0-13 (length)
- Home hoop at `[2 0]`, away hoop at `[2 13]`

## Development

```bash
# Run JVM tests
clojure -X:test

# Run ClojureScript tests
npm install
npm test

# Watch mode
npm run test:watch
```

## Namespaces

| Namespace | Description |
|-----------|-------------|
| `bashketball-game.schema` | Malli schemas for state and actions |
| `bashketball-game.state` | State constructors and accessors |
| `bashketball-game.board` | Hex grid utilities |
| `bashketball-game.actions` | `apply-action` and multimethod handlers |
| `bashketball-game.event-log` | Event querying and replay |
