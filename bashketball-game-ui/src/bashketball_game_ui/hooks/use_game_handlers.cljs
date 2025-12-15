(ns bashketball-game-ui.hooks.use-game-handlers
  "Hook for pre-bound game action handlers.

  Centralizes all game interaction handlers in one place,
  composing context state with action functions."
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
  - `:on-hex-click` - Handler for hex grid clicks
  - `:on-ball-click` - Handler for ball clicks
  - `:on-player-click` - Handler for player token clicks
  - `:on-card-click` - Handler for card clicks
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
  - `:on-substitute` - Handler for player substitution
  - `:on-target-click` - Handler for clicking in-air ball target to resolve
  - `:on-toggle-exhausted` - Handler for toggling player exhaust status
  - `:on-enter-standard-action` - Handler for entering standard action mode
  - `:on-cancel-standard-action` - Handler for canceling standard action mode
  - `:on-proceed-standard-action` - Handler for proceeding to standard action selection
  - `:on-submit-standard-action` - Handler for submitting standard action with selected card slug"
  []
  (let [{:keys [game-state my-team is-my-turn actions
                selection pass discard ball-mode fate-reveal substitute-mode
                attach-ability-modal standard-action-mode]}
        (use-game-context)

        {:keys [setup-mode valid-moves valid-setup-positions
                valid-pass-targets selected-player-id pass-active ball-active
                discard-active discard-cards selected-card phase
                standard-action-active]}
        (use-game-derived)

        ball-in-air
        (= "BallInAir" (get-in game-state [:ball :__typename]))

        on-hex-click
        (use-callback
         (fn [q r]
           (cond
             ;; Ball in air - redirect to different position (failed pass/shot)
             ball-in-air
             (-> ((:set-ball-loose actions) q r)
                 (.then #(js/console.log "Ball redirected to:" %))
                 (.catch #(js/console.error "Ball redirect error:" %)))

             ;; Loose ball selected - move it
             ball-active
             (do
               (-> ((:set-ball-loose actions) q r)
                   (.then #(js/console.log "Ball move result:" %))
                   (.catch #(js/console.error "Ball move error:" %)))
               ((:cancel ball-mode)))

             :else
             (when-let [action (h/hex-click-action
                                {:setup-mode            setup-mode
                                 :is-my-turn            is-my-turn
                                 :selected-player       selected-player-id
                                 :valid-setup-positions valid-setup-positions
                                 :valid-moves           valid-moves}
                                q r)]
               (-> ((:move-player actions) (:player-id action) (first (:position action)) (second (:position action)))
                   (.then #(js/console.log "Move result:" %))
                   (.catch #(js/console.error "Move error:" %)))
               ((:set-selected-player selection) nil))))
         [ball-in-air ball-active ball-mode setup-mode is-my-turn selected-player-id
          valid-setup-positions valid-moves actions selection])

        on-ball-click
        (use-callback
         (fn []
           (if ball-active
             ((:cancel ball-mode))
             ((:select ball-mode))))
         [ball-active ball-mode])

        on-player-click
        (use-callback
         (fn [player-id]
           (cond
             ;; Ball in air - redirect to this player (successful catch)
             ball-in-air
             (-> ((:set-ball-possessed actions) player-id)
                 (.then #(js/console.log "Ball caught by:" %))
                 (.catch #(js/console.error "Ball catch error:" %)))

             ;; Loose ball selected - give to player
             ball-active
             (do
               ((:set-ball-possessed actions) player-id)
               ((:cancel ball-mode)))

             :else
             (let [action (h/player-click-action
                           {:pass-mode            pass-active
                            :valid-pass-targets   valid-pass-targets
                            :ball-holder-position (actions/get-ball-holder-position game-state)
                            :selected-player      selected-player-id}
                           player-id)]
               (case (:action action)
                 :pass (do
                         ((:pass-ball actions) (:origin action) (:target-player-id action))
                         ((:cancel pass))
                         ((:set-selected-player selection) nil))
                 :toggle-selection ((:toggle-player selection) player-id)))))
         [ball-in-air selected-player-id pass-active valid-pass-targets game-state actions pass selection ball-active ball-mode])

        on-card-click
        (use-callback
         (fn [instance-id]
           (cond
             ;; Standard action mode - toggle card for discard selection
             standard-action-active
             ((:toggle-card standard-action-mode) instance-id)

             ;; Regular discard or selection
             :else
             (let [action (h/card-click-action
                           {:discard-mode  discard-active
                            :discard-cards discard-cards
                            :selected-card selected-card}
                           instance-id)]
               (case (:action action)
                 :toggle-discard ((:toggle-card discard) instance-id)
                 :toggle-card-selection ((:toggle-card selection) instance-id)))))
         [discard-active discard-cards selected-card discard selection standard-action-active standard-action-mode])

        on-end-turn
        (use-callback
         (fn []
           ((:end-turn actions))
           ((:clear selection)))
         [actions selection])

        on-shoot
        (use-callback
         (fn []
           (when-let [origin (actions/get-ball-holder-position game-state)]
             (let [target (sel/target-basket my-team)]
               ((:shoot-ball actions) origin target)
               ((:set-selected-player selection) nil))))
         [game-state my-team actions selection])

        on-play-card
        (use-callback
         (fn []
           (when selected-card
             ((:stage-card actions) my-team selected-card)
             ((:set-selected-card selection) nil)))
         [selected-card my-team actions selection])

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
           ((:clear selection)))
         [actions selection])

        on-next-phase
        (use-callback
         (fn []
           (when-let [next (sel/next-phase phase)]
             ((:set-phase actions) (name next))))
         [phase actions])

        on-submit-discard
        (use-callback
         (fn []
           (when (pos? (:count discard))
             (let [cards ((:get-cards-and-exit discard))]
               ((:discard-cards actions) my-team (vec cards)))))
         [discard my-team actions])

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

        on-substitute
        (use-callback
         (fn [off-court-id]
           (let [on-court-id (:on-court-id substitute-mode)]
             (-> ((:substitute actions) on-court-id off-court-id)
                 (.then #(js/console.log "Substitute result:" %))
                 (.catch #(js/console.error "Substitute error:" %)))
             ((:cancel substitute-mode))))
         [actions substitute-mode])

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
         [game-state actions])

        on-enter-standard-action
        (use-callback
         (fn []
           ((:enter standard-action-mode)))
         [standard-action-mode])

        on-cancel-standard-action
        (use-callback
         (fn []
           ((:cancel standard-action-mode)))
         [standard-action-mode])

        on-proceed-standard-action
        (use-callback
         (fn []
           ((:proceed standard-action-mode)))
         [standard-action-mode])

        on-submit-standard-action
        (use-callback
         (fn [card-slug]
           (let [cards ((:get-cards-and-exit standard-action-mode))]
             (-> ((:stage-virtual-standard-action actions) my-team cards card-slug)
                 (.then #(js/console.log "Standard action staged:" %))
                 (.catch #(js/console.error "Standard action error:" %)))))
         [my-team actions standard-action-mode])]

    {:on-hex-click         on-hex-click
     :on-ball-click        on-ball-click
     :on-player-click      on-player-click
     :on-card-click        on-card-click
     :on-end-turn          on-end-turn
     :on-shoot             on-shoot
     :on-play-card         on-play-card
     :on-resolve-card      on-resolve-card
     :on-open-attach-modal on-open-attach-modal
     :on-resolve-ability   on-resolve-ability
     :on-draw              on-draw
     :on-start-game        on-start-game
     :on-start-from-tipoff on-start-from-tipoff
     :on-setup-done        on-setup-done
     :on-next-phase        on-next-phase
     :on-submit-discard    on-submit-discard
     :on-reveal-fate       on-reveal-fate
     :on-shuffle           on-shuffle
     :on-return-discard    on-return-discard
     :on-move-asset        on-move-asset
     :on-substitute             on-substitute
     :on-target-click           on-target-click
     :on-toggle-exhausted       on-toggle-exhausted
     :on-enter-standard-action  on-enter-standard-action
     :on-cancel-standard-action on-cancel-standard-action
     :on-proceed-standard-action on-proceed-standard-action
     :on-submit-standard-action on-submit-standard-action}))
