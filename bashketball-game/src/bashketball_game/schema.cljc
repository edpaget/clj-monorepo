(ns bashketball-game.schema
  "Malli schemas for Bashketball game state and actions.

  Defines all data structures used in the game engine, including the game state,
  board, players, ball, and the multi-schema for action validation."
  (:require [malli.core :as m]))

;; -----------------------------------------------------------------------------
;; Primitive Schemas

(def HexPosition
  "Axial coordinates for hex grid position [q r]."
  [:tuple {:graphql/scalar :HexPosition} [:int {:min 0 :max 4}] [:int {:min 0 :max 13}]])

(def Team
  "Game player identifier (the human controlling a team)."
  [:enum {:graphql/type :Team} :HOME :AWAY])

(def Phase
  "Game phase."
  [:enum {:graphql/type :Phase} :SETUP :UPKEEP :ACTIONS :RESOLUTION :END_OF_TURN :GAME_OVER])

(def Size
  "Basketball player size category."
  [:enum {:graphql/type :Size} :SMALL :MID :BIG])

(def Stat
  "Basketball player stat names."
  [:enum {:graphql/type :Stat} :SPEED :SHOOTING :PASSING :DRIBBLING :DEFENSE])

(def BallActionType
  "Type of ball action when in-air."
  [:enum {:graphql/type :BallActionType} :SHOT :PASS])

(def Terrain
  "Board tile terrain types."
  [:enum {:graphql/type :Terrain} :COURT :THREE_POINT_LINE :PAINT :HOOP])

;; -----------------------------------------------------------------------------
;; Component Schemas

(def Modifier
  "Temporary stat modifier applied to a basketball player."
  [:map {:graphql/type :Modifier}
   [:id :string]
   [:stat Stat]
   [:amount :int]
   [:source {:optional true} :string]
   [:expires-at {:optional true} :int]])

(def PlayerStats
  "Basketball player stats."
  [:map {:graphql/type :PlayerStats}
   [:size Size]
   [:speed :int]
   [:shooting :int]
   [:passing :int]
   [:dribbling :int]
   [:defense :int]])

(def BasketballPlayer
  "A basketball player on a team."
  [:map {:graphql/type :BasketballPlayer}
   [:id :string]
   [:card-slug :string]
   [:name :string]
   [:position {:optional true} [:maybe HexPosition]]
   [:exhausted? :boolean]
   [:stats PlayerStats]
   [:abilities [:vector :string]]
   [:modifiers [:vector Modifier]]])

(def TeamRoster
  "A team's roster of basketball players."
  [:map {:graphql/type :TeamRoster}
   [:starters [:vector :string]]
   [:bench [:vector :string]]
   [:players [:map-of :string BasketballPlayer]]])

(def CardInstance
  "A single instance of a card in a deck, with unique identifier."
  [:map {:graphql/type :CardInstance}
   [:instance-id :string]
   [:card-slug :string]])

(def DeckState
  "A game player's deck state during a game."
  [:map {:graphql/type :DeckState}
   [:draw-pile [:vector CardInstance]]
   [:hand [:vector CardInstance]]
   [:discard [:vector CardInstance]]
   [:removed [:vector CardInstance]]])

(def GamePlayer
  "A game player (human/AI controlling a team)."
  [:map {:graphql/type :GamePlayer}
   [:id Team]
   [:actions-remaining :int]
   [:deck DeckState]
   [:team TeamRoster]
   [:assets [:vector :string]]])

(def Tile
  "A board tile."
  [:map {:graphql/type :Tile}
   [:terrain Terrain]
   [:side {:optional true} Team]])

(def OccupantType
  "Type of occupant on a board tile."
  [:enum {:graphql/type :OccupantType} :BASKETBALL_PLAYER :BALL])

(def Occupant
  "An occupant of a board tile."
  [:map {:graphql/type :Occupant}
   [:type OccupantType]
   [:id {:optional true} :string]])

(def Board
  "The game board (5x14 hex grid)."
  [:map {:graphql/type :Board}
   [:width :int]
   [:height :int]
   [:tiles [:map-of [:tuple :int :int] Tile]]
   [:occupants [:map-of [:tuple :int :int] Occupant]]])

(def BallPossessed
  "Ball state when held by a player."
  [:map {:graphql/type :BallPossessed}
   [:status [:= :POSSESSED]]
   [:holder-id :string]])

(def BallLoose
  "Ball state when on the ground."
  [:map {:graphql/type :BallLoose}
   [:status [:= :LOOSE]]
   [:position HexPosition]])

(def BallTarget
  "Target for a ball in air - either a position or player ID."
  [:or {:graphql/scalar :String} HexPosition :string])

(def BallInAir
  "Ball state when in flight."
  [:map {:graphql/type :BallInAir}
   [:status [:= :IN_AIR]]
   [:origin HexPosition]
   [:target BallTarget]
   [:action-type BallActionType]])

(def Ball
  "Ball state (one of three states)."
  [:multi {:dispatch :status :graphql/type :Ball}
   [:POSSESSED BallPossessed]
   [:LOOSE BallLoose]
   [:IN_AIR BallInAir]])

(def StackEffect
  "An effect on the resolution stack."
  [:map {:graphql/type :StackEffect}
   [:id :string]
   [:type :keyword]
   [:source {:optional true} :string]
   [:data {:optional true} :map]])

(def Event
  "A logged game event."
  [:map {:graphql/type :Event}
   [:type :keyword]
   [:timestamp :string]
   [:data {:optional true} :map]])

(def Score
  "Game score for both teams."
  [:map {:graphql/type :Score}
   [:HOME {:graphql/name :HOME} :int]
   [:AWAY {:graphql/name :AWAY} :int]])

(def Players
  "Both players in a game."
  [:map {:graphql/type :Players}
   [:HOME {:graphql/name :HOME} GamePlayer]
   [:AWAY {:graphql/name :AWAY} GamePlayer]])

(def GameState
  "The complete game state."
  [:map {:graphql/type :GameState}
   [:game-id :string]
   [:phase Phase]
   [:turn-number :int]
   [:active-player Team]
   [:score Score]
   [:board Board]
   [:ball Ball]
   [:players Players]
   [:stack [:vector StackEffect]]
   [:events [:vector Event]]
   [:metadata :map]])

;; -----------------------------------------------------------------------------
;; Action Schemas

(def SetPhaseAction
  [:map
   [:type [:= :bashketball/set-phase]]
   [:phase Phase]])

(def AdvanceTurnAction
  [:map
   [:type [:= :bashketball/advance-turn]]])

(def SetActivePlayerAction
  [:map
   [:type [:= :bashketball/set-active-player]]
   [:player Team]])

(def SetActionsAction
  [:map
   [:type [:= :bashketball/set-actions]]
   [:player Team]
   [:amount :int]])

(def DrawCardsAction
  [:map
   [:type [:= :bashketball/draw-cards]]
   [:player Team]
   [:count pos-int?]])

(def DiscardCardsAction
  [:map
   [:type [:= :bashketball/discard-cards]]
   [:player Team]
   [:instance-ids [:vector :string]]])

(def RemoveCardsAction
  [:map
   [:type [:= :bashketball/remove-cards]]
   [:player Team]
   [:instance-ids [:vector :string]]])

(def ShuffleDeckAction
  [:map
   [:type [:= :bashketball/shuffle-deck]]
   [:player Team]])

(def ReturnDiscardAction
  [:map
   [:type [:= :bashketball/return-discard]]
   [:player Team]])

(def MovePlayerAction
  [:map
   [:type [:= :bashketball/move-player]]
   [:player-id :string]
   [:position HexPosition]])

(def ExhaustPlayerAction
  [:map
   [:type [:= :bashketball/exhaust-player]]
   [:player-id :string]])

(def RefreshPlayerAction
  [:map
   [:type [:= :bashketball/refresh-player]]
   [:player-id :string]])

(def RefreshAllAction
  [:map
   [:type [:= :bashketball/refresh-all]]
   [:team Team]])

(def AddModifierAction
  [:map
   [:type [:= :bashketball/add-modifier]]
   [:player-id :string]
   [:modifier Modifier]])

(def RemoveModifierAction
  [:map
   [:type [:= :bashketball/remove-modifier]]
   [:player-id :string]
   [:modifier-id :string]])

(def ClearModifiersAction
  [:map
   [:type [:= :bashketball/clear-modifiers]]
   [:player-id :string]])

(def SubstituteAction
  [:map
   [:type [:= :bashketball/substitute]]
   [:starter-id :string]
   [:bench-id :string]])

(def SetBallPossessedAction
  [:map
   [:type [:= :bashketball/set-ball-possessed]]
   [:holder-id :string]])

(def SetBallLooseAction
  [:map
   [:type [:= :bashketball/set-ball-loose]]
   [:position HexPosition]])

(def SetBallInAirAction
  [:map
   [:type [:= :bashketball/set-ball-in-air]]
   [:origin HexPosition]
   [:target BallTarget]
   [:action-type BallActionType]])

(def AddScoreAction
  [:map
   [:type [:= :bashketball/add-score]]
   [:team Team]
   [:points pos-int?]])

(def PushStackAction
  [:map
   [:type [:= :bashketball/push-stack]]
   [:effect StackEffect]])

(def PopStackAction
  [:map
   [:type [:= :bashketball/pop-stack]]])

(def ClearStackAction
  [:map
   [:type [:= :bashketball/clear-stack]]])

(def RevealFateAction
  [:map
   [:type [:= :bashketball/reveal-fate]]
   [:player Team]])

(def RecordSkillTestAction
  [:map
   [:type [:= :bashketball/record-skill-test]]
   [:player-id :string]
   [:stat Stat]
   [:base :int]
   [:fate :int]
   [:modifiers [:vector :int]]
   [:total :int]])

(def Action
  "Multi-schema for all action types, dispatching on :type."
  [:multi {:dispatch :type}
   [:bashketball/set-phase SetPhaseAction]
   [:bashketball/advance-turn AdvanceTurnAction]
   [:bashketball/set-active-player SetActivePlayerAction]
   [:bashketball/set-actions SetActionsAction]
   [:bashketball/draw-cards DrawCardsAction]
   [:bashketball/discard-cards DiscardCardsAction]
   [:bashketball/remove-cards RemoveCardsAction]
   [:bashketball/shuffle-deck ShuffleDeckAction]
   [:bashketball/return-discard ReturnDiscardAction]
   [:bashketball/move-player MovePlayerAction]
   [:bashketball/exhaust-player ExhaustPlayerAction]
   [:bashketball/refresh-player RefreshPlayerAction]
   [:bashketball/refresh-all RefreshAllAction]
   [:bashketball/add-modifier AddModifierAction]
   [:bashketball/remove-modifier RemoveModifierAction]
   [:bashketball/clear-modifiers ClearModifiersAction]
   [:bashketball/substitute SubstituteAction]
   [:bashketball/set-ball-possessed SetBallPossessedAction]
   [:bashketball/set-ball-loose SetBallLooseAction]
   [:bashketball/set-ball-in-air SetBallInAirAction]
   [:bashketball/add-score AddScoreAction]
   [:bashketball/push-stack PushStackAction]
   [:bashketball/pop-stack PopStackAction]
   [:bashketball/clear-stack ClearStackAction]
   [:bashketball/reveal-fate RevealFateAction]
   [:bashketball/record-skill-test RecordSkillTestAction]])

;; -----------------------------------------------------------------------------
;; Validation Helpers

(defn valid-action?
  "Returns true if the action is valid according to the Action schema."
  [action]
  (m/validate Action action))

(defn explain-action
  "Returns an explanation of why the action is invalid, or nil if valid."
  [action]
  (m/explain Action action))

(defn valid-game-state?
  "Returns true if the game state is valid according to the GameState schema."
  [state]
  (m/validate GameState state))

(defn explain-game-state
  "Returns an explanation of why the game state is invalid, or nil if valid."
  [state]
  (m/explain GameState state))
