(ns polix.triggers.effects
  "Integration with polix.effects for effect application.

  Delegates to [[polix.effects.core/apply-effect]] to apply effects when
  triggers fire. This thin wrapper provides a stable interface for the
  trigger system while effect implementation details are handled by
  the polix.effects namespace."
  (:require [polix.effects.core :as fx]))

(defn apply-effect
  "Applies an effect via polix.effects.

  Takes state, effect definition, and context. Returns a result map with:

  - `:state` - The new state after applying the effect
  - `:applied` - Vector of effects that were successfully applied
  - `:failed` - Vector of failure info for effects that failed
  - `:pending` - Nil, or pending choice info

  The context map should contain:

  - `:state` - The current application state
  - `:event` - The triggering event
  - `:trigger` - The trigger being processed
  - `:self`, `:owner`, `:source` - Trigger binding context"
  [state effect ctx]
  (fx/apply-effect state effect ctx))
