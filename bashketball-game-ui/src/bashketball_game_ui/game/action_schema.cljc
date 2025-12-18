(ns bashketball-game-ui.game.action-schema
  "Malli schemas for game action types.

  Defines schemas for all dispatchable actions in the game UI. Actions are
  grouped by source:

  - **State machine actions**: Emitted by selection, discard, substitute, and peek machines
  - **Dispatcher actions**: Handler actions dispatched from components
  - **UI actions**: Modal and UI state control actions

  All actions share a common shape with `:type` as the discriminator.
  Uses shared schemas from [[bashketball-game.schema]] where appropriate."
  (:require [bashketball-game.schema :as game-schema]
            [malli.core :as m]))

(def Position
  "Schema for hex grid position as a map.

  Note: This differs from [[bashketball-game.schema/HexPosition]] which uses
  tuple format `[q r]`. The UI dispatcher uses map format `{:q q :r r}` and
  converts to tuple when calling game actions."
  [:map
   [:q :int]
   [:r :int]])

(def PlayerRef
  "Schema for player reference."
  [:map
   [:player-id :string]])

(def ActionType
  "All valid action type keywords."
  [:enum
   ;; State machine actions (selection)
   :move-player
   :set-ball-loose
   :set-ball-possessed
   :pass-to-player
   :pass-to-hex
   :standard-action
   ;; State machine actions (modal)
   :substitute
   :discard-cards
   :resolve-peek
   ;; Handler actions
   :end-turn
   :shoot
   :play-card
   :resolve-card
   :open-attach-modal
   :resolve-ability
   :draw
   :start-game
   :start-from-tipoff
   :setup-done
   :next-phase
   :submit-discard
   :reveal-fate
   :shuffle
   :return-discard
   :move-asset
   :target-click
   :toggle-exhausted
   ;; UI actions
   :show-detail-modal
   :close-detail-modal
   :show-create-token-modal
   :close-create-token-modal
   :show-attach-modal
   :close-attach-modal])

(def MovePlayerAction
  "Schema for moving a player to a hex position."
  [:map
   [:type [:= :move-player]]
   [:from [:map [:player-id :string]]]
   [:to Position]])

(def SetBallLooseAction
  "Schema for setting ball loose at a position."
  [:map
   [:type [:= :set-ball-loose]]
   [:to Position]])

(def SetBallPossessedAction
  "Schema for giving ball possession to a player."
  [:map
   [:type [:= :set-ball-possessed]]
   [:to PlayerRef]])

(def PassToPlayerAction
  "Schema for passing ball to a player."
  [:map
   [:type [:= :pass-to-player]]
   [:to PlayerRef]])

(def PassToHexAction
  "Schema for passing ball to a hex position."
  [:map
   [:type [:= :pass-to-hex]]
   [:to Position]])

(def StandardAction
  "Schema for virtual standard action (discard 2 cards, stage action)."
  [:map
   [:type [:= :standard-action]]
   [:from [:map [:cards [:set :string]]]]
   [:to [:map [:card-slug :string]]]])

(def SubstituteAction
  "Schema for substituting players."
  [:map
   [:type [:= :substitute]]
   [:on-court-id :string]
   [:off-court-id :string]])

(def DiscardCardsAction
  "Schema for discarding cards."
  [:map
   [:type [:= :discard-cards]]
   [:cards [:set :string]]])

(def Placement
  "Schema for a single card placement."
  [:map
   [:instance-id :string]
   [:destination [:enum "top" "bottom" "discard"]]])

(def ResolvePeekAction
  "Schema for resolving peeked cards."
  [:map
   [:type [:= :resolve-peek]]
   [:target-team game-schema/Team]
   [:placements [:vector Placement]]])

(def EndTurnAction
  "Schema for ending turn."
  [:map
   [:type [:= :end-turn]]])

(def ShootAction
  "Schema for shooting the ball."
  [:map
   [:type [:= :shoot]]])

(def PlayCardAction
  "Schema for playing a card from hand."
  [:map
   [:type [:= :play-card]]])

(def ResolveCardAction
  "Schema for resolving a card in play area."
  [:map
   [:type [:= :resolve-card]]
   [:instance-id :string]
   [:target-player-id {:optional true} :string]])

(def OpenAttachModalAction
  "Schema for opening attach ability modal."
  [:map
   [:type [:= :open-attach-modal]]
   [:instance-id :string]
   [:card-slug :string]
   [:played-by game-schema/Team]])

(def ResolveAbilityAction
  "Schema for resolving an ability attachment."
  [:map
   [:type [:= :resolve-ability]]
   [:target-player-id :string]])

(def DrawAction
  "Schema for drawing cards."
  [:map
   [:type [:= :draw]]
   [:count {:optional true} :int]])

(def StartGameAction
  "Schema for starting game from setup phase."
  [:map
   [:type [:= :start-game]]])

(def StartFromTipoffAction
  "Schema for winning tip-off."
  [:map
   [:type [:= :start-from-tipoff]]])

(def SetupDoneAction
  "Schema for completing setup."
  [:map
   [:type [:= :setup-done]]])

(def NextPhaseAction
  "Schema for advancing to next phase."
  [:map
   [:type [:= :next-phase]]])

(def SubmitDiscardAction
  "Schema for submitting discard selection."
  [:map
   [:type [:= :submit-discard]]])

(def RevealFateAction
  "Schema for revealing fate from deck."
  [:map
   [:type [:= :reveal-fate]]])

(def ShuffleAction
  "Schema for shuffling deck."
  [:map
   [:type [:= :shuffle]]])

(def ReturnDiscardAction
  "Schema for returning discard pile to deck."
  [:map
   [:type [:= :return-discard]]])

(def MoveAssetAction
  "Schema for moving an asset to discard or removed zone.

  Uses [[bashketball-game.schema/AssetDestination]] for the destination enum."
  [:map
   [:type [:= :move-asset]]
   [:instance-id :string]
   [:destination game-schema/AssetDestination]])

(def TargetClickAction
  "Schema for resolving in-air ball to target."
  [:map
   [:type [:= :target-click]]])

(def ToggleExhaustedAction
  "Schema for toggling player exhausted status."
  [:map
   [:type [:= :toggle-exhausted]]
   [:player-id :string]])

(def ShowDetailModalAction
  "Schema for showing card detail modal."
  [:map
   [:type [:= :show-detail-modal]]
   [:card-slug :string]])

(def CloseDetailModalAction
  "Schema for closing card detail modal."
  [:map
   [:type [:= :close-detail-modal]]])

(def ShowCreateTokenModalAction
  "Schema for showing create token modal."
  [:map
   [:type [:= :show-create-token-modal]]])

(def CloseCreateTokenModalAction
  "Schema for closing create token modal."
  [:map
   [:type [:= :close-create-token-modal]]])

(def ShowAttachModalAction
  "Schema for showing attach ability modal."
  [:map
   [:type [:= :show-attach-modal]]
   [:instance-id :string]
   [:card-slug :string]
   [:played-by game-schema/Team]])

(def CloseAttachModalAction
  "Schema for closing attach ability modal."
  [:map
   [:type [:= :close-attach-modal]]])

(def Action
  "Union schema for all action types."
  [:multi {:dispatch :type}
   [:move-player MovePlayerAction]
   [:set-ball-loose SetBallLooseAction]
   [:set-ball-possessed SetBallPossessedAction]
   [:pass-to-player PassToPlayerAction]
   [:pass-to-hex PassToHexAction]
   [:standard-action StandardAction]
   [:substitute SubstituteAction]
   [:discard-cards DiscardCardsAction]
   [:resolve-peek ResolvePeekAction]
   [:end-turn EndTurnAction]
   [:shoot ShootAction]
   [:play-card PlayCardAction]
   [:resolve-card ResolveCardAction]
   [:open-attach-modal OpenAttachModalAction]
   [:resolve-ability ResolveAbilityAction]
   [:draw DrawAction]
   [:start-game StartGameAction]
   [:start-from-tipoff StartFromTipoffAction]
   [:setup-done SetupDoneAction]
   [:next-phase NextPhaseAction]
   [:submit-discard SubmitDiscardAction]
   [:reveal-fate RevealFateAction]
   [:shuffle ShuffleAction]
   [:return-discard ReturnDiscardAction]
   [:move-asset MoveAssetAction]
   [:target-click TargetClickAction]
   [:toggle-exhausted ToggleExhaustedAction]
   [:show-detail-modal ShowDetailModalAction]
   [:close-detail-modal CloseDetailModalAction]
   [:show-create-token-modal ShowCreateTokenModalAction]
   [:close-create-token-modal CloseCreateTokenModalAction]
   [:show-attach-modal ShowAttachModalAction]
   [:close-attach-modal CloseAttachModalAction]])

(defn valid?
  "Returns true if action validates against schema."
  [action]
  (m/validate Action action))

(defn explain
  "Returns explanation of validation errors, or nil if valid."
  [action]
  (m/explain Action action))
