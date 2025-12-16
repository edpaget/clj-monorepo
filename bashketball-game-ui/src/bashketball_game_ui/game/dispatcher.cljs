(ns bashketball-game-ui.game.dispatcher
  "Action dispatcher that routes actions to game action handlers.

  Creates a dispatch function from game actions that routes action maps
  to the appropriate handler. Used by the selection state machine to
  execute game actions when transitions trigger them.")

(defn create-dispatcher
  "Creates a dispatch function from game actions and context.

  Takes `actions` from [[use-game-actions]] and a `context` map with:
  - `:my-team` - The current player's team
  - `:get-ball-holder-position` - Fn returning ball holder's [q r] or nil
  - `:selection` - Selection hook for clearing after actions

  Returns a function that dispatches action maps to appropriate handlers."
  [{:keys [actions my-team get-ball-holder-position selection]}]
  (fn dispatch [{:keys [type from to] :as action}]
    (case type
      :move-player
      (let [player-id (:player-id from)
            q         (:q to)
            r         (:r to)]
        (-> ((:move-player actions) player-id q r)
            (.then #(js/console.log "Move result:" %))
            (.catch #(js/console.error "Move error:" %)))
        (when selection
          ((:set-selected-player selection) nil)))

      :set-ball-loose
      (let [q (:q to)
            r (:r to)]
        (-> ((:set-ball-loose actions) q r)
            (.then #(js/console.log "Ball move result:" %))
            (.catch #(js/console.error "Ball move error:" %))))

      :set-ball-possessed
      (let [player-id (:player-id to)]
        (-> ((:set-ball-possessed actions) player-id)
            (.then #(js/console.log "Ball possession result:" %))
            (.catch #(js/console.error "Ball possession error:" %))))

      :pass-to-player
      (let [origin    (get-ball-holder-position)
            player-id (:player-id to)]
        (when origin
          (-> ((:pass-ball actions) origin player-id)
              (.then #(js/console.log "Pass result:" %))
              (.catch #(js/console.error "Pass error:" %)))
          (when selection
            ((:set-selected-player selection) nil))))

      :pass-to-hex
      (let [origin (get-ball-holder-position)
            target [(:q to) (:r to)]]
        (when origin
          (-> ((:pass-ball actions) origin target)
              (.then #(js/console.log "Pass to hex result:" %))
              (.catch #(js/console.error "Pass to hex error:" %)))
          (when selection
            ((:set-selected-player selection) nil))))

      :standard-action
      (let [cards     (:cards from)
            card-slug (:card-slug to)]
        (-> ((:stage-virtual-standard-action actions) my-team cards card-slug)
            (.then #(js/console.log "Standard action staged:" %))
            (.catch #(js/console.error "Standard action error:" %))))

      :substitute
      (let [on-court-id  (:on-court-id action)
            off-court-id (:off-court-id action)]
        (-> ((:substitute actions) on-court-id off-court-id)
            (.then #(js/console.log "Substitute result:" %))
            (.catch #(js/console.error "Substitute error:" %))))

      :discard-cards
      (let [cards (:cards action)]
        (-> ((:discard-cards actions) my-team (vec cards))
            (.then #(js/console.log "Discard result:" %))
            (.catch #(js/console.error "Discard error:" %))))

      :resolve-peek
      (let [target-team (:target-team action)
            placements  (:placements action)]
        (-> ((:resolve-examined-cards actions) target-team placements)
            (.then #(js/console.log "Peek resolve result:" %))
            (.catch #(js/console.error "Peek resolve error:" %))))

      (js/console.warn "Unknown action type:" type action))))
