(ns polix-triggers.schema
  "Malli schemas for the polix-triggers event system.

  Defines the core data structures for triggers, events, and processing results.
  All enum values use namespaced keywords following the `polix-triggers.<category>/value`
  pattern for clarity and collision avoidance."
  (:require [malli.core :as m]))

(def Timing
  "Trigger timing determines when it fires relative to the event.

  - `:polix-triggers.timing/before` - fires before action resolves, can prevent
  - `:polix-triggers.timing/instead` - replaces the action entirely
  - `:polix-triggers.timing/after` - fires after action resolves
  - `:polix-triggers.timing/at` - fires at specific moments (phase transitions)"
  [:enum
   :polix-triggers.timing/before
   :polix-triggers.timing/instead
   :polix-triggers.timing/after
   :polix-triggers.timing/at])

(def TriggerDef
  "Template for defining a trigger before registration.

  Event types are keywords defined by the consumer domain (e.g., `:entity/damaged`).
  The condition is a polix policy expression evaluated against the trigger document."
  [:map
   [:event-types [:set :keyword]]
   [:timing Timing]
   [:condition {:optional true} :any]
   [:effect :any]
   [:once? {:optional true} :boolean]
   [:priority {:optional true} :int]
   [:replacement? {:optional true} :boolean]])

(def Trigger
  "Fully instantiated trigger with ID and binding context.

  Created by [[polix-triggers.registry/register-trigger]] from a [[TriggerDef]].
  The `:condition-fn` is the compiled condition function for efficient evaluation."
  [:map
   [:id :string]
   [:source :any]
   [:owner :any]
   [:self {:optional true} :any]
   [:event-types [:set :keyword]]
   [:timing Timing]
   [:condition {:optional true} :any]
   [:condition-fn {:optional true} fn?]
   [:effect :any]
   [:once? {:optional true} :boolean]
   [:priority {:optional true} :int]
   [:replacement? {:optional true} :boolean]])

(def Event
  "Minimal event structure requiring only a `:type` keyword.

  Consumers extend with domain-specific fields. Event types should be namespaced
  keywords following domain conventions (e.g., `:entity/damaged`, `:turn/started`)."
  [:map
   [:type :keyword]])

(def TriggerResult
  "Result of processing a single trigger during event firing.

  The `:condition-result` may be `true`, `false`, or a map with `:residual` for
  partial evaluation when the document lacks required keys."
  [:map
   [:trigger-id :string]
   [:fired? :boolean]
   [:condition-result [:or :boolean [:map [:residual :any]]]]
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
  for each trigger, and whether the action was prevented by a before trigger."
  [:map
   [:state :any]
   [:registry Registry]
   [:event Event]
   [:results [:vector TriggerResult]]
   [:prevented? :boolean]])
