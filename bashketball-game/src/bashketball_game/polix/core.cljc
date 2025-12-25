(ns bashketball-game.polix.core
  "Main entry point for bashketball polix integration.

  Provides [[initialize!]] to register all bashketball operators and effects
  with the polix libraries. Call once at application startup before evaluating
  any policies or applying any effects.

  For trigger integration, see [[bashketball-game.polix.triggers]]."
  (:require
   [bashketball-game.polix.effects :as effects]
   [bashketball-game.polix.operators :as operators]))

(defn initialize!
  "Initializes all bashketball polix integrations.

  Registers:
  - Bashketball operators with polix (see [[operators/register-operators!]])
  - Bashketball effects with polix (see [[effects/register-effects!]])

  Call once at application startup before evaluating any policies or
  applying any effects. Safe to call multiple times - operators and effects
  will be re-registered."
  []
  (operators/register-operators!)
  (effects/register-effects!))
