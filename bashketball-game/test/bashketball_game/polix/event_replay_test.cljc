(ns bashketball-game.polix.event-replay-test
  (:require
   [bashketball-game.event-log :as event-log]
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.event-replay :as event-replay]
   [bashketball-game.polix.fixtures :as fixtures]
   [bashketball-game.polix.game-rules :as game-rules]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [f]
    (polix/initialize!)
    (f)))

(defn opts-with-registry
  "Returns opts map with a registry containing game rules."
  []
  {:validate? false
   :registry (-> (triggers/create-registry)
                 (game-rules/register-game-rules!))})

;; =============================================================================
;; Event to Effect Mapping Tests
;; =============================================================================

(deftest event->effect-move-player-test
  (let [event  {:type :bashketball/move-player
                :player-id "p1"
                :from-position [2 3]
                :to-position [2 5]
                :timestamp "2024-01-01T00:00:00Z"}
        effect (event-replay/event->effect event)]
    (is (= :bashketball/move-player (:type effect)))
    (is (= "p1" (:player-id effect)))
    (is (= [2 5] (:position effect)))))

(deftest event->effect-exhaust-player-test
  (let [event  {:type :bashketball/exhaust-player
                :player-id "p1"
                :timestamp "2024-01-01T00:00:00Z"}
        effect (event-replay/event->effect event)]
    (is (= :bashketball/do-exhaust-player (:type effect)))
    (is (= "p1" (:player-id effect)))))

(deftest event->effect-draw-cards-test
  (let [event  {:type :bashketball/draw-cards
                :player :team/HOME
                :count 3
                :cards [{:instance-id "c1"} {:instance-id "c2"} {:instance-id "c3"}]
                :timestamp "2024-01-01T00:00:00Z"}
        effect (event-replay/event->effect event)]
    (is (= :bashketball/do-draw-cards (:type effect)))
    (is (= :team/HOME (:player effect)))
    (is (= 3 (:count effect)))))

(deftest event->effect-set-phase-test
  (let [event  {:type :bashketball/set-phase
                :phase :phase/MAIN
                :timestamp "2024-01-01T00:00:00Z"}
        effect (event-replay/event->effect event)]
    (is (= :bashketball/do-set-phase (:type effect)))
    (is (= :phase/MAIN (:phase effect)))))

(deftest event->effect-unknown-returns-nil-test
  (let [event {:type :bashketball/unknown-event :timestamp "2024-01-01T00:00:00Z"}]
    (is (nil? (event-replay/event->effect event)))))

;; =============================================================================
;; Replay Single Event Tests
;; =============================================================================

(deftest replay-event-exhaust-player-test
  (let [state   (fixtures/base-game-state)
        event   {:type :bashketball/exhaust-player
                 :player-id fixtures/home-player-1
                 :timestamp "2024-01-01T00:00:00Z"}
        result  (event-replay/replay-event state event)
        player  (state/get-basketball-player result fixtures/home-player-1)]
    (is (true? (:exhausted player)))))

(deftest replay-event-unknown-preserves-state-test
  (let [state  (fixtures/base-game-state)
        event  {:type :bashketball/unknown-event :timestamp "2024-01-01T00:00:00Z"}
        result (event-replay/replay-event state event)]
    (is (= state result))))

;; =============================================================================
;; Replay Event Sequence Tests
;; =============================================================================

(deftest replay-events-sequence-test
  (let [state  (fixtures/base-game-state)
        events [{:type :bashketball/exhaust-player
                 :player-id fixtures/home-player-1
                 :timestamp "2024-01-01T00:00:00Z"}
                {:type :bashketball/add-score
                 :team :team/HOME
                 :points 3
                 :timestamp "2024-01-01T00:00:01Z"}]
        result (event-replay/replay-events state events)]
    (testing "player is exhausted"
      (is (true? (:exhausted (state/get-basketball-player result fixtures/home-player-1)))))
    (testing "score is updated"
      (is (= 3 (get-in (state/get-score result) [:team/HOME]))))))

;; =============================================================================
;; Round-Trip Replay Tests
;; =============================================================================

(deftest round-trip-exhaust-and-refresh-test
  (let [initial (fixtures/base-game-state)
        ;; Apply effects to produce events
        result1 (fx/apply-effect initial
                                 {:type :bashketball/exhaust-player
                                  :player-id fixtures/home-player-1}
                                 {} (opts-with-registry))
        result2 (fx/apply-effect (:state result1)
                                 {:type :bashketball/refresh-player
                                  :player-id fixtures/home-player-1}
                                 {} (opts-with-registry))
        final   (:state result2)
        ;; Extract events and replay
        events  (event-log/get-events final)
        replayed (event-replay/replay-events initial events)]
    (testing "replayed state matches final state (excluding events)"
      (is (= (dissoc final :events)
             (dissoc replayed :events))))))

(deftest round-trip-draw-cards-test
  (let [initial     (fixtures/base-game-state)
        draw-pile   (state/get-draw-pile initial :team/HOME)
        result      (fx/apply-effect initial
                                     {:type :bashketball/draw-cards
                                      :player :team/HOME
                                      :count 2}
                                     {} (opts-with-registry))
        final       (:state result)
        events      (event-log/get-events final)
        replayed    (event-replay/replay-events initial events)]
    (testing "replayed hand matches final hand"
      (is (= (state/get-hand final :team/HOME)
             (state/get-hand replayed :team/HOME))))
    (testing "replayed draw pile matches final draw pile"
      (is (= (state/get-draw-pile final :team/HOME)
             (state/get-draw-pile replayed :team/HOME))))))

(deftest round-trip-score-test
  (let [initial  (fixtures/base-game-state)
        result   (fx/apply-effect initial
                                  {:type :bashketball/add-score
                                   :team :team/HOME
                                   :points 3}
                                  {} (opts-with-registry))
        final    (:state result)
        events   (event-log/get-events final)
        replayed (event-replay/replay-events initial events)]
    (testing "replayed score matches final score"
      (is (= (state/get-score final)
             (state/get-score replayed))))))
