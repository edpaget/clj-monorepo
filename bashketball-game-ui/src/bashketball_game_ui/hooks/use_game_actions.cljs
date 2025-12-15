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
  - `:substitute` - fn [on-court-id off-court-id] -> Promise (swap on-court with off-court player)
  - `:stage-card` - fn [team instance-id] -> Promise (move card from hand to play area)
  - `:resolve-card` - fn [instance-id target-player-id?] -> Promise (move card from play area to discard/assets/attachment)
  - `:move-asset` - fn [team instance-id destination] -> Promise (move asset to :discard or :removed)
  - `:add-score` - fn [team points] -> Promise (add points to team, negative to decrement)
  - `:create-token` - fn [team card placement target-player-id] -> Promise (create token as asset or attached)
  - `:stage-virtual-standard-action` - fn [team discard-instance-ids card-slug] -> Promise (discard 2 cards and stage virtual standard action)
  - `:exhaust-player` - fn [player-id] -> Promise (mark player as exhausted)
  - `:refresh-player` - fn [player-id] -> Promise (mark player as not exhausted)
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
                                                             (fn [on-court-id off-court-id]
                                                               (submit {:type         "bashketball/substitute"
                                                                        :on-court-id  on-court-id
                                                                        :off-court-id off-court-id}))
                                                             [submit])

        stage-card                                          (use-callback
                                                             (fn [team instance-id]
                                                               (submit {:type        "bashketball/stage-card"
                                                                        :player      (name team)
                                                                        :instance-id instance-id}))
                                                             [submit])

        resolve-card                                        (use-callback
                                                             (fn [instance-id & [target-player-id]]
                                                               (submit (cond-> {:type        "bashketball/resolve-card"
                                                                                :instance-id instance-id}
                                                                         target-player-id (assoc :target-player-id target-player-id))))
                                                             [submit])

        move-asset                                          (use-callback
                                                             (fn [team instance-id destination]
                                                               (submit {:type        "bashketball/move-asset"
                                                                        :player      (name team)
                                                                        :instance-id instance-id
                                                                        :destination (name destination)}))
                                                             [submit])

        add-score                                           (use-callback
                                                             (fn [team points]
                                                               (submit {:type   "bashketball/add-score"
                                                                        :team   (name team)
                                                                        :points points}))
                                                             [submit])

        create-token                                        (use-callback
                                                             (fn [team card placement target-player-id]
                                                               (submit (cond-> {:type      "bashketball/create-token"
                                                                                :player    (name team)
                                                                                :card      card
                                                                                :placement (name placement)}
                                                                         target-player-id (assoc :target-player-id target-player-id))))
                                                             [submit])

        stage-virtual-standard-action                       (use-callback
                                                             (fn [team discard-instance-ids card-slug]
                                                               (submit {:type                 "bashketball/stage-virtual-standard-action"
                                                                        :player               (name team)
                                                                        :discard-instance-ids (vec discard-instance-ids)
                                                                        :card-slug            card-slug}))
                                                             [submit])

        exhaust-player                                      (use-callback
                                                             (fn [player-id]
                                                               (submit {:type      "bashketball/exhaust-player"
                                                                        :player-id player-id}))
                                                             [submit])

        refresh-player                                      (use-callback
                                                             (fn [player-id]
                                                               (submit {:type      "bashketball/refresh-player"
                                                                        :player-id player-id}))
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
        :stage-card         stage-card
        :resolve-card       resolve-card
        :move-asset         move-asset
        :add-score                      add-score
        :create-token                   create-token
        :stage-virtual-standard-action  stage-virtual-standard-action
        :exhaust-player                 exhaust-player
        :refresh-player                 refresh-player
        :loading                        loading
        :error                          error})
     [submit move-player pass-ball shoot-ball set-ball-loose set-ball-possessed
      reveal-fate draw-cards discard-cards end-turn set-phase shuffle-deck
      return-discard substitute stage-card resolve-card move-asset add-score
      create-token stage-virtual-standard-action exhaust-player refresh-player loading error])))
