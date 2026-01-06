(ns bashketball-game.polix.event-replay
  "Event replay via effects.

  Maps logged events to terminal effects for state reconstruction.
  Used by [[bashketball-game.event-log/replay-events]] to rebuild game
  state from event history without using the legacy action system.

  Replay skips triggers and validation for performance - events are
  assumed to be valid since they were produced by valid game operations."
  (:require
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
     :instance-id (:instance-id event)
     :target-player-id (:target-player-id event)}

    ;; Skill test effects
    :bashketball/initiate-skill-test
    {:type :bashketball/initiate-skill-test
     :actor-id (:actor-id event)
     :stat (:stat event)
     :target-value (:target-value event)
     :context (:context event)}

    :bashketball/modify-skill-test
    {:type :bashketball/modify-skill-test
     :source (:source event)
     :amount (:amount event)
     :advantage (:advantage event)
     :reason (:reason event)}

    :bashketball/set-skill-test-fate
    {:type :bashketball/do-set-skill-test-fate
     :fate (:fate event)}

    :bashketball/resolve-skill-test
    {:type :bashketball/do-resolve-skill-test}

    :bashketball/clear-skill-test
    {:type :bashketball/do-clear-skill-test}

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
     :team (:team event)}

    :bashketball/submit-choice
    {:type :bashketball/do-submit-choice
     :choice-id (:choice-id event)
     :selected (:selected event)}

    :bashketball/clear-choice
    {:type :bashketball/do-clear-choice}

    ;; Turn/phase management effects
    :bashketball/set-active-player
    {:type :bashketball/do-set-active-player
     :player (:player event)}

    :bashketball/start-from-tipoff
    {:type :bashketball/start-from-tipoff
     :player (:player event)}

    ;; Player resource effects
    :bashketball/set-actions
    {:type :bashketball/do-set-actions
     :player (:player event)
     :amount (:amount event)}

    :bashketball/remove-cards
    {:type :bashketball/do-remove-cards
     :player (:player event)
     :instance-ids (:instance-ids event)}

    :bashketball/refresh-all
    {:type :bashketball/refresh-all
     :team (:team event)}

    :bashketball/reveal-fate
    {:type :bashketball/do-reveal-fate
     :player (:player event)}

    ;; Deck management effects
    :bashketball/shuffle-deck
    {:type :bashketball/do-shuffle-deck
     :player (:player event)}

    :bashketball/return-discard
    {:type :bashketball/do-return-discard
     :player (:player event)}

    ;; Stack effects
    :bashketball/push-stack
    {:type :bashketball/do-push-stack
     :effect (:effect event)}

    :bashketball/pop-stack
    {:type :bashketball/do-pop-stack}

    :bashketball/clear-stack
    {:type :bashketball/do-clear-stack}

    ;; Ball state effects
    :bashketball/set-ball-in-air
    {:type :bashketball/do-set-ball-in-air
     :origin (:origin event)
     :target (:target event)
     :action-type (:action-type event)}

    ;; Card lifecycle effects
    :bashketball/stage-card
    {:type :bashketball/do-stage-card
     :player (:player event)
     :instance-id (get-in event [:staged-card :instance-id])}

    :bashketball/stage-virtual-standard-action
    {:type :bashketball/do-stage-virtual-standard-action
     :player (get-in event [:virtual-card :played-by])
     :discard-instance-ids (mapv :instance-id (:discarded-cards event))
     :card-slug (get-in event [:virtual-card :card-slug])}

    :bashketball/resolve-card
    {:type :bashketball/do-resolve-card
     :instance-id (get-in event [:resolved-card :instance-id])
     :target-player-id (:target-player-id event)}

    :bashketball/move-asset
    {:type :bashketball/do-move-asset
     :player (:player event)
     :instance-id (:instance-id event)
     :destination (:destination event)}

    :bashketball/create-token
    {:type :bashketball/do-create-token
     :player (:player event)
     :card (get-in event [:created-token :card])
     :placement (:placement event)
     :target-player-id (:target-player-id event)}

    :bashketball/substitute
    {:type :bashketball/do-substitute
     :on-court-id (:on-court-id event)
     :off-court-id (:off-court-id event)}

    ;; Examine cards effects
    :bashketball/examine-cards
    {:type :bashketball/do-examine-cards
     :player (:player event)
     :count (:count event)}

    :bashketball/resolve-examined-cards
    {:type :bashketball/do-resolve-examined-cards
     :player (:player event)
     :placements (:placements event)}

    ;; Informational events - no replay needed
    :bashketball/record-skill-test nil

    ;; Default - unknown events return nil
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
