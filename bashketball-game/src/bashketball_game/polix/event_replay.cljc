(ns bashketball-game.polix.event-replay
  "Event replay via effects.

  Maps logged events to terminal effects for state reconstruction.
  Used by [[bashketball-game.event-log/replay-events]] to rebuild game
  state from event history without using the legacy action system.

  Replay skips triggers and validation for performance - events are
  assumed to be valid since they were produced by valid game operations."
  (:require
   [bashketball-game.polix.core :as polix]
   [polix.effects.core :as fx]))

(defn event->effect
  "Maps an event to its corresponding terminal effect for replay.

  Returns an effect map that can be applied via `fx/apply-effect` to
  reproduce the state change recorded by the event. Returns `nil` for
  events that don't require replay (informational events)."
  [event]
  (case (:type event)
    ;; Movement effects
    :bashketball/move-player
    {:type :bashketball/move-player
     :player-id (:player-id event)
     :position (:to-position event)}

    :bashketball/begin-movement
    {:type :bashketball/begin-movement
     :player-id (:player-id event)
     :speed (:speed event)}

    :bashketball/move-step
    {:type :bashketball/do-move-step
     :player-id (:player-id event)
     :to-position (:to-position event)
     :cost (:cost event)}

    :bashketball/end-movement
    {:type :bashketball/end-movement
     :player-id (:player-id event)}

    ;; Player state effects
    :bashketball/exhaust-player
    {:type :bashketball/do-exhaust-player
     :player-id (:player-id event)}

    :bashketball/refresh-player
    {:type :bashketball/do-refresh-player
     :player-id (:player-id event)}

    ;; Ball effects
    :bashketball/set-ball-possessed
    {:type :bashketball/do-set-ball-possessed
     :holder-id (:holder-id event)}

    :bashketball/set-ball-loose
    {:type :bashketball/do-set-ball-loose
     :position (:position event)}

    ;; Card effects
    :bashketball/draw-cards
    {:type :bashketball/do-draw-cards
     :player (:player event)
     :count (:count event)}

    :bashketball/discard-cards
    {:type :bashketball/do-discard-cards
     :player (:player event)
     :instance-ids (:instance-ids event)}

    ;; Score effects
    :bashketball/add-score
    {:type :bashketball/do-add-score
     :team (:team event)
     :points (:points event)}

    ;; Modifier effects
    :bashketball/add-modifier
    {:type :bashketball/do-add-modifier
     :player-id (:player-id event)
     :stat (:stat event)
     :amount (:amount event)
     :source (:source event)
     :expires-at (:expires-at event)}

    :bashketball/remove-modifier
    {:type :bashketball/do-remove-modifier
     :player-id (:player-id event)
     :modifier-id (:modifier-id event)}

    :bashketball/clear-modifiers
    {:type :bashketball/do-clear-modifiers
     :player-id (:player-id event)}

    ;; Phase/turn effects
    :bashketball/set-phase
    {:type :bashketball/do-set-phase
     :phase (:phase event)}

    :bashketball/advance-turn
    {:type :bashketball/do-advance-turn}

    ;; Ability attachment effects
    :bashketball/attach-ability
    {:type :bashketball/attach-ability
     :player (:player event)
     :instance-id (:instance-id event)
     :target-player-id (:target-player-id event)}

    :bashketball/detach-ability
    {:type :bashketball/detach-ability
     :player (:player event)
     :target-player-id (:target-player-id event)
     :instance-id (:instance-id event)}

    ;; Skill test effects
    :bashketball/initiate-skill-test
    {:type :bashketball/initiate-skill-test
     :skill-test (:skill-test event)}

    :bashketball/modify-skill-test
    {:type :bashketball/modify-skill-test
     :stat (:stat event)
     :amount (:amount event)
     :source (:source event)}

    ;; Choice effects
    :bashketball/offer-choice
    {:type :bashketball/offer-choice
     :choice-type (:choice-type event)
     :options (:options event)
     :waiting-for (:waiting-for event)}

    :bashketball/force-choice
    {:type :bashketball/force-choice
     :choice-type (:choice-type event)
     :options (:options event)
     :waiting-for (:waiting-for event)}

    :bashketball/check-hand-limit
    {:type :bashketball/check-hand-limit
     :player (:player event)}

    ;; Informational events - no replay needed
    nil))

(def ^:private replay-opts
  "Options for replay - skip validation and registry updates."
  {:validate? false})

(defn replay-event
  "Replays a single event, returning the new state.

  Returns the original state unchanged if the event doesn't map to
  an effect (informational events)."
  [state event]
  (if-let [effect (event->effect event)]
    (:state (fx/apply-effect state effect {} replay-opts))
    state))

(defn replay-events
  "Replays a sequence of events to rebuild game state.

  Takes an initial game state and a sequence of events, applies each
  event's corresponding effect in order, and returns the final state.

  Skips triggers and validation for performance. The events vector
  in the returned state will be empty - call this function when you
  need to reconstruct state without preserving history."
  [initial-state events]
  (reduce replay-event initial-state events))
