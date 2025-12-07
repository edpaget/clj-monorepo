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
  - `:on-play-card` - Handler for playing a card
  - `:on-draw` - Handler for drawing cards
  - `:on-start-game` - Handler for starting game from setup
  - `:on-setup-done` - Handler for completing setup
  - `:on-next-phase` - Handler for advancing phase
  - `:on-submit-discard` - Handler for submitting discard
  - `:on-reveal-fate` - Handler for revealing fate
  - `:on-shuffle` - Handler for shuffling deck
  - `:on-return-discard` - Handler for returning discard to deck
  - `:on-substitute` - Handler for player substitution"
  []
  (let [{:keys [game-state my-team is-my-turn actions
                selection pass discard ball-mode fate-reveal substitute-mode]}
        (use-game-context)

        {:keys [setup-mode valid-moves valid-setup-positions
                valid-pass-targets selected-player-id pass-active ball-active
                discard-active discard-cards selected-card phase]}
        (use-game-derived)

        on-hex-click
        (use-callback
         (fn [q r]
           (cond
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
         [ball-active ball-mode setup-mode is-my-turn selected-player-id
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
         [selected-player-id pass-active valid-pass-targets game-state actions pass selection ball-active ball-mode])

        on-card-click
        (use-callback
         (fn [card-slug]
           (let [action (h/card-click-action
                         {:discard-mode  discard-active
                          :discard-cards discard-cards
                          :selected-card selected-card}
                         card-slug)]
             (case (:action action)
               :toggle-discard ((:toggle-card discard) card-slug)
               :toggle-card-selection ((:toggle-card selection) card-slug))))
         [discard-active discard-cards selected-card discard selection])

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
             ((:submit actions) {:type      "bashketball/play-card"
                                 :card-slug selected-card
                                 :player    (name my-team)})
             ((:set-selected-card selection) nil)))
         [selected-card my-team actions selection])

        on-draw
        (use-callback
         (fn []
           ((:draw-cards actions) my-team 1))
         [my-team actions])

        on-start-game
        (use-callback
         (fn []
           ((:set-phase actions) "UPKEEP"))
         [actions])

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

        on-substitute
        (use-callback
         (fn [bench-id]
           (let [starter-id (:starter-id substitute-mode)]
             (-> ((:substitute actions) starter-id bench-id)
                 (.then #(js/console.log "Substitute result:" %))
                 (.catch #(js/console.error "Substitute error:" %)))
             ((:cancel substitute-mode))))
         [actions substitute-mode])]

    {:on-hex-click      on-hex-click
     :on-ball-click     on-ball-click
     :on-player-click   on-player-click
     :on-card-click     on-card-click
     :on-end-turn       on-end-turn
     :on-shoot          on-shoot
     :on-play-card      on-play-card
     :on-draw           on-draw
     :on-start-game     on-start-game
     :on-setup-done     on-setup-done
     :on-next-phase     on-next-phase
     :on-submit-discard on-submit-discard
     :on-reveal-fate    on-reveal-fate
     :on-shuffle        on-shuffle
     :on-return-discard on-return-discard
     :on-substitute     on-substitute}))
