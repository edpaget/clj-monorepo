(ns bashketball-game.polix.phase-policies
  "Phase transition policies and game structure constants.

  Defines the valid phase transitions, turn structure (12 turns per quarter,
  4 quarters per game), and action costs for the main phase.")

(def hand-limit
  "Maximum cards in hand before discarding is required."
  8)

(def turns-per-quarter
  "Number of turns in each quarter."
  12)

(def quarters-per-game
  "Number of quarters in a game."
  4)

(def valid-transitions
  "Map of current phase to set of allowed next phases.

  The game follows this flow:
  SETUP -> TIP_OFF -> UPKEEP -> ACTIONS -> END_OF_TURN -> (UPKEEP or GAME_OVER)"
  {:phase/SETUP       #{:phase/TIP_OFF}
   :phase/TIP_OFF     #{:phase/UPKEEP}
   :phase/UPKEEP      #{:phase/ACTIONS}
   :phase/ACTIONS     #{:phase/END_OF_TURN}
   :phase/END_OF_TURN #{:phase/UPKEEP :phase/GAME_OVER}})

(defn valid-transition?
  "Returns true if transitioning from `from-phase` to `to-phase` is allowed."
  [from-phase to-phase]
  (contains? (get valid-transitions from-phase) to-phase))

(def action-costs
  "Fuel costs for actions during the main (ACTIONS) phase.

  - :move - Free action, no fuel cost
  - :play-card - Requires discarding 1 card as fuel
  - :standard-action - Requires discarding 3 cards as fuel (virtual action)"
  {:move 0
   :play-card 1
   :standard-action 3})
