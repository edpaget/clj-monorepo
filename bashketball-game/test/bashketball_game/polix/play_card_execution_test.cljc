(ns bashketball-game.polix.play-card-execution-test
  "Tests for play card event-driven execution.

  Tests the full resolution flow: signal events → response checks →
  play effect application → after event."
  (:require
   [bashketball-game.effect-catalog :as catalog]
   [bashketball-game.polix.card-execution :as exec]
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.game-rules :as game-rules]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [t]
    (polix/initialize!)
    (t)))

;; =============================================================================
;; Test Data
;; =============================================================================

(def sample-play-card
  {:slug "fast-break"
   :name "Fast Break"
   :set-slug "base"
   :card-type :card-type/PLAY_CARD
   :fate 4
   :play {:play/id "fast-break"
          :play/name "Fast Break"
          :play/targets [:target/player-id]
          :play/effect {:effect/type :bashketball/refresh-player
                        :player-id :target/player-id}}})

(def sample-coaching-card-with-signal
  {:slug "quick-release"
   :name "Quick Release"
   :set-slug "base"
   :card-type :card-type/COACHING_CARD
   :fate 2
   :signal {:signal/effect {:effect/type :bashketball/draw-cards
                            :player :self/team
                            :count 1}}})

(def sample-coaching-card-no-signal
  {:slug "timeout"
   :name "Timeout"
   :set-slug "base"
   :card-type :card-type/COACHING_CARD
   :fate 3})

(defn test-catalog
  "Creates a test effect catalog with sample cards."
  [& cards]
  (catalog/create-catalog-from-seq cards))

;; =============================================================================
;; Basic Play Card Execution Tests
;; =============================================================================

(deftest execute-play-card-applies-effect-test
  (let [cat       (test-catalog sample-play-card)
        game      (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 5])
                      (f/with-exhausted f/home-player-1))
        registry  (-> (triggers/create-registry)
                      (game-rules/register-game-rules!))
        main-card {:instance-id "card-1" :card-slug "fast-break"}
        result    (exec/execute-play-card cat game registry main-card []
                                          {:target/player-id f/home-player-1}
                                          :team/HOME)]
    (testing "play effect is applied to game state"
      (let [player (state/get-basketball-player (:state result) f/home-player-1)]
        (is (not (:exhausted player)))))))

(deftest execute-play-card-without-fuel-test
  (let [cat       (test-catalog sample-play-card)
        game      (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 5])
                      (f/with-exhausted f/home-player-1))
        registry  (-> (triggers/create-registry)
                      (game-rules/register-game-rules!))
        main-card {:instance-id "card-1" :card-slug "fast-break"}
        result    (exec/execute-play-card cat game registry main-card []
                                          {:target/player-id f/home-player-1}
                                          :team/HOME)]
    (testing "returns valid result structure"
      (is (map? (:state result)))
      (is (nil? (:pending result))))))

;; =============================================================================
;; Signal Tests
;; =============================================================================

(deftest execute-play-card-with-signal-fuel-test
  (let [cat               (test-catalog sample-play-card sample-coaching-card-with-signal)
        game              (-> (f/base-game-state)
                              (f/with-player-at f/home-player-1 [2 5])
                              (f/with-exhausted f/home-player-1)
                              (f/with-drawn-cards :team/HOME 3))
        registry          (-> (triggers/create-registry)
                              (game-rules/register-game-rules!))
        main-card         {:instance-id "card-1" :card-slug "fast-break"}
        fuel-card         {:instance-id "fuel-1" :card-slug "quick-release"}
        initial-hand-size (count (get-in game [:players :team/HOME :deck :hand]))
        result            (exec/execute-play-card cat game registry main-card [fuel-card]
                                                  {:target/player-id f/home-player-1}
                                                  :team/HOME)]
    (testing "signal effect is applied (draws card)"
      (let [final-hand-size (count (get-in (:state result) [:players :team/HOME :deck :hand]))]
        (is (= (inc initial-hand-size) final-hand-size))))))

(deftest execute-play-card-fuel-without-signal-test
  (let [cat               (test-catalog sample-play-card sample-coaching-card-no-signal)
        game              (-> (f/base-game-state)
                              (f/with-player-at f/home-player-1 [2 5])
                              (f/with-exhausted f/home-player-1)
                              (f/with-drawn-cards :team/HOME 3))
        registry          (-> (triggers/create-registry)
                              (game-rules/register-game-rules!))
        main-card         {:instance-id "card-1" :card-slug "fast-break"}
        fuel-card         {:instance-id "fuel-1" :card-slug "timeout"}
        initial-hand-size (count (get-in game [:players :team/HOME :deck :hand]))
        result            (exec/execute-play-card cat game registry main-card [fuel-card]
                                                  {:target/player-id f/home-player-1}
                                                  :team/HOME)]
    (testing "no signal means no extra draw"
      (let [final-hand-size (count (get-in (:state result) [:players :team/HOME :deck :hand]))]
        (is (= initial-hand-size final-hand-size))))))

;; =============================================================================
;; Signal Ordering Tests
;; =============================================================================

(deftest execute-play-card-multiple-signals-prompts-ordering-test
  (let [cat        (test-catalog sample-play-card sample-coaching-card-with-signal)
        game       (-> (f/base-game-state)
                       (f/with-player-at f/home-player-1 [2 5]))
        registry   (-> (triggers/create-registry)
                       (game-rules/register-game-rules!))
        main-card  {:instance-id "card-1" :card-slug "fast-break"}
        fuel-cards [{:instance-id "fuel-1" :card-slug "quick-release"}
                    {:instance-id "fuel-2" :card-slug "quick-release"}]
        result     (exec/execute-play-card-with-ordering
                    cat game registry main-card fuel-cards
                    {:target/player-id f/home-player-1}
                    :team/HOME)]
    (testing "returns pending choice for signal ordering"
      (is (some? (:pending result)))
      (is (= :choice (get-in result [:pending :type]))))
    (testing "pending choice has correct type"
      (is (= :signal-ordering (get-in result [:state :pending-choice :type]))))
    (testing "pending choice has options for each signal"
      (is (= 2 (count (get-in result [:state :pending-choice :options])))))))

(deftest execute-play-card-single-signal-no-ordering-test
  (let [cat       (test-catalog sample-play-card sample-coaching-card-with-signal)
        game      (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 5])
                      (f/with-exhausted f/home-player-1)
                      (f/with-drawn-cards :team/HOME 3))
        registry  (-> (triggers/create-registry)
                      (game-rules/register-game-rules!))
        main-card {:instance-id "card-1" :card-slug "fast-break"}
        fuel-card {:instance-id "fuel-1" :card-slug "quick-release"}
        result    (exec/execute-play-card-with-ordering
                   cat game registry main-card [fuel-card]
                   {:target/player-id f/home-player-1}
                   :team/HOME)]
    (testing "single signal does not require ordering"
      (is (nil? (:pending result))))
    (testing "play effect is applied"
      (let [player (state/get-basketball-player (:state result) f/home-player-1)]
        (is (not (:exhausted player)))))))

;; =============================================================================
;; Direct Effect Application Test
;; =============================================================================

(deftest play-card-direct-effect-test
  (let [cat       (test-catalog sample-play-card)
        game      (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 5])
                      (f/with-exhausted f/home-player-1))
        registry  (-> (triggers/create-registry)
                      (game-rules/register-game-rules!))
        main-card {:instance-id "card-1" :card-slug "fast-break"}
        play-def  (catalog/get-play cat "fast-break")
        context   {:self/team :team/HOME
                   :target/player-id f/home-player-1}
        result    (fx/apply-effect game
                                   {:type :bashketball/play-card
                                    :main-card main-card
                                    :fuel-cards []
                                    :targets {:target/player-id f/home-player-1}
                                    :play-effect (:play/effect play-def)
                                    :effect-context context}
                                   {}
                                   {:validate? false
                                    :registry registry})]
    (testing "play effect is applied with registry"
      (let [player (state/get-basketball-player (:state result) f/home-player-1)]
        (is (not (:exhausted player)))))))
