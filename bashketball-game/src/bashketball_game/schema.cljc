(ns bashketball-game.schema
  "Malli schemas for Bashketball game state and actions.

  Defines all data structures used in the game engine, including the game state,
  board, players, ball, and the multi-schema for action validation."
  (:require [malli.core :as m]))

;; -----------------------------------------------------------------------------
;; Primitive Schemas

(def HexPosition
  "Axial coordinates for hex grid position [q r]."
  [:tuple [:int {:min 0 :max 4}] [:int {:min 0 :max 13}]])

(def Team
  "Game player identifier (the human controlling a team)."
  [:enum :home :away])

(def Phase
  "Game phase."
  [:enum :setup :upkeep :actions :resolution :end-of-turn :game-over])

(def Size
  "Basketball player size category."
  [:enum :small :mid :big])

(def Stat
  "Basketball player stat names."
  [:enum :speed :shooting :passing :dribbling :defense])

(def BallActionType
  "Type of ball action when in-air."
  [:enum :shot :pass])

(def Terrain
  "Board tile terrain types."
  [:enum :court :three-point-line :paint :hoop])

;; -----------------------------------------------------------------------------
;; Component Schemas

(def Modifier
  "Temporary stat modifier applied to a basketball player."
  [:map
   [:id :string]
   [:stat Stat]
   [:amount :int]
   [:source {:optional true} :string]
   [:expires-at {:optional true} :int]])

(def BasketballPlayer
  "A basketball player on a team."
  [:map
   [:id :string]
   [:card-slug :string]
   [:name :string]
   [:position {:optional true} [:maybe HexPosition]]
   [:exhausted? :boolean]
   [:stats [:map
            [:size Size]
            [:speed :int]
            [:shooting :int]
            [:passing :int]
            [:dribbling :int]
            [:defense :int]]]
   [:abilities [:vector :string]]
   [:modifiers [:vector Modifier]]])

(def TeamRoster
  "A team's roster of basketball players."
  [:map
   [:starters [:vector :string]]
   [:bench [:vector :string]]
   [:players [:map-of :string BasketballPlayer]]])

(def Deck
  "A game player's deck state."
  [:map
   [:draw-pile [:vector :string]]
   [:hand [:vector :string]]
   [:discard [:vector :string]]
   [:removed [:vector :string]]])

(def GamePlayer
  "A game player (human/AI controlling a team)."
  [:map
   [:id Team]
   [:actions-remaining :int]
   [:deck Deck]
   [:team TeamRoster]
   [:assets [:vector :string]]])

(def Tile
  "A board tile."
  [:map
   [:terrain Terrain]
   [:side {:optional true} Team]])

(def Occupant
  "An occupant of a board tile."
  [:map
   [:type [:enum :basketball-player :ball]]
   [:id {:optional true} :string]])

(def Board
  "The game board (5x14 hex grid)."
  [:map
   [:width :int]
   [:height :int]
   [:tiles [:map-of [:tuple :int :int] Tile]]
   [:occupants [:map-of [:tuple :int :int] Occupant]]])

(def BallPossessed
  "Ball state when held by a player."
  [:map
   [:status [:= :possessed]]
   [:holder-id :string]])

(def BallLoose
  "Ball state when on the ground."
  [:map
   [:status [:= :loose]]
   [:position HexPosition]])

(def BallInAir
  "Ball state when in flight."
  [:map
   [:status [:= :in-air]]
   [:origin HexPosition]
   [:target [:or HexPosition :string]]
   [:action-type BallActionType]])

(def Ball
  "Ball state (one of three states)."
  [:multi {:dispatch :status}
   [:possessed BallPossessed]
   [:loose BallLoose]
   [:in-air BallInAir]])

(def StackEffect
  "An effect on the resolution stack."
  [:map
   [:id :string]
   [:type :keyword]
   [:source {:optional true} :string]
   [:data {:optional true} :map]])

(def Event
  "A logged game event."
  [:map
   [:type :keyword]
   [:timestamp :string]
   [:data {:optional true} :map]])

(def GameState
  "The complete game state."
  [:map
   [:game-id :string]
   [:phase Phase]
   [:turn-number :int]
   [:active-player Team]
   [:score [:map [:home :int] [:away :int]]]
   [:board Board]
   [:ball Ball]
   [:players [:map [:home GamePlayer] [:away GamePlayer]]]
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
   [:card-slugs [:vector :string]]])

(def RemoveCardsAction
  [:map
   [:type [:= :bashketball/remove-cards]]
   [:player Team]
   [:card-slugs [:vector :string]]])

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
   [:target [:or HexPosition :string]]
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
