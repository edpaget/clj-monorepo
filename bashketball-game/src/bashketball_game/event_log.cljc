(ns bashketball-game.event-log
  "Event log utilities for querying and replaying game events.

  The event log is an append-only vector stored in the game state under :events.
  Each event is the action map with a :timestamp added."
  (:require [bashketball-game.actions :as actions]))

(defn get-events
  "Returns all events from the game state, optionally filtered by type.

  When called with just state, returns all events.
  When called with state and type, returns only events of that type."
  ([state]
   (:events state))
  ([state event-type]
   (filter #(= (:type %) event-type) (:events state))))

(defn get-events-since
  "Returns all events after the given timestamp."
  [state timestamp]
  (drop-while #(not (pos? (compare (:timestamp %) timestamp)))
              (:events state)))

(defn get-events-by-player
  "Returns all events involving a specific basketball player."
  [state player-id]
  (filter (fn [event]
            (or (= (:player-id event) player-id)
                (= (:holder-id event) player-id)
                (= (:on-court-id event) player-id)
                (= (:off-court-id event) player-id)))
          (:events state)))

(defn get-events-by-team
  "Returns all events involving a specific team."
  [state team]
  (filter (fn [event]
            (or (= (:player event) team)
                (= (:team event) team)))
          (:events state)))

(defn event-count
  "Returns the total number of events in the game state."
  [state]
  (count (:events state)))

(defn last-event
  "Returns the most recent event, or nil if no events."
  [state]
  (peek (:events state)))

(defn replay-actions
  "Replays a sequence of actions to rebuild game state.

  Takes an initial game state and a sequence of actions (without timestamps),
  applies each action in order, and returns the final state.

  Uses [[actions/do-action]] since triggers should not fire during replay."
  [initial-state actions]
  (reduce actions/do-action initial-state actions))

(defn actions-from-events
  "Extracts the action data from events (removes :timestamp).

  Useful for serializing actions separately from their timestamps."
  [events]
  (mapv #(dissoc % :timestamp) events))
