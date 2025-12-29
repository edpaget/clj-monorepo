(ns bashketball-game.polix.effects
  "Bashketball-specific effects for polix.

  Registers effects that wrap existing action handlers. Effects are declarative
  descriptions of state mutations that can be stored in card EDN and resolved
  at runtime. Use [[register-effects!]] at application startup.

  Each effect delegates to the corresponding action in [[bashketball-game.actions]],
  reusing validation and event logging."
  (:require
   [bashketball-game.actions :as actions]
   [bashketball-game.state :as state]
   [polix.effects.core :as fx]))

(defn- resolve-param
  "Resolves a parameter value from context bindings.

  Handles:
  - Keywords: looked up in context bindings, returned as-is if not found
  - Vectors starting with :ctx: resolved from context path
  - Vectors starting with :state: resolved from state path
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

    :else
    param))

(defn register-effects!
  "Registers all bashketball effects with polix.

  Call once at application startup before applying any effects.

  Effects registered:
  - `:bashketball/move-player` - Move player to a position
  - `:bashketball/exhaust-player` - Mark player as exhausted
  - `:bashketball/refresh-player` - Remove exhaustion from player
  - `:bashketball/give-ball` - Set ball possessed by player
  - `:bashketball/loose-ball` - Set ball loose at position
  - `:bashketball/draw-cards` - Draw cards from deck
  - `:bashketball/discard-cards` - Discard cards by instance IDs
  - `:bashketball/add-score` - Add points to a team's score
  - `:bashketball/initiate-skill-test` - Start a skill test for a player
  - `:bashketball/modify-skill-test` - Add a modifier to the pending skill test
  - `:bashketball/offer-choice` - Present a choice to a player (pauses execution)
  - `:bashketball/force-choice` - Force a target player to choose between options"
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
                       (fn [state {:keys [player count]} ctx _opts]
                         (let [resolved-player (resolve-param player ctx state)
                               resolved-count  (resolve-param count ctx state)
                               action          {:type :bashketball/draw-cards
                                                :player resolved-player
                                                :count resolved-count}
                               new-state       (actions/do-action state action)]
                           (fx/success new-state [action]))))

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
                                            :target resolved-target})))))
