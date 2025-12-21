(ns polix.collection-ops-test
  "Tests for extensible collection operators."
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.collection-ops :as coll-ops]
   [polix.engine :as engine]))

;;; ---------------------------------------------------------------------------
;;; Protocol Tests
;;; ---------------------------------------------------------------------------

(deftest built-in-operators-registered-test
  (testing "forall is registered"
    (is (some? (coll-ops/get-collection-op :forall))))

  (testing "exists is registered"
    (is (some? (coll-ops/get-collection-op :exists))))

  (testing "count is registered"
    (is (some? (coll-ops/get-collection-op :count))))

  (testing "sum is registered"
    (is (some? (coll-ops/get-collection-op :sum))))

  (testing "unknown operator returns nil"
    (is (nil? (coll-ops/get-collection-op :unknown-op)))))

(deftest operator-types-test
  (testing "forall is a quantifier"
    (is (= :quantifier (coll-ops/op-type (coll-ops/get-collection-op :forall)))))

  (testing "exists is a quantifier"
    (is (= :quantifier (coll-ops/op-type (coll-ops/get-collection-op :exists)))))

  (testing "count is an aggregation"
    (is (= :aggregation (coll-ops/op-type (coll-ops/get-collection-op :count)))))

  (testing "sum is an aggregation"
    (is (= :aggregation (coll-ops/op-type (coll-ops/get-collection-op :sum))))))

(deftest empty-result-test
  (testing "forall empty result is true (vacuous truth)"
    (is (= true (coll-ops/empty-result (coll-ops/get-collection-op :forall)))))

  (testing "exists empty result is false"
    (is (= false (coll-ops/empty-result (coll-ops/get-collection-op :exists)))))

  (testing "count empty result is 0"
    (is (= 0 (coll-ops/empty-result (coll-ops/get-collection-op :count)))))

  (testing "sum empty result is 0"
    (is (= 0 (coll-ops/empty-result (coll-ops/get-collection-op :sum))))))

;;; ---------------------------------------------------------------------------
;;; Custom Operator Registration Tests
;;; ---------------------------------------------------------------------------

(deftest register-custom-operator-test
  (testing "can register a custom operator"
    (coll-ops/register-collection-op!
     :every
     {:op-type :quantifier
      :empty-result true
      :init-state (fn [] {})
      :process-element (fn [state _elem body-result _idx]
                         (if (false? body-result)
                           {:short-circuit false}
                           {:state state}))
      :finalize (fn [_state residuals]
                  (if (empty? residuals)
                    true
                    {:residual residuals}))})

    (is (some? (coll-ops/get-collection-op :every)))
    (is (= :quantifier (coll-ops/op-type (coll-ops/get-collection-op :every))))))

(deftest custom-avg-operator-test
  (testing "can create an avg aggregation operator"
    (coll-ops/register-collection-op!
     :avg
     {:op-type :aggregation
      :empty-result 0
      :init-state (fn [] {:sum 0 :count 0})
      :process-element (fn [state elem filter-result _idx]
                         {:state (if (and (true? filter-result) (number? elem))
                                   (-> state
                                       (update :sum + elem)
                                       (update :count inc))
                                   state)})
      :finalize (fn [{:keys [sum count]} residuals]
                  (if (empty? residuals)
                    (if (zero? count) 0 (/ sum count))
                    {:partial-avg (if (zero? count) 0 (/ sum count))
                     :residual residuals}))})

    (let [avg-op (coll-ops/get-collection-op :avg)]
      (is (some? avg-op))
      (is (= :aggregation (coll-ops/op-type avg-op))))))

;;; ---------------------------------------------------------------------------
;;; Helper Function Tests
;;; ---------------------------------------------------------------------------

(deftest residual-helper-test
  (testing "residual? detects residual results"
    (is (coll-ops/residual? {:residual {[:users] [[:any]]}}))
    (is (not (coll-ops/residual? true)))
    (is (not (coll-ops/residual? false)))
    (is (not (coll-ops/residual? {:result true})))))

(deftest index-residual-test
  (testing "prefixes residual paths with collection index"
    (let [residual {:residual {[:role] [[:= "admin"]]}}
          indexed  (coll-ops/index-residual residual [:users] 0)]
      (is (= {:residual {[:users 0 :role] [[:= "admin"]]}}
             indexed)))))

;;; ---------------------------------------------------------------------------
;;; Integration Tests via Engine
;;; ---------------------------------------------------------------------------

(deftest forall-integration-test
  (testing "forall all pass"
    (is (= true (engine/evaluate
                 [:forall [:u :doc/users] [:= :u/role "admin"]]
                 {:users [{:role "admin"} {:role "admin"}]}))))

  (testing "forall one fails"
    (is (= false (engine/evaluate
                  [:forall [:u :doc/users] [:= :u/role "admin"]]
                  {:users [{:role "admin"} {:role "guest"}]})))))

(deftest exists-integration-test
  (testing "exists finds match"
    (is (= true (engine/evaluate
                 [:exists [:u :doc/users] [:= :u/role "admin"]]
                 {:users [{:role "user"} {:role "admin"}]}))))

  (testing "exists no match"
    (is (= false (engine/evaluate
                  [:exists [:u :doc/users] [:= :u/role "admin"]]
                  {:users [{:role "user"} {:role "guest"}]})))))

(deftest count-integration-test
  (testing "count basic"
    (is (= true (engine/evaluate
                 [:= [:fn/count :doc/users] 2]
                 {:users [{} {}]}))))

  (testing "count with filter"
    (is (= true (engine/evaluate
                 [:= [:fn/count [:u :doc/users :where [:= :u/active true]]] 1]
                 {:users [{:active true} {:active false}]})))))

;;; ---------------------------------------------------------------------------
;;; Simplification Protocol Tests
;;; ---------------------------------------------------------------------------

(deftest can-merge-test
  (testing "forall can merge with forall"
    (let [forall-op (coll-ops/get-collection-op :forall)]
      (is (coll-ops/can-merge? forall-op :forall))))

  (testing "exists can merge with exists"
    (let [exists-op (coll-ops/get-collection-op :exists)]
      (is (coll-ops/can-merge? exists-op :exists))))

  (testing "forall cannot merge with exists"
    (let [forall-op (coll-ops/get-collection-op :forall)]
      (is (not (coll-ops/can-merge? forall-op :exists))))))

(deftest simplify-comparison-test
  (testing "count > 0 simplifies to exists"
    (let [count-op (coll-ops/get-collection-op :count)]
      (is (= :simplify-to-exists
             (coll-ops/simplify-comparison count-op :> 0)))))

  (testing "count >= 1 simplifies to exists"
    (let [count-op (coll-ops/get-collection-op :count)]
      (is (= :simplify-to-exists
             (coll-ops/simplify-comparison count-op :>= 1)))))

  (testing "count = 0 simplifies to not exists"
    (let [count-op (coll-ops/get-collection-op :count)]
      (is (= :simplify-to-not-exists
             (coll-ops/simplify-comparison count-op := 0)))))

  (testing "other comparisons don't simplify"
    (let [count-op (coll-ops/get-collection-op :count)]
      (is (nil? (coll-ops/simplify-comparison count-op :> 5))))))
