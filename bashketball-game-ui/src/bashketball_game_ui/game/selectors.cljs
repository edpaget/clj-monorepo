(ns bashketball-game-ui.game.selectors
  "Pure functions for extracting and computing game data.

  All functions are pure and independently testable. These selectors extract
  derived data from the game state without any React dependencies.")

(defn opponent-team
  "Returns the opponent's team keyword given the player's team."
  [my-team]
  (if (= my-team :team/HOME) :team/AWAY :team/HOME))

(defn- phase->keyword
  "Converts a phase value to keyword, handling both string and keyword inputs."
  [phase]
  (if (keyword? phase)
    phase
    (keyword phase)))

(defn setup-mode?
  "Returns true if the game is in setup phase."
  [phase]
  (= (phase->keyword phase) :phase/SETUP))

(defn tip-off-mode?
  "Returns true if the game is in tip-off phase."
  [phase]
  (= (phase->keyword phase) :phase/TIP_OFF))

(defn get-player-by-id
  "Looks up a player by ID, handling both string and keyword keys."
  [players id]
  (or (get players id)
      (get players (keyword id))))

(defn valid-pass-targets
  "Returns set of player IDs that can receive a pass.

  Excludes the current ball holder from valid targets."
  [my-players ball-holder-id]
  (when (and ball-holder-id my-players)
    (->> (vals my-players)
         (remove #(= (:id %) ball-holder-id))
         (map :id)
         set)))

(defn setup-placed-count
  "Returns count of players that have been placed on the board."
  [my-players]
  (when my-players
    (->> (vals my-players)
         (filter :position)
         count)))

(defn team-setup-complete?
  "Returns true if exactly 3 players have been placed on the court."
  [game-state team]
  (let [players (get-in game-state [:players team :team :players])]
    (= 3 (count (filter :position (vals players))))))

(defn both-teams-setup-complete?
  "Returns true if both HOME and AWAY teams have placed 3 players on court."
  [game-state]
  (and (team-setup-complete? game-state :team/HOME)
       (team-setup-complete? game-state :team/AWAY)))

(defn target-basket
  "Returns the target basket position for shooting based on team."
  [my-team]
  (if (= my-team :team/HOME) [2 13] [2 0]))

(defn my-player-data
  "Extracts all player-related data from game state for the given team.

  Returns a map with :my-player, :my-players, and :my-hand."
  [game-state my-team]
  (let [my-player (get-in game-state [:players my-team])]
    {:my-player  my-player
     :my-players (get-in my-player [:team :players])
     :my-hand    (get-in my-player [:deck :hand])}))

(defn opponent-data
  "Extracts opponent player data from game state."
  [game-state opponent-team]
  (get-in game-state [:players opponent-team]))

(defn all-players
  "Returns home and away player maps for the hex grid."
  [game-state]
  {:home-players (get-in game-state [:players :team/HOME :team :players])
   :away-players (get-in game-state [:players :team/AWAY :team :players])})

(def phase-labels
  {:phase/SETUP       "Setup"
   :phase/TIP_OFF     "Tip-Off"
   :phase/UPKEEP      "Upkeep"
   :phase/DRAW        "Draw"
   :phase/ACTIONS     "Actions"
   :phase/RESOLUTION  "Resolution"
   :phase/END_OF_TURN "End of Turn"
   :phase/GAME_OVER   "Game Over"})

(defn phase-label
  "Returns human-readable label for a phase."
  [phase]
  (get phase-labels (keyword phase) (str phase)))

(defn next-phase
  "Returns the next phase in sequence, or nil if at terminal state.

  Phase flow: UPKEEP → ACTIONS → RESOLUTION → END_OF_TURN → UPKEEP (loop)
  SETUP and TIP_OFF use special buttons instead.
  GAME_OVER is terminal."
  [current-phase]
  (let [phase-kw (if (keyword? current-phase)
                   current-phase
                   (keyword current-phase))]
    (case phase-kw
      :phase/UPKEEP      :phase/ACTIONS
      :phase/ACTIONS     :phase/RESOLUTION
      :phase/RESOLUTION  :phase/END_OF_TURN
      :phase/END_OF_TURN :phase/UPKEEP
      :phase/SETUP       nil
      :phase/TIP_OFF     nil
      :phase/GAME_OVER   nil
      nil)))

(defn can-advance-phase?
  "Returns true if the phase can be advanced via Next Phase button."
  [phase]
  (some? (next-phase phase)))

(defn build-player-index-map
  "Builds a map of player-id -> 1-based index for a team.

  Sorts players by ID to ensure consistent ordering across components."
  [players-map]
  (->> (keys players-map)
       sort
       (map-indexed (fn [idx id] [id (inc idx)]))
       (into {})))

(defn deck-stats
  "Returns deck statistics for a team: draw pile, hand, discard, and removed counts."
  [game-state team]
  (let [deck (get-in game-state [:players team :deck])]
    {:draw-pile-count (count (:draw-pile deck))
     :hand-count      (count (:hand deck))
     :discard-count   (count (:discard deck))
     :removed-count   (count (:removed deck))}))
