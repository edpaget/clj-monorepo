(ns bashketball-game-ui.game.dispatcher
  "Action dispatcher that routes actions to game action handlers.

  Creates a dispatch function from game actions that routes action maps
  to the appropriate handler. Supports all action types from state machines
  and UI handlers."
  (:require
   [bashketball-game-ui.game.actions :as game-actions]
   [bashketball-game-ui.game.handlers :as h]
   [bashketball-game-ui.game.selectors :as sel]))

(defn create-dispatcher
  "Creates a dispatch function from game actions and context.

  Takes a config map with:
  - `:actions` - Action functions from [[use-game-actions]]
  - `:my-team` - The current player's team keyword
  - `:get-ball-holder-position` - Fn returning ball holder's [q r] or nil
  - `:get-game-state` - Fn returning current game state (for handlers needing state)
  - `:get-selection-data` - Fn returning selection machine data
  - `:send` - Fn to send events to selection machine
  - `:get-discard-machine` - Fn returning discard machine state
  - `:send-discard` - Fn to send events to discard machine
  - `:fate-reveal` - Fate reveal modal state `{:show :close}`
  - `:attach-ability-modal` - Attach ability modal state
  - `:detail-modal` - Detail modal state `{:show :close}`
  - `:create-token-modal` - Create token modal state

  Returns a function that dispatches action maps to appropriate handlers."
  [{:keys [actions my-team get-ball-holder-position get-game-state
           get-selection-data send get-discard-machine send-discard
           fate-reveal attach-ability-modal detail-modal create-token-modal]}]
  (fn dispatch [{:keys [type from to] :as action}]
    (case type
      ;; === State machine actions (selection) ===
      :move-player
      (let [player-id (:player-id from)
            q         (:q to)
            r         (:r to)]
        ((:move-player actions) player-id q r))

      :set-ball-loose
      (let [q (:q to)
            r (:r to)]
        ((:set-ball-loose actions) q r))

      :set-ball-possessed
      (let [player-id (:player-id to)]
        ((:set-ball-possessed actions) player-id))

      :pass-to-player
      (when-let [origin (get-ball-holder-position)]
        (let [player-id (:player-id to)]
          ((:pass-ball actions) origin player-id)))

      :pass-to-hex
      (when-let [origin (get-ball-holder-position)]
        (let [target [(:q to) (:r to)]]
          ((:pass-ball actions) origin target)))

      :standard-action
      (let [cards     (:cards from)
            card-slug (:card-slug to)]
        ((:stage-virtual-standard-action actions) my-team cards card-slug))

      ;; === State machine actions (modal) ===
      :substitute
      (let [on-court-id  (:on-court-id action)
            off-court-id (:off-court-id action)]
        ((:substitute actions) on-court-id off-court-id))

      :discard-cards
      (let [cards (:cards action)]
        ((:discard-cards actions) my-team (vec cards)))

      :resolve-peek
      (let [target-team (:target-team action)
            placements  (:placements action)]
        ((:resolve-examined-cards actions) target-team placements))

      ;; === Handler actions ===
      :end-turn
      (do
        ((:end-turn actions))
        (when send (send {:type :escape})))

      :shoot
      (when-let [origin (get-ball-holder-position)]
        (let [target (sel/target-basket my-team)]
          ((:shoot-ball actions) origin target)
          (when send (send {:type :escape}))))

      :play-card
      (when-let [selection-data (and get-selection-data (get-selection-data))]
        (when-let [card-id (:selected-card selection-data)]
          ((:stage-card actions) my-team card-id)
          (when send (send {:type :click-card :data {:instance-id card-id}}))))

      :resolve-card
      (let [instance-id      (:instance-id action)
            target-player-id (:target-player-id action)]
        (if target-player-id
          ((:resolve-card actions) instance-id target-player-id)
          ((:resolve-card actions) instance-id)))

      :open-attach-modal
      (when attach-ability-modal
        ((:show attach-ability-modal)
         (:instance-id action)
         (:card-slug action)
         (:played-by action)))

      :resolve-ability
      (when attach-ability-modal
        (let [instance-id      (:instance-id attach-ability-modal)
              target-player-id (:target-player-id action)]
          ((:resolve-card actions) instance-id target-player-id)
          ((:close attach-ability-modal))))

      :draw
      (let [count (or (:count action) 1)]
        ((:draw-cards actions) my-team count))

      :start-game
      ((:set-phase actions) "TIP_OFF")

      :start-from-tipoff
      ((:submit actions) (game-actions/make-start-from-tipoff-action my-team))

      :setup-done
      (do
        ((:end-turn actions))
        (when send (send {:type :escape})))

      :next-phase
      (when-let [game-state (and get-game-state (get-game-state))]
        (let [phase (:phase game-state)]
          (when-let [next (sel/next-phase phase)]
            ((:set-phase actions) (name next)))))

      :submit-discard
      (when (and get-discard-machine send-discard get-selection-data send)
        (let [discard-machine (get-discard-machine)
              cards           (get-in discard-machine [:data :cards])
              selection-data  (get-selection-data)
              selected-card   (:selected-card selection-data)]
          (when (seq cards)
            (let [new-selection (h/selection-after-discard selected-card cards)]
              (when (not= new-selection selected-card)
                (send {:type :click-card :data {:instance-id new-selection}}))
              (send-discard {:type :submit})))))

      :reveal-fate
      (when fate-reveal
        (-> ((:reveal-fate actions) my-team)
            (.then (fn [result]
                     (when-let [fate (-> result :data :submit-action :revealed-fate)]
                       ((:show fate-reveal) fate))))))

      :shuffle
      ((:shuffle-deck actions) my-team)

      :return-discard
      ((:return-discard actions) my-team)

      :move-asset
      (let [instance-id (:instance-id action)
            destination (:destination action)]
        ((:move-asset actions) my-team instance-id destination))

      :target-click
      (when-let [game-state (and get-game-state (get-game-state))]
        (let [ball   (:ball game-state)
              target (:target ball)]
          (cond
            (:position target)
            (let [[q r] (:position target)]
              ((:set-ball-loose actions) q r))

            (:player-id target)
            ((:set-ball-possessed actions) (:player-id target)))))

      :toggle-exhausted
      (when-let [game-state (and get-game-state (get-game-state))]
        (let [player-id   (:player-id action)
              home-player (get-in game-state [:players :team/HOME :team :players player-id])
              away-player (get-in game-state [:players :team/AWAY :team :players player-id])
              player      (or home-player away-player)
              exhausted?  (:exhausted player)]
          (if exhausted?
            ((:refresh-player actions) player-id)
            ((:exhaust-player actions) player-id))))

      ;; === UI modal actions ===
      :show-detail-modal
      (when detail-modal
        ((:show detail-modal) (:card-slug action)))

      :close-detail-modal
      (when detail-modal
        ((:close detail-modal)))

      :show-create-token-modal
      (when create-token-modal
        ((:show create-token-modal)))

      :close-create-token-modal
      (when create-token-modal
        ((:close create-token-modal)))

      :show-attach-modal
      (when attach-ability-modal
        ((:show attach-ability-modal)
         (:instance-id action)
         (:card-slug action)
         (:played-by action)))

      :close-attach-modal
      (when attach-ability-modal
        ((:close attach-ability-modal)))

      ;; Unknown action - silently ignore
      nil)))
