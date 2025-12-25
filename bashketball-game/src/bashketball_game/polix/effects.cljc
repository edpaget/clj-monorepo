(ns bashketball-game.polix.effects
  "Bashketball-specific effects for polix.

  Registers effects that wrap existing action handlers. Effects are declarative
  descriptions of state mutations that can be stored in card EDN and resolved
  at runtime. Use [[register-effects!]] at application startup.

  Each effect delegates to the corresponding action in [[bashketball-game.actions]],
  reusing validation and event logging."
  (:require
   [bashketball-game.actions :as actions]
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
  - `:bashketball/add-score` - Add points to a team's score"
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
                           (fx/success new-state [action])))))
