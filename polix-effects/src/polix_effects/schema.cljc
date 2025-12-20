(ns polix-effects.schema
  "Malli schemas for effects and results.

  Defines the structure of effects, references, and the result type returned
  by [[polix-effects.core/apply-effect]]. Built-in effect types use the
  `:polix-effects/*` namespace, while domain-specific effects use their own
  namespace (e.g., `:bashketball/draw-cards`)."
  (:require [malli.core :as m]
            [malli.registry :as mr]))

;;; ---------------------------------------------------------------------------
;;; Path and Reference Schemas
;;; ---------------------------------------------------------------------------

(def PathSegment
  "A single segment in a path - keyword, string, or integer."
  [:or :keyword :string :int])

(def Path
  "A vector of path segments for navigating nested data structures."
  [:vector PathSegment])

(def StateRef
  "Reference to a value in the application state."
  [:cat [:= :state] [:* PathSegment]])

(def CtxRef
  "Reference to a value in the effect context."
  [:cat [:= :ctx] [:* PathSegment]])

(def ParamRef
  "Reference to an effect parameter."
  [:cat [:= :param] PathSegment])

(def Reference
  "A symbolic reference resolved at effect application time.

  Can be:
  - A keyword (e.g., `:self`, `:target`) resolved from context bindings
  - A state path `[:state :path :to :value]`
  - A context path `[:ctx :bindings :foo]`
  - A param reference `[:param :amount]`"
  [:or
   :keyword
   StateRef
   CtxRef
   ParamRef])

(def ValueOrRef
  "Any value, or a reference to be resolved at apply time."
  :any)

;;; ---------------------------------------------------------------------------
;;; Result Schema
;;; ---------------------------------------------------------------------------

(def Failure
  "A single failure entry with effect, error code, and optional message."
  [:map
   [:effect :map]
   [:error :keyword]
   [:message {:optional true} :string]])

(def Result
  "The result of applying an effect.

  - `:state` - The new state after applying the effect
  - `:applied` - Vector of effects that were successfully applied
  - `:failed` - Vector of failure entries
  - `:pending` - Nil, or pending choice info for `:polix-effects/choice` effects"
  [:map
   [:state :any]
   [:applied [:vector :map]]
   [:failed [:vector Failure]]
   [:pending [:maybe :map]]])

;;; ---------------------------------------------------------------------------
;;; Effect Schemas
;;; ---------------------------------------------------------------------------

(def NoopEffect
  "Effect that does nothing. Useful as a placeholder or in conditionals."
  [:map
   [:type [:= :polix-effects/noop]]])

(def SequenceEffect
  "Applies a sequence of effects in order, threading state through each."
  [:map
   [:type [:= :polix-effects/sequence]]
   [:effects [:vector [:ref ::Effect]]]])

(def AssocInEffect
  "Sets a value at a path in the state."
  [:map
   [:type [:= :polix-effects/assoc-in]]
   [:path [:or Path Reference]]
   [:value ValueOrRef]])

(def UpdateInEffect
  "Updates a value at a path by applying a function."
  [:map
   [:type [:= :polix-effects/update-in]]
   [:path [:or Path Reference]]
   [:f [:or :keyword :symbol [:fn fn?]]]
   [:args {:optional true} [:vector ValueOrRef]]])

(def DissocInEffect
  "Removes a value at a path in the state."
  [:map
   [:type [:= :polix-effects/dissoc-in]]
   [:path [:or Path Reference]]])

(def MergeInEffect
  "Merges a map into the value at a path."
  [:map
   [:type [:= :polix-effects/merge-in]]
   [:path [:or Path Reference]]
   [:value ValueOrRef]])

;;; ---------------------------------------------------------------------------
;;; Collection Effect Schemas
;;; ---------------------------------------------------------------------------

(def ConjInEffect
  "Adds a value to a collection at a path."
  [:map
   [:type [:= :polix-effects/conj-in]]
   [:path [:or Path Reference]]
   [:value ValueOrRef]])

(def RemoveInEffect
  "Removes items from a collection matching a predicate."
  [:map
   [:type [:= :polix-effects/remove-in]]
   [:path [:or Path Reference]]
   [:predicate [:or :keyword [:fn fn?] :map]]])

(def MoveEffect
  "Moves items from one collection to another."
  [:map
   [:type [:= :polix-effects/move]]
   [:from-path [:or Path Reference]]
   [:to-path [:or Path Reference]]
   [:predicate [:or :keyword [:fn fn?] :map]]])

;;; ---------------------------------------------------------------------------
;;; Composite Effect Schemas
;;; ---------------------------------------------------------------------------

(def TransactionEffect
  "Applies effects atomically, rolling back on failure."
  [:map
   [:type [:= :polix-effects/transaction]]
   [:effects [:vector [:ref ::Effect]]]])

(def LetEffect
  "Binds values for use in a nested effect."
  [:map
   [:type [:= :polix-effects/let]]
   [:bindings [:vector :any]]
   [:effect [:ref ::Effect]]])

(def ConditionalEffect
  "Evaluates a polix policy condition and applies then or else effect.

  The condition is a polix policy expression evaluated against the current state.
  If true, applies the `:then` effect. If false or residual, applies the `:else`
  effect. Both branches are optional - if missing, acts as noop for that branch."
  [:map
   [:type [:= :polix-effects/conditional]]
   [:condition :any]
   [:then {:optional true} [:ref ::Effect]]
   [:else {:optional true} [:ref ::Effect]]])

;;; ---------------------------------------------------------------------------
;;; Effect Multi-Schema
;;; ---------------------------------------------------------------------------

(def Effect
  "Multi-schema dispatching on `:type` to validate effect structures.

  Built-in effects use the `:polix-effects/*` namespace. The schema is
  extensible - domain-specific effects can be added to the registry."
  [:multi {:dispatch :type}
   ;; Control flow
   [:polix-effects/noop NoopEffect]
   [:polix-effects/sequence SequenceEffect]
   ;; State mutations
   [:polix-effects/assoc-in AssocInEffect]
   [:polix-effects/update-in UpdateInEffect]
   [:polix-effects/dissoc-in DissocInEffect]
   [:polix-effects/merge-in MergeInEffect]
   ;; Collections
   [:polix-effects/conj-in ConjInEffect]
   [:polix-effects/remove-in RemoveInEffect]
   [:polix-effects/move MoveEffect]
   ;; Composite
   [:polix-effects/transaction TransactionEffect]
   [:polix-effects/let LetEffect]
   [:polix-effects/conditional ConditionalEffect]])

;;; ---------------------------------------------------------------------------
;;; Registry Setup
;;; ---------------------------------------------------------------------------

(def registry
  "Custom Malli registry with Effect schema for recursive references."
  (mr/composite-registry
   (m/default-schemas)
   {::Effect Effect}))

;;; ---------------------------------------------------------------------------
;;; Validation Functions
;;; ---------------------------------------------------------------------------

(defn valid?
  "Returns true if the effect is valid according to the Effect schema."
  [effect]
  (m/validate Effect effect {:registry registry}))

(defn explain
  "Returns an explanation map if the effect is invalid, nil otherwise."
  [effect]
  (m/explain Effect effect {:registry registry}))
