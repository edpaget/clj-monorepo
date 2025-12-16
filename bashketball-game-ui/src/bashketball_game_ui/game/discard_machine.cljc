(ns bashketball-game-ui.game.discard-machine
  "Pure state machine for discard mode.

  Manages the card discard selection flow where users select cards from
  their hand to discard. This namespace has no dependencies on React or
  UI concerns.

  Use [[transition]] to compute next state from current state and event.
  Use [[valid-events]] to query which events are valid for a state.")

(def states
  "All valid discard machine states."
  #{:inactive :selecting})

(def events
  "All valid event types."
  #{:enter :toggle-card :submit :cancel})

(def machine
  "State transition table.

  Keys are current states, values are maps of event-type to transition spec.
  Transition spec can be:
  - keyword: transition to that state
  - `:toggle-card`: special handler for toggling card selection
  - `[:action action-type]`: emit action and transition to :inactive
  - nil: invalid transition"
  {:inactive
   {:enter       :selecting
    :toggle-card nil
    :submit      nil
    :cancel      nil}

   :selecting
   {:enter       nil
    :toggle-card :toggle-card
    :submit      [:action :discard-cards]
    :cancel      :inactive}})

(defn init
  "Returns initial discard machine state."
  []
  {:state :inactive
   :data  nil})

(defn- toggle-card-in-set
  "Toggles an instance-id in the cards set."
  [cards instance-id]
  (let [cards (or cards #{})]
    (if (contains? cards instance-id)
      (disj cards instance-id)
      (conj cards instance-id))))

(defn can-submit?
  "Returns true if submit is valid in the given machine state.

  Submit requires at least one card selected."
  [{:keys [state data]}]
  (and (= state :selecting)
       (seq (:cards data))))

(defn valid-events
  "Returns set of valid events for the given machine state.

  Respects guards - :submit only valid when cards selected."
  [{:keys [state] :as machine-state}]
  (let [base-events (->> (get machine state)
                         (keep (fn [[k v]] (when v k)))
                         set)]
    (if (and (= state :selecting)
             (not (can-submit? machine-state)))
      (disj base-events :submit)
      base-events)))

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

      (= spec :toggle-card)
      {:state state
       :data  (update data :cards toggle-card-in-set (:instance-id event-data))}

      (= spec :selecting)
      {:state :selecting
       :data  {:cards #{}}}

      (= spec :inactive)
      {:state :inactive
       :data  nil}

      (vector? spec)
      (let [[_ action-type] spec]
        (if (can-submit? current)
          {:state  :inactive
           :data   nil
           :action {:type  action-type
                    :cards (:cards data)}}
          current)))))
