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
  derived from the team and card slug with an index (e.g., 'HOME-michael-jordan-0')."
  [team-id index {:keys [card-slug name stats abilities] :or {abilities []}}]
  (let [team-prefix (clojure.core/name team-id)
        id          (str team-prefix "-" card-slug "-" index)]
    {:id id
     :card-slug card-slug
     :name name
     :position nil
     :exhausted false
     :stats stats
     :abilities abilities
     :modifiers []
     :attachments []}))

(defn- create-team-roster
  "Creates a team roster from a list of player specs.

  All players start without positions. During setup phase, exactly 3 players
  can be placed on the court."
  [team-id player-specs]
  (let [players    (map-indexed (partial create-basketball-player team-id) player-specs)
        player-map (into {} (map (juxt :id identity) players))]
    {:players player-map}))

(defn- create-card-instance
  "Creates a card instance with a unique identifier."
  [card-slug]
  {:instance-id (generate-id)
   :card-slug card-slug})

(defn- create-deck
  "Creates a deck from a list of card slugs, assigning unique instance IDs."
  [card-slugs]
  {:draw-pile (mapv create-card-instance card-slugs)
   :hand []
   :discard []
   :removed []
   :examined []})

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
                       :stats {:size :size/LG :speed 2 :shooting 2
                               :passing 1 :defense 4}}
                      ...]}
     :away {...}})
  ```"
  [config]
  {:game-id (generate-id)
   :phase :phase/SETUP
   :turn-number 1
   :quarter 1
   :active-player :team/HOME
   :score {:team/HOME 0 :team/AWAY 0}
   :board (board/create-board)
   :ball {:status :ball-status/LOOSE :position [2 7]}
   :players {:team/HOME (create-game-player :team/HOME (:home config))
             :team/AWAY (create-game-player :team/AWAY (:away config))}
   :play-area []
   :stack []
   :events []
   :metadata {}})

(defn get-game-player
  "Returns the game player for the given team (:team/HOME or :team/AWAY)."
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
  (or (get-player-from-team state :team/HOME player-id)
      (get-player-from-team state :team/AWAY player-id)))

(defn get-basketball-player-team
  "Returns the team (:team/HOME or :team/AWAY) that the basketball player belongs to.

  Handles both string and keyword player IDs since JSON serialization may convert keys."
  [state player-id]
  (cond
    (get-player-from-team state :team/HOME player-id) :team/HOME
    (get-player-from-team state :team/AWAY player-id) :team/AWAY
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

(defn get-all-players
  "Returns the map of all players for a team."
  [state team]
  (get-in state [:players team :team :players]))

(defn get-on-court-players
  "Returns a vector of player IDs that are on the court (have a position)."
  [state team]
  (->> (get-all-players state team)
       vals
       (filter :position)
       (map :id)
       vec))

(defn get-off-court-players
  "Returns a vector of player IDs that are off the court (no position)."
  [state team]
  (->> (get-all-players state team)
       vals
       (remove :position)
       (map :id)
       vec))

(defn get-hand
  "Returns the hand (list of card instances) for a team."
  [state team]
  (get-in state [:players team :deck :hand]))

(defn get-draw-pile
  "Returns the draw pile (list of card instances) for a team."
  [state team]
  (get-in state [:players team :deck :draw-pile]))

(defn get-discard
  "Returns the discard pile (list of card instances) for a team."
  [state team]
  (get-in state [:players team :deck :discard]))

(defn get-examined
  "Returns the examined zone (list of card instances) for a team."
  [state team]
  (get-in state [:players team :deck :examined]))

(defn get-play-area
  "Returns the shared play area cards."
  [state]
  (or (:play-area state) []))

(defn find-card-in-play-area
  "Finds a card in the play area by instance-id. Returns nil if not found."
  [state instance-id]
  (first (filter #(= (:instance-id %) instance-id) (get-play-area state))))

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

(defn get-attachments
  "Returns the attachments for a basketball player."
  [state player-id]
  (:attachments (get-basketball-player state player-id)))

(defn find-attachment
  "Finds an attachment on a basketball player by instance-id. Returns nil if not found."
  [state player-id instance-id]
  (first (filter #(= (:instance-id %) instance-id)
                 (get-attachments state player-id))))

(defn has-attachment?
  "Returns true if the player has an attachment with the given instance-id."
  [state player-id instance-id]
  (some? (find-attachment state player-id instance-id)))

(defn token?
  "Returns true if the card instance is a token."
  [card-instance]
  (true? (:token card-instance)))

(defn get-token-card
  "Returns the inline card definition from a token instance."
  [token-instance]
  (:card token-instance))

(defn get-ability-card-properties
  "Looks up `:removable` and `:detach-destination` for an ability card.

  Searches the player's card definitions for a card matching `card-slug`
  and extracts attachment properties. Returns a map with defaults applied
  if the card or properties are not found:

  - `:removable` defaults to `true`
  - `:detach-destination` defaults to `:detach/DISCARD`"
  [state player card-slug]
  (let [cards (get-in state [:players player :deck :cards])
        card  (some #(when (= (:slug %) card-slug) %) cards)]
    {:removable          (get card :removable true)
     :detach-destination (get card :detach-destination :detach/DISCARD)}))

;;; ---------------------------------------------------------------------------
;;; Pending Movement Accessors
;;; ---------------------------------------------------------------------------

(defn get-pending-movement
  "Returns the pending movement context, or nil if no movement in progress."
  [state]
  (:pending-movement state))

(defn set-pending-movement
  "Sets the pending movement context."
  [state movement]
  (assoc state :pending-movement movement))

(defn clear-pending-movement
  "Clears the pending movement context."
  [state]
  (dissoc state :pending-movement))

(defn update-pending-movement
  "Updates the pending movement context by applying f with args."
  [state f & args]
  (apply update state :pending-movement f args))
