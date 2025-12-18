(ns bashketball-game-ui.game.dispatch-actions
  "Pure action constructor functions.

  Provides functions to create action maps for dispatching. These provide
  inspectable data structures for game actions.

  Each function returns an action map that can be passed to dispatch.")

(defn move-player
  "Creates action to move a player to a hex position."
  [player-id q r]
  {:type :move-player
   :from {:player-id player-id}
   :to   {:q q :r r}})

(defn set-ball-loose
  "Creates action to set ball loose at a position."
  [q r]
  {:type :set-ball-loose
   :to   {:q q :r r}})

(defn set-ball-possessed
  "Creates action to give ball possession to a player."
  [player-id]
  {:type :set-ball-possessed
   :to   {:player-id player-id}})

(defn pass-to-player
  "Creates action to pass ball to a player."
  [player-id]
  {:type :pass-to-player
   :to   {:player-id player-id}})

(defn pass-to-hex
  "Creates action to pass ball to a hex position."
  [q r]
  {:type :pass-to-hex
   :to   {:q q :r r}})

(defn standard-action
  "Creates action for virtual standard action (discard 2 cards, stage action)."
  [cards card-slug]
  {:type :standard-action
   :from {:cards cards}
   :to   {:card-slug card-slug}})

(defn substitute
  "Creates action to substitute players."
  [on-court-id off-court-id]
  {:type         :substitute
   :on-court-id  on-court-id
   :off-court-id off-court-id})

(defn discard-cards
  "Creates action to discard cards."
  [cards]
  {:type  :discard-cards
   :cards cards})

(defn resolve-peek
  "Creates action to resolve peeked cards with placements."
  [target-team placements]
  {:type        :resolve-peek
   :target-team target-team
   :placements  placements})

(defn end-turn
  "Creates action to end turn."
  []
  {:type :end-turn})

(defn shoot
  "Creates action to shoot the ball."
  []
  {:type :shoot})

(defn play-card
  "Creates action to play a card from hand."
  []
  {:type :play-card})

(defn resolve-card
  "Creates action to resolve a card in play area."
  ([instance-id]
   {:type        :resolve-card
    :instance-id instance-id})
  ([instance-id target-player-id]
   {:type             :resolve-card
    :instance-id      instance-id
    :target-player-id target-player-id}))

(defn open-attach-modal
  "Creates action to open attach ability modal."
  [instance-id card-slug played-by]
  {:type        :open-attach-modal
   :instance-id instance-id
   :card-slug   card-slug
   :played-by   played-by})

(defn resolve-ability
  "Creates action to resolve an ability attachment."
  [target-player-id]
  {:type             :resolve-ability
   :target-player-id target-player-id})

(defn draw
  "Creates action to draw cards."
  ([]
   {:type :draw})
  ([count]
   {:type  :draw
    :count count}))

(defn start-game
  "Creates action to start game from setup phase."
  []
  {:type :start-game})

(defn start-from-tipoff
  "Creates action to win tip-off."
  []
  {:type :start-from-tipoff})

(defn setup-done
  "Creates action to complete setup."
  []
  {:type :setup-done})

(defn next-phase
  "Creates action to advance to next phase."
  []
  {:type :next-phase})

(defn submit-discard
  "Creates action to submit discard selection."
  []
  {:type :submit-discard})

(defn reveal-fate
  "Creates action to reveal fate from deck."
  []
  {:type :reveal-fate})

(defn shuffle-deck
  "Creates action to shuffle deck."
  []
  {:type :shuffle})

(defn return-discard
  "Creates action to return discard pile to deck."
  []
  {:type :return-discard})

(defn move-asset
  "Creates action to move an asset to discard or removed zone."
  [instance-id destination]
  {:type        :move-asset
   :instance-id instance-id
   :destination destination})

(defn target-click
  "Creates action to resolve in-air ball to target."
  []
  {:type :target-click})

(defn toggle-exhausted
  "Creates action to toggle player exhausted status."
  [player-id]
  {:type      :toggle-exhausted
   :player-id player-id})

(defn show-detail-modal
  "Creates action to show card detail modal."
  [card-slug]
  {:type      :show-detail-modal
   :card-slug card-slug})

(defn close-detail-modal
  "Creates action to close card detail modal."
  []
  {:type :close-detail-modal})

(defn show-create-token-modal
  "Creates action to show create token modal."
  []
  {:type :show-create-token-modal})

(defn close-create-token-modal
  "Creates action to close create token modal."
  []
  {:type :close-create-token-modal})

(defn show-attach-modal
  "Creates action to show attach ability modal."
  [instance-id card-slug played-by]
  {:type        :show-attach-modal
   :instance-id instance-id
   :card-slug   card-slug
   :played-by   played-by})

(defn close-attach-modal
  "Creates action to close attach ability modal."
  []
  {:type :close-attach-modal})
