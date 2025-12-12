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
  (state/update-basketball-player state player-id assoc :exhausted? true))

(defmethod -apply-action :bashketball/refresh-player
  [state {:keys [player-id]}]
  (state/update-basketball-player state player-id assoc :exhausted? false))

(defmethod -apply-action :bashketball/refresh-all
  [state {:keys [team]}]
  (let [player-ids (concat (state/get-starters state team)
                           (state/get-bench state team))]
    (reduce (fn [s pid]
              (state/update-basketball-player s pid assoc :exhausted? false))
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
  [state {:keys [starter-id bench-id]}]
  (let [team         (state/get-basketball-player-team state starter-id)
        team-path    [:players team :team]
        starters     (get-in state (conj team-path :starters))
        bench        (get-in state (conj team-path :bench))
        starter-pos  (get-in state [:players team :team :players starter-id :position])
        new-starters (mapv #(if (= % starter-id) bench-id %) starters)
        new-bench    (mapv #(if (= % bench-id) starter-id %) bench)]
    (-> state
        (assoc-in (conj team-path :starters) new-starters)
        (assoc-in (conj team-path :bench) new-bench)
        (state/update-basketball-player starter-id assoc :position nil)
        (state/update-basketball-player bench-id assoc :position starter-pos)
        (cond->
         starter-pos
          (update :board board/set-occupant starter-pos {:type :occupant/BASKETBALL_PLAYER :id bench-id})))))

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
