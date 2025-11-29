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
  3. Appends action to event log with timestamp
  4. Returns new state

  Throws an exception if the action is invalid."
  [game-state action]
  (when-not (m/validate schema/Action action)
    (throw (ex-info "Invalid action"
                    {:action action
                     :explanation (m/explain schema/Action action)})))
  (-> game-state
      (-apply-action action)
      (update :events conj (assoc action :timestamp (now)))))

;; -----------------------------------------------------------------------------
;; Game Flow Actions

(defmethod -apply-action :bashketball/set-phase
  [state {:keys [phase]}]
  (assoc state :phase phase))

(defmethod -apply-action :bashketball/advance-turn
  [state _action]
  (-> state
      (update :turn-number inc)
      (update :active-player {:home :away :away :home})))

(defmethod -apply-action :bashketball/set-active-player
  [state {:keys [player]}]
  (assoc state :active-player player))

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
  [state {:keys [player card-slugs]}]
  (let [deck-path [:players player :deck]
        hand      (get-in state (conj deck-path :hand))
        card-set  (set card-slugs)
        new-hand  (vec (remove card-set hand))]
    (-> state
        (assoc-in (conj deck-path :hand) new-hand)
        (update-in (conj deck-path :discard) into card-slugs))))

(defmethod -apply-action :bashketball/remove-cards
  [state {:keys [player card-slugs]}]
  (let [deck-path [:players player :deck]
        hand      (get-in state (conj deck-path :hand))
        card-set  (set card-slugs)
        new-hand  (vec (remove card-set hand))]
    (-> state
        (assoc-in (conj deck-path :hand) new-hand)
        (update-in (conj deck-path :removed) into card-slugs))))

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
        (update :board board/set-occupant position {:type :basketball-player :id player-id}))))

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
          (update :board board/set-occupant starter-pos {:type :basketball-player :id bench-id})))))

;; -----------------------------------------------------------------------------
;; Ball Actions

(defmethod -apply-action :bashketball/set-ball-possessed
  [state {:keys [holder-id]}]
  (let [old-ball     (:ball state)
        old-position (when (= (:status old-ball) :loose)
                       (:position old-ball))]
    (-> state
        (assoc :ball {:status :possessed :holder-id holder-id})
        (cond->
         old-position (update :board board/remove-occupant old-position)))))

(defmethod -apply-action :bashketball/set-ball-loose
  [state {:keys [position]}]
  (let [old-ball     (:ball state)
        old-position (when (= (:status old-ball) :loose)
                       (:position old-ball))]
    (-> state
        (assoc :ball {:status :loose :position position})
        (cond->
         old-position (update :board board/remove-occupant old-position))
        (update :board board/set-occupant position {:type :ball}))))

(defmethod -apply-action :bashketball/set-ball-in-air
  [state {:keys [origin target action-type]}]
  (let [old-ball     (:ball state)
        old-position (when (= (:status old-ball) :loose)
                       (:position old-ball))]
    (-> state
        (assoc :ball {:status :in-air
                      :origin origin
                      :target target
                      :action-type action-type})
        (cond->
         old-position (update :board board/remove-occupant old-position)))))

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
        (update-in (conj deck-path :discard) conj revealed))))

(defmethod -apply-action :bashketball/record-skill-test
  [state _action]
  ;; This action only logs to events, no state change
  state)
