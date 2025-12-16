(ns bashketball-game-ui.game.peek-machine
  "Pure state machine for peek deck modal.

  Manages the multi-step peek flow:
  1. Select how many cards to peek (1-5)
  2. View cards, select each and assign destination (TOP/BOTTOM/DISCARD)
  3. Confirm when all cards placed

  This namespace has no dependencies on React or UI concerns.

  Use [[transition]] to compute next state from current state and event.
  Use [[valid-events]] to query which events are valid for a state.")

(def states
  "All valid peek machine states."
  #{:closed :select-count :place-cards})

(def events
  "All valid event types."
  #{:show :set-count :proceed :select-card :deselect-card :place-card :finish :cancel})

(def valid-destinations
  "Valid card placement destinations."
  #{"TOP" "BOTTOM" "DISCARD"})

(def default-count 3)
(def min-count 1)
(def max-count 5)

(def machine
  "State transition table.

  Keys are current states, values are maps of event-type to transition spec."
  {:closed
   {:show          :select-count
    :set-count     nil
    :proceed       nil
    :select-card   nil
    :deselect-card nil
    :place-card    nil
    :finish        nil
    :cancel        nil}

   :select-count
   {:show          nil
    :set-count     :update-count
    :proceed       :place-cards
    :select-card   nil
    :deselect-card nil
    :place-card    nil
    :finish        nil
    :cancel        :closed}

   :place-cards
   {:show          nil
    :set-count     nil
    :proceed       nil
    :select-card   :update-selected
    :deselect-card :clear-selected
    :place-card    :assign-placement
    :finish        [:action :resolve-peek]
    :cancel        :closed}})

(defn init
  "Returns initial peek machine state."
  []
  {:state :closed
   :data  nil})

(defn- clamp-count
  "Clamps count to valid range [1, 5]."
  [n]
  (-> n (max min-count) (min max-count)))

(defn all-placed?
  "Returns true if all cards have been assigned placements."
  [{:keys [cards placements]}]
  (and (seq cards)
       (= (count cards) (count placements))))

(defn can-finish?
  "Returns true if finish is valid in the given machine state."
  [{:keys [state data]}]
  (and (= state :place-cards)
       (all-placed? data)))

(defn valid-events
  "Returns set of valid events for the given machine state.

  Respects guards - :finish only valid when all cards placed."
  [{:keys [state] :as machine-state}]
  (let [base-events (->> (get machine state)
                         (keep (fn [[k v]] (when v k)))
                         set)]
    (if (and (= state :place-cards)
             (not (can-finish? machine-state)))
      (disj base-events :finish)
      base-events)))

(defn- build-placement-vec
  "Builds the placement vector for the action from cards and placements."
  [{:keys [cards placements]}]
  (mapv (fn [instance-id]
          {:instance-id instance-id
           :destination (get placements instance-id)})
        cards))

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

      (= spec :select-count)
      {:state :select-count
       :data  {:target-team (:team event-data)
               :count       default-count}}

      (= spec :update-count)
      {:state state
       :data  (assoc data :count (clamp-count (:count event-data)))}

      (= spec :place-cards)
      {:state :place-cards
       :data  (assoc data
                     :cards       (:cards event-data)
                     :selected-id nil
                     :placements  {})}

      (= spec :update-selected)
      {:state state
       :data  (assoc data :selected-id (:instance-id event-data))}

      (= spec :clear-selected)
      {:state state
       :data  (assoc data :selected-id nil)}

      (= spec :assign-placement)
      (let [destination (:destination event-data)]
        (if (and (:selected-id data)
                 (contains? valid-destinations destination))
          {:state state
           :data  (-> data
                      (assoc-in [:placements (:selected-id data)] destination)
                      (assoc :selected-id nil))}
          current))

      (= spec :closed)
      {:state :closed
       :data  nil}

      (vector? spec)
      (let [[_ action-type] spec]
        (if (can-finish? current)
          {:state  :closed
           :data   nil
           :action {:type        action-type
                    :target-team (:target-team data)
                    :count       (:count data)
                    :placements  (build-placement-vec data)}}
          current)))))
