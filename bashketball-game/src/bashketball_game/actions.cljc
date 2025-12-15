(ns bashketball-game.actions
  "Data-driven action application with multimethod dispatch.

  Provides `apply-action` which validates actions against the schema,
  dispatches to the appropriate handler, and logs the event."
  (:require [bashketball-game.board :as board]
            [bashketball-game.schema :as schema]
            [bashketball-game.state :as state]
            [malli.core :as m]))

(defn- now
  "Returns the current timestamp as an ISO-8601 string."
  []
  #?(:clj (.toString (java.time.Instant/now))
     :cljs (.toISOString (js/Date.))))

(defn- generate-id
  "Generates a random UUID string."
  []
  #?(:clj (str (java.util.UUID/randomUUID))
     :cljs (str (random-uuid))))

(defmulti -apply-action
  "Multimethod dispatching on :type. Implementations receive [game-state action]
   and return the modified game-state. Should NOT handle event logging - that's
   done by the wrapper function."
  (fn [_game-state action] (:type action)))

(defn apply-action
  "Applies a validated action to game state.

  Handles common bookkeeping:
  1. Validates action against schema
  2. Dispatches to -apply-action multimethod
  3. Validates board invariants (no duplicate occupant IDs)
  4. Appends action to event log with timestamp (merging any :event-data from state)
  5. Returns new state

  Actions can store additional event data by assoc'ing :event-data onto the state.
  This data is merged into the logged event and removed from the final state.

  Throws an exception if the action is invalid or if board invariants are violated."
  [game-state action]
  (when-not (m/validate schema/Action action)
    (throw (ex-info "Invalid action"
                    {:action action
                     :explanation (m/explain schema/Action action)})))
  (let [new-state  (-apply-action game-state action)
        event-data (:event-data new-state)
        event      (cond-> (assoc action :timestamp (now))
                     event-data (merge event-data))]
    (when-let [invariant-error (board/check-occupant-invariants (:board new-state))]
      (throw (ex-info "Board invariant violation: duplicate occupant IDs"
                      {:action action
                       :error invariant-error})))
    (-> new-state
        (dissoc :event-data)
        (update :events conj event))))

;; -----------------------------------------------------------------------------
;; Game Flow Actions

(defmethod -apply-action :bashketball/set-phase
  [state {:keys [phase]}]
  (assoc state :phase phase))

(defmethod -apply-action :bashketball/advance-turn
  [state _action]
  (-> state
      (update :turn-number inc)
      (update :active-player {:team/HOME :team/AWAY :team/AWAY :team/HOME})))

(defmethod -apply-action :bashketball/set-active-player
  [state {:keys [player]}]
  (assoc state :active-player player))

(defmethod -apply-action :bashketball/start-from-tipoff
  [state {:keys [player]}]
  (-> state
      (assoc :phase :phase/UPKEEP)
      (assoc :active-player player)))

;; -----------------------------------------------------------------------------
;; Player Resource Actions

(defmethod -apply-action :bashketball/set-actions
  [state {:keys [player amount]}]
  (assoc-in state [:players player :actions-remaining] amount))

(defmethod -apply-action :bashketball/draw-cards
  [state {:keys [player count]}]
  (let [deck-path [:players player :deck]
        draw-pile (get-in state (conj deck-path :draw-pile))
        drawn     (vec (take count draw-pile))
        remaining (vec (drop count draw-pile))]
    (-> state
        (assoc-in (conj deck-path :draw-pile) remaining)
        (update-in (conj deck-path :hand) into drawn))))

(defmethod -apply-action :bashketball/discard-cards
  [state {:keys [player instance-ids]}]
  (let [deck-path [:players player :deck]
        hand      (get-in state (conj deck-path :hand))
        id-set    (set instance-ids)
        discarded (filterv #(id-set (:instance-id %)) hand)
        new-hand  (filterv #(not (id-set (:instance-id %))) hand)]
    (-> state
        (assoc-in (conj deck-path :hand) new-hand)
        (update-in (conj deck-path :discard) into discarded))))

(defmethod -apply-action :bashketball/remove-cards
  [state {:keys [player instance-ids]}]
  (let [deck-path [:players player :deck]
        hand      (get-in state (conj deck-path :hand))
        id-set    (set instance-ids)
        removed   (filterv #(id-set (:instance-id %)) hand)
        new-hand  (filterv #(not (id-set (:instance-id %))) hand)]
    (-> state
        (assoc-in (conj deck-path :hand) new-hand)
        (update-in (conj deck-path :removed) into removed))))

(defmethod -apply-action :bashketball/shuffle-deck
  [state {:keys [player]}]
  (update-in state [:players player :deck :draw-pile] shuffle))

(defmethod -apply-action :bashketball/return-discard
  [state {:keys [player]}]
  (let [deck-path [:players player :deck]
        discard   (get-in state (conj deck-path :discard))]
    (-> state
        (update-in (conj deck-path :draw-pile) into discard)
        (assoc-in (conj deck-path :discard) []))))

;; -----------------------------------------------------------------------------
;; Basketball Player Actions

(defmethod -apply-action :bashketball/move-player
  [state {:keys [player-id position]}]
  (let [player       (state/get-basketball-player state player-id)
        old-position (:position player)]
    (-> state
        (state/update-basketball-player player-id assoc :position position)
        (cond->
         old-position (update :board board/remove-occupant old-position))
        (update :board board/set-occupant position {:type :occupant/BASKETBALL_PLAYER :id player-id}))))

(defmethod -apply-action :bashketball/exhaust-player
  [state {:keys [player-id]}]
  (state/update-basketball-player state player-id assoc :exhausted true))

(defmethod -apply-action :bashketball/refresh-player
  [state {:keys [player-id]}]
  (state/update-basketball-player state player-id assoc :exhausted false))

(defmethod -apply-action :bashketball/refresh-all
  [state {:keys [team]}]
  (let [player-ids (keys (state/get-all-players state team))]
    (reduce (fn [s pid]
              (state/update-basketball-player s pid assoc :exhausted false))
            state
            player-ids)))

(defmethod -apply-action :bashketball/add-modifier
  [state {:keys [player-id modifier]}]
  (state/update-basketball-player state player-id update :modifiers conj modifier))

(defmethod -apply-action :bashketball/remove-modifier
  [state {:keys [player-id modifier-id]}]
  (state/update-basketball-player state player-id
                                  update :modifiers
                                  (fn [mods]
                                    (vec (remove #(= (:id %) modifier-id) mods)))))

(defmethod -apply-action :bashketball/clear-modifiers
  [state {:keys [player-id]}]
  (state/update-basketball-player state player-id assoc :modifiers []))

(defmethod -apply-action :bashketball/substitute
  [state {:keys [on-court-id off-court-id]}]
  (let [on-court-pos (:position (state/get-basketball-player state on-court-id))]
    (-> state
        (state/update-basketball-player on-court-id assoc :position nil)
        (state/update-basketball-player off-court-id assoc :position on-court-pos)
        (cond->
         on-court-pos
          (update :board board/set-occupant on-court-pos {:type :occupant/BASKETBALL_PLAYER :id off-court-id})))))

;; -----------------------------------------------------------------------------
;; Ball Actions

(defmethod -apply-action :bashketball/set-ball-possessed
  [state {:keys [holder-id]}]
  (assoc state :ball {:status :ball-status/POSSESSED :holder-id holder-id}))

(defmethod -apply-action :bashketball/set-ball-loose
  [state {:keys [position]}]
  (assoc state :ball {:status :ball-status/LOOSE :position position}))

(defmethod -apply-action :bashketball/set-ball-in-air
  [state {:keys [origin target action-type]}]
  (assoc state :ball {:status :ball-status/IN_AIR
                      :origin origin
                      :target target
                      :action-type action-type}))

;; -----------------------------------------------------------------------------
;; Scoring Actions

(defmethod -apply-action :bashketball/add-score
  [state {:keys [team points]}]
  (update-in state [:score team] + points))

;; -----------------------------------------------------------------------------
;; Stack Actions

(defmethod -apply-action :bashketball/push-stack
  [state {:keys [effect]}]
  (update state :stack conj effect))

(defmethod -apply-action :bashketball/pop-stack
  [state _action]
  (update state :stack pop))

(defmethod -apply-action :bashketball/clear-stack
  [state _action]
  (assoc state :stack []))

;; -----------------------------------------------------------------------------
;; Fate/Skill Test Actions

(defmethod -apply-action :bashketball/reveal-fate
  [state {:keys [player]}]
  (let [deck-path [:players player :deck]
        draw-pile (get-in state (conj deck-path :draw-pile))
        revealed  (first draw-pile)]
    (-> state
        (update-in (conj deck-path :draw-pile) (comp vec rest))
        (update-in (conj deck-path :discard) conj revealed)
        (assoc :event-data {:revealed-card revealed}))))

(defmethod -apply-action :bashketball/record-skill-test
  [state _action]
  ;; This action only logs to events, no state change
  state)

(defn- get-card-type
  "Looks up the card-type for a card instance from the deck's card catalog."
  [state player card-slug]
  (let [cards (get-in state [:players player :deck :cards])]
    (some (fn [card]
            (when (= (:slug card) card-slug)
              (:card-type card)))
          cards)))

(defn- team-asset-card?
  "Returns true if the card is a team asset card."
  [state player card-slug]
  (= (get-card-type state player card-slug) :card-type/TEAM_ASSET_CARD))

(defmethod -apply-action :bashketball/play-card
  [state {:keys [player instance-id]}]
  (let [deck-path [:players player :deck]
        hand      (get-in state (conj deck-path :hand))
        played    (first (filter #(= (:instance-id %) instance-id) hand))
        new-hand  (filterv #(not= (:instance-id %) instance-id) hand)
        is-asset  (team-asset-card? state player (:card-slug played))]
    (-> state
        (assoc-in (conj deck-path :hand) new-hand)
        (cond->
         is-asset       (update-in [:players player :assets] conj played)
         (not is-asset) (update-in (conj deck-path :discard) conj played))
        (assoc :event-data {:played-card played}))))

(defmethod -apply-action :bashketball/stage-card
  [state {:keys [player instance-id]}]
  (let [deck-path      [:players player :deck]
        hand           (get-in state (conj deck-path :hand))
        card           (first (filter #(= (:instance-id %) instance-id) hand))
        new-hand       (filterv #(not= (:instance-id %) instance-id) hand)
        play-area-card {:instance-id (:instance-id card)
                        :card-slug   (:card-slug card)
                        :played-by   player}]
    (-> state
        (assoc-in (conj deck-path :hand) new-hand)
        (update :play-area conj play-area-card)
        (assoc :event-data {:staged-card card}))))

(defn- ability-card?
  "Returns true if the card with given slug is an ABILITY_CARD."
  [state player card-slug]
  (let [cards (get-in state [:players player :deck :cards])
        card  (some #(when (= (:slug %) card-slug) %) cards)]
    (= (:card-type card) :card-type/ABILITY_CARD)))

(defn- get-ability-card-properties
  "Looks up the :removable and :detach-destination for an ability card.
  Returns a map with defaults applied if the card or properties are not found."
  [state player card-slug]
  (let [cards (get-in state [:players player :deck :cards])
        card  (some #(when (= (:slug %) card-slug) %) cards)]
    {:removable          (get card :removable true)
     :detach-destination (get card :detach-destination :detach/DISCARD)}))

(defmethod -apply-action :bashketball/resolve-card
  [state {:keys [instance-id target-player-id]}]
  (let [play-area-card (state/find-card-in-play-area state instance-id)
        owner          (:played-by play-area-card)
        card-slug      (:card-slug play-area-card)
        is-virtual     (:virtual play-area-card)
        card-instance  {:instance-id instance-id :card-slug card-slug}
        is-asset       (and (not is-virtual) (team-asset-card? state owner card-slug))
        is-attach      (and (not is-virtual)
                            target-player-id
                            (ability-card? state owner card-slug))]
    (-> state
        (update :play-area (fn [pa] (filterv #(not= (:instance-id %) instance-id) pa)))
        (cond->
         is-attach
          (as-> s
                (let [props      (get-ability-card-properties s owner card-slug)
                      attachment {:instance-id        instance-id
                                  :card-slug          card-slug
                                  :removable          (:removable props)
                                  :detach-destination (:detach-destination props)
                                  :attached-at        (now)}]
                  (state/update-basketball-player s target-player-id
                                                  update :attachments conj attachment)))

          is-asset
          (update-in [:players owner :assets] conj card-instance)

          (and (not is-virtual) (not is-asset) (not is-attach))
          (update-in [:players owner :deck :discard] conj card-instance))
        (assoc :event-data (cond-> {:resolved-card play-area-card
                                    :virtual       is-virtual}
                             target-player-id (assoc :target-player-id target-player-id))))))

(defmethod -apply-action :bashketball/move-asset
  [state {:keys [player instance-id destination]}]
  (let [assets     (get-in state [:players player :assets])
        moved-card (first (filter #(= (:instance-id %) instance-id) assets))
        is-token?  (state/token? moved-card)
        new-assets (filterv #(not= (:instance-id %) instance-id) assets)
        dest-key   ({:DISCARD :discard :REMOVED :removed} destination)]
    (-> state
        (assoc-in [:players player :assets] new-assets)
        (cond->
         (not is-token?)
          (update-in [:players player :deck dest-key] conj moved-card))
        (assoc :event-data {:moved-asset    moved-card
                            :destination    (if is-token? :deleted destination)
                            :token-deleted? is-token?}))))

;; -----------------------------------------------------------------------------
;; Ability Attachment Actions

(defmethod -apply-action :bashketball/attach-ability
  [state {:keys [player instance-id target-player-id]}]
  (let [deck-path  [:players player :deck]
        hand       (get-in state (conj deck-path :hand))
        card       (first (filter #(= (:instance-id %) instance-id) hand))
        new-hand   (filterv #(not= (:instance-id %) instance-id) hand)
        props      (get-ability-card-properties state player (:card-slug card))
        attachment {:instance-id       (:instance-id card)
                    :card-slug         (:card-slug card)
                    :removable        (:removable props)
                    :detach-destination (:detach-destination props)
                    :attached-at       (now)}]
    (-> state
        (assoc-in (conj deck-path :hand) new-hand)
        (state/update-basketball-player target-player-id
                                        update :attachments conj attachment)
        (assoc :event-data {:attached-card card
                            :target-player-id target-player-id}))))

(defmethod -apply-action :bashketball/detach-ability
  [state {:keys [player target-player-id instance-id]}]
  (let [attachment    (state/find-attachment state target-player-id instance-id)
        is-token?     (state/token? attachment)
        destination   (:detach-destination attachment)
        card-instance {:instance-id (:instance-id attachment)
                       :card-slug   (:card-slug attachment)}
        dest-key      (if (= destination :detach/REMOVED) :removed :discard)]
    (-> state
        (state/update-basketball-player
         target-player-id
         update :attachments
         (fn [atts] (filterv #(not= (:instance-id %) instance-id) atts)))
        (cond->
         (not is-token?)
          (update-in [:players player :deck dest-key] conj card-instance))
        (assoc :event-data {:detached-card    attachment
                            :target-player-id target-player-id
                            :destination      (if is-token? :deleted destination)
                            :token-deleted?   is-token?}))))

;; -----------------------------------------------------------------------------
;; Token Actions

(defn- attach-token-to-player
  "Creates an attachment from a token card definition and attaches it to a player."
  [state target-player-id card instance-id]
  (let [attachment {:instance-id        instance-id
                    :token             true
                    :card               card
                    :removable         (get card :removable true)
                    :detach-destination (get card :detach-destination :detach/DISCARD)
                    :attached-at        (now)}]
    (state/update-basketball-player state target-player-id
                                    update :attachments conj attachment)))

(defn- normalize-token-card
  "Fills in required fields for token cards with sensible defaults.
  Ensures tokens pass card schema validation."
  [card]
  (let [card-type (keyword "card-type" (name (:card-type card)))
        base      (-> card
                      (assoc :card-type card-type)
                      (update :set-slug #(or % "tokens")))]
    (case card-type
      :card-type/TEAM_ASSET_CARD
      (update base :asset-power #(or % ""))

      :card-type/ABILITY_CARD
      (-> base
          (update :fate #(or % 0))
          (update :abilities #(or % [])))

      base)))

(defmethod -apply-action :bashketball/create-token
  [state {:keys [player card placement target-player-id]}]
  (let [normalized-card (normalize-token-card card)
        instance-id     (generate-id)
        token-instance  {:instance-id instance-id
                         :token       true
                         :card        normalized-card}]
    (-> state
        (cond->
         (= placement :placement/ASSET)
          (update-in [:players player :assets] conj token-instance)

          (= placement :placement/ATTACH)
          (attach-token-to-player target-player-id normalized-card instance-id))
        (assoc :event-data {:created-token token-instance
                            :placement     placement
                            :target-player-id target-player-id}))))

;; -----------------------------------------------------------------------------
;; Virtual Standard Action

(defmethod -apply-action :bashketball/stage-virtual-standard-action
  [state {:keys [player discard-instance-ids card-slug]}]
  (let [deck-path      [:players player :deck]
        hand           (get-in state (conj deck-path :hand))
        id-set         (set discard-instance-ids)
        discarded      (filterv #(id-set (:instance-id %)) hand)
        new-hand       (filterv #(not (id-set (:instance-id %))) hand)
        instance-id    (generate-id)
        play-area-card {:instance-id instance-id
                        :card-slug   card-slug
                        :played-by   player
                        :virtual     true}]
    (-> state
        (assoc-in (conj deck-path :hand) new-hand)
        (update-in (conj deck-path :discard) into discarded)
        (update :play-area conj play-area-card)
        (assoc :event-data {:discarded-cards discarded
                            :virtual-card    play-area-card}))))
