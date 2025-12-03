(ns bashketball-game-ui.hooks.use-game-actions
  "Hook for submitting game actions.

  Provides a convenient interface for submitting various game actions
  to the API via the submitAction GraphQL mutation.
  Uses automatic variable key conversion (kebab-case → camelCase)."
  (:require
   [bashketball-game-ui.graphql.hooks :as gql]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-ui.core]
   [uix.core :refer [use-callback use-memo]]))

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
  (let [[mutate-fn {:keys [loading error]}]
        (gql/use-mutation mutations/SUBMIT_ACTION_MUTATION)

        submit                                              (use-callback
                                                             (fn [action]
                                                               (mutate-fn {:variables {:game-id game-id
                                                                                       :action  action}}))
                                                             [mutate-fn game-id])

        move-player                                         (use-callback
                                                             (fn [player-id q r]
                                                               (submit {:type      "bashketball/move-player"
                                                                        :player-id player-id
                                                                        :position  [q r]}))
                                                             [submit])

        pass-ball                                           (use-callback
                                                             (fn [origin target]
                                                               (submit {:type        "bashketball/set-ball-in-air"
                                                                        :origin      origin
                                                                        :target      target
                                                                        :action-type "pass"}))
                                                             [submit])

        shoot-ball                                          (use-callback
                                                             (fn [origin target]
                                                               (submit {:type        "bashketball/set-ball-in-air"
                                                                        :origin      origin
                                                                        :target      target
                                                                        :action-type "shot"}))
                                                             [submit])

        draw-cards                                          (use-callback
                                                             (fn [team count]
                                                               (submit {:type   "bashketball/draw-cards"
                                                                        :player (name team)
                                                                        :count  count}))
                                                             [submit])

        discard-cards                                       (use-callback
                                                             (fn [team card-slugs]
                                                               (submit {:type       "bashketball/discard-cards"
                                                                        :player     (name team)
                                                                        :card-slugs card-slugs}))
                                                             [submit])

        end-turn                                            (use-callback
                                                             (fn []
                                                               (submit {:type "bashketball/advance-turn"}))
                                                             [submit])

        set-phase                                           (use-callback
                                                             (fn [phase]
                                                               (submit {:type  "bashketball/set-phase"
                                                                        :phase (if (string? phase) phase (name phase))}))
                                                             [submit])]

    (use-memo
     (fn []
       {:submit        submit
        :move-player   move-player
        :pass-ball     pass-ball
        :shoot-ball    shoot-ball
        :draw-cards    draw-cards
        :discard-cards discard-cards
        :end-turn      end-turn
        :set-phase     set-phase
        :loading       loading
        :error         error})
     [submit move-player pass-ball shoot-ball draw-cards discard-cards end-turn set-phase loading error])))
