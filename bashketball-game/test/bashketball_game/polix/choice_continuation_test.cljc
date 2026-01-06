(ns bashketball-game.polix.choice-continuation-test
  "Tests for choice effect continuation.

  Tests the continuation mechanism where effects can pause for player input
  and resume after selection with `:choice/selected` bound in context."
  (:require
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.game-rules :as game-rules]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [f]
    (polix/initialize!)
    (f)))

(defn- apply-effect
  "Helper to apply effect and return just the state."
  [game-state effect]
  (:state (fx/apply-effect game-state effect {} {})))

;; =============================================================================
;; Effect Tests
;; =============================================================================

(deftest offer-choice-stores-continuation-test
  (let [continuation {:type :bashketball/exhaust-player
                      :player-id :choice/selected}
        game         (f/base-game-state)
        result       (apply-effect game
                                   {:type :bashketball/offer-choice
                                    :choice-type :select-target
                                    :options [{:id :player-1 :label "Player 1"}
                                              {:id :player-2 :label "Player 2"}]
                                    :waiting-for :team/HOME
                                    :continuation continuation})]
    (testing "stores continuation in pending-choice"
      (is (= continuation (get-in result [:pending-choice :continuation]))))

    (testing "stores other choice fields"
      (is (= :select-target (get-in result [:pending-choice :type])))
      (is (= :team/HOME (get-in result [:pending-choice :waiting-for])))
      (is (= 2 (count (get-in result [:pending-choice :options])))))))

(deftest offer-choice-without-continuation-test
  (let [game   (f/base-game-state)
        result (apply-effect game
                             {:type :bashketball/offer-choice
                              :choice-type :simple-choice
                              :options [{:id :yes :label "Yes"}
                                        {:id :no :label "No"}]
                              :waiting-for :team/AWAY})]
    (testing "works without continuation"
      (is (some? (:pending-choice result)))
      (is (nil? (get-in result [:pending-choice :continuation]))))))

(deftest submit-choice-sets-selected-test
  (let [game      (apply-effect (f/base-game-state)
                                {:type :bashketball/offer-choice
                                 :choice-type :test-choice
                                 :options [{:id :option-a :label "A"}
                                           {:id :option-b :label "B"}]
                                 :waiting-for :team/HOME})
        choice-id (get-in game [:pending-choice :id])
        result    (apply-effect game
                                {:type :bashketball/do-submit-choice
                                 :choice-id choice-id
                                 :selected :option-a})]
    (testing "sets selected on pending-choice"
      (is (= :option-a (get-in result [:pending-choice :selected]))))))

(deftest offer-choice-effect-passes-continuation-test
  (let [game         (f/base-game-state)
        registry     (-> (triggers/create-registry)
                         (game-rules/register-game-rules!))
        continuation {:type :bashketball/draw-cards
                      :player :team/HOME
                      :count 1}
        result       (fx/apply-effect game
                                      {:type :bashketball/offer-choice
                                       :choice-type :draw-choice
                                       :options [{:id :draw :label "Draw"}]
                                       :waiting-for :team/HOME
                                       :continuation continuation}
                                      {}
                                      {:registry registry})]
    (testing "effect stores continuation"
      (is (= continuation
             (get-in (:state result) [:pending-choice :continuation]))))

    (testing "effect returns pending flag"
      (is (= :choice (get-in result [:pending :type]))))))

(deftest execute-choice-continuation-effect-test
  (let [;; Set up game with pending choice that has a continuation
        game     (-> (f/base-game-state)
                     (f/with-player-at f/home-player-1 [2 5])
                     (assoc :pending-choice
                            {:id "test-choice"
                             :type :select-target
                             :options [{:id f/home-player-1 :label "Player 1"}]
                             :waiting-for :team/HOME
                             :selected f/home-player-1
                             :continuation {:type :bashketball/exhaust-player
                                            :player-id :choice/selected}}))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/execute-choice-continuation}
                                  {}
                                  {:registry registry})]
    (testing "continuation executes with choice/selected bound"
      (let [player (state/get-basketball-player (:state result) f/home-player-1)]
        (is (:exhausted player))))

    (testing "clears pending-choice after execution"
      (is (nil? (get-in (:state result) [:pending-choice]))))))

(deftest execute-choice-continuation-no-continuation-test
  (let [game     (-> (f/base-game-state)
                     (assoc :pending-choice
                            {:id "test-choice"
                             :type :simple-choice
                             :options [{:id :yes :label "Yes"}]
                             :waiting-for :team/HOME
                             :selected :yes}))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/execute-choice-continuation}
                                  {}
                                  {:registry registry})]
    (testing "clears pending-choice when no continuation"
      (is (nil? (get-in (:state result) [:pending-choice]))))))

(deftest execute-choice-continuation-no-pending-choice-test
  (let [game     (f/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/execute-choice-continuation}
                                  {}
                                  {:registry registry})]
    (testing "no-op when no pending choice"
      (is (nil? (get-in (:state result) [:pending-choice]))))))

;; =============================================================================
;; Integration Tests
;; =============================================================================

(deftest vector-continuation-executes-all-effects-test
  (let [;; Set up game with vector continuation that exhausts two players
        game     (-> (f/base-game-state)
                     (f/with-player-at f/home-player-1 [2 5])
                     (f/with-player-at f/home-player-2 [2 6])
                     (assoc :pending-choice
                            {:id "test-choice"
                             :type :multi-effect
                             :options [{:id f/home-player-1 :label "Player 1"}]
                             :waiting-for :team/HOME
                             :selected f/home-player-1
                             :continuation [{:type :bashketball/exhaust-player
                                             :player-id :choice/selected}
                                            {:type :bashketball/exhaust-player
                                             :player-id f/home-player-2}]}))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/execute-choice-continuation}
                                  {}
                                  {:registry registry})]
    (testing "executes all effects in vector"
      (let [state   (:state result)
            player1 (state/get-basketball-player state f/home-player-1)
            player2 (state/get-basketball-player state f/home-player-2)]
        (is (:exhausted player1) "first effect exhausted player 1")
        (is (:exhausted player2) "second effect exhausted player 2")))))

(deftest continuation-preserves-existing-context-bindings-test
  (let [;; Set up game with continuation that uses both choice/selected and self/id
        game     (-> (f/base-game-state)
                     (f/with-player-at f/home-player-1 [2 5])
                     (assoc :pending-choice
                            {:id "test-choice"
                             :type :contextual
                             :options [{:id :some-option :label "Option"}]
                             :waiting-for :team/HOME
                             :selected :some-option
                             :continuation {:type :bashketball/exhaust-player
                                            :player-id :self/id}}))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        ctx      {:bindings {:self/id f/home-player-1
                             :owner :team/HOME}}
        result   (fx/apply-effect game
                                  {:type :bashketball/execute-choice-continuation}
                                  ctx
                                  {:registry registry})]
    (testing "continuation can use existing context bindings"
      (let [player (state/get-basketball-player (:state result) f/home-player-1)]
        (is (:exhausted player))))))
