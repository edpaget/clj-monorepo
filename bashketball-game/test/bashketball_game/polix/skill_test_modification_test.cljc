(ns bashketball-game.polix.skill-test-modification-test
  "Tests for skill test modification effect and action.

  Tests the `:bashketball/modify-skill-test` effect which allows card abilities
  to add modifiers to pending skill tests during the skill test flow."
  (:require
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.game-rules :as game-rules]
   [bashketball-game.polix.triggers :as triggers]
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

(defn with-pending-skill-test
  "Adds a pending skill test to the game state for testing."
  [game-state]
  (-> game-state
      (f/with-player-at f/home-player-1 [2 3])
      (apply-effect {:type :bashketball/initiate-skill-test
                     :actor-id f/home-player-1
                     :stat :stat/SHOOTING
                     :target-value 5
                     :context {:type :shoot}})))

;; =============================================================================
;; Action Tests (now using effects)
;; =============================================================================

(deftest modify-skill-test-adds-numeric-modifier-test
  (let [game   (-> (f/base-game-state)
                   (with-pending-skill-test))
        result (apply-effect game
                             {:type :bashketball/modify-skill-test
                              :source "test-ability"
                              :amount 2})]
    (testing "adds modifier to pending skill test"
      (is (= 1 (count (get-in result [:pending-skill-test :modifiers])))))

    (testing "modifier has correct source and amount"
      (let [modifier (first (get-in result [:pending-skill-test :modifiers]))]
        (is (= "test-ability" (:source modifier)))
        (is (= 2 (:amount modifier)))))))

(deftest modify-skill-test-adds-advantage-modifier-test
  (let [game   (-> (f/base-game-state)
                   (with-pending-skill-test))
        result (apply-effect game
                             {:type :bashketball/modify-skill-test
                              :source "clutch-ability"
                              :advantage :advantage/ADVANTAGE})]
    (testing "adds modifier with advantage"
      (let [modifier (first (get-in result [:pending-skill-test :modifiers]))]
        (is (= "clutch-ability" (:source modifier)))
        (is (= 0 (:amount modifier)))
        (is (= :advantage/ADVANTAGE (:advantage modifier)))))))

(deftest modify-skill-test-adds-combined-modifier-test
  (let [game   (-> (f/base-game-state)
                   (with-pending-skill-test))
        result (apply-effect game
                             {:type :bashketball/modify-skill-test
                              :source "power-play"
                              :amount 3
                              :advantage :advantage/ADVANTAGE
                              :reason "+3 and advantage from power play"})]
    (testing "modifier has both amount and advantage"
      (let [modifier (first (get-in result [:pending-skill-test :modifiers]))]
        (is (= "power-play" (:source modifier)))
        (is (= 3 (:amount modifier)))
        (is (= :advantage/ADVANTAGE (:advantage modifier)))
        (is (= "+3 and advantage from power play" (:reason modifier)))))))

(deftest modify-skill-test-no-op-without-pending-test
  (let [game   (f/base-game-state)
        result (fx/apply-effect game
                                {:type :bashketball/modify-skill-test
                                 :source "test"
                                 :amount 1}
                                {} {})]
    (testing "effect is no-op when no pending skill test"
      (is (nil? (:pending-skill-test (:state result))))
      (is (empty? (:applied result))))))

(deftest modify-skill-test-accumulates-modifiers-test
  (let [game   (-> (f/base-game-state)
                   (with-pending-skill-test))
        result (-> game
                   (apply-effect {:type :bashketball/modify-skill-test
                                  :source "ability-1"
                                  :amount 2})
                   (apply-effect {:type :bashketball/modify-skill-test
                                  :source "ability-2"
                                  :amount -1
                                  :advantage :advantage/DISADVANTAGE}))]
    (testing "accumulates multiple modifiers"
      (is (= 2 (count (get-in result [:pending-skill-test :modifiers])))))

    (testing "modifiers maintain order"
      (let [mods (get-in result [:pending-skill-test :modifiers])]
        (is (= "ability-1" (:source (first mods))))
        (is (= "ability-2" (:source (second mods))))))))

;; =============================================================================
;; Effect Tests
;; =============================================================================

(deftest modify-skill-test-effect-test
  (let [game     (-> (f/base-game-state)
                     (with-pending-skill-test))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/modify-skill-test
                                   :source "effect-source"
                                   :amount 5}
                                  {}
                                  {:registry registry})]
    (testing "effect adds modifier to skill test"
      (let [modifier (first (get-in (:state result) [:pending-skill-test :modifiers]))]
        (is (= "effect-source" (:source modifier)))
        (is (= 5 (:amount modifier)))))))

(deftest modify-skill-test-effect-resolves-parameters-test
  (let [game     (-> (f/base-game-state)
                     (with-pending-skill-test))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        ctx      {:bindings {:ability-source "bound-source"
                             :bonus-amount 3}}
        result   (fx/apply-effect game
                                  {:type :bashketball/modify-skill-test
                                   :source :ability-source
                                   :amount :bonus-amount}
                                  ctx
                                  {:registry registry})]
    (testing "resolves parameters from context bindings"
      (let [modifier (first (get-in (:state result) [:pending-skill-test :modifiers]))]
        (is (= "bound-source" (:source modifier)))
        (is (= 3 (:amount modifier)))))))

(deftest modify-skill-test-effect-no-op-without-pending-test
  (let [game     (f/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/modify-skill-test
                                   :source "test"
                                   :amount 1}
                                  {}
                                  {:registry registry})]
    (testing "effect is no-op when no pending skill test"
      (is (nil? (:pending-skill-test (:state result))))
      (is (empty? (:applied result))))))

;; =============================================================================
;; Resolution Integration Tests
;; =============================================================================

(deftest modifiers-included-in-resolution-test
  (let [game   (-> (f/base-game-state)
                   (with-pending-skill-test)
                   (apply-effect {:type :bashketball/modify-skill-test
                                  :source "bonus-1"
                                  :amount 2})
                   (apply-effect {:type :bashketball/modify-skill-test
                                  :source "bonus-2"
                                  :amount 3})
                   (apply-effect {:type :bashketball/do-set-skill-test-fate
                                  :fate 4}))
        result (apply-effect game {:type :bashketball/do-resolve-skill-test})]
    (testing "total includes base value, modifiers, and fate"
      (let [total (get-in result [:pending-skill-test :total])
            base  (get-in result [:pending-skill-test :base-value])]
        ;; total = base + 2 + 3 + 4 = base + 9
        (is (= (+ base 9) total))))))
