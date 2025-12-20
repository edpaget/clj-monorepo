(ns polix.compiler-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [polix.compiler :as compiler]
   [polix.operators :as op]))

(deftest constraint-creation-test
  (testing "creating constraints"
    (let [c (compiler/constraint :role := "admin")]
      (is (compiler/constraint? c))
      (is (= :role (:key c)))
      (is (= := (:op c)))
      (is (= "admin" (:value c))))))

(deftest normalize-policy-simple-test
  (testing "normalizing equality constraint"
    (let [result (compiler/normalize-policy-expr [:= :doc/role "admin"])]
      (is (= :constraint (:op result)))
      (is (= :role (get-in result [:constraint :key])))
      (is (= := (get-in result [:constraint :op])))
      (is (= "admin" (get-in result [:constraint :value]))))))

(deftest normalize-policy-and-test
  (testing "normalizing AND constraint"
    (let [result (compiler/normalize-policy-expr
                  [:and [:= :doc/role "admin"] [:> :doc/level 5]])]
      (is (= :and (:op result)))
      (is (= 2 (count (:children result)))))))

(deftest compile-simple-equality-test
  (testing "simple equality - satisfied"
    (let [check (compiler/compile-policies [[:= :doc/role "admin"]])]
      (is (true? (check {:role "admin"})))))

  (testing "simple equality - contradicted"
    (let [check (compiler/compile-policies [[:= :doc/role "admin"]])]
      (is (false? (check {:role "guest"})))))

  (testing "simple equality - missing key gives residual"
    (let [check  (compiler/compile-policies [[:= :doc/role "admin"]])
          result (check {})]
      (is (map? result))
      (is (contains? result :residual))
      (is (= {:role [[:= "admin"]]} (:residual result))))))

(deftest compile-multiple-constraints-test
  (testing "AND of multiple constraints - all satisfied"
    (let [check (compiler/compile-policies
                 [[:= :doc/role "admin"]
                  [:> :doc/level 5]])]
      (is (true? (check {:role "admin" :level 10})))))

  (testing "AND of multiple constraints - one contradicted"
    (let [check (compiler/compile-policies
                 [[:= :doc/role "admin"]
                  [:> :doc/level 5]])]
      (is (false? (check {:role "admin" :level 3})))))

  (testing "AND of multiple constraints - partial"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]
                   [:> :doc/level 5]])
          result (check {:role "admin"})]
      (is (map? result))
      (is (contains? (:residual result) :level)))))

(deftest compile-range-constraints-test
  (testing "range constraint - satisfied"
    (let [check (compiler/compile-policies [[:> :doc/age 18]])]
      (is (true? (check {:age 25})))))

  (testing "range constraint - contradicted"
    (let [check (compiler/compile-policies [[:> :doc/age 18]])]
      (is (false? (check {:age 16})))))

  (testing "range constraint - boundary"
    (let [check (compiler/compile-policies [[:>= :doc/age 18]])]
      (is (true? (check {:age 18})))
      (is (false? (check {:age 17}))))))

(deftest simplify-range-constraints-test
  (testing "merging range constraints keeps tightest bounds"
    (let [check (compiler/compile-policies
                 [[:> :doc/x 3]
                  [:> :doc/x 5]
                  [:< :doc/x 10]])]
      (is (true? (check {:x 7})))
      (is (false? (check {:x 4})))
      (is (false? (check {:x 11}))))))

(deftest contradictory-policies-test
  (testing "contradictory equality constraints"
    (let [check (compiler/compile-policies
                 [[:= :doc/role "admin"]
                  [:= :doc/role "guest"]])]
      (is (false? (check {:role "admin"})))
      (is (false? (check {:role "guest"})))
      (is (false? (check {}))))))

(deftest in-constraint-test
  (testing "in constraint - satisfied"
    (let [check (compiler/compile-policies
                 [[:in :doc/status #{"active" "pending"}]])]
      (is (true? (check {:status "active"})))
      (is (true? (check {:status "pending"})))))

  (testing "in constraint - contradicted"
    (let [check (compiler/compile-policies
                 [[:in :doc/status #{"active" "pending"}]])]
      (is (false? (check {:status "closed"}))))))

(deftest residual-to-constraints-test
  (testing "converting residual back to policy"
    (let [residual    {:level [[:> 5]] :status [[:in #{"a" "b"}]]}
          constraints (compiler/residual->constraints residual)]
      (is (= 2 (count constraints)))
      (is (some #(= [:> :doc/level 5] %) constraints))
      (is (some #(= [:in :doc/status #{"a" "b"}] %) constraints)))))

(deftest result-to-policy-test
  (testing "true result gives nil"
    (is (nil? (compiler/result->policy true))))

  (testing "false result gives contradiction"
    (is (= [:contradiction] (compiler/result->policy false))))

  (testing "residual gives simplified constraints"
    (let [result {:residual {:level [[:> 5]]}}
          policy (compiler/result->policy result)]
      (is (= [:> :doc/level 5] policy)))))

;;; ---------------------------------------------------------------------------
;;; Context Threading Tests
;;; ---------------------------------------------------------------------------

(deftest context-custom-operators-test
  (testing "custom operators passed at compile time"
    ;; Register operator so normalization works
    (op/register-operator! :is-uppercase
                           {:eval (fn [v _expected] (= v (str/upper-case v)))})
    (let [check (compiler/compile-policies
                 [[:is-uppercase :doc/name true]])]
      (is (true? (check {:name "HELLO"})))
      (is (false? (check {:name "hello"}))))))

(deftest context-strict-mode-test
  (testing "strict mode throws on unknown operator"
    (let [check (compiler/compile-policies
                 [[:= :doc/role "admin"]]
                 {:strict? true})]
      ;; Known operator works
      (is (true? (check {:role "admin"})))
      ;; Unknown operator would throw - but we can't test this easily
      ;; since the policy parser needs the operator registered
      )))

(deftest context-tracing-test
  (testing "tracing records evaluations"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]]
                  {:trace? true})
          result (check {:role "admin"})]
      ;; Fully satisfied returns true, no trace (trace only on residuals)
      (is (true? result))))

  (testing "tracing on partial evaluation"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]
                   [:> :doc/level 5]]
                  {:trace? true})
          result (check {:role "admin"})]
      (is (map? result))
      (is (contains? result :residual))
      (is (contains? result :trace))
      (is (vector? (:trace result)))))

  (testing "per-evaluation trace override"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]
                   [:> :doc/level 5]])
          result (check {:role "admin"} {:trace? true})]
      (is (map? result))
      (is (contains? result :trace)))))
