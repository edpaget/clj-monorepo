(ns bashketball-game.polix.explain-test
  (:require
   [bashketball-game.polix.explain :as explain]
   [clojure.test :refer [deftest is testing]]
   [polix.residual :as res]))

;; =============================================================================
;; Constraint Explanation Tests
;; =============================================================================

(deftest explain-constraint-test
  (testing "explains player exhausted conflict"
    (let [result (explain/explain-constraint [:player-exhausted] [:= false] true)]
      (is (= :player-exhausted (:key result)))
      (is (= "Player is exhausted and cannot act" (:message result)))
      (is (= true (:witness result)))))

  (testing "explains player off court"
    (let [result (explain/explain-constraint [:player-on-court] [:= true])]
      (is (= :player-off-court (:key result)))
      (is (= "Player must be on the court" (:message result)))))

  (testing "returns nil for unknown constraint"
    (is (nil? (explain/explain-constraint [:unknown-path] [:= "value"])))))

(deftest explain-constraint-wildcards-test
  (testing "wildcard matches any value"
    (let [result (explain/explain-constraint [:player] [:= :team/HOME])]
      (is (= :not-active-player (:key result))))))

;; =============================================================================
;; Residual Explanation Tests
;; =============================================================================

(deftest explain-residual-conflicts-test
  (testing "explains conflict residuals"
    (let [residual     (res/conflict-residual [:player-exhausted] [:= false] true)
          explanations (explain/explain-residual residual)]
      (is (= 1 (count explanations)))
      (is (= :player-exhausted (:key (first explanations))))
      (is (= true (:witness (first explanations)))))))

(deftest explain-residual-open-test
  (testing "explains open residuals"
    (let [residual     {[:player-on-court] [[:= true]]}
          explanations (explain/explain-residual residual)]
      (is (= 1 (count explanations)))
      (is (= :player-off-court (:key (first explanations)))))))

(deftest explain-residual-multiple-test
  (testing "explains multiple constraints"
    (let [residual     {[:player-on-court] [[:= true]]
                        [:player-exhausted] [[:= false]]}
          explanations (explain/explain-residual residual)]
      (is (= 2 (count explanations))))))

(deftest explain-first-test
  (testing "returns first explanation"
    (let [residual {[:player-exhausted] [[:= false]]}]
      (is (some? (explain/explain-first residual)))))

  (testing "returns nil for empty residual"
    (is (nil? (explain/explain-first {})))))

;; =============================================================================
;; Action-Specific Explanation Tests
;; =============================================================================

(deftest explain-action-failure-test
  (testing "explains move failure"
    (let [residual (res/conflict-residual [:player-exhausted] [:= false] true)
          result   (explain/explain-action-failure :bashketball/move-player residual)]
      (is (= :player-exhausted (:key result)))))

  (testing "explains substitute failure"
    (let [residual {[:off-court-on-court] [[:= false]]}
          result   (explain/explain-action-failure :bashketball/substitute residual)]
      (is (= :off-court-already-on (:key result)))))

  (testing "handles unknown action types"
    (let [residual {[:player-on-court] [[:= true]]}
          result   (explain/explain-action-failure :unknown-action residual)]
      (is (some? result)))))
