(ns bashketball-game.polix.effects
  "Bashketball-specific effects for polix.

  Registers effects that wrap existing action handlers. Effects are declarative
  descriptions of state mutations that can be stored in card EDN and resolved
  at runtime. Use [[register-effects!]] at application startup.

  ## Effect Types

  There are two categories of effects:

  **Request Effects** (e.g., `:bashketball/draw-cards`):
  Fire events through the trigger system, allowing card abilities to intercept.

  **Terminal Effects** (e.g., `:bashketball/do-draw-cards`):
  Directly modify state. Only called by catchall game rules or internally."
  (:require
   [bashketball-game.actions :as actions]
   [bashketball-game.polix.functions :as functions]
   [bashketball-game.polix.standard-action-policies :as sap]
   [bashketball-game.polix.standard-action-resolution :as sar]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [polix.effects.core :as fx]))

(defn normalize-effect
  "Normalizes an effect definition from schema format to runtime format.

   Converts `:effect/type` to `:type` recursively through nested effects.
   This allows effects defined with the schema key to be applied via polix."
  [effect]
  (when effect
    (cond-> effect
      (:effect/type effect) (-> (assoc :type (:effect/type effect))
                                (dissoc :effect/type))
      (:effects effect)     (update :effects #(mapv normalize-effect %)))))

(defn- resolve-param
  "Resolves a parameter value from context bindings.

  Handles:
  - Keywords: looked up in context bindings, returned as-is if not found
  - Vectors starting with :ctx: resolved from context path
  - Vectors starting with :state: resolved from state path
  - Vectors starting with :bashketball-fn/...: calls registered function
  - Other values: returned as-is"
  [param ctx state]
  (cond
    (and (keyword? param)
         (contains? (:bindings ctx) param))
    (get-in ctx [:bindings param])

    (and (vector? param)
         (= :ctx (first param)))
    (get-in ctx (rest param))

    (and (vector? param)
         (= :state (first param)))
    (get-in state (rest param))

    (and (vector? param)
         (keyword? (first param))
         (= "bashketball-fn" (namespace (first param))))
    (let [[fn-key & args] param
          resolved-args   (mapv #(resolve-param % ctx state) args)
          f               (functions/get-fn fn-key)]
      (if f
        (apply f state ctx resolved-args)
        (throw (ex-info "Unknown function" {:fn-key fn-key}))))

    :else
    param))

(defn register-effects!
  "Registers all bashketball effects with polix.

  Call once at application startup before applying any effects.

  **Request Effects** (fire events through triggers):
  - `:bashketball/draw-cards` - Draw cards (fires event if registry provided)

  **Terminal Effects** (directly modify state):
  - `:bashketball/do-draw-cards` - Terminal: actually draw cards

  **Legacy Effects** (call actions directly):
  - `:bashketball/move-player` - Move player to a position
  - `:bashketball/exhaust-player` - Mark player as exhausted
  - `:bashketball/refresh-player` - Remove exhaustion from player
  - `:bashketball/give-ball` - Set ball possessed by player
  - `:bashketball/loose-ball` - Set ball loose at position
  - `:bashketball/discard-cards` - Discard cards by instance IDs
  - `:bashketball/add-score` - Add points to a team's score
  - `:bashketball/initiate-skill-test` - Start a skill test for a player
  - `:bashketball/modify-skill-test` - Add a modifier to the pending skill test
  - `:bashketball/offer-choice` - Present a choice to a player (pauses execution)
  - `:bashketball/force-choice` - Force a target player to choose between options
  - `:bashketball/add-modifier` - Add stat modifier to player
  - `:bashketball/remove-modifier` - Remove modifier by ID
  - `:bashketball/clear-modifiers` - Clear all modifiers from player
  - `:bashketball/attach-ability` - Attach ability card to player
  - `:bashketball/detach-ability` - Detach ability card from player

  **Phase Effects** (direct state mutations):
  - `:do-set-phase` - Direct phase mutation
  - `:do-set-quarter` - Direct quarter mutation
  - `:do-reset-turn-number` - Reset turn to 1
  - `:do-increment-turn` - Increment turn number
  - `:do-swap-active-player` - Swap active player between HOME/AWAY

  **Event Effects**:
  - `:bashketball/fire-event` - Fire an event through the trigger system

  **Standard Action Resolution Effects**:
  - `:bashketball/resolve-standard-action` - Full resolution orchestration
  - `:bashketball/process-response-choice` - Handle Apply/Pass response choice
  - `:bashketball/execute-skill-test-flow` - Setup and initiate skill test
  - `:bashketball/evaluate-skill-test-result` - Branch on success/failure

  **Play Card Resolution Effects**:
  - `:bashketball/play-card` - Play card resolution with response support
  - `:bashketball/do-play-card` - Terminal: apply play effect and fire after event
  - `:bashketball/do-fire-signal` - Terminal: apply signal effect from fuel card"
  []

  (fx/register-effect! :bashketball/move-player
                       (fn [state {:keys [player-id position]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               resolved-position  (resolve-param position ctx state)
                               action             {:type :bashketball/move-player
                                                   :player-id resolved-player-id
                                                   :position resolved-position}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  ;; Step-by-step Movement Effects

  (fx/register-effect! :bashketball/begin-movement
                       (fn [state {:keys [player-id speed]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               resolved-speed     (resolve-param speed ctx state)
                               action             {:type :bashketball/begin-movement
                                                   :player-id resolved-player-id
                                                   :speed resolved-speed}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/move-step
                       (fn [state {:keys [player-id to-position]} ctx opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               resolved-position  (resolve-param to-position ctx state)
                               player             (state/get-basketball-player state resolved-player-id)
                               from-position      (:position player)
                               team               (state/get-basketball-player-team state resolved-player-id)
                               movement           (state/get-pending-movement state)
                               ;; Fire exit event
                               exit-event         {:event-type      :bashketball/player-exiting-hex.request
                                                   :team            team
                                                   :player-id       resolved-player-id
                                                   :from-position   from-position
                                                   :to-position     resolved-position
                                                   :remaining-speed (:remaining-speed movement)
                                                   :step-number     (:step-number movement)
                                                   :causation       (:causation opts)}
                               exit-result        (triggers/fire-request-event
                                                   {:state            state
                                                    :registry         (:registry opts)
                                                    :event-counters   (:event-counters opts)
                                                    :executing-triggers (:executing-triggers opts)}
                                                   exit-event)]
                           (if (:prevented? exit-result)
                             {:state            (:state exit-result)
                              :applied          []
                              :failed           []
                              :pending          nil
                              :prevented?       true
                              :event-counters   (:event-counters exit-result)
                              :registry         (:registry exit-result)}
                             ;; Fire enter event - cost computed by catchall rule via :bashketball-fn/step-cost
                             (let [enter-event  {:event-type       :bashketball/player-entering-hex.request
                                                 :team             team
                                                 :player-id        resolved-player-id
                                                 :from-position    from-position
                                                 :to-position      resolved-position
                                                 :remaining-speed  (:remaining-speed movement)
                                                 :step-number      (:step-number movement)
                                                 :causation        (:causation opts)}
                                   enter-result (triggers/fire-request-event
                                                 {:state              (:state exit-result)
                                                  :registry           (:registry exit-result)
                                                  :event-counters     (:event-counters exit-result)
                                                  :executing-triggers (:executing-triggers exit-result)}
                                                 enter-event)]
                               {:state            (:state enter-result)
                                :applied          []
                                :failed           []
                                :pending          nil
                                :prevented?       (:prevented? enter-result)
                                :event-counters   (:event-counters enter-result)
                                :registry         (:registry enter-result)})))))

  (fx/register-effect! :bashketball/do-move-step
                       (fn [state {:keys [player-id to-position cost]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               resolved-position  (resolve-param to-position ctx state)
                               resolved-cost      (resolve-param cost ctx state)
                               action             {:type :bashketball/do-move-step
                                                   :player-id resolved-player-id
                                                   :to-position resolved-position
                                                   :cost resolved-cost}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/end-movement
                       (fn [state {:keys [player-id]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               action             {:type :bashketball/end-movement
                                                   :player-id resolved-player-id}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/exhaust-player
                       (fn [state {:keys [player-id]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               action             {:type :bashketball/exhaust-player
                                                   :player-id resolved-player-id}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/refresh-player
                       (fn [state {:keys [player-id]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               action             {:type :bashketball/refresh-player
                                                   :player-id resolved-player-id}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/give-ball
                       (fn [state {:keys [player-id]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               action             {:type :bashketball/set-ball-possessed
                                                   :holder-id resolved-player-id}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/loose-ball
                       (fn [state {:keys [position]} ctx _opts]
                         (let [resolved-position (resolve-param position ctx state)
                               action            {:type :bashketball/set-ball-loose
                                                  :position resolved-position}
                               new-state         (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/draw-cards
                       (fn [state {:keys [player count]} ctx opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-count  (resolve-param count ctx state)]
                           ;; Check if we should use event-driven path
                           (if (:registry opts)
                             ;; Event-driven: fire request event through triggers
                             (let [event  {:event-type :bashketball/draw-cards.request
                                           :team resolved-player
                                           :player resolved-player
                                           :count (or resolved-count 1)
                                           :causation (:causation opts)}
                                   result (triggers/fire-request-event
                                           {:state state
                                            :registry (:registry opts)
                                            :event-counters (:event-counters opts)
                                            :executing-triggers (:executing-triggers opts)}
                                           event)]
                               ;; Return effect result with updated context
                               {:state (:state result)
                                :applied []
                                :failed []
                                :pending nil
                                :event-counters (:event-counters result)
                                :registry (:registry result)})
                             ;; Legacy path: call action directly
                             (let [action    {:type :bashketball/draw-cards
                                              :player resolved-player
                                              :count resolved-count}
                                   new-state (actions/do-action state action)]
                               (fx/success new-state [action]))))))

  ;; Terminal effect: directly modifies state (called by catchall game rule)
  (fx/register-effect! :bashketball/do-draw-cards
                       (fn [state {:keys [player count]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-count  (or (resolve-param count ctx state) 1)
                               deck-path       [:players resolved-player :deck]
                               draw-pile       (get-in state (conj deck-path :draw-pile))
                               drawn           (vec (take resolved-count draw-pile))
                               remaining       (vec (drop resolved-count draw-pile))
                               new-state       (-> state
                                                   (assoc-in (conj deck-path :draw-pile) remaining)
                                                   (update-in (conj deck-path :hand) into drawn))]
                           (fx/success new-state [{:drew drawn
                                                   :count (clojure.core/count drawn)}]))))

  (fx/register-effect! :bashketball/discard-cards
                       (fn [state {:keys [player instance-ids]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-ids    (resolve-param instance-ids ctx state)
                               action          {:type :bashketball/discard-cards
                                                :player resolved-player
                                                :instance-ids resolved-ids}
                               new-state       (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/add-score
                       (fn [state {:keys [team points]} ctx _opts]
                         (let [resolved-team   (resolve-param team ctx state)
                               resolved-points (resolve-param points ctx state)
                               action          {:type :bashketball/add-score
                                                :team resolved-team
                                                :points resolved-points}
                               new-state       (actions/do-action state action)]
                           (fx/success new-state [action]))))

  ;; Skill Test Effects

  (fx/register-effect! :bashketball/initiate-skill-test
                       (fn [state {:keys [actor-id stat target-value context]} ctx _opts]
                         (let [resolved-actor (resolve-param actor-id ctx state)
                               action         {:type :bashketball/initiate-skill-test
                                               :actor-id resolved-actor
                                               :stat stat
                                               :target-value target-value
                                               :context context}
                               new-state      (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/modify-skill-test
                       (fn [state {:keys [source amount advantage reason]} ctx _opts]
                         (if (:pending-skill-test state)
                           (let [resolved-source    (resolve-param source ctx state)
                                 resolved-amount    (when amount (resolve-param amount ctx state))
                                 resolved-advantage (when advantage (resolve-param advantage ctx state))
                                 action             (cond-> {:type :bashketball/modify-skill-test
                                                             :source resolved-source}
                                                      resolved-amount    (assoc :amount resolved-amount)
                                                      resolved-advantage (assoc :advantage resolved-advantage)
                                                      reason             (assoc :reason reason))
                                 new-state          (actions/do-action state action)]
                             (fx/success new-state [action]))
                           (fx/success state []))))

  ;; Choice Effects

  (fx/register-effect! :bashketball/offer-choice
                       (fn [state {:keys [choice-type options waiting-for context continuation]} ctx _opts]
                         (let [resolved-team (resolve-param waiting-for ctx state)
                               action        (cond-> {:type        :bashketball/offer-choice
                                                      :choice-type choice-type
                                                      :options     options
                                                      :waiting-for resolved-team}
                                               context      (assoc :context context)
                                               continuation (assoc :continuation continuation))
                               new-state     (actions/do-action state action)]
                           ;; Return with pending flag so caller knows execution should pause
                           (assoc (fx/success new-state [action])
                                  :pending {:type :choice
                                            :choice-id (get-in new-state [:pending-choice :id])}))))

  (fx/register-effect! :bashketball/force-choice
                       (fn [game-state {:keys [target choice-type options context]} ctx _opts]
                         (let [resolved-target (resolve-param target ctx game-state)
                               target-team     (state/get-basketball-player-team game-state resolved-target)
                               ;; Build choice options with labels from keywords or pass through maps
                               choice-options  (mapv (fn [opt]
                                                       (if (keyword? opt)
                                                         {:id opt :label (name opt)}
                                                         opt))
                                                     options)
                               action          {:type :bashketball/offer-choice
                                                :choice-type (or choice-type :forced-choice)
                                                :options choice-options
                                                :waiting-for target-team
                                                :context (merge context
                                                                {:force-target resolved-target
                                                                 :original-options options})}
                               new-state       (actions/do-action game-state action)]
                           ;; Return with pending flag so caller knows execution should pause
                           (assoc (fx/success new-state [action])
                                  :pending {:type :forced-choice
                                            :choice-id (get-in new-state [:pending-choice :id])
                                            :target resolved-target}))))

  (fx/register-effect! :bashketball/execute-choice-continuation
                       (fn [state _params ctx opts]
                         (if-let [{:keys [continuation selected]} (:pending-choice state)]
                           (if continuation
                             (let [ctx'   (update ctx :bindings assoc :choice/selected selected)
                                   state' (dissoc state :pending-choice)
                                   effect (if (vector? continuation)
                                            {:type :polix.effects/sequence :effects continuation}
                                            continuation)]
                               (fx/apply-effect state' effect ctx' opts))
                             (fx/success (dissoc state :pending-choice) []))
                           (fx/success state []))))

  ;; Modifier Effects

  (fx/register-effect! :bashketball/add-modifier
                       (fn [state {:keys [player-id stat amount source expires-at]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               resolved-stat      (resolve-param stat ctx state)
                               resolved-amount    (resolve-param amount ctx state)
                               resolved-source    (resolve-param source ctx state)
                               resolved-expires   (resolve-param expires-at ctx state)
                               action             {:type :bashketball/add-modifier
                                                   :player-id resolved-player-id
                                                   :stat resolved-stat
                                                   :amount resolved-amount
                                                   :source resolved-source
                                                   :expires-at resolved-expires}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/remove-modifier
                       (fn [state {:keys [player-id modifier-id]} ctx _opts]
                         (let [resolved-player-id   (resolve-param player-id ctx state)
                               resolved-modifier-id (resolve-param modifier-id ctx state)
                               action               {:type :bashketball/remove-modifier
                                                     :player-id resolved-player-id
                                                     :modifier-id resolved-modifier-id}
                               new-state            (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/clear-modifiers
                       (fn [state {:keys [player-id]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               action             {:type :bashketball/clear-modifiers
                                                   :player-id resolved-player-id}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  ;; Ability Effects

  (fx/register-effect! :bashketball/attach-ability
                       (fn [state {:keys [player instance-id target-id]} ctx _opts]
                         (let [resolved-player   (resolve-param player ctx state)
                               resolved-instance (resolve-param instance-id ctx state)
                               resolved-target   (resolve-param target-id ctx state)
                               action            {:type :bashketball/attach-ability
                                                  :player resolved-player
                                                  :instance-id resolved-instance
                                                  :target-id resolved-target}
                               new-state         (actions/do-action state action)]
                           (fx/success new-state [action]))))

  (fx/register-effect! :bashketball/detach-ability
                       (fn [state {:keys [player-id instance-id]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               resolved-instance  (resolve-param instance-id ctx state)
                               action             {:type :bashketball/detach-ability
                                                   :player-id resolved-player-id
                                                   :instance-id resolved-instance}
                               new-state          (actions/do-action state action)]
                           (fx/success new-state [action]))))

  ;; Phase Effects

  ;; Request effect: fires events for phase transitions, allowing card abilities to intercept
  (fx/register-effect! :bashketball/transition-phase
                       (fn [state {:keys [to-phase]} ctx opts]
                         (let [resolved-to-phase (resolve-param to-phase ctx state)
                               from-phase        (:phase state)
                               team              (:active-player state)]
                           ;; Fire phase-ending.request for the current phase
                           (if (nil? from-phase)
                             ;; No current phase, skip to starting the new phase
                             (let [start-event  {:event-type :bashketball/phase-starting.request
                                                 :team       team
                                                 :from-phase nil
                                                 :to-phase   resolved-to-phase
                                                 :causation  (:causation opts)}
                                   start-result (triggers/fire-request-event
                                                 {:state              state
                                                  :registry           (:registry opts)
                                                  :event-counters     (:event-counters opts)
                                                  :executing-triggers (:executing-triggers opts)}
                                                 start-event)]
                               {:state            (:state start-result)
                                :applied          []
                                :failed           []
                                :pending          nil
                                :prevented?       (:prevented? start-result)
                                :event-counters   (:event-counters start-result)
                                :registry         (:registry start-result)})
                             ;; Fire ending event for current phase
                             (let [end-event  {:event-type :bashketball/phase-ending.request
                                               :team       team
                                               :from-phase from-phase
                                               :to-phase   resolved-to-phase
                                               :causation  (:causation opts)}
                                   end-result (triggers/fire-request-event
                                               {:state              state
                                                :registry           (:registry opts)
                                                :event-counters     (:event-counters opts)
                                                :executing-triggers (:executing-triggers opts)}
                                               end-event)]
                               (if (:prevented? end-result)
                                 {:state            (:state end-result)
                                  :applied          []
                                  :failed           []
                                  :pending          nil
                                  :prevented?       true
                                  :event-counters   (:event-counters end-result)
                                  :registry         (:registry end-result)}
                                 ;; Fire starting event for new phase
                                 (let [start-event  {:event-type :bashketball/phase-starting.request
                                                     :team       team
                                                     :from-phase from-phase
                                                     :to-phase   resolved-to-phase
                                                     :causation  (:causation opts)}
                                       start-result (triggers/fire-request-event
                                                     {:state              (:state end-result)
                                                      :registry           (:registry end-result)
                                                      :event-counters     (:event-counters end-result)
                                                      :executing-triggers (:executing-triggers end-result)}
                                                     start-event)]
                                   {:state            (:state start-result)
                                    :applied          []
                                    :failed           []
                                    :pending          nil
                                    :prevented?       (:prevented? start-result)
                                    :event-counters   (:event-counters start-result)
                                    :registry         (:registry start-result)})))))))

  ;; Terminal effect: uses action handler
  (fx/register-effect! :bashketball/do-set-phase
                       (fn [state {:keys [phase]} ctx _opts]
                         (let [resolved-phase (resolve-param phase ctx state)
                               action         {:type :bashketball/do-set-phase
                                               :phase resolved-phase}
                               new-state      (actions/do-action state action)]
                           (fx/success new-state [action]))))

  ;; Legacy alias for backward compatibility with phase_triggers
  (fx/register-effect! :do-set-phase
                       (fn [state {:keys [phase]} _ctx _opts]
                         (fx/success (assoc state :phase phase) [])))

  ;; Turn Effects

  ;; Request effect: fires events for turn transitions with auto-sequence to UPKEEP
  (fx/register-effect! :bashketball/end-turn
                       (fn [state _params _ctx opts]
                         (let [team        (:active-player state)
                               turn-number (:turn-number state)
                               end-event   {:event-type  :bashketball/turn-ending.request
                                            :team        team
                                            :turn-number turn-number
                                            :causation   (:causation opts)}
                               end-result  (triggers/fire-request-event
                                            {:state              state
                                             :registry           (:registry opts)
                                             :event-counters     (:event-counters opts)
                                             :executing-triggers (:executing-triggers opts)}
                                            end-event)]
                           (if (:prevented? end-result)
                             {:state            (:state end-result)
                              :applied          []
                              :failed           []
                              :pending          nil
                              :prevented?       true
                              :event-counters   (:event-counters end-result)
                              :registry         (:registry end-result)}
                               ;; Fire turn-starting.request for new turn
                             (let [start-event  {:event-type  :bashketball/turn-starting.request
                                                 :team        team
                                                 :turn-number (inc turn-number)
                                                 :causation   (:causation opts)}
                                   start-result (triggers/fire-request-event
                                                 {:state              (:state end-result)
                                                  :registry           (:registry end-result)
                                                  :event-counters     (:event-counters end-result)
                                                  :executing-triggers (:executing-triggers end-result)}
                                                 start-event)]
                               {:state            (:state start-result)
                                :applied          []
                                :failed           []
                                :pending          nil
                                :prevented?       (:prevented? start-result)
                                :event-counters   (:event-counters start-result)
                                :registry         (:registry start-result)})))))

  ;; Terminal effect: advances turn counter and swaps active player
  (fx/register-effect! :bashketball/do-advance-turn
                       (fn [state _params _ctx _opts]
                         (let [action    {:type :bashketball/do-advance-turn}
                               new-state (actions/do-action state action)]
                           (fx/success new-state [action]))))

  ;; Hand Limit Effects

  ;; Check if over hand limit and offer discard choice if needed
  (fx/register-effect! :bashketball/check-hand-limit
                       (fn [game-state {:keys [team]} ctx _opts]
                         (let [resolved-team (resolve-param team ctx game-state)
                               hand          (state/get-hand game-state resolved-team)
                               hand-size     (count hand)
                               hand-limit    8
                               excess        (max 0 (- hand-size hand-limit))]
                           (if (pos? excess)
                             ;; Over limit: directly offer the choice
                             (let [action    {:type :bashketball/offer-choice
                                              :choice-type :discard-to-hand-limit
                                              :waiting-for resolved-team
                                              :options (mapv (fn [card]
                                                               {:id (keyword (:instance-id card))
                                                                :label (:card-slug card)})
                                                             hand)
                                              :context {:discard-count excess
                                                        :hand-limit hand-limit}}
                                   new-state (actions/do-action game-state action)]
                               (assoc (fx/success new-state [action])
                                      :pending {:type :choice
                                                :choice-id (get-in new-state [:pending-choice :id])}))
                             ;; Under or at limit: no action needed
                             (fx/success game-state [])))))

  (fx/register-effect! :do-set-quarter
                       (fn [state {:keys [quarter]} _ctx _opts]
                         (fx/success (assoc state :quarter quarter) [])))

  (fx/register-effect! :do-reset-turn-number
                       (fn [state _params _ctx _opts]
                         (fx/success (assoc state :turn-number 1) [])))

  (fx/register-effect! :do-increment-turn
                       (fn [state _params _ctx _opts]
                         (fx/success (update state :turn-number inc) [])))

  (fx/register-effect! :do-swap-active-player
                       (fn [state _params _ctx _opts]
                         (let [swap-fn {:team/HOME :team/AWAY :team/AWAY :team/HOME}]
                           (fx/success (update state :active-player swap-fn) []))))

  ;; Event Firing Effect

  (fx/register-effect! :bashketball/fire-event
                       (fn [state {:keys [event-type params]} _ctx opts]
                         (let [event  (merge (or params {})
                                             {:type          event-type
                                              :event-type    event-type
                                              :turn-number   (:turn-number state)
                                              :active-player (:active-player state)
                                              :phase         (:phase state)
                                              :causation     (:causation opts)})
                               result (triggers/fire-request-event
                                       {:state              state
                                        :registry           (:registry opts)
                                        :event-counters     (:event-counters opts)
                                        :executing-triggers (:executing-triggers opts)}
                                       event)]
                           {:state          (:state result)
                            :applied        []
                            :failed         []
                            :pending        nil
                            :prevented?     (:prevented? result)
                            :event-counters (:event-counters result)
                            :registry       (:registry result)})))

  ;; Standard Action Resolution Effects

  (fx/register-effect! :bashketball/process-response-choice
                       (fn [state {:keys [response-asset response-effect next-continuation]} ctx opts]
                         (let [selected (get-in ctx [:bindings :choice/selected])]
                           (if (= selected :apply)
                             ;; Reveal and apply the response, then continue
                             (let [team    (:owner response-asset)
                                   state'  (sar/reveal-response-asset state team response-asset)
                                   effects (if response-effect
                                             [response-effect next-continuation]
                                             [next-continuation])]
                               (fx/apply-effect state'
                                                {:type :polix.effects/sequence :effects effects}
                                                ctx
                                                opts))
                             ;; Pass - just continue to next step
                             (fx/apply-effect state next-continuation ctx opts)))))

  (fx/register-effect! :bashketball/execute-skill-test-flow
                       (fn [state {:keys [action-type attacker-id defender-id result-continuation]} ctx opts]
                         (let [resolved-attacker    (resolve-param attacker-id ctx state)
                               resolved-defender    (resolve-param defender-id ctx state)
                               ;; Get the appropriate skill test setup based on action type
                               test-config          (case action-type
                                                      :shoot (sap/setup-shoot-test state resolved-attacker)
                                                      :pass  (sap/setup-pass-test state resolved-attacker resolved-defender)
                                                      :steal (sap/setup-steal-test state resolved-attacker resolved-defender)
                                                      :screen (sap/setup-screen-test state resolved-attacker resolved-defender)
                                                      :check (sap/setup-check-test state resolved-attacker resolved-defender)
                                                      :block (sap/setup-shoot-test state resolved-defender))
                               {:keys [difficulty]} test-config
                               attacker-pos         (:position (state/get-basketball-player state resolved-attacker))
                               ;; Build proper SkillTestContext
                               skill-test-context   (cond-> {:type action-type}
                                                      attacker-pos     (assoc :origin attacker-pos)
                                                      resolved-defender (assoc :defender-id resolved-defender
                                                                               :target resolved-defender))
                               ;; Build sequence: initiate skill test, then resolve with continuation
                               effects              [{:type :bashketball/initiate-skill-test
                                                      :actor-id resolved-attacker
                                                      :stat (case action-type
                                                              (:shoot :block) :stat/SHOOTING
                                                              (:pass) :stat/PASSING
                                                              (:steal :screen :check) :stat/DEFENSE)
                                                      :target-value difficulty
                                                      :context skill-test-context}
                                                     result-continuation]]
                           (fx/apply-effect state
                                            {:type :polix.effects/sequence :effects effects}
                                            ctx
                                            opts))))

  (fx/register-effect! :bashketball/evaluate-skill-test-result
                       (fn [state {:keys [success-effect failure-effect after-event-params]} ctx opts]
                         (let [test-result    (:last-skill-test-result state)
                               success?       (get test-result :success? false)
                               ;; Select the appropriate effect based on success/failure
                               outcome-effect (if success? success-effect failure-effect)
                               ;; Build after event with success info
                               after-event    {:type :bashketball/fire-event
                                               :event-type :bashketball/standard-action.after
                                               :params (assoc after-event-params :success? success?)}
                               ;; Chain outcome effect (if present) with after event
                               effects        (if outcome-effect
                                                [outcome-effect after-event]
                                                [after-event])]
                           (fx/apply-effect state
                                            {:type :polix.effects/sequence :effects effects}
                                            ctx
                                            opts))))

  (fx/register-effect! :bashketball/resolve-standard-action
                       (fn [game-state params ctx opts]
                         (let [{:keys [action-type attacker-id defender-id
                                       success-effect failure-effect effect-catalog]} params
                               resolved-attacker                                      (resolve-param attacker-id ctx game-state)
                               resolved-defender                                      (resolve-param defender-id ctx game-state)
                               attacker-team                                          (state/get-basketball-player-team game-state resolved-attacker)
                               defending-team                                         (sap/opposing-team attacker-team)
                               ;; Build the offense continuation (skill test → result)
                               offense-cont                                           (sar/build-offense-continuation
                                                                                       {:action-type    action-type
                                                                                        :attacker-id    resolved-attacker
                                                                                        :defender-id    resolved-defender
                                                                                        :success-effect success-effect
                                                                                        :failure-effect failure-effect})
                               ;; Find matching responses for defending team
                               before-event                                           {:type        :bashketball/standard-action.before
                                                                                       :action-type action-type
                                                                                       :attacker-id resolved-attacker
                                                                                       :defender-id resolved-defender
                                                                                       :acting-team attacker-team}
                               responses                                              (when effect-catalog
                                                                                        (sar/find-matching-responses
                                                                                         game-state effect-catalog (:registry opts)
                                                                                         before-event defending-team))
                               ;; Build full continuation chain with response prompts
                               full-continuation                                      (if (seq responses)
                                                                                        (sar/build-response-chain responses offense-cont)
                                                                                        offense-cont)
                               ;; Build the effect sequence: before event → continuation chain
                               effects                                                [{:type       :bashketball/fire-event
                                                                                        :event-type :bashketball/standard-action.before
                                                                                        :params     {:action-type action-type
                                                                                                     :attacker-id resolved-attacker
                                                                                                     :defender-id resolved-defender
                                                                                                     :acting-team attacker-team}}
                                                                                       full-continuation]]
                           (fx/apply-effect game-state
                                            {:type :polix.effects/sequence :effects effects}
                                            ctx
                                            opts))))

  ;; =========================================================================
  ;; Play Card Resolution Effects
  ;; =========================================================================

  (fx/register-effect! :bashketball/play-card
                       (fn [state {:keys [main-card fuel-cards targets play-effect
                                          effect-context effect-catalog]} ctx opts]
                         (if (:registry opts)
                           ;; Event-driven path
                           (let [team           (:self/team effect-context)
                                 defending-team (if (= team :team/HOME) :team/AWAY :team/HOME)

                                 ;; Step 1: Process fuel cards (fire signal events)
                                 fuel-ctx (reduce
                                           (fn [acc fuel-card]
                                             (if-let [signal-effect (:signal-effect fuel-card)]
                                               (let [event {:event-type     :bashketball/card-discarded-as-fuel.request
                                                            :team           team
                                                            :fuel-card      fuel-card
                                                            :signal-effect  signal-effect
                                                            :signal-context (:signal-context fuel-card)
                                                            :main-card      main-card
                                                            :main-targets   targets}]
                                                 (triggers/fire-request-event acc event))
                                               acc))
                                           {:state            state
                                            :registry         (:registry opts)
                                            :event-counters   (:event-counters opts)}
                                           fuel-cards)

                                 ;; Step 2: Build play execution continuation
                                 play-continuation {:type           :bashketball/do-play-card
                                                    :play-effect    play-effect
                                                    :effect-context effect-context}

                                 ;; Step 3: Check for matching Response assets
                                 before-event {:type        :bashketball/play-card.before
                                               :team        team
                                               :card-slug   (:card-slug main-card)
                                               :instance-id (:instance-id main-card)
                                               :targets     targets}
                                 responses    (sar/find-matching-responses
                                               (:state fuel-ctx) effect-catalog (:registry fuel-ctx)
                                               before-event defending-team)

                                 ;; Step 4: Build response chain (if any) + play continuation
                                 full-continuation (if (seq responses)
                                                     (sar/build-response-chain responses play-continuation)
                                                     play-continuation)]

                             ;; Step 5: Apply the full continuation
                             (fx/apply-effect (:state fuel-ctx) full-continuation ctx
                                              (assoc opts
                                                     :registry       (:registry fuel-ctx)
                                                     :event-counters (:event-counters fuel-ctx))))

                           ;; Legacy path - normalize and apply effect directly with bindings
                           (fx/apply-effect state (normalize-effect play-effect) {:bindings effect-context} opts))))

  (fx/register-effect! :bashketball/do-play-card
                       (fn [state {:keys [play-effect effect-context]} _ctx opts]
                         ;; Apply the play effect with context bindings
                         (let [effect-result (fx/apply-effect state play-effect {:bindings effect-context} opts)]
                           (if (:pending effect-result)
                             effect-result
                             ;; Fire after event
                             (let [team         (:self/team effect-context)
                                   after-event  {:event-type  :bashketball/play-card.after
                                                 :team        team
                                                 :card-slug   (:card/slug effect-context)
                                                 :instance-id (:card/instance-id effect-context)}
                                   after-result (triggers/fire-request-event
                                                 {:state          (:state effect-result)
                                                  :registry       (or (:registry effect-result) (:registry opts))
                                                  :event-counters (or (:event-counters effect-result) (:event-counters opts))}
                                                 after-event)]
                               {:state          (:state after-result)
                                :applied        (:applied effect-result)
                                :failed         []
                                :pending        nil
                                :event-counters (:event-counters after-result)
                                :registry       (:registry after-result)})))))

  (fx/register-effect! :bashketball/do-fire-signal
                       (fn [state {:keys [signal-effect signal-context]} ctx opts]
                         ;; Resolve path expressions (e.g., [:ctx :event :signal-effect])
                         ;; and normalize effects from schema format
                         (let [resolved-effect  (resolve-param signal-effect ctx state)
                               resolved-context (resolve-param signal-context ctx state)
                               normalized       (normalize-effect resolved-effect)]
                           (if normalized
                             (fx/apply-effect state normalized {:bindings resolved-context} opts)
                             (fx/success state []))))))
