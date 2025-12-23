(ns polix-triggers.effects
  "Integration with polix-effects for effect application.

  Delegates to [[polix-effects.core/apply-effect]] to actually apply
  effects when triggers fire."
  (:require [polix-effects.core :as fx]))

(defn apply-effect
  "Applies an effect via polix-effects.

  Takes state, effect definition, and context. Returns a result map with:
  - `:state` - The new state after applying the effect
  - `:applied` - Vector of effects that were successfully applied
  - `:failed` - Vector of failure info for effects that failed
  - `:pending` - Nil, or pending choice info"
  [state effect ctx]
  (fx/apply-effect state effect ctx))
