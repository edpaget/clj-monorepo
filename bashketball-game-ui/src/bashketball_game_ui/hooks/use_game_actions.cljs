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
  - `:set-ball-loose` - fn [q r] -> Promise (move ball to position)
  - `:set-ball-possessed` - fn [player-id] -> Promise (give ball to player)
  - `:reveal-fate` - fn [team] -> Promise (reveal top card's fate, returns revealedFate)
  - `:draw-cards` - fn [team count] -> Promise
  - `:discard-cards` - fn [team instance-ids] -> Promise
  - `:end-turn` - fn [] -> Promise
  - `:set-phase` - fn [phase] -> Promise
  - `:shuffle-deck` - fn [team] -> Promise (shuffle draw pile)
  - `:return-discard` - fn [team] -> Promise (return discard pile to draw pile)
  - `:substitute` - fn [starter-id bench-id] -> Promise (swap starter with bench player)
  - `:add-score` - fn [team points] -> Promise (add points to team, negative to decrement)
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
                                                                        :target      (if (vector? target)
                                                                                       {:target-type "position"
                                                                                        :position    target}
                                                                                       {:target-type "player"
                                                                                        :player-id   target})
                                                                        :action-type "pass"}))
                                                             [submit])

        shoot-ball                                          (use-callback
                                                             (fn [origin target]
                                                               (submit {:type        "bashketball/set-ball-in-air"
                                                                        :origin      origin
                                                                        :target      {:target-type "position"
                                                                                      :position    target}
                                                                        :action-type "shot"}))
                                                             [submit])

        set-ball-loose                                      (use-callback
                                                             (fn [q r]
                                                               (submit {:type     "bashketball/set-ball-loose"
                                                                        :position [q r]}))
                                                             [submit])

        set-ball-possessed                                  (use-callback
                                                             (fn [player-id]
                                                               (submit {:type      "bashketball/set-ball-possessed"
                                                                        :holder-id player-id}))
                                                             [submit])

        reveal-fate                                         (use-callback
                                                             (fn [team]
                                                               (submit {:type   "bashketball/reveal-fate"
                                                                        :player (name team)}))
                                                             [submit])

        draw-cards                                          (use-callback
                                                             (fn [team count]
                                                               (submit {:type   "bashketball/draw-cards"
                                                                        :player (name team)
                                                                        :count  count}))
                                                             [submit])

        discard-cards                                       (use-callback
                                                             (fn [team instance-ids]
                                                               (submit {:type         "bashketball/discard-cards"
                                                                        :player       (name team)
                                                                        :instance-ids instance-ids}))
                                                             [submit])

        end-turn                                            (use-callback
                                                             (fn []
                                                               (submit {:type "bashketball/advance-turn"}))
                                                             [submit])

        set-phase                                           (use-callback
                                                             (fn [phase]
                                                               (submit {:type  "bashketball/set-phase"
                                                                        :phase (if (string? phase) phase (name phase))}))
                                                             [submit])

        shuffle-deck                                        (use-callback
                                                             (fn [team]
                                                               (submit {:type   "bashketball/shuffle-deck"
                                                                        :player (name team)}))
                                                             [submit])

        return-discard                                      (use-callback
                                                             (fn [team]
                                                               (submit {:type   "bashketball/return-discard"
                                                                        :player (name team)}))
                                                             [submit])

        substitute                                          (use-callback
                                                             (fn [starter-id bench-id]
                                                               (submit {:type       "bashketball/substitute"
                                                                        :starter-id starter-id
                                                                        :bench-id   bench-id}))
                                                             [submit])

        add-score                                           (use-callback
                                                             (fn [team points]
                                                               (submit {:type   "bashketball/add-score"
                                                                        :team   (name team)
                                                                        :points points}))
                                                             [submit])]

    (use-memo
     (fn []
       {:submit             submit
        :move-player        move-player
        :pass-ball          pass-ball
        :shoot-ball         shoot-ball
        :set-ball-loose     set-ball-loose
        :set-ball-possessed set-ball-possessed
        :reveal-fate        reveal-fate
        :draw-cards         draw-cards
        :discard-cards      discard-cards
        :end-turn           end-turn
        :set-phase          set-phase
        :shuffle-deck       shuffle-deck
        :return-discard     return-discard
        :substitute         substitute
        :add-score          add-score
        :loading            loading
        :error              error})
     [submit move-player pass-ball shoot-ball set-ball-loose set-ball-possessed
      reveal-fate draw-cards discard-cards end-turn set-phase shuffle-deck
      return-discard substitute add-score loading error])))
