(ns bashketball-game-ui.game.substitute-machine
  "Pure state machine for player substitution.

  Manages the two-step substitution flow:
  1. Select on-court player to remove
  2. Select off-court player to bring in

  This namespace has no dependencies on React or UI concerns.

  Use [[transition]] to compute next state from current state and event.
  Use [[valid-events]] to query which events are valid for a state.")

(def states
  "All valid substitute machine states."
  #{:inactive :selecting-on-court :selecting-off-court})

(def events
  "All valid event types."
  #{:enter :select-on-court :select-off-court :back :cancel})

(def machine
  "State transition table.

  Keys are current states, values are maps of event-type to transition spec."
  {:inactive
   {:enter            :selecting-on-court
    :select-on-court  nil
    :select-off-court nil
    :back             nil
    :cancel           nil}

   :selecting-on-court
   {:enter            nil
    :select-on-court  :selecting-off-court
    :select-off-court nil
    :back             nil
    :cancel           :inactive}

   :selecting-off-court
   {:enter            nil
    :select-on-court  nil
    :select-off-court [:action :substitute]
    :back             :selecting-on-court
    :cancel           :inactive}})

(defn init
  "Returns initial substitute machine state."
  []
  {:state :inactive
   :data  nil})

(defn valid-events
  "Returns set of valid events for the given machine state."
  [{:keys [state]}]
  (->> (get machine state)
       (keep (fn [[k v]] (when v k)))
       set))

(defn transition
  "Computes next state given current state and event.

  Takes current machine state as a map with `:state` and `:data` keys,
  and an event map with `:type` and optional `:data` keys.

  Returns a map with:
  - `:state` - the new state keyword
  - `:data` - preserved/updated selection data
  - `:action` - action to dispatch (only present if transition triggers one)"
  [{:keys [state data] :as current} {:keys [type] :as event}]
  (let [event-data (:data event)
        spec       (get-in machine [state type])]
    (cond
      (nil? spec)
      current

      (= spec :selecting-on-court)
      {:state :selecting-on-court
       :data  nil}

      (= spec :selecting-off-court)
      {:state :selecting-off-court
       :data  {:on-court-id (:player-id event-data)}}

      (= spec :inactive)
      {:state :inactive
       :data  nil}

      (vector? spec)
      (let [[_ action-type] spec]
        {:state  :inactive
         :data   nil
         :action {:type         action-type
                  :on-court-id  (:on-court-id data)
                  :off-court-id (:player-id event-data)}}))))
