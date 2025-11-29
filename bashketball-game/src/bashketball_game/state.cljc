(ns bashketball-game.state
  "Core state constructors and accessors for Bashketball games.

  Provides functions to create initial game state and access components
  of the game state."
  (:require [bashketball-game.board :as board]))

(defn- generate-id
  "Generates a random UUID string."
  []
  #?(:clj (str (java.util.UUID/randomUUID))
     :cljs (str (random-uuid))))

(defn- create-basketball-player
  "Creates a basketball player from a player spec.

  The spec should contain :card-slug, :name, and :stats. The player ID is
  derived from the team and card slug with an index."
  [team-id index {:keys [card-slug name stats abilities] :or {abilities []}}]
  (let [id (str (clojure.core/name team-id) "-" card-slug "-" index)]
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
                       :stats {:size :big :speed 2 :shooting 2
                               :passing 1 :dribbling 1 :defense 4}}
                      ...]}
     :away {...}})
  ```"
  [config]
  {:game-id (generate-id)
   :phase :setup
   :turn-number 1
   :active-player :home
   :score {:home 0 :away 0}
   :board (board/create-board)
   :ball {:status :loose :position [2 7]}
   :players {:home (create-game-player :home (:home config))
             :away (create-game-player :away (:away config))}
   :stack []
   :events []
   :metadata {}})

(defn get-game-player
  "Returns the game player for the given team (:home or :away)."
  [state team]
  (get-in state [:players team]))

(defn get-basketball-player
  "Returns the basketball player with the given ID.

  Searches both teams for the player."
  [state player-id]
  (or (get-in state [:players :home :team :players player-id])
      (get-in state [:players :away :team :players player-id])))

(defn get-basketball-player-team
  "Returns the team (:home or :away) that the basketball player belongs to."
  [state player-id]
  (cond
    (get-in state [:players :home :team :players player-id]) :home
    (get-in state [:players :away :team :players player-id]) :away
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

(defn update-basketball-player
  "Updates a basketball player by applying f to the player map."
  [state player-id f & args]
  (if-let [team (get-basketball-player-team state player-id)]
    (apply update-in state [:players team :team :players player-id] f args)
    state))
