(ns polix.effects.conditional-strategies-test
  "Tests for Phase 8 conditional effect strategies and transaction enhancements."
  (:require [clojure.test :refer [deftest is testing]]
            [polix.effects.core :as fx]))

;;; ---------------------------------------------------------------------------
;;; Block Strategy (Default)
;;; ---------------------------------------------------------------------------

(deftest block-strategy-default-test
  (testing "block is the default strategy (else on residual)"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :else {:type :polix.effects/assoc-in :path [:unknown] :value true}})]
      (is (= {:name "test" :unknown true} (:state result)))
      (is (nil? (get-in (:state result) [:alive]))))))

(deftest block-strategy-explicit-test
  (testing "explicit :block behaves same as default"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :else {:type :polix.effects/assoc-in :path [:unknown] :value true}
                   :on-residual :block})]
      (is (= {:name "test" :unknown true} (:state result))))))

(deftest block-strategy-noop-when-no-else-test
  (testing "block with no else branch is noop on residual"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :on-residual :block})]
      (is (= {:name "test"} (:state result)))
      (is (= 1 (count (:applied result)))))))

;;; ---------------------------------------------------------------------------
;;; Defer Strategy
;;; ---------------------------------------------------------------------------

(deftest defer-strategy-returns-pending-test
  (testing "defer returns pending with effect and residual"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :on-residual :defer})]
      (is (some? (:pending result)))
      (is (= :deferred (get-in result [:pending :type])))
      (is (= {:name "test"} (:state result)))
      (is (empty? (:applied result))))))

(deftest defer-strategy-preserves-effect-test
  (testing "deferred pending contains original effect"
    (let [effect {:type :polix.effects/conditional
                  :condition [:> :doc/health 0]
                  :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                  :on-residual :defer}
          result (fx/apply-effect {:name "test"} effect)]
      (is (= effect (get-in result [:pending :effect]))))))

(deftest defer-strategy-preserves-residual-test
  (testing "deferred pending contains residual info"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :on-residual :defer})]
      (is (map? (get-in result [:pending :residual])))
      (is (contains? (get-in result [:pending :residual]) [:health])))))

;;; ---------------------------------------------------------------------------
;;; Proceed Strategy
;;; ---------------------------------------------------------------------------

(deftest proceed-strategy-executes-then-test
  (testing "proceed executes then branch on residual"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :on-residual :proceed})]
      (is (= {:name "test" :alive true} (:state result)))
      (is (= 1 (count (:applied result)))))))

(deftest proceed-strategy-attaches-residual-test
  (testing "proceed attaches condition-residual to applied effects"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :on-residual :proceed})]
      (is (some? (get-in result [:applied 0 :condition-residual])))
      (is (map? (get-in result [:applied 0 :condition-residual]))))))

(deftest proceed-strategy-noop-when-no-then-test
  (testing "proceed with no then branch is noop"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :else {:type :polix.effects/assoc-in :path [:x] :value 1}
                   :on-residual :proceed})]
      (is (= {:name "test"} (:state result)))
      (is (= 1 (count (:applied result)))))))

;;; ---------------------------------------------------------------------------
;;; Speculate Strategy
;;; ---------------------------------------------------------------------------

(deftest speculate-strategy-executes-then-test
  (testing "speculate executes then branch on residual"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :on-residual :speculate})]
      (is (= {:name "test" :alive true} (:state result))))))

(deftest speculate-strategy-marks-speculative-test
  (testing "speculate marks applied effects as speculative"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :on-residual :speculate})]
      (is (true? (get-in result [:applied 0 :speculative?])))
      (is (some? (get-in result [:applied 0 :speculation-condition])))
      (is (some? (get-in result [:applied 0 :condition-residual]))))))

(deftest speculate-strategy-tracks-conditions-test
  (testing "speculate tracks speculative conditions in result"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:> :doc/health 0]
                   :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                   :on-residual :speculate})]
      (is (= 1 (count (:speculative-conditions result))))
      (is (= [:> :doc/health 0]
             (get-in result [:speculative-conditions 0 :condition]))))))

;;; ---------------------------------------------------------------------------
;;; Satisfied/Conflict Behavior (unchanged)
;;; ---------------------------------------------------------------------------

(deftest satisfied-ignores-strategy-test
  (testing "satisfied condition uses then branch regardless of strategy"
    (doseq [strategy [:block :defer :proceed :speculate]]
      (let [result (fx/apply-effect
                    {:health 10}
                    {:type :polix.effects/conditional
                     :condition [:> :doc/health 0]
                     :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                     :on-residual strategy})]
        (is (= {:health 10 :alive true} (:state result))
            (str "Failed for strategy: " strategy))))))

(deftest conflict-ignores-strategy-test
  (testing "conflicting condition uses else branch regardless of strategy"
    (doseq [strategy [:block :defer :proceed :speculate]]
      (let [result (fx/apply-effect
                    {:health 0}
                    {:type :polix.effects/conditional
                     :condition [:> :doc/health 0]
                     :then {:type :polix.effects/assoc-in :path [:alive] :value true}
                     :else {:type :polix.effects/assoc-in :path [:dead] :value true}
                     :on-residual strategy})]
        (is (= {:health 0 :dead true} (:state result))
            (str "Failed for strategy: " strategy))))))

;;; ---------------------------------------------------------------------------
;;; Transaction with Speculation
;;; ---------------------------------------------------------------------------

(deftest transaction-speculation-rollback-test
  (testing "transaction rolls back when speculation becomes conflict"
    ;; Start with missing :health (residual), speculate it's > 5, then set to 0
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/transaction
                   :effects [{:type :polix.effects/conditional
                              :condition [:> :doc/health 5]
                              :then {:type :polix.effects/assoc-in :path [:status] :value "healthy"}
                              :on-residual :speculate}
                             {:type :polix.effects/assoc-in :path [:health] :value 0}]})]
      (is (= {:name "test"} (:state result)))
      (is (= 1 (count (:failed result))))
      (is (= :speculation-conflict (get-in result [:failed 0 :error]))))))

(deftest transaction-speculation-valid-test
  (testing "transaction succeeds when speculation remains valid"
    ;; Start with missing :health (residual), speculate it's > 5, then set to 20
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/transaction
                   :effects [{:type :polix.effects/conditional
                              :condition [:> :doc/health 5]
                              :then {:type :polix.effects/assoc-in :path [:status] :value "healthy"}
                              :on-residual :speculate}
                             {:type :polix.effects/assoc-in :path [:health] :value 20}]})]
      (is (= {:name "test" :health 20 :status "healthy"} (:state result)))
      (is (empty? (:failed result))))))

(deftest transaction-defer-propagation-test
  (testing "transaction propagates pending on deferred effect"
    (let [result (fx/apply-effect
                  {:a 1}
                  {:type :polix.effects/transaction
                   :effects [{:type :polix.effects/assoc-in :path [:b] :value 2}
                             {:type :polix.effects/conditional
                              :condition [:> :doc/health 0]
                              :then {:type :polix.effects/assoc-in :path [:c] :value 3}
                              :on-residual :defer}]})]
      (is (some? (:pending result)))
      (is (= {:a 1} (:state result)))
      (is (empty? (:applied result))))))

(deftest transaction-failure-strategy-rollback-test
  (testing "rollback strategy reverts on failure"
    (let [result (fx/apply-effect
                  {:a 1}
                  {:type :polix.effects/transaction
                   :effects [{:type :polix.effects/assoc-in :path [:b] :value 2}
                             {:type :unknown/effect}]
                   :on-failure :rollback}
                  {}
                  {:validate? false})]
      (is (= {:a 1} (:state result)))
      (is (empty? (:applied result))))))

(deftest transaction-failure-strategy-partial-test
  (testing "partial strategy keeps successful effects"
    (let [result (fx/apply-effect
                  {:a 1}
                  {:type :polix.effects/transaction
                   :effects [{:type :polix.effects/assoc-in :path [:b] :value 2}
                             {:type :unknown/effect}]
                   :on-failure :partial}
                  {}
                  {:validate? false})]
      (is (= {:a 1 :b 2} (:state result)))
      (is (= 1 (count (:applied result))))
      (is (= 1 (count (:failed result)))))))
