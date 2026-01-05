(ns bashketball-game.polix.terminal-utils
  "Shared utilities for terminal effects.

  Provides event logging and board invariant checking functions that
  replace the functionality from `apply-action-impl`. Terminal effects
  call these utilities to maintain event history and ensure board validity."
  (:require [bashketball-game.board :as board]))

(defn now
  "Returns the current timestamp as an ISO-8601 string."
  []
  #?(:clj (.toString (java.time.Instant/now))
     :cljs (.toISOString (js/Date.))))

(defn generate-id
  "Generates a random UUID string."
  []
  #?(:clj (str (java.util.UUID/randomUUID))
     :cljs (str (random-uuid))))

(defn log-event
  "Appends an event to the game state's event log.

  Takes the game state, event type keyword, and event data map.
  Returns the updated state with the event appended to `:events`.

  The event is automatically timestamped."
  [state event-type event-data]
  (let [event (assoc event-data
                     :type event-type
                     :timestamp (now))]
    (update state :events conj event)))

(defn check-board-invariants!
  "Validates board occupancy invariants. Throws on violation.

  Checks that no occupant ID appears in multiple positions on the board.
  Call this after any terminal effect that modifies the `:board`."
  [state context]
  (when-let [invariant-error (board/check-occupant-invariants (:board state))]
    (throw (ex-info "Board invariant violation: duplicate occupant IDs"
                    {:context context
                     :error invariant-error})))
  state)
