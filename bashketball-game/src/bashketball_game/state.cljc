(ns bashketball-game.state
  "Core state constructors and accessors for Bashketball games.

  Provides functions to create initial game state and access components
  of the game state."
  (:require [bashketball-game.board :as board]
            [clojure.string :as str]))

(defn- generate-id
  "Generates a random UUID string."
  []
  #?(:clj (str (java.util.UUID/randomUUID))
     :cljs (str (random-uuid))))

(defn- create-basketball-player
  "Creates a basketball player from a player spec.

  The spec should contain :card-slug, :name, and :stats. The player ID is
  derived from the team and card slug with an index. Uses lowercase team prefix
  for consistency with GraphQL decoder which lowercases keys."
  [team-id index {:keys [card-slug name stats abilities] :or {abilities []}}]
  (let [team-prefix (-> team-id clojure.core/name str/lower-case)
        id          (str team-prefix "-" card-slug "-" index)]
    {:id id
     :card-slug card-slug
     :name name
     :position nil
     :exhausted? false
     :stats stats
     :abilities abilities
     :modifiers []}))

(defn- create-team-roster
  "Creates a team roster from a list of player specs.

  First 3 players are starters, rest are bench."
  [team-id player-specs]
  (let [players    (map-indexed (partial create-basketball-player team-id) player-specs)
        player-map (into {} (map (juxt :id identity) players))
        player-ids (map :id players)]
    {:starters (vec (take 3 player-ids))
     :bench (vec (drop 3 player-ids))
     :players player-map}))

(defn- create-deck
  "Creates a deck from a list of card slugs."
  [card-slugs]
  {:draw-pile (vec card-slugs)
   :hand []
   :discard []
   :removed []})

(defn- create-game-player
  "Creates a game player (human/AI controlling a team)."
  [team-id {:keys [deck players]}]
  {:id team-id
   :actions-remaining 3
   :deck (create-deck deck)
   :team (create-team-roster team-id players)
   :assets []})

(defn create-game
  "Creates a new game state from the given configuration.

  Config should be a map with :home and :away keys, each containing:
  - :deck - vector of card slugs
  - :players - vector of player specs with :card-slug, :name, :stats

  Example:
  ```clojure
  (create-game
    {:home {:deck [\"drive-rebound\" \"shoot-check\" ...]
            :players [{:card-slug \"orc-center\"
                       :name \"Grukk\"
                       :stats {:size :BIG :speed 2 :shooting 2
                               :passing 1 :dribbling 1 :defense 4}}
                      ...]}
     :away {...}})
  ```"
  [config]
  {:game-id (generate-id)
   :phase :SETUP
   :turn-number 1
   :active-player :HOME
   :score {:HOME 0 :AWAY 0}
   :board (board/create-board)
   :ball {:status :LOOSE :position [2 7]}
   :players {:HOME (create-game-player :HOME (:home config))
             :AWAY (create-game-player :AWAY (:away config))}
   :stack []
   :events []
   :metadata {}})

(defn get-game-player
  "Returns the game player for the given team (:HOME or :AWAY)."
  [state team]
  (get-in state [:players team]))

(defn- get-player-from-team
  "Gets a player from a team's player map, trying both string and keyword keys."
  [state team player-id]
  (let [players (get-in state [:players team :team :players])]
    (or (get players player-id)
        (get players (keyword player-id))
        (get players (name player-id)))))

(defn get-basketball-player
  "Returns the basketball player with the given ID.

  Searches both teams for the player. Handles both string and keyword player IDs
  since JSON serialization may convert keys."
  [state player-id]
  (or (get-player-from-team state :HOME player-id)
      (get-player-from-team state :AWAY player-id)))

(defn get-basketball-player-team
  "Returns the team (:HOME or :AWAY) that the basketball player belongs to.

  Handles both string and keyword player IDs since JSON serialization may convert keys."
  [state player-id]
  (cond
    (get-player-from-team state :HOME player-id) :HOME
    (get-player-from-team state :AWAY player-id) :AWAY
    :else nil))

(defn get-ball
  "Returns the current ball state."
  [state]
  (:ball state))

(defn get-phase
  "Returns the current game phase."
  [state]
  (:phase state))

(defn get-active-player
  "Returns the currently active player (:home or :away)."
  [state]
  (:active-player state))

(defn get-score
  "Returns the score map {:home n :away m}."
  [state]
  (:score state))

(defn get-starters
  "Returns the list of starter IDs for a team."
  [state team]
  (get-in state [:players team :team :starters]))

(defn get-bench
  "Returns the list of bench player IDs for a team."
  [state team]
  (get-in state [:players team :team :bench]))

(defn get-hand
  "Returns the hand (list of card slugs) for a team."
  [state team]
  (get-in state [:players team :deck :hand]))

(defn get-draw-pile
  "Returns the draw pile (list of card slugs) for a team."
  [state team]
  (get-in state [:players team :deck :draw-pile]))

(defn get-discard
  "Returns the discard pile (list of card slugs) for a team."
  [state team]
  (get-in state [:players team :deck :discard]))

(defn- find-player-key
  "Finds the actual key used for a player in the team's player map.

  Returns the key (string or keyword) that matches the player-id, or nil."
  [state team player-id]
  (let [players (get-in state [:players team :team :players])]
    (cond
      (contains? players player-id) player-id
      (contains? players (keyword player-id)) (keyword player-id)
      (contains? players (name player-id)) (name player-id)
      :else nil)))

(defn update-basketball-player
  "Updates a basketball player by applying f to the player map.

  Handles both string and keyword player IDs since JSON serialization may convert keys."
  [state player-id f & args]
  (if-let [team (get-basketball-player-team state player-id)]
    (if-let [actual-key (find-player-key state team player-id)]
      (apply update-in state [:players team :team :players actual-key] f args)
      state)
    state))
