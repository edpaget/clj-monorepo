(ns bashketball-game-ui.hooks.use-game-handlers
  "Hook for pre-bound game action handlers.

  Centralizes all game interaction handlers in one place,
  composing context state with action functions.

  Selection-related handlers (player clicks, hex clicks, card clicks, etc.)
  have been migrated to the selection state machine. Use `send` from context
  to dispatch selection events."
  (:require
   [bashketball-game-ui.context.game :refer [use-game-context]]
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-game-ui.game.handlers :as h]
   [bashketball-game-ui.game.selectors :as sel]
   [bashketball-game-ui.hooks.use-game-derived :refer [use-game-derived]]
   [uix.core :refer [use-callback]]))

(defn use-game-handlers
  "Returns all pre-bound game action handlers.

  Consumes [[use-game-context]] and [[use-game-derived]] internally
  to create ready-to-use callbacks for game interactions.

  Returns a map with:
  - `:on-end-turn` - Handler for ending turn
  - `:on-shoot` - Handler for shooting
  - `:on-play-card` - Handler for playing a card (stages card to play area)
  - `:on-resolve-card` - Handler for resolving a card from play area (discard)
  - `:on-open-attach-modal` - Handler for opening attach ability modal
  - `:on-resolve-ability` - Handler for resolving ability (attach or discard)
  - `:on-draw` - Handler for drawing cards
  - `:on-start-game` - Handler for starting game from setup (transitions to TIP_OFF)
  - `:on-start-from-tipoff` - Handler for starting game from tip-off (first to press wins)
  - `:on-setup-done` - Handler for completing setup
  - `:on-next-phase` - Handler for advancing phase
  - `:on-submit-discard` - Handler for submitting discard
  - `:on-reveal-fate` - Handler for revealing fate
  - `:on-shuffle` - Handler for shuffling deck
  - `:on-return-discard` - Handler for returning discard to deck
  - `:on-move-asset` - Handler for moving asset to discard or removed zone
  - `:on-target-click` - Handler for clicking in-air ball target to resolve
  - `:on-toggle-exhausted` - Handler for toggling player exhaust status"
  []
  (let [{:keys [game-state my-team actions send selection-data
                discard-machine send-discard fate-reveal attach-ability-modal]}
        (use-game-context)

        {:keys [selected-card phase]}
        (use-game-derived)

        on-end-turn
        (use-callback
         (fn []
           ((:end-turn actions))
           (send {:type :escape}))
         [actions send])

        on-shoot
        (use-callback
         (fn []
           (when-let [origin (actions/get-ball-holder-position game-state)]
             (let [target (sel/target-basket my-team)]
               ((:shoot-ball actions) origin target)
               (send {:type :escape}))))
         [game-state my-team actions send])

        on-play-card
        (use-callback
         (fn []
           (when-let [card-id (:selected-card selection-data)]
             ((:stage-card actions) my-team card-id)
             (send {:type :click-card :data {:instance-id card-id}})))
         [selection-data my-team actions send])

        on-resolve-card
        (use-callback
         (fn [instance-id]
           ((:resolve-card actions) instance-id))
         [actions])

        on-open-attach-modal
        (use-callback
         (fn [instance-id card-slug played-by]
           ((:show attach-ability-modal) instance-id card-slug played-by))
         [attach-ability-modal])

        on-resolve-ability
        (use-callback
         (fn [target-player-id]
           (let [instance-id (:instance-id attach-ability-modal)]
             (-> ((:resolve-card actions) instance-id target-player-id)
                 (.then #(js/console.log "Ability resolved:" %))
                 (.catch #(js/console.error "Ability resolve error:" %)))
             ((:close attach-ability-modal))))
         [actions attach-ability-modal])

        on-draw
        (use-callback
         (fn []
           ((:draw-cards actions) my-team 1))
         [my-team actions])

        on-start-game
        (use-callback
         (fn []
           ((:set-phase actions) "TIP_OFF"))
         [actions])

        on-start-from-tipoff
        (use-callback
         (fn []
           ((:submit actions) (actions/make-start-from-tipoff-action my-team)))
         [my-team actions])

        on-setup-done
        (use-callback
         (fn []
           ((:end-turn actions))
           (send {:type :escape}))
         [actions send])

        on-next-phase
        (use-callback
         (fn []
           (when-let [next (sel/next-phase phase)]
             ((:set-phase actions) (name next))))
         [phase actions])

        on-submit-discard
        (use-callback
         (fn []
           (let [cards (get-in discard-machine [:data :cards])]
             (when (seq cards)
               (let [new-selection (h/selection-after-discard selected-card cards)]
                 (when (not= new-selection selected-card)
                   (send {:type :click-card :data {:instance-id new-selection}}))
                 (send-discard {:type :submit})))))
         [discard-machine send-discard selected-card send])

        on-reveal-fate
        (use-callback
         (fn []
           (-> ((:reveal-fate actions) my-team)
               (.then (fn [result]
                        (prn result)
                        (when-let [fate (-> result :data :submit-action :revealed-fate)]
                          ((:show fate-reveal) fate))))
               (.catch #(js/console.error "Reveal fate error:" %))))
         [my-team actions fate-reveal])

        on-shuffle
        (use-callback
         (fn []
           ((:shuffle-deck actions) my-team))
         [my-team actions])

        on-return-discard
        (use-callback
         (fn []
           ((:return-discard actions) my-team))
         [my-team actions])

        on-move-asset
        (use-callback
         (fn [instance-id destination]
           (-> ((:move-asset actions) my-team instance-id destination)
               (.then #(js/console.log "Asset moved:" %))
               (.catch #(js/console.error "Move asset error:" %))))
         [my-team actions])

        on-target-click
        (use-callback
         (fn []
           (let [ball   (:ball game-state)
                 target (:target ball)]
             (cond
               (:position target)
               (let [[q r] (:position target)]
                 (-> ((:set-ball-loose actions) q r)
                     (.then #(js/console.log "Resolve to position:" %))
                     (.catch #(js/console.error "Resolve error:" %))))

               (:player-id target)
               (-> ((:set-ball-possessed actions) (:player-id target))
                   (.then #(js/console.log "Resolve to player:" %))
                   (.catch #(js/console.error "Resolve error:" %))))))
         [game-state actions])

        on-toggle-exhausted
        (use-callback
         (fn [player-id]
           (let [home-player (get-in game-state [:players :team/HOME :team :players player-id])
                 away-player (get-in game-state [:players :team/AWAY :team :players player-id])
                 player      (or home-player away-player)
                 exhausted?  (:exhausted player)]
             (if exhausted?
               (-> ((:refresh-player actions) player-id)
                   (.then #(js/console.log "Player refreshed:" %))
                   (.catch #(js/console.error "Refresh error:" %)))
               (-> ((:exhaust-player actions) player-id)
                   (.then #(js/console.log "Player exhausted:" %))
                   (.catch #(js/console.error "Exhaust error:" %))))))
         [game-state actions])]

    {:on-end-turn           on-end-turn
     :on-shoot              on-shoot
     :on-play-card          on-play-card
     :on-resolve-card       on-resolve-card
     :on-open-attach-modal  on-open-attach-modal
     :on-resolve-ability    on-resolve-ability
     :on-draw               on-draw
     :on-start-game         on-start-game
     :on-start-from-tipoff  on-start-from-tipoff
     :on-setup-done         on-setup-done
     :on-next-phase         on-next-phase
     :on-submit-discard     on-submit-discard
     :on-reveal-fate        on-reveal-fate
     :on-shuffle            on-shuffle
     :on-return-discard     on-return-discard
     :on-move-asset         on-move-asset
     :on-target-click       on-target-click
     :on-toggle-exhausted   on-toggle-exhausted}))
