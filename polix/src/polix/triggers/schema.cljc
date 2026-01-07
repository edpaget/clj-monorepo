(ns polix.triggers.schema
  "Malli schemas for the trigger event system.

  Defines the core data structures for triggers, events, and processing results.
  All enum values use namespaced keywords following the `polix.triggers.<category>/value`
  pattern for clarity and collision avoidance.

  See [[polix.triggers.core]] for the public API."
  (:require [malli.core :as m]))

(def Timing
  "Trigger timing determines when it fires relative to the event.

  - `:polix.triggers.timing/before` - fires before action resolves, can prevent
  - `:polix.triggers.timing/instead` - replaces the action entirely
  - `:polix.triggers.timing/after` - fires after action resolves
  - `:polix.triggers.timing/at` - fires at specific moments (phase transitions)"
  [:enum
   :polix.triggers.timing/before
   :polix.triggers.timing/instead
   :polix.triggers.timing/after
   :polix.triggers.timing/at])

(def TriggerDef
  "Template for defining a trigger before registration.

  Event types are keywords defined by the consumer domain (e.g., `:entity/damaged`).
  The condition is a polix policy expression evaluated via [[polix.unify/unify]]
  against the trigger document built from event and trigger context.

  Response triggers (`:response? true`) are not fired automatically. Instead, they
  are returned from [[fire-request-event]] for the caller to present as player choices."
  [:map
   [:event-types [:set :keyword]]
   [:timing Timing]
   [:condition {:optional true} :any]
   [:effect :any]
   [:once? {:optional true} :boolean]
   [:priority {:optional true} :int]
   [:replacement? {:optional true} :boolean]
   [:response? {:optional true} :boolean]])

(def Trigger
  "Fully instantiated trigger with ID and binding context.

  Created by [[polix.triggers.registry/register-trigger]] from a [[TriggerDef]].
  The `:condition` is the raw polix policy expression evaluated lazily via
  [[polix.unify/unify]] when the trigger is processed.

  Response triggers (`:response? true`) are returned from [[fire-request-event]]
  instead of being fired automatically, allowing callers to present player choices."
  [:map
   [:id :string]
   [:source :any]
   [:owner :any]
   [:self {:optional true} :any]
   [:event-types [:set :keyword]]
   [:timing Timing]
   [:condition {:optional true} :any]
   [:effect :any]
   [:once? {:optional true} :boolean]
   [:priority {:optional true} :int]
   [:replacement? {:optional true} :boolean]
   [:response? {:optional true} :boolean]])

(def Event
  "Minimal event structure requiring only a `:type` keyword.

  Consumers extend with domain-specific fields. Event types should be namespaced
  keywords following domain conventions (e.g., `:entity/damaged`, `:turn/started`)."
  [:map
   [:type :keyword]])

(def ConditionResult
  "Result of evaluating a trigger condition.

  - `:satisfied` - condition met, trigger fires
  - `:conflict` - condition violated, trigger does not fire
  - `:open` - condition has unresolved constraints, trigger does not fire (conservative)"
  [:enum :satisfied :conflict :open])

(def TriggerResult
  "Result of processing a single trigger during event firing.

  The `:condition-result` is one of `:satisfied`, `:conflict`, or `:open`."
  [:map
   [:trigger-id :string]
   [:fired? :boolean]
   [:condition-result ConditionResult]
   [:effect-result {:optional true} :any]
   [:removed? :boolean]])

(def Registry
  "Trigger registry containing all registered triggers with event-type index.

  The `:index-by-event` map provides O(1) lookup of trigger IDs by event type."
  [:map
   [:triggers [:map-of :string Trigger]]
   [:index-by-event [:map-of :keyword [:set :string]]]])

(def FireEventResult
  "Result of firing an event through the trigger system.

  Contains the updated state and registry, the original event, processing results
  for each trigger, whether the action was prevented, and any response triggers
  that matched but were not automatically fired."
  [:map
   [:state :any]
   [:registry Registry]
   [:event Event]
   [:results [:vector TriggerResult]]
   [:prevented? :boolean]
   [:response-triggers {:optional true} [:vector Trigger]]])
