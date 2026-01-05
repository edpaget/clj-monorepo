(ns bashketball-game.polix.effects-test
  (:require
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.effects :as effects]
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

(deftest move-player-effect-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-player-at fixtures/home-player-1 [2 3]))
        result (fx/apply-effect state
                                {:type :bashketball/move-player
                                 :player-id fixtures/home-player-1
                                 :position [2 5]}
                                {} (opts-with-registry))]
    (testing "moves player to new position"
      (is (= [2 5] (:position (state/get-basketball-player (:state result) fixtures/home-player-1)))))

    (testing "returns applied action"
      (is (= 1 (count (:applied result)))))

    (testing "no failures"
      (is (empty? (:failed result))))))

(deftest exhaust-player-effect-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effect state
                                {:type :bashketball/exhaust-player
                                 :player-id fixtures/home-player-1}
                                {} (opts-with-registry))]
    (testing "marks player as exhausted"
      (is (true? (:exhausted (state/get-basketball-player (:state result) fixtures/home-player-1)))))))

(deftest refresh-player-effect-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-exhausted fixtures/home-player-1))
        result (fx/apply-effect state
                                {:type :bashketball/refresh-player
                                 :player-id fixtures/home-player-1}
                                {} (opts-with-registry))]
    (testing "removes exhaustion from player"
      (is (false? (:exhausted (state/get-basketball-player (:state result) fixtures/home-player-1)))))))

(deftest give-ball-effect-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effect state
                                {:type :bashketball/give-ball
                                 :player-id fixtures/home-player-1}
                                {} (opts-with-registry))]
    (testing "sets ball as possessed by player"
      (is (= :ball-status/POSSESSED (:status (state/get-ball (:state result)))))
      (is (= fixtures/home-player-1 (:holder-id (state/get-ball (:state result))))))))

(deftest loose-ball-effect-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-ball-possessed fixtures/home-player-1))
        result (fx/apply-effect state
                                {:type :bashketball/loose-ball
                                 :position [3 5]}
                                {} (opts-with-registry))]
    (testing "sets ball as loose at position"
      (is (= :ball-status/LOOSE (:status (state/get-ball (:state result)))))
      (is (= [3 5] (:position (state/get-ball (:state result))))))))

(deftest draw-cards-effect-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effect state
                                {:type :bashketball/draw-cards
                                 :player :team/HOME
                                 :count 3}
                                {} (opts-with-registry))]
    (testing "draws cards into hand"
      (is (= 3 (count (state/get-hand (:state result) :team/HOME)))))

    (testing "reduces draw pile"
      (is (= 2 (count (state/get-draw-pile (:state result) :team/HOME)))))))

(deftest add-score-effect-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effect state
                                {:type :bashketball/add-score
                                 :team :team/HOME
                                 :points 3}
                                {} (opts-with-registry))]
    (testing "adds points to team score"
      (is (= 3 (get-in (state/get-score (:state result)) [:team/HOME]))))))

(deftest effect-with-context-bindings-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-player-at fixtures/home-player-1 [2 3]))
        ctx    {:bindings {:self fixtures/home-player-1
                           :target-position [2 6]}}
        result (fx/apply-effect state
                                {:type :bashketball/move-player
                                 :player-id :self
                                 :position :target-position}
                                ctx (opts-with-registry))]
    (testing "resolves :self binding"
      (is (= [2 6] (:position (state/get-basketball-player (:state result) fixtures/home-player-1)))))))

(deftest discard-cards-effect-test
  (let [state  (-> (fixtures/base-game-state)
                   (fixtures/with-drawn-cards :team/HOME 3))
        hand   (state/get-hand state :team/HOME)
        ids    [(-> hand first :instance-id)]
        result (fx/apply-effect state
                                {:type :bashketball/discard-cards
                                 :player :team/HOME
                                 :instance-ids ids}
                                {} (opts-with-registry))]
    (testing "removes card from hand"
      (is (= 2 (count (state/get-hand (:state result) :team/HOME)))))

    (testing "adds card to discard"
      (is (= 1 (count (state/get-discard (:state result) :team/HOME)))))))

(deftest sequence-of-effects-test
  (let [state  (fixtures/base-game-state)
        result (fx/apply-effects state
                                 [{:type :bashketball/draw-cards
                                   :player :team/HOME
                                   :count 2}
                                  {:type :bashketball/add-score
                                   :team :team/HOME
                                   :points 2}]
                                 {} (opts-with-registry))]
    (testing "both effects applied"
      (is (= 2 (count (state/get-hand (:state result) :team/HOME))))
      (is (= 2 (get-in (state/get-score (:state result)) [:team/HOME]))))))
