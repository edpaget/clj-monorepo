(ns bashketball-game.polix.core
  "Main entry point for bashketball polix integration.

  Provides [[initialize!]] to register all bashketball operators and effects
  with the polix libraries. Call once at application startup before evaluating
  any policies or applying any effects."
  (:require
   [bashketball-game.polix.operators :as operators]
   [bashketball-game.polix.effects :as effects]))

(defn initialize!
  "Initializes all bashketball polix integrations.

  Registers:
  - Bashketball operators with polix (see [[operators/register-operators!]])
  - Bashketball effects with polix-effects (see [[effects/register-effects!]])

  Call once at application startup before evaluating any policies or
  applying any effects. Safe to call multiple times - operators and effects
  will be re-registered."
  []
  (operators/register-operators!)
  (effects/register-effects!))
