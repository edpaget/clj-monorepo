(ns bashketball-game-ui.game.skill-test-machine
  "Pure state machine for skill test resolution.

  Manages the skill test flow:
  1. Test initiated - waiting for fate reveal
  2. Fate revealed - showing result with modifiers
  3. Awaiting choice (if required by triggers/effects)
  4. Test resolved

  This namespace has no dependencies on React or UI concerns.

  Use [[transition]] to compute next state from current state and event.
  Use [[valid-events]] to query which events are valid for a state.")

(def states
  "All valid skill test machine states."
  #{:inactive :awaiting-fate :fate-revealed :awaiting-choice :resolved})

(def events
  "All valid event types."
  #{:initiate :reveal-fate :offer-choice :submit-choice :complete :cancel})

(def machine
  "State transition table.

  Keys are current states, values are maps of event-type to transition spec.
  A transition spec can be:
  - nil: event is ignored
  - keyword: next state
  - vector: [:action action-type] triggers an action and transitions"
  {:inactive
   {:initiate      :awaiting-fate
    :reveal-fate   nil
    :offer-choice  nil
    :submit-choice nil
    :complete      nil
    :cancel        nil}

   :awaiting-fate
   {:initiate      nil
    :reveal-fate   :fate-revealed
    :offer-choice  nil
    :submit-choice nil
    :complete      nil
    :cancel        :inactive}

   :fate-revealed
   {:initiate      nil
    :reveal-fate   nil
    :offer-choice  :awaiting-choice
    :submit-choice nil
    :complete      :resolved
    :cancel        nil}

   :awaiting-choice
   {:initiate      nil
    :reveal-fate   nil
    :offer-choice  nil
    :submit-choice [:action :submit-choice]
    :complete      nil
    :cancel        nil}

   :resolved
   {:initiate      nil
    :reveal-fate   nil
    :offer-choice  nil
    :submit-choice nil
    :complete      :inactive
    :cancel        nil}})

(defn init
  "Returns initial skill test machine state."
  []
  {:state :inactive
   :data  nil})

(defn valid-events
  "Returns set of valid events for the given machine state."
  [{:keys [state]}]
  (->> (get machine state)
       (keep (fn [[k v]] (when v k)))
       set))

(defn active?
  "Returns true if a skill test is in progress."
  [{:keys [state]}]
  (not= state :inactive))

(defn awaiting-input?
  "Returns true if the skill test is waiting for player input."
  [{:keys [state]}]
  (contains? #{:awaiting-fate :awaiting-choice} state))

(defn transition
  "Computes next state given current state and event.

  Takes current machine state as a map with `:state` and `:data` keys,
  and an event map with `:type` and optional `:data` keys.

  Returns a map with:
  - `:state` - the new state keyword
  - `:data` - skill test data (test info, fate value, modifiers, choice)
  - `:action` - action to dispatch (only present if transition triggers one)

  Event data fields:
  - `:initiate` - {:test-id :actor-id :stat :base-value :target-value :context}
  - `:reveal-fate` - {:fate fate-value}
  - `:offer-choice` - {:choice-id :choice-type :options :waiting-for}
  - `:submit-choice` - {:selected option-id}"
  [{:keys [state data] :as current} {:keys [type] :as event}]
  (let [event-data (:data event)
        spec       (get-in machine [state type])]
    (cond
      (nil? spec)
      current

      ;; Start skill test
      (and (= state :inactive) (= type :initiate))
      {:state :awaiting-fate
       :data  (select-keys event-data [:test-id :actor-id :stat :base-value
                                       :target-value :context :modifiers])}

      ;; Fate revealed
      (and (= state :awaiting-fate) (= type :reveal-fate))
      {:state :fate-revealed
       :data  (assoc data :fate (:fate event-data))}

      ;; Choice offered
      (and (= state :fate-revealed) (= type :offer-choice))
      {:state :awaiting-choice
       :data  (assoc data
                     :choice-id (:choice-id event-data)
                     :choice-type (:choice-type event-data)
                     :options (:options event-data)
                     :waiting-for (:waiting-for event-data))}

      ;; Choice submitted - produces action
      (and (= state :awaiting-choice) (= type :submit-choice) (vector? spec))
      (let [[_ action-type] spec]
        {:state  :fate-revealed
         :data   (-> data
                     (assoc :selected (:selected event-data))
                     (dissoc :choice-id :choice-type :options :waiting-for))
         :action {:type      action-type
                  :choice-id (:choice-id data)
                  :selected  (:selected event-data)}})

      ;; Complete from fate-revealed -> resolved
      (and (= state :fate-revealed) (= type :complete))
      {:state :resolved
       :data  data}

      ;; Complete from resolved -> inactive
      (and (= state :resolved) (= type :complete))
      {:state :inactive
       :data  nil}

      ;; Cancel
      (= spec :inactive)
      {:state :inactive
       :data  nil}

      ;; Default keyword transition
      (keyword? spec)
      {:state spec
       :data  data})))
