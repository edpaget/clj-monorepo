(ns bashketball-game.polix.context
  "Document and context builders for polix policy evaluation.

  Provides functions to transform game state into flat documents suitable for
  policy evaluation. Documents use namespaced keys (`:doc/*`, `:action/*`,
  `:self/*`) that policies can reference."
  (:require [bashketball-game.state :as state]))

(defn- prefix-key
  "Adds a namespace prefix to a keyword."
  [prefix k]
  (keyword (name prefix) (name k)))

(defn- prefix-keys
  "Adds a namespace prefix to all keys in a map."
  [m prefix]
  (into {} (map (fn [[k v]] [(prefix-key prefix k) v]) m)))

(defn build-game-document
  "Builds a flat document from game state for policy evaluation.

  The document contains game-level fields under the `:doc/` namespace:
  - `:doc/phase` - Current game phase
  - `:doc/turn-number` - Current turn number
  - `:doc/active-player` - Team currently taking their turn
  - `:doc/score` - Score map {:team/HOME n :team/AWAY m}
  - `:doc/ball` - Ball state map
  - `:doc/state` - Full game state (for operators that need deep access)"
  [game-state]
  {:doc/phase (:phase game-state)
   :doc/turn-number (:turn-number game-state)
   :doc/active-player (:active-player game-state)
   :doc/score (:score game-state)
   :doc/ball (:ball game-state)
   :doc/state game-state})

(defn build-action-document
  "Builds a document for validating an action.

  Combines game document fields with action fields under `:action/` namespace.
  For example, a move action would add:
  - `:action/type` - The action type
  - `:action/player-id` - The player being moved
  - `:action/position` - The target position"
  [game-state action]
  (merge (build-game-document game-state)
         (prefix-keys action :action)))

(defn build-player-context
  "Builds context for a specific player.

  Adds fields under `:self/` namespace:
  - `:self/id` - Player ID
  - `:self/position` - Current position (or nil if off-court)
  - `:self/exhausted` - Whether player is exhausted
  - `:self/team` - Team the player belongs to
  - `:self/has-ball` - Whether player has the ball"
  [game-state player-id]
  (let [player   (state/get-basketball-player game-state player-id)
        ball     (state/get-ball game-state)
        has-ball (and (= (:status ball) :ball-status/POSSESSED)
                      (= (:holder-id ball) player-id))]
    {:self/id player-id
     :self/position (:position player)
     :self/exhausted (:exhausted player)
     :self/team (state/get-basketball-player-team game-state player-id)
     :self/has-ball has-ball}))

(defn build-trigger-document
  "Builds a document for trigger evaluation.

  Combines:
  - Game document fields (`:doc/*`)
  - Event fields (`:event/*`)
  - Self context for the trigger owner (`:self/*`)

  The `event` map should contain the event type and any event-specific data.
  The `self-id` is the player ID that owns the triggered ability."
  [game-state event self-id]
  (merge (build-game-document game-state)
         (prefix-keys event :event)
         (build-player-context game-state self-id)))

(defn build-effect-context
  "Builds a context map for effect execution.

  The context contains:
  - `:bindings` - Map of symbolic bindings (`:self`, `:target`, etc.)
  - `:source` - Identifier of what initiated this effect

  Effects can reference these bindings in their parameters."
  [{:keys [self-id target-id source]}]
  {:bindings (cond-> {}
               self-id (assoc :self self-id)
               target-id (assoc :target target-id))
   :source source})

(defn build-skill-test-document
  "Builds a document for skill test trigger evaluation.

  Combines:
  - Game document fields (`:doc/*`)
  - Skill test fields (`:test/*`)
  - Actor context (`:self/*`) for the player performing the test

  The test fields include:
  - `:test/id` - Unique skill test identifier
  - `:test/stat` - Stat being tested (:stat/SHOOTING, etc.)
  - `:test/type` - Test type from context (:shoot, :pass, etc.)
  - `:test/base-value` - Base stat value
  - `:test/modifiers` - Vector of modifier maps
  - `:test/fate` - Fate value (nil until revealed)
  - `:test/total` - Computed total (nil until resolved)
  - `:test/target-value` - Target number to beat (optional)"
  [game-state]
  (if-let [test (:pending-skill-test game-state)]
    (merge (build-game-document game-state)
           {:test/id (:id test)
            :test/actor-id (:actor-id test)
            :test/stat (:stat test)
            :test/type (get-in test [:context :type])
            :test/base-value (:base-value test)
            :test/modifiers (:modifiers test)
            :test/fate (:fate test)
            :test/total (:total test)
            :test/target-value (:target-value test)}
           (build-player-context game-state (:actor-id test)))
    (build-game-document game-state)))

(defn build-choice-document
  "Builds a document for choice-related trigger evaluation.

  Combines:
  - Game document fields (`:doc/*`)
  - Choice fields (`:choice/*`)

  The choice fields include:
  - `:choice/id` - Unique choice identifier
  - `:choice/type` - Choice type keyword
  - `:choice/waiting-for` - Team that needs to respond
  - `:choice/selected` - Selected option (after submission)"
  [game-state]
  (if-let [choice (:pending-choice game-state)]
    (merge (build-game-document game-state)
           {:choice/id (:id choice)
            :choice/type (:type choice)
            :choice/waiting-for (:waiting-for choice)
            :choice/selected (:selected choice)})
    (build-game-document game-state)))
