(ns polix-triggers.effects
  "Stub interface for polix-effects integration.

  This module provides a minimal stub implementation of effect application,
  allowing polix-triggers to be tested independently of polix-effects. Replace
  with the real polix-effects integration when that library is implemented.")

(defn apply-effect
  "Stub for polix-effects integration.

  In the real implementation, this delegates to polix-effects to apply the
  effect to the state. This stub simply records the effect as applied without
  actually modifying the state.

  Returns a result map with:
  - `:state` - the (unchanged) state
  - `:applied` - vector containing the effect
  - `:failed` - empty vector
  - `:pending` - nil"
  [state effect ctx]
  {:state state
   :applied [effect]
   :failed []
   :pending nil})
