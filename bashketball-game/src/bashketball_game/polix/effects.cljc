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
   [bashketball-game.board :as board]
   [bashketball-game.polix.card-effects :as card-effects]
   [bashketball-game.polix.functions :as functions]
   [bashketball-game.polix.standard-action-policies :as sap]
   [bashketball-game.polix.standard-action-resolution :as sar]
   [bashketball-game.polix.terminal-utils :as tu]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [clojure.set :as set]
   [clojure.string :as str]
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
  ([param ctx state]
   (resolve-param param ctx state nil))
  ([param ctx state opts]
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
           resolved-args   (mapv #(resolve-param % ctx state opts) args)
           f               (functions/get-fn fn-key)]
       (if f
         (apply f state ctx opts resolved-args)
         (throw (ex-info "Unknown function" {:fn-key fn-key}))))

     :else
     param)))

(defn- stat->stats-key
  "Converts a stat keyword to the corresponding stats map key.
   e.g. :stat/SHOOTING -> :shooting"
  [stat]
  (-> stat name str/lower-case keyword))

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
                               player             (state/get-basketball-player state resolved-player-id)
                               old-position       (:position player)
                               new-state          (-> state
                                                      (state/update-basketball-player resolved-player-id
                                                                                      assoc :position resolved-position)
                                                      (cond->
                                                       old-position (update :board board/remove-occupant old-position))
                                                      (update :board board/set-occupant resolved-position
                                                              {:type :occupant/BASKETBALL_PLAYER :id resolved-player-id})
                                                      (tu/log-event :bashketball/move-player
                                                                    {:player-id resolved-player-id
                                                                     :from-position old-position
                                                                     :to-position resolved-position})
                                                      (tu/check-board-invariants! {:effect :move-player
                                                                                   :player-id resolved-player-id
                                                                                   :position resolved-position}))]
                           (fx/success new-state [{:player-id resolved-player-id
                                                   :position resolved-position}]))))

  ;; Step-by-step Movement Effects

  (fx/register-effect! :bashketball/begin-movement
                       (fn [state {:keys [player-id speed]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               resolved-speed     (resolve-param speed ctx state)
                               player             (state/get-basketball-player state resolved-player-id)
                               team               (state/get-basketball-player-team state resolved-player-id)
                               position           (:position player)
                               movement-id        (tu/generate-id)
                               new-state          (-> state
                                                      (state/set-pending-movement
                                                       {:id               movement-id
                                                        :player-id        resolved-player-id
                                                        :team             team
                                                        :starting-position position
                                                        :current-position position
                                                        :initial-speed    resolved-speed
                                                        :remaining-speed  resolved-speed
                                                        :path-taken       [position]
                                                        :step-number      0})
                                                      (tu/log-event :bashketball/begin-movement
                                                                    {:player-id resolved-player-id
                                                                     :position position
                                                                     :speed resolved-speed}))]
                           (fx/success new-state [{:player-id resolved-player-id
                                                   :speed resolved-speed}]))))

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
                       (fn [state {:keys [player-id to-position cost]} ctx opts]
                         (let [resolved-player-id (resolve-param player-id ctx state opts)
                               resolved-position  (resolve-param to-position ctx state opts)
                               resolved-cost      (resolve-param cost ctx state opts)
                               player             (state/get-basketball-player state resolved-player-id)
                               old-position       (:position player)
                               movement           (state/get-pending-movement state)
                               new-state          (-> state
                                                      (state/update-basketball-player resolved-player-id
                                                                                      assoc :position resolved-position)
                                                      (cond->
                                                       old-position (update :board board/remove-occupant old-position))
                                                      (update :board board/set-occupant resolved-position
                                                              {:type :occupant/BASKETBALL_PLAYER :id resolved-player-id})
                                                      (state/update-pending-movement
                                                       (fn [m]
                                                         (-> m
                                                             (assoc :current-position resolved-position)
                                                             (update :remaining-speed - resolved-cost)
                                                             (update :path-taken conj resolved-position)
                                                             (update :step-number inc))))
                                                      (tu/log-event :bashketball/move-step
                                                                    {:player-id resolved-player-id
                                                                     :from-position old-position
                                                                     :to-position resolved-position
                                                                     :cost resolved-cost
                                                                     :remaining (- (:remaining-speed movement) resolved-cost)})
                                                      (tu/check-board-invariants! {:effect :do-move-step
                                                                                   :player-id resolved-player-id
                                                                                   :to-position resolved-position}))]
                           (fx/success new-state [{:player-id resolved-player-id
                                                   :to-position resolved-position
                                                   :cost resolved-cost}]))))

  (fx/register-effect! :bashketball/end-movement
                       (fn [state {:keys [player-id]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               movement           (state/get-pending-movement state)
                               new-state          (-> state
                                                      (state/clear-pending-movement)
                                                      (tu/log-event :bashketball/end-movement
                                                                    {:player-id   resolved-player-id
                                                                     :path-taken  (:path-taken movement)
                                                                     :total-steps (:step-number movement)}))]
                           (fx/success new-state [{:player-id   resolved-player-id
                                                   :path-taken  (:path-taken movement)
                                                   :total-steps (:step-number movement)}]))))

  ;; =========================================================================
  ;; Player State Effects (Event-Driven)
  ;; =========================================================================

  (fx/register-effect! :bashketball/exhaust-player
                       (fn [state {:keys [player-id]} ctx opts]
                         (let [resolved-id (resolve-param player-id ctx state)
                               team        (state/get-basketball-player-team state resolved-id)
                               event       {:event-type :bashketball/exhaust-player.request
                                            :player-id resolved-id
                                            :team team
                                            :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

  (fx/register-effect! :bashketball/do-exhaust-player
                       (fn [state {:keys [player-id]} ctx _opts]
                         (let [resolved-id (resolve-param player-id ctx state)
                               new-state   (-> state
                                               (state/update-basketball-player resolved-id assoc :exhausted true)
                                               (tu/log-event :bashketball/exhaust-player {:player-id resolved-id}))]
                           (fx/success new-state [{:player-id resolved-id}]))))

  (fx/register-effect! :bashketball/refresh-player
                       (fn [state {:keys [player-id]} ctx opts]
                         (let [resolved-id (resolve-param player-id ctx state)
                               team        (state/get-basketball-player-team state resolved-id)
                               event       {:event-type :bashketball/refresh-player.request
                                            :player-id resolved-id
                                            :team team
                                            :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

  (fx/register-effect! :bashketball/do-refresh-player
                       (fn [state {:keys [player-id]} ctx _opts]
                         (let [resolved-id (resolve-param player-id ctx state)
                               new-state   (-> state
                                               (state/update-basketball-player resolved-id assoc :exhausted false)
                                               (tu/log-event :bashketball/refresh-player {:player-id resolved-id}))]
                           (fx/success new-state [{:player-id resolved-id}]))))

  ;; =========================================================================
  ;; Ball State Effects (Event-Driven)
  ;; =========================================================================

  (fx/register-effect! :bashketball/give-ball
                       (fn [state {:keys [player-id]} ctx opts]
                         (let [resolved-id (resolve-param player-id ctx state)
                               team        (state/get-basketball-player-team state resolved-id)
                               event       {:event-type :bashketball/set-ball-possessed.request
                                            :holder-id resolved-id
                                            :team team
                                            :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

  (fx/register-effect! :bashketball/do-set-ball-possessed
                       (fn [state {:keys [holder-id]} ctx _opts]
                         (let [resolved-id (resolve-param holder-id ctx state)
                               new-state   (-> state
                                               (assoc :ball {:status :ball-status/POSSESSED :holder-id resolved-id})
                                               (tu/log-event :bashketball/set-ball-possessed {:holder-id resolved-id}))]
                           (fx/success new-state [{:holder-id resolved-id}]))))

  (fx/register-effect! :bashketball/loose-ball
                       (fn [state {:keys [position]} ctx opts]
                         (let [resolved-pos (resolve-param position ctx state)
                               event        {:event-type :bashketball/set-ball-loose.request
                                             :position resolved-pos
                                             :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

  (fx/register-effect! :bashketball/do-set-ball-loose
                       (fn [state {:keys [position]} ctx _opts]
                         (let [resolved-pos (resolve-param position ctx state)
                               new-state    (-> state
                                                (assoc :ball {:status :ball-status/LOOSE :position resolved-pos})
                                                (tu/log-event :bashketball/set-ball-loose {:position resolved-pos}))]
                           (fx/success new-state [{:position resolved-pos}]))))

  (fx/register-effect! :bashketball/draw-cards
                       (fn [state {:keys [player count]} ctx opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-count  (resolve-param count ctx state)
                               event           {:event-type :bashketball/draw-cards.request
                                                :team resolved-player
                                                :player resolved-player
                                                :count (or resolved-count 1)
                                                :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

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
                                                   (update-in (conj deck-path :hand) into drawn)
                                                   (tu/log-event :bashketball/draw-cards
                                                                 {:player resolved-player
                                                                  :count (clojure.core/count drawn)
                                                                  :cards drawn}))]
                           (fx/success new-state [{:drew drawn
                                                   :count (clojure.core/count drawn)}]))))

  ;; =========================================================================
  ;; Card & Score Effects (Event-Driven)
  ;; =========================================================================

  (fx/register-effect! :bashketball/discard-cards
                       (fn [state {:keys [player instance-ids]} ctx opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-ids    (resolve-param instance-ids ctx state)
                               event           {:event-type :bashketball/discard-cards.request
                                                :player resolved-player
                                                :instance-ids resolved-ids
                                                :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

  (fx/register-effect! :bashketball/do-discard-cards
                       (fn [state {:keys [player instance-ids]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-ids    (resolve-param instance-ids ctx state)
                               deck-path       [:players resolved-player :deck]
                               hand            (get-in state (conj deck-path :hand))
                               id-set          (set resolved-ids)
                               discarded       (filterv #(id-set (:instance-id %)) hand)
                               new-hand        (filterv #(not (id-set (:instance-id %))) hand)
                               new-state       (-> state
                                                   (assoc-in (conj deck-path :hand) new-hand)
                                                   (update-in (conj deck-path :discard) into discarded)
                                                   (tu/log-event :bashketball/discard-cards
                                                                 {:player resolved-player
                                                                  :instance-ids resolved-ids}))]
                           (fx/success new-state [{:discarded discarded}]))))

  (fx/register-effect! :bashketball/add-score
                       (fn [state {:keys [team points]} ctx opts]
                         (let [resolved-team   (resolve-param team ctx state)
                               resolved-points (resolve-param points ctx state)
                               event           {:event-type :bashketball/add-score.request
                                                :team resolved-team
                                                :points resolved-points
                                                :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

  (fx/register-effect! :bashketball/do-add-score
                       (fn [state {:keys [team points]} ctx _opts]
                         (let [resolved-team   (resolve-param team ctx state)
                               resolved-points (resolve-param points ctx state)
                               new-state       (-> state
                                                   (update-in [:score resolved-team] + resolved-points)
                                                   (tu/log-event :bashketball/add-score
                                                                 {:team resolved-team
                                                                  :points resolved-points}))]
                           (fx/success new-state [{:team resolved-team :points resolved-points}]))))

  ;; Skill Test Effects

  (fx/register-effect! :bashketball/initiate-skill-test
                       (fn [state {:keys [actor-id stat target-value context]} ctx _opts]
                         (let [resolved-actor (resolve-param actor-id ctx state)
                               player         (state/get-basketball-player state resolved-actor)
                               stats-key      (stat->stats-key stat)
                               base-value     (get-in player [:stats stats-key] 0)
                               skill-test-id  (tu/generate-id)
                               new-state      (-> state
                                                  (assoc :pending-skill-test
                                                         {:id           skill-test-id
                                                          :actor-id     resolved-actor
                                                          :stat         stat
                                                          :base-value   base-value
                                                          :modifiers    []
                                                          :fate         nil
                                                          :total        nil
                                                          :target-value target-value
                                                          :context      context})
                                                  (tu/log-event :bashketball/initiate-skill-test
                                                                {:actor-id resolved-actor
                                                                 :stat stat
                                                                 :base-value base-value
                                                                 :target-value target-value}))]
                           (fx/success new-state [{:actor-id resolved-actor
                                                   :stat stat
                                                   :target-value target-value}]))))

  (fx/register-effect! :bashketball/modify-skill-test
                       (fn [state {:keys [source amount advantage reason]} ctx _opts]
                         (if (:pending-skill-test state)
                           (let [resolved-source    (resolve-param source ctx state)
                                 resolved-amount    (when amount (resolve-param amount ctx state))
                                 resolved-advantage (when advantage (resolve-param advantage ctx state))
                                 modifier           (cond-> {:source resolved-source :amount (or resolved-amount 0)}
                                                      reason             (assoc :reason reason)
                                                      resolved-advantage (assoc :advantage resolved-advantage))
                                 new-state          (-> state
                                                        (update-in [:pending-skill-test :modifiers] conj modifier)
                                                        (tu/log-event :bashketball/modify-skill-test modifier))]
                             (fx/success new-state [modifier]))
                           (fx/success state []))))

  (fx/register-effect! :bashketball/do-set-skill-test-fate
                       (fn [state {:keys [fate]} ctx _opts]
                         (if (:pending-skill-test state)
                           (let [resolved-fate (resolve-param fate ctx state)
                                 new-state     (-> state
                                                   (assoc-in [:pending-skill-test :fate] resolved-fate)
                                                   (tu/log-event :bashketball/set-skill-test-fate
                                                                 {:fate resolved-fate}))]
                             (fx/success new-state [{:fate resolved-fate}]))
                           (fx/success state []))))

  (fx/register-effect! :bashketball/do-resolve-skill-test
                       (fn [state _params _ctx _opts]
                         (if-let [pending (:pending-skill-test state)]
                           (let [{:keys [base-value modifiers fate]} pending
                                 modifier-total                      (reduce + 0 (map :amount modifiers))
                                 total                               (+ base-value modifier-total (or fate 0))
                                 resolved-test                       (assoc pending :total total)
                                 new-state                           (-> state
                                                                         (assoc-in [:pending-skill-test :total] total)
                                                                         (tu/log-event :bashketball/resolve-skill-test
                                                                                       {:skill-test resolved-test}))]
                             (fx/success new-state [{:skill-test resolved-test}]))
                           (fx/success state []))))

  (fx/register-effect! :bashketball/do-clear-skill-test
                       (fn [state _params _ctx _opts]
                         (let [cleared   (:pending-skill-test state)
                               new-state (-> state
                                             (dissoc :pending-skill-test)
                                             (tu/log-event :bashketball/clear-skill-test
                                                           {:cleared-test cleared}))]
                           (fx/success new-state [{:cleared-test cleared}]))))

  ;; Choice Effects

  (fx/register-effect! :bashketball/offer-choice
                       (fn [state {:keys [choice-type options waiting-for context continuation]} ctx _opts]
                         (let [resolved-team  (resolve-param waiting-for ctx state)
                               choice-id      (tu/generate-id)
                               pending-choice (cond-> {:id          choice-id
                                                       :type        choice-type
                                                       :options     options
                                                       :waiting-for resolved-team}
                                                context      (assoc :context context)
                                                continuation (assoc :continuation continuation))
                               new-state      (-> state
                                                  (assoc :pending-choice pending-choice)
                                                  (tu/log-event :bashketball/offer-choice pending-choice))]
                           ;; Return with pending flag so caller knows execution should pause
                           (assoc (fx/success new-state [pending-choice])
                                  :pending {:type :choice
                                            :choice-id choice-id}))))

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
                               choice-id       (tu/generate-id)
                               pending-choice  {:id          choice-id
                                                :type        (or choice-type :forced-choice)
                                                :options     choice-options
                                                :waiting-for target-team
                                                :context     (merge context
                                                                    {:force-target resolved-target
                                                                     :original-options options})}
                               new-state       (-> game-state
                                                   (assoc :pending-choice pending-choice)
                                                   (tu/log-event :bashketball/force-choice pending-choice))]
                           ;; Return with pending flag so caller knows execution should pause
                           (assoc (fx/success new-state [pending-choice])
                                  :pending {:type :forced-choice
                                            :choice-id choice-id
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

  (fx/register-effect! :bashketball/do-submit-choice
                       (fn [state {:keys [choice-id selected]} ctx _opts]
                         (if-let [pending (:pending-choice state)]
                           (let [resolved-choice-id (resolve-param choice-id ctx state)
                                 resolved-selected  (resolve-param selected ctx state)]
                             (if (= resolved-choice-id (:id pending))
                               (let [new-state (-> state
                                                   (assoc-in [:pending-choice :selected] resolved-selected)
                                                   (tu/log-event :bashketball/submit-choice
                                                                 {:choice-id resolved-choice-id
                                                                  :selected resolved-selected}))]
                                 (fx/success new-state [{:choice-id resolved-choice-id
                                                         :selected resolved-selected}]))
                               (fx/success state [])))
                           (fx/success state []))))

  (fx/register-effect! :bashketball/do-clear-choice
                       (fn [state _params _ctx _opts]
                         (let [cleared   (:pending-choice state)
                               new-state (-> state
                                             (dissoc :pending-choice)
                                             (tu/log-event :bashketball/clear-choice
                                                           {:cleared-choice cleared}))]
                           (fx/success new-state [{:cleared-choice cleared}]))))

  ;; =========================================================================
  ;; Modifier Effects (Event-Driven)
  ;; =========================================================================

  (fx/register-effect! :bashketball/add-modifier
                       (fn [state {:keys [player-id stat amount source expires-at]} ctx opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               resolved-stat      (resolve-param stat ctx state)
                               resolved-amount    (resolve-param amount ctx state)
                               resolved-source    (resolve-param source ctx state)
                               resolved-expires   (resolve-param expires-at ctx state)
                               team               (state/get-basketball-player-team state resolved-player-id)
                               event              {:event-type :bashketball/add-modifier.request
                                                   :player-id resolved-player-id
                                                   :stat resolved-stat
                                                   :amount resolved-amount
                                                   :source resolved-source
                                                   :expires-at resolved-expires
                                                   :team team
                                                   :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

  (fx/register-effect! :bashketball/do-add-modifier
                       (fn [state {:keys [player-id id stat amount source expires-at]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               resolved-id        (or (resolve-param id ctx state) (tu/generate-id))
                               resolved-stat      (resolve-param stat ctx state)
                               resolved-amount    (resolve-param amount ctx state)
                               resolved-source    (resolve-param source ctx state)
                               resolved-expires   (resolve-param expires-at ctx state)
                               modifier           (cond-> {:id     resolved-id
                                                           :stat   resolved-stat
                                                           :amount resolved-amount}
                                                    resolved-source  (assoc :source resolved-source)
                                                    resolved-expires (assoc :expires-at resolved-expires))
                               new-state          (-> state
                                                      (state/update-basketball-player
                                                       resolved-player-id update :modifiers conj modifier)
                                                      (tu/log-event :bashketball/add-modifier
                                                                    {:player-id resolved-player-id
                                                                     :modifier modifier}))]
                           (fx/success new-state [{:modifier modifier}]))))

  (fx/register-effect! :bashketball/remove-modifier
                       (fn [state {:keys [player-id modifier-id]} ctx opts]
                         (let [resolved-player-id   (resolve-param player-id ctx state)
                               resolved-modifier-id (resolve-param modifier-id ctx state)
                               team                 (state/get-basketball-player-team state resolved-player-id)
                               event                {:event-type :bashketball/remove-modifier.request
                                                     :player-id resolved-player-id
                                                     :modifier-id resolved-modifier-id
                                                     :team team
                                                     :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

  (fx/register-effect! :bashketball/do-remove-modifier
                       (fn [state {:keys [player-id modifier-id]} ctx _opts]
                         (let [resolved-player-id   (resolve-param player-id ctx state)
                               resolved-modifier-id (resolve-param modifier-id ctx state)
                               new-state            (-> state
                                                        (state/update-basketball-player
                                                         resolved-player-id update :modifiers
                                                         (fn [mods]
                                                           (vec (remove #(= (:id %) resolved-modifier-id) mods))))
                                                        (tu/log-event :bashketball/remove-modifier
                                                                      {:player-id resolved-player-id
                                                                       :modifier-id resolved-modifier-id}))]
                           (fx/success new-state [{:player-id resolved-player-id
                                                   :modifier-id resolved-modifier-id}]))))

  (fx/register-effect! :bashketball/clear-modifiers
                       (fn [state {:keys [player-id]} ctx opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               team               (state/get-basketball-player-team state resolved-player-id)
                               event              {:event-type :bashketball/clear-modifiers.request
                                                   :player-id resolved-player-id
                                                   :team team
                                                   :causation (:causation opts)}]
                           (triggers/fire-request-event
                            {:state              state
                             :registry           (:registry opts)
                             :event-counters     (:event-counters opts)
                             :executing-triggers (:executing-triggers opts)}
                            event))))

  (fx/register-effect! :bashketball/do-clear-modifiers
                       (fn [state {:keys [player-id]} ctx _opts]
                         (let [resolved-player-id (resolve-param player-id ctx state)
                               new-state          (-> state
                                                      (state/update-basketball-player
                                                       resolved-player-id assoc :modifiers [])
                                                      (tu/log-event :bashketball/clear-modifiers
                                                                    {:player-id resolved-player-id}))]
                           (fx/success new-state [{:player-id resolved-player-id}]))))

  ;; Ability Effects

  (fx/register-effect! :bashketball/attach-ability
                       (fn [state {:keys [player instance-id target-player-id]} ctx opts]
                         (let [resolved-player   (resolve-param player ctx state)
                               resolved-instance (resolve-param instance-id ctx state)
                               resolved-target   (resolve-param target-player-id ctx state)
                               deck-path         [:players resolved-player :deck]
                               hand              (get-in state (conj deck-path :hand))
                               card              (first (filter #(= (:instance-id %) resolved-instance) hand))
                               new-hand          (filterv #(not= (:instance-id %) resolved-instance) hand)
                               props             (state/get-ability-card-properties state resolved-player (:card-slug card))
                               attachment        {:instance-id        (:instance-id card)
                                                  :card-slug          (:card-slug card)
                                                  :removable          (:removable props)
                                                  :detach-destination (:detach-destination props)
                                                  :attached-at        (tu/now)}
                               new-state         (-> state
                                                     (assoc-in (conj deck-path :hand) new-hand)
                                                     (state/update-basketball-player resolved-target
                                                                                     update :attachments conj attachment)
                                                     (tu/log-event :bashketball/attach-ability
                                                                   {:player           resolved-player
                                                                    :instance-id      resolved-instance
                                                                    :attached-card    card
                                                                    :target-player-id resolved-target}))
                               team              (state/get-basketball-player-team new-state resolved-target)
                               new-registry      (when (and (:registry opts) (:effect-catalog opts))
                                                   (card-effects/register-attached-abilities
                                                    (:registry opts) (:effect-catalog opts)
                                                    attachment resolved-target team))]
                           (cond-> (fx/success new-state [{:instance-id resolved-instance
                                                           :target-player-id resolved-target}])
                             new-registry (assoc :registry new-registry)))))

  (fx/register-effect! :bashketball/detach-ability
                       (fn [state {:keys [player target-player-id instance-id]} ctx opts]
                         (let [resolved-player   (resolve-param player ctx state)
                               resolved-target   (resolve-param target-player-id ctx state)
                               resolved-instance (resolve-param instance-id ctx state)
                               attachment        (state/find-attachment state resolved-target resolved-instance)
                               is-token?         (state/token? attachment)
                               destination       (:detach-destination attachment)
                               card-instance     {:instance-id (:instance-id attachment)
                                                  :card-slug   (:card-slug attachment)}
                               dest-key          (if (= destination :detach/REMOVED) :removed :discard)
                               new-state         (-> state
                                                     (state/update-basketball-player
                                                      resolved-target
                                                      update :attachments
                                                      (fn [atts] (filterv #(not= (:instance-id %) resolved-instance) atts)))
                                                     (cond->
                                                      (not is-token?)
                                                       (update-in [:players resolved-player :deck dest-key] conj card-instance))
                                                     (tu/log-event :bashketball/detach-ability
                                                                   {:player           resolved-player
                                                                    :instance-id      resolved-instance
                                                                    :detached-card    attachment
                                                                    :target-player-id resolved-target
                                                                    :destination      (if is-token? :deleted destination)
                                                                    :token-deleted?   is-token?}))
                               new-registry      (when (:registry opts)
                                                   (card-effects/unregister-attached-abilities
                                                    (:registry opts) resolved-instance))]
                           (cond-> (fx/success new-state [{:instance-id resolved-instance}])
                             new-registry (assoc :registry new-registry)))))

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

  ;; Terminal effect: sets game phase
  (fx/register-effect! :bashketball/do-set-phase
                       (fn [state {:keys [phase]} ctx _opts]
                         (let [resolved-phase (resolve-param phase ctx state)
                               new-state      (-> state
                                                  (assoc :phase resolved-phase)
                                                  (tu/log-event :bashketball/set-phase {:phase resolved-phase}))]
                           (fx/success new-state [{:phase resolved-phase}]))))

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
                         (let [prev-active (:active-player state)
                               new-active  ({:team/HOME :team/AWAY :team/AWAY :team/HOME} prev-active)
                               new-turn    (inc (:turn-number state))
                               new-state   (-> state
                                               (assoc :turn-number new-turn)
                                               (assoc :active-player new-active)
                                               (tu/log-event :bashketball/advance-turn
                                                             {:turn-number new-turn
                                                              :active-player new-active}))]
                           (fx/success new-state [{:turn-number new-turn
                                                   :active-player new-active}]))))

  (fx/register-effect! :bashketball/do-set-active-player
                       (fn [state {:keys [player]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               new-state       (-> state
                                                   (assoc :active-player resolved-player)
                                                   (tu/log-event :bashketball/set-active-player
                                                                 {:player resolved-player}))]
                           (fx/success new-state [{:player resolved-player}]))))

  ;; Player Resource Effects

  (fx/register-effect! :bashketball/do-set-actions
                       (fn [state {:keys [player amount]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-amount (resolve-param amount ctx state)
                               new-state       (-> state
                                                   (assoc-in [:players resolved-player :actions-remaining] resolved-amount)
                                                   (tu/log-event :bashketball/set-actions
                                                                 {:player resolved-player
                                                                  :amount resolved-amount}))]
                           (fx/success new-state [{:player resolved-player :amount resolved-amount}]))))

  (fx/register-effect! :bashketball/do-remove-cards
                       (fn [state {:keys [player instance-ids]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-ids    (resolve-param instance-ids ctx state)
                               deck-path       [:players resolved-player :deck]
                               hand            (get-in state (conj deck-path :hand))
                               id-set          (set resolved-ids)
                               removed         (filterv #(id-set (:instance-id %)) hand)
                               new-hand        (filterv #(not (id-set (:instance-id %))) hand)
                               new-state       (-> state
                                                   (assoc-in (conj deck-path :hand) new-hand)
                                                   (update-in (conj deck-path :removed) into removed)
                                                   (tu/log-event :bashketball/remove-cards
                                                                 {:player resolved-player
                                                                  :instance-ids resolved-ids
                                                                  :removed-cards removed}))]
                           (fx/success new-state [{:player resolved-player :removed-cards removed}]))))

  (fx/register-effect! :bashketball/do-shuffle-deck
                       (fn [state {:keys [player]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               new-state       (-> state
                                                   (update-in [:players resolved-player :deck :draw-pile] shuffle)
                                                   (tu/log-event :bashketball/shuffle-deck
                                                                 {:player resolved-player}))]
                           (fx/success new-state [{:player resolved-player}]))))

  (fx/register-effect! :bashketball/do-return-discard
                       (fn [state {:keys [player]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               deck-path       [:players resolved-player :deck]
                               discard         (get-in state (conj deck-path :discard))
                               new-state       (-> state
                                                   (update-in (conj deck-path :draw-pile) into discard)
                                                   (assoc-in (conj deck-path :discard) [])
                                                   (tu/log-event :bashketball/return-discard
                                                                 {:player resolved-player
                                                                  :count (count discard)}))]
                           (fx/success new-state [{:player resolved-player :count (count discard)}]))))

  ;; Stack Effects

  (fx/register-effect! :bashketball/do-push-stack
                       (fn [state {:keys [effect]} ctx _opts]
                         (let [resolved-effect (resolve-param effect ctx state)
                               new-state       (-> state
                                                   (update :stack conj resolved-effect)
                                                   (tu/log-event :bashketball/push-stack
                                                                 {:effect resolved-effect}))]
                           (fx/success new-state [{:effect resolved-effect}]))))

  (fx/register-effect! :bashketball/do-pop-stack
                       (fn [state _params _ctx _opts]
                         (let [popped    (peek (:stack state))
                               new-state (-> state
                                             (update :stack pop)
                                             (tu/log-event :bashketball/pop-stack
                                                           {:popped-effect popped}))]
                           (fx/success new-state [{:popped-effect popped}]))))

  (fx/register-effect! :bashketball/do-clear-stack
                       (fn [state _params _ctx _opts]
                         (let [cleared   (:stack state)
                               new-state (-> state
                                             (assoc :stack [])
                                             (tu/log-event :bashketball/clear-stack
                                                           {:cleared-count (count cleared)}))]
                           (fx/success new-state [{:cleared-count (count cleared)}]))))

  ;; Ball In-Air Effect

  (fx/register-effect! :bashketball/do-set-ball-in-air
                       (fn [state {:keys [origin target action-type]} ctx _opts]
                         (let [resolved-origin (resolve-param origin ctx state)
                               resolved-target (resolve-param target ctx state)
                               resolved-action (resolve-param action-type ctx state)
                               new-state       (-> state
                                                   (assoc :ball {:status      :ball-status/IN_AIR
                                                                 :origin      resolved-origin
                                                                 :target      resolved-target
                                                                 :action-type resolved-action})
                                                   (tu/log-event :bashketball/set-ball-in-air
                                                                 {:origin resolved-origin
                                                                  :target resolved-target
                                                                  :action-type resolved-action}))]
                           (fx/success new-state [{:origin resolved-origin
                                                   :target resolved-target}]))))

  ;; Reveal Fate (for skill tests)

  (fx/register-effect! :bashketball/do-reveal-fate
                       (fn [state {:keys [player]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               deck-path       [:players resolved-player :deck]
                               draw-pile       (get-in state (conj deck-path :draw-pile))
                               revealed        (first draw-pile)
                               new-state       (-> state
                                                   (update-in (conj deck-path :draw-pile) (comp vec rest))
                                                   (update-in (conj deck-path :discard) conj revealed)
                                                   (tu/log-event :bashketball/reveal-fate
                                                                 {:player resolved-player
                                                                  :revealed-card revealed}))]
                           (fx/success new-state [{:revealed-card revealed}]))))

  ;; Record Skill Test (event logging only)

  (fx/register-effect! :bashketball/do-record-skill-test
                       (fn [state {:keys [skill-test]} ctx _opts]
                         (let [resolved-test (resolve-param skill-test ctx state)
                               new-state     (tu/log-event state :bashketball/record-skill-test
                                                           {:skill-test resolved-test})]
                           (fx/success new-state [{:skill-test resolved-test}]))))

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
                             ;; Over limit: directly create pending-choice
                             (let [choice-id      (tu/generate-id)
                                   options        (mapv (fn [card]
                                                          {:id (keyword (:instance-id card))
                                                           :label (:card-slug card)})
                                                        hand)
                                   pending-choice {:id          choice-id
                                                   :type        :discard-to-hand-limit
                                                   :options     options
                                                   :waiting-for resolved-team
                                                   :context     {:discard-count excess
                                                                 :hand-limit hand-limit}}
                                   new-state      (-> game-state
                                                      (assoc :pending-choice pending-choice)
                                                      (tu/log-event :bashketball/check-hand-limit pending-choice))]
                               (assoc (fx/success new-state [pending-choice])
                                      :pending {:type :choice
                                                :choice-id choice-id}))
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
                         (let [resolved-attacker    (resolve-param attacker-id ctx state opts)
                               resolved-defender    (resolve-param defender-id ctx state opts)
                               ;; Build context for setup functions (need registry for stat calculation)
                               setup-ctx            {:state state :registry (:registry opts)}
                               ;; Get the appropriate skill test setup based on action type
                               test-config          (case action-type
                                                      :shoot (sap/setup-shoot-test setup-ctx resolved-attacker)
                                                      :pass  (sap/setup-pass-test setup-ctx resolved-attacker resolved-defender)
                                                      :steal (sap/setup-steal-test setup-ctx resolved-attacker resolved-defender)
                                                      :screen (sap/setup-screen-test setup-ctx resolved-attacker resolved-defender)
                                                      :check (sap/setup-check-test setup-ctx resolved-attacker resolved-defender)
                                                      :block (sap/setup-shoot-test setup-ctx resolved-defender))
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
                           (let [team              (:self/team effect-context)
                                 defending-team    (if (= team :team/HOME) :team/AWAY :team/HOME)

                                 ;; Step 1: Process fuel cards (fire signal events)
                                 fuel-ctx          (reduce
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
                                 before-event      {:type        :bashketball/play-card.before
                                                    :team        team
                                                    :card-slug   (:card-slug main-card)
                                                    :instance-id (:instance-id main-card)
                                                    :targets     targets}
                                 responses         (sar/find-matching-responses
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
                         ;; Apply the play effect with context bindings (normalize :effect/type -> :type)
                         (let [effect-result (fx/apply-effect state (normalize-effect play-effect)
                                                              {:bindings effect-context} opts)]
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
                             (fx/success state [])))))

  ;; =========================================================================
  ;; Lifecycle Effects (with registry updates)
  ;; =========================================================================

  (fx/register-effect! :bashketball/do-substitute
                       (fn [state {:keys [on-court-id off-court-id]} ctx opts]
                         (let [resolved-on-court  (resolve-param on-court-id ctx state)
                               resolved-off-court (resolve-param off-court-id ctx state)
                               on-court-pos       (:position (state/get-basketball-player state resolved-on-court))
                               team               (state/get-basketball-player-team state resolved-on-court)
                               new-state          (-> state
                                                      (state/update-basketball-player resolved-on-court assoc :position nil)
                                                      (state/update-basketball-player resolved-off-court assoc :position on-court-pos)
                                                      (cond->
                                                       on-court-pos
                                                        (update :board board/set-occupant on-court-pos
                                                                {:type :occupant/BASKETBALL_PLAYER :id resolved-off-court}))
                                                      (tu/log-event :bashketball/substitute
                                                                    {:on-court-id resolved-on-court
                                                                     :off-court-id resolved-off-court
                                                                     :position on-court-pos}))
                               entering-player    (state/get-basketball-player new-state resolved-off-court)
                               new-registry       (when (and (:registry opts) (:effect-catalog opts))
                                                    (-> (:registry opts)
                                                        (card-effects/handle-player-leaving-court resolved-on-court)
                                                        (card-effects/handle-player-entering-court
                                                         (:effect-catalog opts) entering-player team)))]
                           (cond-> (fx/success new-state [{:on-court-id resolved-on-court
                                                           :off-court-id resolved-off-court}])
                             new-registry (assoc :registry new-registry)))))

  (fx/register-effect! :bashketball/do-move-asset
                       (fn [state {:keys [player instance-id destination]} ctx opts]
                         (let [resolved-player      (resolve-param player ctx state)
                               resolved-instance-id (resolve-param instance-id ctx state)
                               resolved-destination (resolve-param destination ctx state)
                               assets               (get-in state [:players resolved-player :assets])
                               moved-card           (first (filter #(= (:instance-id %) resolved-instance-id) assets))
                               is-token?            (state/token? moved-card)
                               new-assets           (filterv #(not= (:instance-id %) resolved-instance-id) assets)
                               dest-key             ({:DISCARD :discard :REMOVED :removed} resolved-destination)
                               new-state            (-> state
                                                        (assoc-in [:players resolved-player :assets] new-assets)
                                                        (cond->
                                                         (not is-token?)
                                                          (update-in [:players resolved-player :deck dest-key] conj moved-card))
                                                        (tu/log-event :bashketball/move-asset
                                                                      {:player         resolved-player
                                                                       :instance-id    resolved-instance-id
                                                                       :moved-asset    moved-card
                                                                       :destination    (if is-token? :deleted resolved-destination)
                                                                       :token-deleted? is-token?}))
                               new-registry         (when (:registry opts)
                                                      (card-effects/unregister-asset-triggers
                                                       (:registry opts) resolved-instance-id))]
                           (cond-> (fx/success new-state [{:instance-id resolved-instance-id
                                                           :destination (if is-token? :deleted resolved-destination)}])
                             new-registry (assoc :registry new-registry)))))

  (fx/register-effect! :bashketball/do-create-token
                       (fn [state {:keys [player card placement target-player-id]} ctx opts]
                         (let [resolved-player    (resolve-param player ctx state)
                               resolved-card      (resolve-param card ctx state)
                               resolved-placement (resolve-param placement ctx state)
                               resolved-target    (resolve-param target-player-id ctx state)
                               ;; Normalize the token card
                               normalized-card    (let [card-type (keyword "card-type" (name (:card-type resolved-card)))
                                                        base      (-> resolved-card
                                                                      (assoc :card-type card-type)
                                                                      (update :set-slug #(or % "tokens")))]
                                                    (case card-type
                                                      :card-type/TEAM_ASSET_CARD
                                                      (update base :asset-power #(or % ""))
                                                      :card-type/ABILITY_CARD
                                                      (-> base
                                                          (update :fate #(or % 0))
                                                          (update :abilities #(or % [])))
                                                      base))
                               instance-id        (tu/generate-id)
                               token-instance     {:instance-id instance-id
                                                   :token       true
                                                   :card        normalized-card}
                               new-state          (-> state
                                                      (cond->
                                                       (= resolved-placement :placement/ASSET)
                                                        (update-in [:players resolved-player :assets] conj token-instance)

                                                        (= resolved-placement :placement/ATTACH)
                                                        (state/update-basketball-player
                                                         resolved-target update :attachments conj
                                                         {:instance-id        instance-id
                                                          :token              true
                                                          :card               normalized-card
                                                          :removable          (get resolved-card :removable true)
                                                          :detach-destination (get resolved-card :detach-destination :detach/DISCARD)
                                                          :attached-at        (tu/now)}))
                                                      (tu/log-event :bashketball/create-token
                                                                    {:player           resolved-player
                                                                     :created-token    token-instance
                                                                     :placement        resolved-placement
                                                                     :target-player-id resolved-target}))
                               new-registry       (when (and (:registry opts) (:effect-catalog opts))
                                                    (case resolved-placement
                                                      :placement/ASSET
                                                      (card-effects/register-asset-triggers
                                                       (:registry opts) (:effect-catalog opts)
                                                       token-instance resolved-player)

                                                      :placement/ATTACH
                                                      (let [team (state/get-basketball-player-team new-state resolved-target)]
                                                        (card-effects/register-attached-abilities
                                                         (:registry opts) (:effect-catalog opts)
                                                         token-instance resolved-target team))

                                                      (:registry opts)))]
                           (cond-> (fx/success new-state [{:instance-id instance-id
                                                           :placement resolved-placement}])
                             new-registry (assoc :registry new-registry)))))

  (fx/register-effect! :bashketball/do-resolve-card
                       (fn [state {:keys [instance-id target-player-id]} ctx opts]
                         (let [resolved-instance-id (resolve-param instance-id ctx state)
                               resolved-target      (resolve-param target-player-id ctx state)
                               play-area-card       (state/find-card-in-play-area state resolved-instance-id)
                               owner                (:played-by play-area-card)
                               card-slug            (:card-slug play-area-card)
                               is-virtual           (:virtual play-area-card)
                               card-instance        {:instance-id resolved-instance-id :card-slug card-slug}
                               ;; Look up card type from owner's deck
                               cards                (get-in state [:players owner :deck :cards])
                               card-def             (some #(when (= (:slug %) card-slug) %) cards)
                               is-asset             (and (not is-virtual)
                                                         (= (:card-type card-def) :card-type/TEAM_ASSET_CARD))
                               is-attach            (and (not is-virtual)
                                                         resolved-target
                                                         (= (:card-type card-def) :card-type/ABILITY_CARD))
                               new-state            (-> state
                                                        (update :play-area (fn [pa] (filterv #(not= (:instance-id %) resolved-instance-id) pa)))
                                                        (cond->
                                                         is-attach
                                                          (as-> s
                                                                (let [props      (state/get-ability-card-properties s owner card-slug)
                                                                      attachment {:instance-id        resolved-instance-id
                                                                                  :card-slug          card-slug
                                                                                  :removable          (:removable props)
                                                                                  :detach-destination (:detach-destination props)
                                                                                  :attached-at        (tu/now)}]
                                                                  (state/update-basketball-player s resolved-target
                                                                                                  update :attachments conj attachment)))

                                                          is-asset
                                                          (update-in [:players owner :assets] conj card-instance)

                                                          (and (not is-virtual) (not is-asset) (not is-attach))
                                                          (update-in [:players owner :deck :discard] conj card-instance))
                                                        (tu/log-event :bashketball/resolve-card
                                                                      (cond-> {:resolved-card play-area-card
                                                                               :virtual       is-virtual}
                                                                        resolved-target (assoc :target-player-id resolved-target))))
                               new-registry         (when (and (:registry opts) (:effect-catalog opts))
                                                      (cond
                                                        is-attach
                                                        (let [attachment (state/find-attachment new-state resolved-target resolved-instance-id)
                                                              team       (state/get-basketball-player-team new-state resolved-target)]
                                                          (card-effects/register-attached-abilities
                                                           (:registry opts) (:effect-catalog opts)
                                                           attachment resolved-target team))

                                                        is-asset
                                                        (let [asset (first (filter #(= (:instance-id %) resolved-instance-id)
                                                                                   (get-in new-state [:players owner :assets])))]
                                                          (card-effects/register-asset-triggers
                                                           (:registry opts) (:effect-catalog opts)
                                                           asset owner))

                                                        :else (:registry opts)))]
                           (cond-> (fx/success new-state [{:instance-id resolved-instance-id
                                                           :resolved-card play-area-card}])
                             new-registry (assoc :registry new-registry)))))

  ;; =========================================================================
  ;; Card Staging Effects
  ;; =========================================================================

  (fx/register-effect! :bashketball/do-stage-card
                       (fn [state {:keys [player instance-id]} ctx _opts]
                         (let [resolved-player      (resolve-param player ctx state)
                               resolved-instance-id (resolve-param instance-id ctx state)
                               deck-path            [:players resolved-player :deck]
                               hand                 (get-in state (conj deck-path :hand))
                               card                 (first (filter #(= (:instance-id %) resolved-instance-id) hand))]
                           (if-not card
                             (throw (ex-info "Card not in hand"
                                             {:player resolved-player :instance-id resolved-instance-id}))
                             (let [new-hand       (filterv #(not= (:instance-id %) resolved-instance-id) hand)
                                   play-area-card {:instance-id (:instance-id card)
                                                   :card-slug   (:card-slug card)
                                                   :played-by   resolved-player}
                                   new-state      (-> state
                                                      (assoc-in (conj deck-path :hand) new-hand)
                                                      (update :play-area conj play-area-card)
                                                      (tu/log-event :bashketball/stage-card
                                                                    {:staged-card card
                                                                     :player resolved-player}))]
                               (fx/success new-state [{:instance-id resolved-instance-id
                                                       :card card}]))))))

  (fx/register-effect! :bashketball/do-stage-virtual-standard-action
                       (fn [state {:keys [player discard-instance-ids card-slug]} ctx _opts]
                         (let [resolved-player      (resolve-param player ctx state)
                               resolved-discard-ids (resolve-param discard-instance-ids ctx state)
                               resolved-card-slug   (resolve-param card-slug ctx state)
                               deck-path            [:players resolved-player :deck]
                               hand                 (get-in state (conj deck-path :hand))
                               id-set               (set resolved-discard-ids)
                               discarded            (filterv #(id-set (:instance-id %)) hand)
                               new-hand             (filterv #(not (id-set (:instance-id %))) hand)
                               instance-id          (tu/generate-id)
                               play-area-card       {:instance-id instance-id
                                                     :card-slug   resolved-card-slug
                                                     :played-by   resolved-player
                                                     :virtual     true}
                               new-state            (-> state
                                                        (assoc-in (conj deck-path :hand) new-hand)
                                                        (update-in (conj deck-path :discard) into discarded)
                                                        (update :play-area conj play-area-card)
                                                        (tu/log-event :bashketball/stage-virtual-standard-action
                                                                      {:discarded-cards discarded
                                                                       :virtual-card    play-area-card}))]
                           (fx/success new-state [{:instance-id instance-id
                                                   :discarded discarded
                                                   :virtual-card play-area-card}]))))

  (fx/register-effect! :bashketball/do-examine-cards
                       (fn [state {:keys [player count]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-count  (resolve-param count ctx state)
                               deck-path       [:players resolved-player :deck]
                               draw-pile       (get-in state (conj deck-path :draw-pile))
                               actual-count    (min resolved-count (clojure.core/count draw-pile))
                               examined        (vec (take actual-count draw-pile))
                               remaining       (vec (drop actual-count draw-pile))
                               new-state       (-> state
                                                   (assoc-in (conj deck-path :draw-pile) remaining)
                                                   (assoc-in (conj deck-path :examined) examined)
                                                   (tu/log-event :bashketball/examine-cards
                                                                 {:player          resolved-player
                                                                  :examined-cards  examined
                                                                  :requested-count resolved-count
                                                                  :actual-count    actual-count}))]
                           (fx/success new-state [{:examined examined
                                                   :actual-count actual-count}]))))

  (fx/register-effect! :bashketball/do-resolve-examined-cards
                       (fn [state {:keys [player placements]} ctx _opts]
                         (let [resolved-player     (resolve-param player ctx state)
                               resolved-placements (resolve-param placements ctx state)
                               deck-path           [:players resolved-player :deck]
                               examined            (get-in state (conj deck-path :examined))
                               ;; Validate placements match examined cards
                               examined-ids        (set (map :instance-id examined))
                               placement-ids       (set (map :instance-id resolved-placements))
                               _                   (when (not= examined-ids placement-ids)
                                                     (throw (ex-info "Placements must include all examined cards"
                                                                     {:examined-ids  examined-ids
                                                                      :placement-ids placement-ids
                                                                      :missing       (set/difference examined-ids placement-ids)
                                                                      :extra         (set/difference placement-ids examined-ids)})))
                               ;; Group cards by destination
                               examined-by-id      (into {} (map (juxt :instance-id identity) examined))
                               grouped             (group-by :destination resolved-placements)
                               top-cards           (mapv #(examined-by-id (:instance-id %)) (get grouped :examine/TOP []))
                               bottom-cards        (mapv #(examined-by-id (:instance-id %)) (get grouped :examine/BOTTOM []))
                               discard-cards       (mapv #(examined-by-id (:instance-id %)) (get grouped :examine/DISCARD []))
                               draw-pile           (get-in state (conj deck-path :draw-pile))
                               new-state           (-> state
                                                       (assoc-in (conj deck-path :examined) [])
                                                       (assoc-in (conj deck-path :draw-pile) (into top-cards draw-pile))
                                                       (update-in (conj deck-path :draw-pile) into bottom-cards)
                                                       (update-in (conj deck-path :discard) into discard-cards)
                                                       (tu/log-event :bashketball/resolve-examined-cards
                                                                     {:player              resolved-player
                                                                      :placements          resolved-placements
                                                                      :top-count           (clojure.core/count top-cards)
                                                                      :bottom-count        (clojure.core/count bottom-cards)
                                                                      :discard-count       (clojure.core/count discard-cards)}))]
                           (fx/success new-state [{:top-count (clojure.core/count top-cards)
                                                   :bottom-count (clojure.core/count bottom-cards)
                                                   :discard-count (clojure.core/count discard-cards)}]))))

  ;; =========================================================================
  ;; Orchestration Effects
  ;; =========================================================================

  (fx/register-effect! :bashketball/start-from-tipoff
                       (fn [state {:keys [player]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               new-state       (-> state
                                                   (assoc :phase :phase/UPKEEP)
                                                   (assoc :active-player resolved-player)
                                                   (tu/log-event :bashketball/start-from-tipoff
                                                                 {:player resolved-player}))]
                           (fx/success new-state [{:player resolved-player}]))))

  (fx/register-effect! :bashketball/refresh-all
                       (fn [state {:keys [team]} ctx _opts]
                         (let [resolved-team (resolve-param team ctx state)
                               player-ids    (keys (state/get-all-players state resolved-team))
                               new-state     (reduce (fn [s pid]
                                                       (state/update-basketball-player s pid assoc :exhausted false))
                                                     state
                                                     player-ids)
                               new-state'    (tu/log-event new-state :bashketball/refresh-all
                                                           {:team resolved-team
                                                            :player-count (count player-ids)})]
                           (fx/success new-state' [{:team resolved-team
                                                    :refreshed-count (count player-ids)}]))))

  ;; Calculation Event Effects

  (fx/register-effect! :bashketball/inject-modifier
                       (fn [state {:keys [stat amount multiplier]} _ctx _opts]
                         ;; This effect doesn't modify state - it returns modifier data
                         ;; that the calculation system collects from trigger results
                         {:state state
                          :applied [{:type :bashketball/inject-modifier}]
                          :failed []
                          :pending nil
                          :modifiers [{:stat stat
                                       :amount amount
                                       :multiplier multiplier}]})))
