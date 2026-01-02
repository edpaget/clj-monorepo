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
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [polix.effects.core :as fx]))

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
          resolved-args (mapv #(resolve-param % ctx state) args)
          f (functions/get-fn fn-key)]
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
  - `:do-swap-active-player` - Swap active player between HOME/AWAY"
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
                               resolved-count (resolve-param count ctx state)]
                           ;; Check if we should use event-driven path
                           (if (:registry opts)
                             ;; Event-driven: fire request event through triggers
                             (let [event {:event-type :bashketball/draw-cards.request
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
                             (let [action {:type :bashketball/draw-cards
                                           :player resolved-player
                                           :count resolved-count}
                                   new-state (actions/do-action state action)]
                               (fx/success new-state [action]))))))

  ;; Terminal effect: directly modifies state (called by catchall game rule)
  (fx/register-effect! :bashketball/do-draw-cards
                       (fn [state {:keys [player count]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-count (or (resolve-param count ctx state) 1)
                               deck-path [:players resolved-player :deck]
                               draw-pile (get-in state (conj deck-path :draw-pile))
                               drawn (vec (take resolved-count draw-pile))
                               remaining (vec (drop resolved-count draw-pile))
                               new-state (-> state
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
                       (fn [state {:keys [source amount reason]} ctx _opts]
                         (let [resolved-source (resolve-param source ctx state)
                               resolved-amount (resolve-param amount ctx state)
                               action          {:type :bashketball/modify-skill-test
                                                :source resolved-source
                                                :amount resolved-amount
                                                :reason reason}
                               new-state       (actions/do-action state action)]
                           (fx/success new-state [action]))))

  ;; Choice Effects

  (fx/register-effect! :bashketball/offer-choice
                       (fn [state {:keys [choice-type options waiting-for context]} ctx _opts]
                         (let [resolved-team (resolve-param waiting-for ctx state)
                               action        {:type :bashketball/offer-choice
                                              :choice-type choice-type
                                              :options options
                                              :waiting-for resolved-team
                                              :context context}
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
                             (let [start-event {:event-type :bashketball/phase-starting.request
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
                             (let [end-event {:event-type :bashketball/phase-ending.request
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
                                 (let [start-event {:event-type :bashketball/phase-starting.request
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
                               turn-number (:turn-number state)]
                           ;; Fire turn-ending.request event
                           (let [end-event  {:event-type  :bashketball/turn-ending.request
                                             :team        team
                                             :turn-number turn-number
                                             :causation   (:causation opts)}
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
                                  :registry         (:registry start-result)}))))))

  ;; Terminal effect: advances turn counter and swaps active player
  (fx/register-effect! :bashketball/do-advance-turn
                       (fn [state _params ctx _opts]
                         (let [action    {:type :bashketball/do-advance-turn}
                               new-state (actions/do-action state action)]
                           (fx/success new-state [action]))))

  ;; Hand Limit Effects

  ;; Check if over hand limit and offer discard choice if needed
  (fx/register-effect! :bashketball/check-hand-limit
                       (fn [game-state {:keys [team]} ctx opts]
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
                           (fx/success (update state :active-player swap-fn) [])))))
