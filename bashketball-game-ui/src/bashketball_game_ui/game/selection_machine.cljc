(ns bashketball-game-ui.game.selection-machine
  "Pure state machine for UI selection modes.

  Defines valid states and transitions for player/ball/hex selection.
  This namespace has no dependencies on React or UI concerns.

  The machine handles the core interaction flow:
  - Player selection and movement
  - Ball selection and placement
  - Pass targeting
  - Standard action card selection

  Use [[transition]] to compute next state from current state and event.
  Use [[valid-events]] to query which events are valid for a state.")

(def states
  "All valid selection states."
  #{:idle
    :player-selected
    :ball-selected
    :targeting-pass
    :standard-action-selecting
    :standard-action-confirming})

(def events
  "All valid event types."
  #{:click-player
    :click-ball
    :click-hex
    :click-card
    :start-pass
    :enter-standard
    :toggle-standard-card
    :select-cards
    :select-action
    :back
    :escape})

(def machine
  "State transition table.

  Keys are current states, values are maps of event-type to transition spec.
  Transition spec can be:
  - keyword: transition to that state
  - vector `[:action action-type]`: emit action and transition to :idle
  - vector `[:action action-type next-state]`: emit action and transition to next-state
  - nil: invalid transition (stays in current state)"
  {:idle
   {:click-player        :player-selected
    :click-ball          :ball-selected
    :click-hex           nil
    :click-card          :toggle-card
    :start-pass          nil
    :enter-standard      :standard-action-selecting
    :toggle-standard-card nil
    :select-cards        nil
    :select-action       nil
    :back                nil
    :escape              :idle}

   :player-selected
   {:click-player        :player-selected
    :click-ball          :ball-selected
    :click-hex           [:action :move-player]
    :click-card          :toggle-card
    :start-pass          :targeting-pass
    :enter-standard      :standard-action-selecting
    :toggle-standard-card nil
    :select-cards        nil
    :select-action       nil
    :back                nil
    :escape              :idle}

   :ball-selected
   {:click-player        [:action :set-ball-possessed]
    :click-ball          :idle
    :click-hex           [:action :set-ball-loose]
    :click-card          nil
    :start-pass          nil
    :enter-standard      nil
    :toggle-standard-card nil
    :select-cards        nil
    :select-action       nil
    :back                nil
    :escape              :idle}

   :targeting-pass
   {:click-player        [:action :pass-to-player]
    :click-ball          nil
    :click-hex           [:action :pass-to-hex]
    :click-card          nil
    :start-pass          nil
    :enter-standard      nil
    :toggle-standard-card nil
    :select-cards        nil
    :select-action       nil
    :back                :player-selected
    :escape              :idle}

   :standard-action-selecting
   {:click-player        nil
    :click-ball          nil
    :click-hex           nil
    :click-card          nil
    :start-pass          nil
    :enter-standard      nil
    :toggle-standard-card :toggle-standard-card
    :select-cards        :standard-action-confirming
    :select-action       nil
    :back                nil
    :escape              :idle}

   :standard-action-confirming
   {:click-player        nil
    :click-ball          nil
    :click-hex           nil
    :click-card          nil
    :start-pass          nil
    :enter-standard      nil
    :toggle-standard-card nil
    :select-cards        nil
    :select-action       [:action :standard-action]
    :back                :standard-action-selecting
    :escape              :idle}})

(defn valid-events
  "Returns set of valid events for the given state.

  An event is valid if it has a non-nil transition defined."
  [state]
  (->> (get machine state)
       (filter (fn [[_ v]] (some? v)))
       (map first)
       set))

(defn- build-action
  "Builds an action map from transition spec and event data."
  [action-type from-data to-data]
  {:type action-type
   :from from-data
   :to   to-data})

(defn- toggle-in-set
  "Toggles a value in a set. Adds if not present, removes if present."
  [s v]
  (if (contains? s v)
    (disj s v)
    (conj (or s #{}) v)))

(defn- toggle-card-selection
  "Toggles card selection in data. If same card, deselect. Otherwise select."
  [data instance-id]
  (let [current (:selected-card data)]
    (assoc data :selected-card (if (= current instance-id) nil instance-id))))

(defn- toggle-standard-action-card
  "Toggles a card in the standard action cards set."
  [data instance-id]
  (update data :cards toggle-in-set instance-id))

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

      ;; Special self-transition for card selection toggle
      (= spec :toggle-card)
      {:state state
       :data  (toggle-card-selection data (:instance-id event-data))}

      ;; Special self-transition for standard action card toggle
      (= spec :toggle-standard-card)
      {:state state
       :data  (toggle-standard-action-card data (:instance-id event-data))}

      (keyword? spec)
      {:state spec
       :data  (if (= spec :idle)
                nil
                (merge data event-data))}

      (vector? spec)
      (let [[_ action-type next-state] spec
            target-state               (or next-state :idle)]
        {:state  target-state
         :data   (when (not= target-state :idle)
                   (merge data event-data))
         :action (build-action action-type data event-data)}))))

(defn init
  "Returns initial machine state."
  []
  {:state :idle
   :data  nil})
