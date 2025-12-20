(ns polix-effects.core
  "Core API for applying effects to state.

  Effects are pure data structures describing state mutations. This namespace
  provides [[apply-effect]] as the primary entry point for effect application,
  with optional schema validation.

  Built-in effect types use the `:polix-effects/*` namespace:
  - `:polix-effects/noop` - Does nothing
  - `:polix-effects/sequence` - Applies effects in order
  - `:polix-effects/assoc-in` - Sets a value at a path
  - `:polix-effects/update-in` - Updates a value at a path with a function
  - `:polix-effects/dissoc-in` - Removes a value at a path

  Domain-specific effects can be registered via [[register-effect!]]."
  (:require [polix-effects.registry :as registry]
            [polix-effects.resolution :as res]
            [polix-effects.schema :as schema]))

;;; ---------------------------------------------------------------------------
;;; Re-exports
;;; ---------------------------------------------------------------------------

(def register-effect!
  "Registers a custom effect handler. See [[polix-effects.registry/register-effect!]]."
  registry/register-effect!)

(def effect-types
  "Returns set of registered effect types. See [[polix-effects.registry/effect-types]]."
  registry/effect-types)

(def success
  "Creates a successful result. See [[polix-effects.registry/success]]."
  registry/success)

(def failure
  "Creates a failed result. See [[polix-effects.registry/failure]]."
  registry/failure)

(def default-resolver
  "The default reference resolver. See [[polix-effects.resolution/default-resolver]]."
  res/default-resolver)

(def resolve-ref
  "Resolves a reference within context. See [[polix-effects.resolution/resolve-ref]]."
  res/resolve-ref)

(def resolve-fn
  "Resolves a function reference. See [[polix-effects.resolution/resolve-fn]]."
  res/resolve-fn)

(def resolve-predicate
  "Resolves a predicate specification. See [[polix-effects.resolution/resolve-predicate]]."
  res/resolve-predicate)

;;; ---------------------------------------------------------------------------
;;; Validation
;;; ---------------------------------------------------------------------------

(defn validate-effect
  "Validates an effect against the schema.

  Returns nil if valid, or an explanation map if invalid."
  [effect]
  (schema/explain effect))

;;; ---------------------------------------------------------------------------
;;; Core API
;;; ---------------------------------------------------------------------------

(defn apply-effect
  "Applies an effect to state within context.

  Takes a state map, an effect data structure, an optional context map, and
  optional options. Returns a result map with:

  - `:state` - The new state after applying the effect
  - `:applied` - Vector of effects that were successfully applied
  - `:failed` - Vector of `{:effect ... :error ... :message ...}` for failures
  - `:pending` - Nil, or pending choice info

  The context map can contain:
  - `:bindings` - Map of symbolic bindings (`:self`, `:target`, etc.)
  - `:source` - Identifier of what initiated this effect

  Options:
  - `:validate?` - Validate effect against schema (default true)"
  ([state effect]
   (apply-effect state effect {} {}))
  ([state effect ctx]
   (apply-effect state effect ctx {}))
  ([state effect ctx opts]
   (let [validate? (get opts :validate? true)]
     (cond
       (and validate? (not (schema/valid? effect)))
       (registry/failure state effect :invalid-effect
                         "Effect failed schema validation")

       :else
       (let [ctx (assoc ctx :state state)]
         (registry/-apply-effect state effect ctx opts))))))

(defn apply-effects
  "Applies multiple effects in sequence.

  Equivalent to wrapping effects in a `:polix-effects/sequence` effect.
  Stops on first pending effect. Accumulates all applied and failed effects."
  ([state effects]
   (apply-effects state effects {} {}))
  ([state effects ctx]
   (apply-effects state effects ctx {}))
  ([state effects ctx opts]
   (apply-effect state {:type :polix-effects/sequence :effects (vec effects)} ctx opts)))
