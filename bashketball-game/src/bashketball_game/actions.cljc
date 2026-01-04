(ns bashketball-game.actions
  "Data-driven action application with multimethod dispatch.

  Provides `apply-action` which validates actions against the schema,
  dispatches to the appropriate handler, fires triggers, and logs the event."
  (:require [bashketball-game.board :as board]
            [bashketball-game.polix.card-effects :as card-effects]
            [bashketball-game.polix.triggers :as triggers]
            [bashketball-game.schema :as schema]
            [bashketball-game.state :as state]
            [clojure.set :as set]
            [clojure.string :as str]
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

(defn- apply-action-impl
  "Core action application logic.

  Validates, dispatches to -apply-action multimethod, validates board invariants,
  and logs the event. Returns the new state."
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

(def ^:private lifecycle-actions
  "Actions that affect trigger lifecycle and require registry updates."
  #{:bashketball/substitute
    :bashketball/attach-ability
    :bashketball/detach-ability
    :bashketball/play-card
    :bashketball/move-asset
    :bashketball/create-token})

(defn apply-action
  "Applies a validated action to game state with trigger processing.

  Takes a context map and an action. Returns
  `{:state new-state :registry new-registry :prevented? bool}`.

  Context map keys:
  - `:state` - current game state (required)
  - `:registry` - trigger registry (optional, nil disables triggers)
  - `:catalog` - effect catalog for ability lookups (optional, needed for
                 lifecycle actions like substitute, attach, detach, play-card, move-asset)

  Processing order:
  1. Validates action against schema
  2. Fires before triggers (can prevent action)
  3. If not prevented, applies action via multimethod
  4. Updates registry for lifecycle actions (substitute, attach, detach, play-card, move-asset)
  5. Validates board invariants, logs event
  6. Fires after triggers

  See also [[do-action]] for a simpler API when triggers aren't needed."
  [{:keys [state registry catalog]} action]
  (if-not registry
    {:state (apply-action-impl state action)
     :registry nil
     :prevented? false}
    (let [events        (triggers/action->events state action)
          before-result (triggers/fire-bashketball-event
                         {:state state :registry registry}
                         (:before events))]
      (if (:prevented? before-result)
        {:state state
         :registry (:registry before-result)
         :prevented? true}
        (let [old-state    (:state before-result)
              new-state    (apply-action-impl old-state action)
              ;; Update registry for lifecycle actions
              updated-reg  (if (and catalog (lifecycle-actions (:type action)))
                             (card-effects/update-registry-for-action
                              (:registry before-result) catalog old-state new-state action)
                             (:registry before-result))
              after-result (triggers/fire-bashketball-event
                            {:state new-state :registry updated-reg}
                            (:after events))]
          {:state (:state after-result)
           :registry (:registry after-result)
           :prevented? false})))))

(defn do-action
  "Applies an action to game state without trigger processing.

  Simple API for when triggers aren't needed. Takes game-state and action,
  returns new game-state.

  For trigger support, use [[apply-action]] with a context map."
  [game-state action]
  (apply-action-impl game-state action))

;; -----------------------------------------------------------------------------
;; Game Flow Actions

(defmethod -apply-action :bashketball/do-set-phase
  [state {:keys [phase]}]
  (assoc state :phase phase))

(defmethod -apply-action :bashketball/do-advance-turn
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

(defmethod -apply-action :bashketball/begin-movement
  [state {:keys [player-id speed]}]
  (let [player   (state/get-basketball-player state player-id)
        team     (state/get-basketball-player-team state player-id)
        position (:position player)]
    (state/set-pending-movement state
                                {:id               (generate-id)
                                 :player-id        player-id
                                 :team             team
                                 :starting-position position
                                 :current-position position
                                 :initial-speed    speed
                                 :remaining-speed  speed
                                 :path-taken       [position]
                                 :step-number      0})))

(defmethod -apply-action :bashketball/do-move-step
  [state {:keys [player-id to-position cost]}]
  (let [player       (state/get-basketball-player state player-id)
        old-position (:position player)
        movement     (state/get-pending-movement state)]
    (-> state
        (state/update-basketball-player player-id assoc :position to-position)
        (cond->
         old-position (update :board board/remove-occupant old-position))
        (update :board board/set-occupant to-position {:type :occupant/BASKETBALL_PLAYER :id player-id})
        (state/update-pending-movement
         (fn [m]
           (-> m
               (assoc :current-position to-position)
               (update :remaining-speed - cost)
               (update :path-taken conj to-position)
               (update :step-number inc))))
        (assoc :event-data {:from-position old-position
                            :to-position   to-position
                            :cost          cost
                            :remaining     (- (:remaining-speed movement) cost)}))))

(defmethod -apply-action :bashketball/end-movement
  [state {:keys [player-id]}]
  (let [movement (state/get-pending-movement state)]
    (-> state
        (state/clear-pending-movement)
        (assoc :event-data {:player-id   player-id
                            :path-taken  (:path-taken movement)
                            :total-steps (:step-number movement)}))))

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

;; -----------------------------------------------------------------------------
;; Skill Test Actions

(defn- stat->stats-key
  "Converts a stat keyword to the corresponding stats map key.
  e.g. :stat/SHOOTING -> :shooting"
  [stat]
  (-> stat name str/lower-case keyword))

(defmethod -apply-action :bashketball/initiate-skill-test
  [state {:keys [actor-id stat target-value context]}]
  (let [player     (state/get-basketball-player state actor-id)
        stats-key  (stat->stats-key stat)
        base-value (get-in player [:stats stats-key] 0)]
    (assoc state :pending-skill-test
           {:id           (generate-id)
            :actor-id     actor-id
            :stat         stat
            :base-value   base-value
            :modifiers    []
            :fate         nil
            :total        nil
            :target-value target-value
            :context      context})))

(defmethod -apply-action :bashketball/modify-skill-test
  [state {:keys [source amount advantage reason]}]
  (when-not (:pending-skill-test state)
    (throw (ex-info "No pending skill test to modify"
                    {:source source :amount amount :advantage advantage})))
  (update-in state [:pending-skill-test :modifiers]
             conj (cond-> {:source source :amount (or amount 0)}
                    reason    (assoc :reason reason)
                    advantage (assoc :advantage advantage))))

(defmethod -apply-action :bashketball/set-skill-test-fate
  [state {:keys [fate]}]
  (when-not (:pending-skill-test state)
    (throw (ex-info "No pending skill test to set fate"
                    {:fate fate})))
  (assoc-in state [:pending-skill-test :fate] fate))

(defmethod -apply-action :bashketball/resolve-skill-test
  [state _action]
  (when-not (:pending-skill-test state)
    (throw (ex-info "No pending skill test to resolve" {})))
  (let [{:keys [base-value modifiers fate]} (:pending-skill-test state)
        modifier-total                      (reduce + 0 (map :amount modifiers))
        total                               (+ base-value modifier-total (or fate 0))]
    (-> state
        (assoc-in [:pending-skill-test :total] total)
        (assoc :event-data {:skill-test (assoc (:pending-skill-test state) :total total)}))))

(defmethod -apply-action :bashketball/clear-skill-test
  [state _action]
  (dissoc state :pending-skill-test))

;; -----------------------------------------------------------------------------
;; Choice Actions

(defmethod -apply-action :bashketball/offer-choice
  [state {:keys [choice-type options waiting-for context]}]
  (assoc state :pending-choice
         {:id          (generate-id)
          :type        choice-type
          :options     options
          :waiting-for waiting-for
          :context     context}))

(defmethod -apply-action :bashketball/submit-choice
  [state {:keys [choice-id selected]}]
  (when-not (:pending-choice state)
    (throw (ex-info "No pending choice to submit"
                    {:choice-id choice-id :selected selected})))
  (when-not (= choice-id (get-in state [:pending-choice :id]))
    (throw (ex-info "Invalid choice ID"
                    {:expected (get-in state [:pending-choice :id])
                     :actual   choice-id})))
  (-> state
      (assoc-in [:pending-choice :selected] selected)
      (assoc :event-data {:choice-id choice-id :selected selected})))

(defmethod -apply-action :bashketball/clear-choice
  [state _action]
  (dissoc state :pending-choice))

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
        played    (first (filter #(= (:instance-id %) instance-id) hand))]
    (when-not played
      (throw (ex-info "Card not in hand"
                      {:player instance-id :instance-id instance-id})))
    (let [new-hand (filterv #(not= (:instance-id %) instance-id) hand)
          is-asset (team-asset-card? state player (:card-slug played))]
      (-> state
          (assoc-in (conj deck-path :hand) new-hand)
          (cond->
           is-asset       (update-in [:players player :assets] conj played)
           (not is-asset) (update-in (conj deck-path :discard) conj played))
          (assoc :event-data {:played-card played})))))

(defmethod -apply-action :bashketball/stage-card
  [state {:keys [player instance-id]}]
  (let [deck-path [:players player :deck]
        hand      (get-in state (conj deck-path :hand))
        card      (first (filter #(= (:instance-id %) instance-id) hand))]
    (when-not card
      (throw (ex-info "Card not in hand"
                      {:player player :instance-id instance-id})))
    (let [new-hand       (filterv #(not= (:instance-id %) instance-id) hand)
          play-area-card {:instance-id (:instance-id card)
                          :card-slug   (:card-slug card)
                          :played-by   player}]
      (-> state
          (assoc-in (conj deck-path :hand) new-hand)
          (update :play-area conj play-area-card)
          (assoc :event-data {:staged-card card})))))

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

;; -----------------------------------------------------------------------------
;; Examine Cards Actions

(defmethod -apply-action :bashketball/examine-cards
  [state {:keys [player count]}]
  (let [deck-path    [:players player :deck]
        draw-pile    (get-in state (conj deck-path :draw-pile))
        actual-count (min count (clojure.core/count draw-pile))
        examined     (vec (take actual-count draw-pile))
        remaining    (vec (drop actual-count draw-pile))]
    (-> state
        (assoc-in (conj deck-path :draw-pile) remaining)
        (assoc-in (conj deck-path :examined) examined)
        (assoc :event-data {:examined-cards  examined
                            :requested-count count
                            :actual-count    actual-count}))))

(defn- validate-examined-placements
  "Validates that placements match examined cards exactly.
  Throws if there are missing or extra placements."
  [examined placements]
  (let [examined-ids  (set (map :instance-id examined))
        placement-ids (set (map :instance-id placements))]
    (when (not= examined-ids placement-ids)
      (throw (ex-info "Placements must include all examined cards"
                      {:examined-ids  examined-ids
                       :placement-ids placement-ids
                       :missing       (set/difference examined-ids placement-ids)
                       :extra         (set/difference placement-ids examined-ids)})))))

(defn- group-examined-by-destination
  "Groups examined cards by placement destination, preserving placement order."
  [examined placements]
  (let [examined-by-id (into {} (map (juxt :instance-id identity) examined))
        grouped        (group-by :destination placements)]
    {:top     (mapv #(examined-by-id (:instance-id %)) (get grouped :examine/TOP []))
     :bottom  (mapv #(examined-by-id (:instance-id %)) (get grouped :examine/BOTTOM []))
     :discard (mapv #(examined-by-id (:instance-id %)) (get grouped :examine/DISCARD []))}))

(defmethod -apply-action :bashketball/resolve-examined-cards
  [state {:keys [player placements]}]
  (let [deck-path [:players player :deck]
        examined  (get-in state (conj deck-path :examined))
        _         (validate-examined-placements examined placements)
        grouped   (group-examined-by-destination examined placements)
        draw-pile (get-in state (conj deck-path :draw-pile))]
    (-> state
        (assoc-in (conj deck-path :examined) [])
        (assoc-in (conj deck-path :draw-pile) (into (:top grouped) draw-pile))
        (update-in (conj deck-path :draw-pile) into (:bottom grouped))
        (update-in (conj deck-path :discard) into (:discard grouped))
        (assoc :event-data {:resolved-placements placements
                            :top-count           (clojure.core/count (:top grouped))
                            :bottom-count        (clojure.core/count (:bottom grouped))
                            :discard-count       (clojure.core/count (:discard grouped))}))))
