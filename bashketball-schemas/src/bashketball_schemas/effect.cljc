(ns bashketball-schemas.effect
  "Effect definition schemas for card abilities and actions.

   Provides Malli schemas for structured effect definitions that replace
   string-based identifiers. Effects are interpreted by the polix policy
   system at runtime.

   Schema hierarchy:
   - [[PolicyExpr]] - Base expression type for conditions
   - [[TriggerDef]] - When an effect should fire
   - [[EffectDef]] - What happens when triggered
   - Card-specific schemas build on these primitives")

;; =============================================================================
;; Base Building Blocks
;; =============================================================================

(def PolicyExpr
  "A polix policy expression.

   Can be a simple value (keyword, string, int, boolean) or a vector form
   representing an operator application like `[:= :doc/phase :phase/PLAY]`
   or `[:bashketball/has-ball? :doc/state :self/id]`.

   Maps to GraphQL `PolicyExpr` scalar. Serialized as EDN strings for transport."
  [:or {:graphql/scalar :PolicyExpr}
   :keyword :string :int :boolean [:vector :any]])

(def TriggerTiming
  "When a trigger fires relative to the event."
  [:enum {:graphql/type :TriggerTiming}
   :before :after :instead :at])

(def TriggerDef
  "Configuration for when an effect should fire.

   - `:trigger/event` - Event type to listen for (e.g., `:bashketball/shoot.after`)
   - `:trigger/condition` - Optional policy expression that must be satisfied
   - `:trigger/timing` - When to fire relative to the event (default: `:after`)
   - `:trigger/priority` - Ordering when multiple triggers fire (higher = first)
   - `:trigger/once?` - If true, trigger unregisters after firing once"
  [:map
   [:trigger/event :keyword]
   [:trigger/condition {:optional true} PolicyExpr]
   [:trigger/timing {:optional true} TriggerTiming]
   [:trigger/priority {:optional true} :int]
   [:trigger/once? {:optional true} :boolean]])

(def EffectDef
  "A game effect to apply.

   The `:effect/type` determines what happens and which additional keys are
   required. Effect types are registered with the polix effect system.

   Common effect types:
   - `:bashketball/move-player` - Move player to position
   - `:bashketball/exhaust-player` - Mark player as exhausted
   - `:bashketball/add-modifier` - Add stat modifier
   - `:bashketball/draw-cards` - Draw cards from deck
   - `:bashketball/initiate-skill-test` - Start a skill test
   - `:bashketball/sequence` - Execute multiple effects in order
   - `:bashketball/force-choice` - Present choice to a player"
  [:map
   [:effect/type :keyword]])

;; =============================================================================
;; Player & Ability Card Schemas
;; =============================================================================

(def AbilityDef
  "An ability on a player or ability card.

   Abilities can be:
   - **Triggered** - Has `:ability/trigger` and `:ability/effect`, fires on events
   - **Passive** - Has `:ability/condition`, modifies rules when condition met
   - **Activated** - Has `:ability/effect` without trigger, manually activated

   The `:ability/id` must be unique within a card."
  [:map
   [:ability/id :string]
   [:ability/name {:optional true} :string]
   [:ability/description {:optional true} :string]
   [:ability/trigger {:optional true} TriggerDef]
   [:ability/condition {:optional true} PolicyExpr]
   [:ability/effect {:optional true} EffectDef]])

;; =============================================================================
;; Play Card Schema
;; =============================================================================

(def PlayDef
  "A play card's effect definition.

   - `:play/id` - Unique identifier for this play
   - `:play/requires` - Optional preconditions to play the card
   - `:play/targets` - Keywords indicating required targets (e.g., `[:target/player-id]`)
   - `:play/effect` - The effect to execute when the card resolves"
  [:map
   [:play/id :string]
   [:play/name {:optional true} :string]
   [:play/description {:optional true} :string]
   [:play/requires {:optional true} PolicyExpr]
   [:play/targets {:optional true} [:vector :keyword]]
   [:play/effect EffectDef]])

;; =============================================================================
;; Standard Action & Split Play Schemas
;; =============================================================================

(def ActionModeDef
  "One mode (offense or defense) of a standard action or split play.

   When a player plays a standard action or split play card, they choose
   ONE mode to execute. The other mode is not used.

   - `:action/id` - Unique identifier for this mode
   - `:action/requires` - Preconditions for this mode to be available
   - `:action/targets` - Required targets for this mode
   - `:action/effect` - The effect to execute"
  [:map
   [:action/id :string]
   [:action/name {:optional true} :string]
   [:action/description {:optional true} :string]
   [:action/requires {:optional true} PolicyExpr]
   [:action/targets {:optional true} [:vector :keyword]]
   [:action/effect EffectDef]])

;; =============================================================================
;; Coaching Card Schemas
;; =============================================================================

(def CallDef
  "The Call mode of a coaching card (played normally).

   Call is the primary effect when playing a coaching card from hand.
   Requires discarding 1 card as fuel (like any other card)."
  [:map
   [:call/id :string]
   [:call/name {:optional true} :string]
   [:call/description {:optional true} :string]
   [:call/requires {:optional true} PolicyExpr]
   [:call/targets {:optional true} [:vector :keyword]]
   [:call/effect EffectDef]])

(def SignalDef
  "The Signal mode of a coaching card (triggers when discarded as fuel).

   When a coaching card is discarded as fuel for another card, its signal
   effect triggers BEFORE the main card resolves. Signals are typically
   smaller, complementary effects.

   The trigger is pre-configured to fire on `:bashketball/card-discarded-as-fuel`
   with condition matching this card's instance ID."
  [:map
   [:signal/id :string]
   [:signal/name {:optional true} :string]
   [:signal/description {:optional true} :string]
   [:signal/effect EffectDef]])

;; =============================================================================
;; Team Asset Card Schemas
;; =============================================================================

(def AssetTriggerDef
  "A triggered ability on a team asset.

   Assets can have multiple triggers that fire while the asset is in play."
  [:map
   [:trigger TriggerDef]
   [:effect EffectDef]])

(def ActivatedAbilityDef
  "An activated ability on a team asset.

   Activated abilities require manual activation and may have costs."
  [:map
   [:cost {:optional true} EffectDef]
   [:effect EffectDef]])

(def ResponseDef
  "A response asset's activation definition.

   Response assets are played face-down. When their trigger condition fires,
   the owner is prompted to Apply (reveal and execute) or Pass (keep hidden).

   - `:response/trigger` - When the response can activate
   - `:response/prompt` - Text shown to player (e.g., \"Call Defensive Timeout?\")
   - `:response/effect` - Effect if player chooses Apply"
  [:map
   [:response/trigger TriggerDef]
   [:response/prompt :string]
   [:response/effect EffectDef]])

(def AssetPowerDef
  "A team asset's power definition.

   Assets can have various types of effects:
   - `:asset/triggers` - Passive triggers that fire while in play
   - `:asset/condition` - Passive condition that modifies rules
   - `:asset/activated` - Manually activated ability with optional cost
   - `:asset/response` - For Response subtype assets (played face-down)"
  [:map
   [:asset/id :string]
   [:asset/name {:optional true} :string]
   [:asset/description {:optional true} :string]
   [:asset/triggers {:optional true} [:vector AssetTriggerDef]]
   [:asset/condition {:optional true} PolicyExpr]
   [:asset/activated {:optional true} ActivatedAbilityDef]
   [:asset/response {:optional true} ResponseDef]])
