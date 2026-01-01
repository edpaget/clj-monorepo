(ns bashketball-game.polix.core
  "Main entry point for bashketball polix integration.

  Provides [[initialize!]] to register all bashketball operators and effects
  with the polix libraries. Call once at application startup before evaluating
  any policies or applying any effects.

  ## Available Modules

  After calling [[initialize!]], the following modules are available:

  - [[bashketball-game.polix.operators]] - Game state operators (ZoC, size, stats)
  - [[bashketball-game.polix.effects]] - State mutation effects (modifiers, abilities)
  - [[bashketball-game.polix.zoc]] - Zone of Control policies
  - [[bashketball-game.polix.skill-tests]] - Skill test resolution with advantage
  - [[bashketball-game.polix.scoring]] - Scoring zones and distance modifiers
  - [[bashketball-game.polix.standard-action-policies]] - Standard action preconditions
  - [[bashketball-game.polix.phase-policies]] - Turn structure and phase transitions
  - [[bashketball-game.polix.phase-triggers]] - Phase transition automation

  For trigger integration, see [[bashketball-game.polix.triggers]]."
  (:require
   [bashketball-game.polix.effects :as effects]
   [bashketball-game.polix.functions :as functions]
   [bashketball-game.polix.operators :as operators]
   ;; Load policy modules to make them available
   [bashketball-game.polix.phase-policies]
   [bashketball-game.polix.phase-triggers]
   [bashketball-game.polix.scoring]
   [bashketball-game.polix.skill-tests]
   [bashketball-game.polix.standard-action-policies]
   [bashketball-game.polix.zoc]))

(defn initialize!
  "Initializes all bashketball polix integrations.

  Registers:
  - Bashketball operators with polix (see [[operators/register-operators!]])
  - Bashketball effects with polix (see [[effects/register-effects!]])
  - Bashketball functions for declarative rules (see [[functions/register-game-functions!]])

  Call once at application startup before evaluating any policies or
  applying any effects. Safe to call multiple times - operators, effects,
  and functions will be re-registered."
  []
  (operators/register-operators!)
  (effects/register-effects!)
  (functions/register-game-functions!))
