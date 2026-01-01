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
  [:enum {:graphql/type :Stat} :stat/SPEED :stat/SHOOTING :stat/PASSING :stat/DEFENSE])

(def Terrain
  "Board tile terrain types."
  [:enum {:graphql/type :Terrain} :terrain/COURT :terrain/THREE_POINT_LINE :terrain/PAINT :terrain/HOOP])

(def OccupantType
  "Type of occupant on a board tile."
  [:enum {:graphql/type :OccupantType} :occupant/BASKETBALL_PLAYER])

(def AssetDestination
  "Destination for moving team asset cards."
  [:enum {:graphql/type :AssetDestination} :DISCARD :REMOVED])

(def ExamineCardsDestination
  "Destination for resolving examined cards from deck inspection."
  [:enum {:graphql/type :ExamineCardsDestination} :examine/TOP :examine/BOTTOM :examine/DISCARD])

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

  The `:examined` zone holds cards temporarily during a two-phase examine action.
  Cards move from draw-pile to examined, then resolve to top/bottom of deck or discard.

  The optional `:cards` field contains hydrated card data for all cards
  referenced in the deck. This is populated at the API layer, not by the
  game engine."
  [:map {:graphql/type :DeckState}
   [:draw-pile [:vector CardInstance]]
   [:hand [:vector CardInstance]]
   [:discard [:vector CardInstance]]
   [:removed [:vector CardInstance]]
   [:examined [:vector CardInstance]]
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

;; -----------------------------------------------------------------------------
;; Skill Test Schemas

(def SkillTestModifier
  "A modifier applied to a pending skill test.

  Modifiers are added by triggers during the skill test flow and accumulated
  into the final total. The `:source` identifies what added the modifier
  (e.g., ability ID, card slug) for display and debugging."
  [:map {:graphql/type :SkillTestModifier}
   [:source :string]
   [:amount :int]
   [:reason {:optional true} :string]])

(def SkillTestContext
  "Context describing what initiated a skill test.

  Provides the trigger system with information about the test type and
  relevant entities for condition evaluation."
  [:map {:graphql/type :SkillTestContext}
   [:type :keyword]
   [:origin {:optional true} HexPosition]
   [:target {:optional true} [:or {:graphql/scalar :SkillTestTarget} HexPosition :string]]
   [:defender-id {:optional true} :string]])

(def PendingSkillTest
  "A skill test in progress awaiting resolution.

  The skill test flows through these stages:
  1. Initiated with `:base-value` from player stats
  2. Before triggers may add `:modifiers`
  3. Fate is revealed and stored in `:fate`
  4. After-fate triggers may add more modifiers
  5. `:total` is computed and test resolves"
  [:map {:graphql/type :PendingSkillTest}
   [:id :string]
   [:actor-id :string]
   [:stat Stat]
   [:base-value :int]
   [:modifiers [:vector SkillTestModifier]]
   [:fate {:optional true} [:maybe :int]]
   [:total {:optional true} [:maybe :int]]
   [:target-value {:optional true} [:maybe :int]]
   [:context SkillTestContext]])

;; -----------------------------------------------------------------------------
;; Choice Schemas

(def ChoiceOption
  "An option presented to a player in a pending choice.

  Disabled options are shown but not selectable, with `:reason` explaining why."
  [:map {:graphql/type :ChoiceOption}
   [:id :keyword]
   [:label :string]
   [:disabled {:optional true} :boolean]
   [:reason {:optional true} :string]])

(def PendingChoice
  "A choice awaiting player input.

  When a choice effect is applied, the game pauses until the player submits
  their selection via a submit-choice action."
  [:map {:graphql/type :PendingChoice}
   [:id :string]
   [:type :keyword]
   [:options [:vector ChoiceOption]]
   [:waiting-for Team]
   [:context {:optional true} :map]])

;; -----------------------------------------------------------------------------
;; Movement Schemas

(def PendingMovement
  "A movement action in progress.

  Tracks the movement context for step-by-step movement through the hex grid.
  Each step fires exit/enter events that triggers can intercept."
  [:map {:graphql/type :PendingMovement}
   [:id :string]
   [:player-id :string]
   [:team enums/Team]
   [:starting-position HexPosition]
   [:current-position HexPosition]
   [:initial-speed :int]
   [:remaining-speed :int]
   [:path-taken [:vector HexPosition]]
   [:step-number :int]])

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
   [:quarter {:optional true} [:int {:min 1 :max 4}]]
   [:active-player enums/Team]
   [:score Score]
   [:board Board]
   [:ball Ball]
   [:players Players]
   [:play-area [:vector PlayAreaCard]]
   [:stack [:vector StackEffect]]
   [:events [:vector Event]]
   [:metadata :map]
   [:pending-skill-test {:optional true} [:maybe PendingSkillTest]]
   [:pending-choice {:optional true} [:maybe PendingChoice]]
   [:pending-movement {:optional true} [:maybe PendingMovement]]])

;; -----------------------------------------------------------------------------
;; Action Schemas

(def SetPhaseAction
  [:map
   [:type [:= :bashketball/set-phase]]
   [:phase enums/GamePhase]])

(def AdvanceTurnAction
  [:map
   [:type [:= :bashketball/advance-turn]]])

(def AdvanceQuarterAction
  "Advances to the next quarter, resetting turn number to 1."
  [:map
   [:type [:= :bashketball/advance-quarter]]])

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

(def BeginMovementAction
  "Action to begin step-by-step movement for a player.

  Creates a movement context tracking the player's speed budget and path."
  [:map
   [:type [:= :bashketball/begin-movement]]
   [:player-id :string]
   [:speed :int]])

(def DoMoveStepAction
  "Action to move a player one hex and deduct movement cost.

  Updates the movement context with new position, path, and remaining speed."
  [:map
   [:type [:= :bashketball/do-move-step]]
   [:player-id :string]
   [:to-position HexPosition]
   [:cost :int]])

(def EndMovementAction
  "Action to end a player's movement and clear the movement context."
  [:map
   [:type [:= :bashketball/end-movement]]
   [:player-id :string]])

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

;; -----------------------------------------------------------------------------
;; Skill Test Actions

(def InitiateSkillTestAction
  "Action to initiate a skill test for a player.

  Creates a pending skill test with the base value derived from the player's
  stat. Triggers can then modify the test before fate is revealed."
  [:map
   [:type [:= :bashketball/initiate-skill-test]]
   [:actor-id :string]
   [:stat Stat]
   [:target-value {:optional true} :int]
   [:context SkillTestContext]])

(def ModifySkillTestAction
  "Action to add a modifier to the pending skill test.

  Called by triggers during the before or fate-revealed phases to add
  bonuses or penalties to the test."
  [:map
   [:type [:= :bashketball/modify-skill-test]]
   [:source :string]
   [:amount :int]
   [:reason {:optional true} :string]])

(def SetSkillTestFateAction
  "Action to set the fate value on the pending skill test.

  Called after the fate card is revealed during skill test resolution."
  [:map
   [:type [:= :bashketball/set-skill-test-fate]]
   [:fate :int]])

(def ResolveSkillTestAction
  "Action to compute the final total and mark the skill test for resolution.

  Sums base value, all modifiers, and fate to produce the total."
  [:map
   [:type [:= :bashketball/resolve-skill-test]]])

(def ClearSkillTestAction
  "Action to clear the pending skill test after resolution."
  [:map
   [:type [:= :bashketball/clear-skill-test]]])

;; -----------------------------------------------------------------------------
;; Choice Actions

(def OfferChoiceAction
  "Action to present a choice to a player.

  Sets pending-choice in the game state, pausing execution until the
  player submits their selection."
  [:map
   [:type [:= :bashketball/offer-choice]]
   [:choice-type :keyword]
   [:options [:vector ChoiceOption]]
   [:waiting-for Team]
   [:context {:optional true} :map]])

(def SubmitChoiceAction
  "Action to submit a player's choice selection.

  Must match the pending choice ID. The selected option ID is recorded
  and can be used by subsequent effects."
  [:map
   [:type [:= :bashketball/submit-choice]]
   [:choice-id :string]
   [:selected :keyword]])

(def ClearChoiceAction
  "Action to clear the pending choice after processing."
  [:map
   [:type [:= :bashketball/clear-choice]]])

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

(def ExamineCardsAction
  "Action to examine the top N cards of the draw pile.

  Moves cards from draw-pile to examined zone for player review.
  When deck has fewer than N cards, examines all remaining cards."
  [:map
   [:type [:= :bashketball/examine-cards]]
   [:player enums/Team]
   [:count pos-int?]])

(def ExamineCardsPlacement
  "Placement instruction for a single examined card."
  [:map
   [:instance-id :string]
   [:destination ExamineCardsDestination]])

(def ResolveExaminedCardsAction
  "Action to resolve examined cards to their destinations.

  Placements must include ALL cards currently in the examined zone.
  Cards going to TOP are placed in the order specified (first in list = top of deck).
  Cards going to BOTTOM are placed in the order specified (first in list = bottom of deck)."
  [:map
   [:type [:= :bashketball/resolve-examined-cards]]
   [:player enums/Team]
   [:placements [:vector ExamineCardsPlacement]]])

(def Action
  "Multi-schema for all action types, dispatching on :type."
  [:multi {:dispatch :type}
   [:bashketball/set-phase SetPhaseAction]
   [:bashketball/advance-turn AdvanceTurnAction]
   [:bashketball/advance-quarter AdvanceQuarterAction]
   [:bashketball/set-active-player SetActivePlayerAction]
   [:bashketball/set-actions SetActionsAction]
   [:bashketball/draw-cards DrawCardsAction]
   [:bashketball/discard-cards DiscardCardsAction]
   [:bashketball/remove-cards RemoveCardsAction]
   [:bashketball/shuffle-deck ShuffleDeckAction]
   [:bashketball/return-discard ReturnDiscardAction]
   [:bashketball/move-player MovePlayerAction]
   [:bashketball/begin-movement BeginMovementAction]
   [:bashketball/do-move-step DoMoveStepAction]
   [:bashketball/end-movement EndMovementAction]
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
   [:bashketball/initiate-skill-test InitiateSkillTestAction]
   [:bashketball/modify-skill-test ModifySkillTestAction]
   [:bashketball/set-skill-test-fate SetSkillTestFateAction]
   [:bashketball/resolve-skill-test ResolveSkillTestAction]
   [:bashketball/clear-skill-test ClearSkillTestAction]
   [:bashketball/offer-choice OfferChoiceAction]
   [:bashketball/submit-choice SubmitChoiceAction]
   [:bashketball/clear-choice ClearChoiceAction]
   [:bashketball/play-card PlayCardAction]
   [:bashketball/stage-card StageCardAction]
   [:bashketball/resolve-card ResolveCardAction]
   [:bashketball/move-asset MoveAssetAction]
   [:bashketball/attach-ability AttachAbilityAction]
   [:bashketball/detach-ability DetachAbilityAction]
   [:bashketball/start-from-tipoff StartFromTipOffAction]
   [:bashketball/create-token CreateTokenAction]
   [:bashketball/stage-virtual-standard-action StageVirtualStandardActionAction]
   [:bashketball/examine-cards ExamineCardsAction]
   [:bashketball/resolve-examined-cards ResolveExaminedCardsAction]])

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
