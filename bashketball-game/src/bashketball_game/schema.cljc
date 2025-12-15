(ns bashketball-game.schema
  "Malli schemas for Bashketball game state and actions.

  Defines all data structures used in the game engine, including the game state,
  board, players, ball, and the multi-schema for action validation.

  Uses shared enums from [[bashketball-schemas.enums]] for cross-project
  consistency (Team, Phase, Size, BallStatus, BallActionType)."
  (:require [bashketball-schemas.card :as card]
            [bashketball-schemas.enums :as enums]
            [malli.core :as m]))

;; -----------------------------------------------------------------------------
;; Primitive Schemas

(def HexPosition
  "Axial coordinates for hex grid position [q r]."
  [:tuple {:graphql/scalar :HexPosition} [:int {:min 0 :max 4}] [:int {:min 0 :max 13}]])

;; Re-export shared enums from bashketball-schemas for convenience
(def Team
  "Game player identifier. Re-exported from [[bashketball-schemas.enums/Team]]."
  enums/Team)

(def Phase
  "Game phase. Re-exported from [[bashketball-schemas.enums/GamePhase]]."
  enums/GamePhase)

(def Size
  "Basketball player size. Re-exported from [[bashketball-schemas.enums/Size]]."
  enums/Size)

(def BallStatus
  "Ball state. Re-exported from [[bashketball-schemas.enums/BallStatus]]."
  enums/BallStatus)

(def BallActionType
  "Ball action type. Re-exported from [[bashketball-schemas.enums/BallActionType]]."
  enums/BallActionType)

;; Game-specific enums (not shared across projects)
(def Stat
  "Basketball player stat names."
  [:enum {:graphql/type :Stat} :stat/SPEED :stat/SHOOTING :stat/PASSING :stat/DRIBBLING :stat/DEFENSE])

(def Terrain
  "Board tile terrain types."
  [:enum {:graphql/type :Terrain} :terrain/COURT :terrain/THREE_POINT_LINE :terrain/PAINT :terrain/HOOP])

(def OccupantType
  "Type of occupant on a board tile."
  [:enum {:graphql/type :OccupantType} :occupant/BASKETBALL_PLAYER])

(def AssetDestination
  "Destination for moving team asset cards."
  [:enum {:graphql/type :AssetDestination} :DISCARD :REMOVED])

(def DetachDestination
  "Destination for detached ability cards."
  [:enum {:graphql/type :DetachDestination} :detach/DISCARD :detach/REMOVED])

(def TokenPlacement
  "Placement destination for token cards."
  [:enum {:graphql/type :TokenPlacement} :placement/ASSET :placement/ATTACH])

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

(def AttachedAbility
  "An ability card attached to a basketball player.

  Regular abilities have `:card-slug`, token abilities have `:token` and `:card`."
  [:map {:graphql/type :AttachedAbility}
   [:instance-id :string]
   [:card-slug {:optional true} :string]
   [:token {:optional true} [:= true]]
   [:card {:optional true} card/Card]
   [:removable :boolean]
   [:detach-destination DetachDestination]
   [:attached-at :string]])

(def PlayerStats
  "Basketball player stats."
  [:map {:graphql/type :PlayerStats}
   [:size enums/Size]
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
   [:exhausted :boolean]
   [:stats PlayerStats]
   [:abilities [:vector :string]]
   [:modifiers [:vector Modifier]]
   [:attachments [:vector AttachedAbility]]])

(def TeamRoster
  "A team's roster of basketball players.

  Players with a non-nil `:position` are on court, others are off court.
  Maximum 3 players can be on court at once."
  [:map {:graphql/type :TeamRoster}
   [:players [:map-of :string BasketballPlayer]]])

(def CardInstance
  "A card instance - either regular (slug reference) or token (inline definition).

  Regular cards have `:card-slug`, token cards have `:token` and `:card`."
  [:map {:graphql/type :CardInstance}
   [:instance-id :string]
   [:card-slug {:optional true} :string]
   [:token {:optional true} :boolean]
   [:card {:optional true} card/Card]])

(def PlayAreaCard
  "A card in the shared play area with metadata about who played it.

  Virtual cards (`:virtual true`) are temporary cards created when a player
  discards 2 cards to play a standard action. They disappear entirely when
  resolved rather than going to any pile."
  [:map {:graphql/type :PlayAreaCard}
   [:instance-id :string]
   [:card-slug :string]
   [:played-by enums/Team]
   [:virtual {:optional true} :boolean]])

(def DeckState
  "A game player's deck state during a game.

  The optional `:cards` field contains hydrated card data for all cards
  referenced in the deck. This is populated at the API layer, not by the
  game engine."
  [:map {:graphql/type :DeckState}
   [:draw-pile [:vector CardInstance]]
   [:hand [:vector CardInstance]]
   [:discard [:vector CardInstance]]
   [:removed [:vector CardInstance]]
   [:cards {:optional true} [:vector card/Card]]])

(def GamePlayer
  "A game player (human/AI controlling a team)."
  [:map {:graphql/type :GamePlayer}
   [:id enums/Team]
   [:actions-remaining :int]
   [:deck DeckState]
   [:team TeamRoster]
   [:assets [:vector CardInstance]]])

(def Tile
  "A board tile."
  [:map {:graphql/type :Tile}
   [:terrain Terrain]
   [:side {:optional true} enums/Team]])

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
   [:status [:= :ball-status/POSSESSED]]
   [:holder-id :string]])

(def BallLoose
  "Ball state when on the ground."
  [:map {:graphql/type :BallLoose}
   [:status [:= :ball-status/LOOSE]]
   [:position HexPosition]])

(def PositionTarget
  "Target specifying a hex position."
  [:map {:graphql/type :PositionTarget}
   [:type [:= :position]]
   [:position HexPosition]])

(def PlayerTarget
  "Target specifying a player by ID."
  [:map {:graphql/type :PlayerTarget}
   [:type [:= :player]]
   [:player-id :string]])

(def BallTarget
  "Target for a ball in air - either a position or player ID."
  [:multi {:dispatch :type :graphql/type :BallTarget}
   [:position PositionTarget]
   [:player PlayerTarget]])

(def BallInAir
  "Ball state when in flight."
  [:map {:graphql/type :BallInAir}
   [:status [:= :ball-status/IN_AIR]]
   [:origin HexPosition]
   [:target BallTarget]
   [:action-type enums/BallActionType]])

(def Ball
  "Ball state (one of three states)."
  [:multi {:dispatch :status :graphql/type :Ball}
   [:ball-status/POSSESSED BallPossessed]
   [:ball-status/LOOSE BallLoose]
   [:ball-status/IN_AIR BallInAir]])

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
   [:team/HOME {:graphql/name :HOME} :int]
   [:team/AWAY {:graphql/name :AWAY} :int]])

(def Players
  "Both players in a game."
  [:map {:graphql/type :Players}
   [:team/HOME {:graphql/name :HOME} GamePlayer]
   [:team/AWAY {:graphql/name :AWAY} GamePlayer]])

(def GameState
  "The complete game state."
  [:map {:graphql/type :GameState}
   [:game-id :string]
   [:phase enums/GamePhase]
   [:turn-number :int]
   [:active-player enums/Team]
   [:score Score]
   [:board Board]
   [:ball Ball]
   [:players Players]
   [:play-area [:vector PlayAreaCard]]
   [:stack [:vector StackEffect]]
   [:events [:vector Event]]
   [:metadata :map]])

;; -----------------------------------------------------------------------------
;; Action Schemas

(def SetPhaseAction
  [:map
   [:type [:= :bashketball/set-phase]]
   [:phase enums/GamePhase]])

(def AdvanceTurnAction
  [:map
   [:type [:= :bashketball/advance-turn]]])

(def SetActivePlayerAction
  [:map
   [:type [:= :bashketball/set-active-player]]
   [:player enums/Team]])

(def SetActionsAction
  [:map
   [:type [:= :bashketball/set-actions]]
   [:player enums/Team]
   [:amount :int]])

(def DrawCardsAction
  [:map
   [:type [:= :bashketball/draw-cards]]
   [:player enums/Team]
   [:count pos-int?]])

(def DiscardCardsAction
  [:map
   [:type [:= :bashketball/discard-cards]]
   [:player enums/Team]
   [:instance-ids [:vector :string]]])

(def RemoveCardsAction
  [:map
   [:type [:= :bashketball/remove-cards]]
   [:player enums/Team]
   [:instance-ids [:vector :string]]])

(def ShuffleDeckAction
  [:map
   [:type [:= :bashketball/shuffle-deck]]
   [:player enums/Team]])

(def ReturnDiscardAction
  [:map
   [:type [:= :bashketball/return-discard]]
   [:player enums/Team]])

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
   [:team enums/Team]])

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
  "Swaps an on-court player with an off-court player."
  [:map
   [:type [:= :bashketball/substitute]]
   [:on-court-id :string]
   [:off-court-id :string]])

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
   [:action-type enums/BallActionType]])

(def AddScoreAction
  [:map
   [:type [:= :bashketball/add-score]]
   [:team enums/Team]
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
   [:player enums/Team]])

(def RecordSkillTestAction
  [:map
   [:type [:= :bashketball/record-skill-test]]
   [:player-id :string]
   [:stat Stat]
   [:base :int]
   [:fate :int]
   [:modifiers [:vector :int]]
   [:total :int]])

(def PlayCardAction
  "Action to play a card from hand, moving it to discard."
  [:map
   [:type [:= :bashketball/play-card]]
   [:player enums/Team]
   [:instance-id :string]])

(def StageCardAction
  "Action to stage a card from hand to the shared play area."
  [:map
   [:type [:= :bashketball/stage-card]]
   [:player enums/Team]
   [:instance-id :string]])

(def ResolveCardAction
  "Action to resolve a card from play area to discard, assets, or attached to a player.

  When `target-player-id` is provided for ability cards, the card attaches to
  that player instead of going to discard."
  [:map
   [:type [:= :bashketball/resolve-card]]
   [:instance-id :string]
   [:target-player-id {:optional true} :string]])

(def MoveAssetAction
  "Action to move a team asset card from assets to discard or removed zone."
  [:map
   [:type [:= :bashketball/move-asset]]
   [:player enums/Team]
   [:instance-id :string]
   [:destination AssetDestination]])

(def AttachAbilityAction
  "Action to attach an ability card from hand to a basketball player."
  [:map
   [:type [:= :bashketball/attach-ability]]
   [:player enums/Team]
   [:instance-id :string]
   [:target-player-id :string]])

(def DetachAbilityAction
  "Action to detach an ability card from a basketball player."
  [:map
   [:type [:= :bashketball/detach-ability]]
   [:player enums/Team]
   [:target-player-id :string]
   [:instance-id :string]])

(def StartFromTipOffAction
  "Action to start the game from tip-off, setting the acting player as first to move."
  [:map
   [:type [:= :bashketball/start-from-tipoff]]
   [:player enums/Team]])

(def TokenCardInput
  "Minimal input schema for creating token cards.
  The backend normalizes these to full card/Card with defaults."
  [:map
   [:slug :string]
   [:name :string]
   [:card-type enums/CardType]
   [:set-slug {:optional true} :string]
   [:asset-power {:optional true} :string]
   [:fate {:optional true} :int]
   [:abilities {:optional true} [:vector :string]]
   [:removable {:optional true} :boolean]
   [:detach-destination {:optional true} DetachDestination]])

(def CreateTokenAction
  "Action to create a token card and place it in assets or attached to a player."
  [:map
   [:type [:= :bashketball/create-token]]
   [:player enums/Team]
   [:card TokenCardInput]
   [:placement TokenPlacement]
   [:target-player-id {:optional true} :string]])

(def StageVirtualStandardActionAction
  "Action to stage a virtual standard action by discarding 2 cards.

  Creates a virtual card in the play area that represents a borrowed
  standard action. The virtual card disappears when resolved rather
  than going to any pile."
  [:map
   [:type [:= :bashketball/stage-virtual-standard-action]]
   [:player enums/Team]
   [:discard-instance-ids [:vector {:min 2 :max 2} :string]]
   [:card-slug :string]])

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
   [:bashketball/record-skill-test RecordSkillTestAction]
   [:bashketball/play-card PlayCardAction]
   [:bashketball/stage-card StageCardAction]
   [:bashketball/resolve-card ResolveCardAction]
   [:bashketball/move-asset MoveAssetAction]
   [:bashketball/attach-ability AttachAbilityAction]
   [:bashketball/detach-ability DetachAbilityAction]
   [:bashketball/start-from-tipoff StartFromTipOffAction]
   [:bashketball/create-token CreateTokenAction]
   [:bashketball/stage-virtual-standard-action StageVirtualStandardActionAction]])

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
