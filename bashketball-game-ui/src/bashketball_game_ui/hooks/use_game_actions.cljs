(ns bashketball-game-ui.hooks.use-game-actions
  "Hook for submitting game actions.

  Provides a convenient interface for submitting various game actions
  to the API via the submitAction GraphQL mutation."
  (:require
   ["@apollo/client" :refer [useMutation]]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-ui.core]))

(defn- clj->action-input
  "Converts a Clojure action map to GraphQL ActionInput format.

  Transforms kebab-case keys to camelCase for GraphQL."
  [action]
  (let [key-map {:type        :type
                 :phase       :phase
                 :player      :player
                 :amount      :amount
                 :count       :count
                 :card-slugs  :cardSlugs
                 :player-id   :playerId
                 :position    :position
                 :modifier-id :modifierId
                 :starter-id  :starterId
                 :bench-id    :benchId
                 :holder-id   :holderId
                 :origin      :origin
                 :target      :target
                 :target-player-id :targetPlayerId
                 :action-type :actionType
                 :team        :team
                 :points      :points
                 :stat        :stat
                 :base        :base
                 :fate        :fate
                 :modifiers   :modifiers
                 :total       :total}]
    (reduce-kv
     (fn [acc k v]
       (if-let [gql-key (get key-map k)]
         (assoc acc gql-key v)
         acc))
     {}
     action)))

(defn use-game-actions
  "Returns action submission functions for a game.

  Takes a `game-id` and returns a map with:
  - `:submit` - Generic action submission fn (action-map) -> Promise
  - `:move-player` - fn [player-id q r] -> Promise
  - `:pass-ball` - fn [origin target] -> Promise (target can be position or player-id)
  - `:shoot-ball` - fn [origin target] -> Promise
  - `:draw-cards` - fn [team count] -> Promise
  - `:discard-cards` - fn [team card-slugs] -> Promise
  - `:end-turn` - fn [] -> Promise
  - `:set-phase` - fn [phase] -> Promise
  - `:loading` - boolean
  - `:error` - error object or nil"
  [game-id]
  (let [[submit-mutation result] (useMutation mutations/SUBMIT_ACTION_MUTATION)

        submit                   (fn [action]
                                   (submit-mutation
                                    #js {:variables #js {:gameId game-id
                                                         :action (clj->js (clj->action-input action))}}))

        move-player              (fn [player-id q r]
                                   (submit {:type "bashketball/move-player"
                                            :player-id player-id
                                            :position [q r]}))

        pass-ball                (fn [origin target]
                                   (submit {:type "bashketball/set-ball-in-air"
                                            :origin origin
                                            :target target
                                            :action-type "pass"}))

        shoot-ball               (fn [origin target]
                                   (submit {:type "bashketball/set-ball-in-air"
                                            :origin origin
                                            :target target
                                            :action-type "shot"}))

        draw-cards               (fn [team count]
                                   (submit {:type "bashketball/draw-cards"
                                            :player (name team)
                                            :count count}))

        discard-cards            (fn [team card-slugs]
                                   (submit {:type "bashketball/discard-cards"
                                            :player (name team)
                                            :card-slugs card-slugs}))

        end-turn                 (fn []
                                   (submit {:type "bashketball/advance-turn"}))

        set-phase                (fn [phase]
                                   (submit {:type "bashketball/set-phase"
                                            :phase (name phase)}))]

    {:submit        submit
     :move-player   move-player
     :pass-ball     pass-ball
     :shoot-ball    shoot-ball
     :draw-cards    draw-cards
     :discard-cards discard-cards
     :end-turn      end-turn
     :set-phase     set-phase
     :loading       (:loading result)
     :error         (:error result)}))
