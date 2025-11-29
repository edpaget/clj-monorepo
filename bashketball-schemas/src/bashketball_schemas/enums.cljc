(ns bashketball-schemas.enums
  "Shared enumerations for the Bashketball ecosystem.

   Provides Malli enum schemas used across game logic, APIs, and UIs.
   All enums use namespaced keywords for clarity and to avoid conflicts.")

(def CardType
  "Card type enumeration for all Bashketball card types.

   - `:card-type/PLAYER_CARD` - Basketball players with stats
   - `:card-type/ABILITY_CARD` - Special ability cards
   - `:card-type/PLAY_CARD` - Single play action cards
   - `:card-type/STANDARD_ACTION_CARD` - Cards available to all players
   - `:card-type/SPLIT_PLAY_CARD` - Cards with offense/defense effects
   - `:card-type/COACHING_CARD` - Team-wide effect cards
   - `:card-type/TEAM_ASSET_CARD` - Persistent team resource cards"
  [:enum {:graphql/type :CardType}
   :card-type/PLAYER_CARD
   :card-type/ABILITY_CARD
   :card-type/PLAY_CARD
   :card-type/STANDARD_ACTION_CARD
   :card-type/SPLIT_PLAY_CARD
   :card-type/COACHING_CARD
   :card-type/TEAM_ASSET_CARD])

(def Size
  "Player size enumeration.

   - `:size/SM` - Small (guards)
   - `:size/MD` - Medium (forwards)
   - `:size/LG` - Large (centers)"
  [:enum {:graphql/type :PlayerSize}
   :size/SM :size/MD :size/LG])

(def GameStatus
  "Game lifecycle status.

   - `:game-status/WAITING` - Created, waiting for opponent
   - `:game-status/ACTIVE` - Game in progress
   - `:game-status/COMPLETED` - Game finished with winner
   - `:game-status/ABANDONED` - Game cancelled"
  [:enum {:graphql/type :GameStatus}
   :game-status/WAITING
   :game-status/ACTIVE
   :game-status/COMPLETED
   :game-status/ABANDONED])

(def Team
  "Team identifier for home/away distinction."
  [:enum {:graphql/type :Team}
   :team/HOME :team/AWAY])

(def GamePhase
  "Turn phase enumeration for game flow.

   - `:phase/SETUP` - Initial game setup
   - `:phase/UPKEEP` - Start of turn maintenance
   - `:phase/ACTIONS` - Main action phase
   - `:phase/RESOLUTION` - Resolve pending effects
   - `:phase/END_OF_TURN` - End of turn cleanup
   - `:phase/GAME_OVER` - Game has ended"
  [:enum {:graphql/type :GamePhase}
   :phase/SETUP
   :phase/UPKEEP
   :phase/ACTIONS
   :phase/RESOLUTION
   :phase/END_OF_TURN
   :phase/GAME_OVER])

(def BallStatus
  "Ball state enumeration.

   - `:ball-status/POSSESSED` - Ball held by a player
   - `:ball-status/LOOSE` - Ball on the court, not held
   - `:ball-status/IN_AIR` - Ball in flight (shot or pass)"
  [:enum {:graphql/type :BallStatus}
   :ball-status/POSSESSED
   :ball-status/LOOSE
   :ball-status/IN_AIR])

(def BallActionType
  "Type of ball movement when in air."
  [:enum {:graphql/type :BallActionType}
   :ball-action/SHOT :ball-action/PASS])

(defn enum-values
  "Extracts the keyword values from a Malli enum schema.

   Returns a vector of the enum values, excluding the `:enum` marker
   and any properties map."
  [enum-schema]
  (let [children (rest enum-schema)]
    (if (map? (first children))
      (vec (rest children))
      (vec children))))

(defn enum->options
  "Converts a Malli enum to a vector of option maps for UI selects.

   Each option has `:value` (the keyword name as string) and `:label`
   (a human-readable version)."
  [enum-schema]
  (mapv (fn [k]
          {:value (name k)
           :label (-> k name
                      (clojure.string/replace "_" " ")
                      clojure.string/lower-case
                      clojure.string/capitalize)})
        (enum-values enum-schema)))
