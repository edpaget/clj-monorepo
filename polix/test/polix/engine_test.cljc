(ns polix.engine-test
  "Tests for the unified evaluation engine.

  Includes equivalence tests validating that the engine and compiler
  produce consistent results for the same inputs."
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.compiler :as compiler]
   [polix.engine :as engine]
   [polix.parser :as parser]
   [polix.result :as r]))

;;; ---------------------------------------------------------------------------
;;; Result Type Tests
;;; ---------------------------------------------------------------------------

(deftest residual?-test
  (testing "residual? identifies residual results"
    (is (engine/residual? {:residual {:role [[:= "admin"]]}}))
    (is (not (engine/residual? true)))
    (is (not (engine/residual? false)))
    (is (not (engine/residual? {:other "map"})))))

(deftest complex?-test
  (testing "complex? identifies complex results"
    (is (engine/complex? {:complex {:op :or :children []}}))
    (is (not (engine/complex? true)))
    (is (not (engine/complex? {:residual {}})))))

(deftest result-type-test
  (testing "result-type returns correct type"
    (is (= :satisfied (engine/result-type true)))
    (is (= :contradicted (engine/result-type false)))
    (is (= :residual (engine/result-type {:residual {}})))
    (is (= :complex (engine/result-type {:complex {}})))))

;;; ---------------------------------------------------------------------------
;;; Boolean Logic Tests
;;; ---------------------------------------------------------------------------

(deftest eval-and-test
  (testing "AND with all true"
    (is (= true (engine/eval-and [true true true]))))
  (testing "AND with any false"
    (is (= false (engine/eval-and [true false true]))))
  (testing "AND with residuals"
    (let [result (engine/eval-and [true {:residual {:a [[1]]}}])]
      (is (engine/residual? result)))))

(deftest eval-or-test
  (testing "OR with any true"
    (is (= true (engine/eval-or [false true false]))))
  (testing "OR with all false"
    (is (= false (engine/eval-or [false false false]))))
  (testing "OR with single residual"
    (let [result (engine/eval-or [false {:residual {:a [[1]]}}])]
      (is (engine/residual? result)))))

(deftest eval-not-test
  (testing "NOT true"
    (is (= false (engine/eval-not true))))
  (testing "NOT false"
    (is (= true (engine/eval-not false))))
  (testing "NOT residual"
    (let [result (engine/eval-not {:residual {:a [[1]]}})]
      (is (engine/complex? result)))))

;;; ---------------------------------------------------------------------------
;;; Constraint Evaluation Tests
;;; ---------------------------------------------------------------------------

(deftest evaluate-constraint-set-test
  (testing "simple equality satisfied"
    (let [constraint-set {:role [{:op := :value "admin"}]}]
      (is (= true (engine/evaluate-constraint-set constraint-set {:role "admin"})))))
  (testing "simple equality contradicted"
    (let [constraint-set {:role [{:op := :value "admin"}]}]
      (is (= false (engine/evaluate-constraint-set constraint-set {:role "guest"})))))
  (testing "missing key returns residual"
    (let [constraint-set {:role [{:op := :value "admin"}]}
          result         (engine/evaluate-constraint-set constraint-set {})]
      (is (engine/residual? result))
      (is (= {:role [[:= "admin"]]} (:residual result))))))

;;; ---------------------------------------------------------------------------
;;; AST Evaluation Tests
;;; ---------------------------------------------------------------------------

(deftest eval-ast-literal-test
  (testing "literal evaluation"
    (let [ast (r/unwrap (parser/parse-policy "hello"))]
      (is (= "hello" (engine/evaluate ast {}))))))

(deftest eval-ast-doc-accessor-test
  (testing "doc accessor with value present"
    (let [ast (r/unwrap (parser/parse-policy :doc/role))]
      (is (= "admin" (engine/evaluate ast {:role "admin"})))))
  (testing "doc accessor with missing key"
    (let [ast    (r/unwrap (parser/parse-policy :doc/role))
          result (engine/evaluate ast {})]
      (is (engine/residual? result)))))

(deftest eval-ast-function-call-test
  (testing "equality comparison satisfied"
    (let [ast (r/unwrap (parser/parse-policy [:= :doc/role "admin"]))]
      (is (= true (engine/evaluate ast {:role "admin"})))))
  (testing "equality comparison contradicted"
    (let [ast (r/unwrap (parser/parse-policy [:= :doc/role "admin"]))]
      (is (= false (engine/evaluate ast {:role "guest"})))))
  (testing "comparison with missing key"
    (let [ast    (r/unwrap (parser/parse-policy [:= :doc/role "admin"]))
          result (engine/evaluate ast {})]
      (is (engine/residual? result)))))

(deftest eval-ast-and-test
  (testing "AND all satisfied"
    (let [ast (r/unwrap (parser/parse-policy [:and [:= :doc/a 1] [:= :doc/b 2]]))]
      (is (= true (engine/evaluate ast {:a 1 :b 2})))))
  (testing "AND one contradicted"
    (let [ast (r/unwrap (parser/parse-policy [:and [:= :doc/a 1] [:= :doc/b 2]]))]
      (is (= false (engine/evaluate ast {:a 1 :b 999})))))
  (testing "AND partial residual"
    (let [ast    (r/unwrap (parser/parse-policy [:and [:= :doc/a 1] [:= :doc/b 2]]))
          result (engine/evaluate ast {:a 1})]
      (is (engine/residual? result)))))

(deftest eval-ast-or-test
  (testing "OR one satisfied"
    (let [ast (r/unwrap (parser/parse-policy [:or [:= :doc/a 1] [:= :doc/a 2]]))]
      (is (= true (engine/evaluate ast {:a 1})))))
  (testing "OR all contradicted"
    (let [ast (r/unwrap (parser/parse-policy [:or [:= :doc/a 1] [:= :doc/a 2]]))]
      (is (= false (engine/evaluate ast {:a 999}))))))

(deftest eval-ast-not-test
  (testing "NOT true becomes false"
    (let [ast (r/unwrap (parser/parse-policy [:not [:= :doc/a 1]]))]
      (is (= false (engine/evaluate ast {:a 1})))))
  (testing "NOT false becomes true"
    (let [ast (r/unwrap (parser/parse-policy [:not [:= :doc/a 1]]))]
      (is (= true (engine/evaluate ast {:a 2}))))))

;;; ---------------------------------------------------------------------------
;;; Equivalence Tests: Engine vs Compiler
;;; ---------------------------------------------------------------------------

(deftest equivalence-simple-equality-test
  (testing "simple equality produces same results"
    (let [expr     [:= :doc/role "admin"]
          compiled (compiler/compile-policies [expr])
          ast      (r/unwrap (parser/parse-policy expr))]
      (is (= true (compiled {:role "admin"})))
      (is (= true (engine/evaluate ast {:role "admin"})))
      (is (= false (compiled {:role "guest"})))
      (is (= false (engine/evaluate ast {:role "guest"})))
      (is (engine/residual? (compiled {})))
      (is (engine/residual? (engine/evaluate ast {}))))))

(deftest equivalence-comparison-test
  (testing "comparison operators produce same results"
    (doseq [op [:> :< :>= :<=]]
      (let [expr     [op :doc/level 5]
            compiled (compiler/compile-policies [expr])
            ast      (r/unwrap (parser/parse-policy expr))
            doc-high {:level 10}
            doc-low  {:level 2}
            doc-eq   {:level 5}]
        (is (= (compiled doc-high) (engine/evaluate ast doc-high))
            (str "High value mismatch for " op))
        (is (= (compiled doc-low) (engine/evaluate ast doc-low))
            (str "Low value mismatch for " op))
        (is (= (compiled doc-eq) (engine/evaluate ast doc-eq))
            (str "Equal value mismatch for " op))))))

(deftest equivalence-set-membership-test
  (testing "set membership produces same results"
    (let [expr     [:in :doc/status #{"active" "pending"}]
          compiled (compiler/compile-policies [expr])
          ast      (r/unwrap (parser/parse-policy expr))]
      (is (= true (compiled {:status "active"})))
      (is (= true (engine/evaluate ast {:status "active"})))
      (is (= false (compiled {:status "closed"})))
      (is (= false (engine/evaluate ast {:status "closed"})))
      (is (engine/residual? (compiled {})))
      (is (engine/residual? (engine/evaluate ast {}))))))

(deftest equivalence-multiple-constraints-test
  (testing "multiple constraints produce same residual structure"
    (let [expr1    [:= :doc/role "admin"]
          expr2    [:> :doc/level 5]
          compiled (compiler/compile-policies [expr1 expr2])
          ast      (r/unwrap (parser/parse-policy [:and expr1 expr2]))]
      (is (= true (compiled {:role "admin" :level 10})))
      (is (= true (engine/evaluate ast {:role "admin" :level 10})))
      (is (= false (compiled {:role "guest" :level 10})))
      (is (= false (engine/evaluate ast {:role "guest" :level 10})))
      (let [compiled-result (compiled {:role "admin"})
            engine-result   (engine/evaluate ast {:role "admin"})]
        (is (engine/residual? compiled-result))
        (is (engine/residual? engine-result))))))

(deftest equivalence-contradiction-test
  (testing "contradictions produce false"
    (let [compiled (compiler/compile-policies [[:= :doc/a 1] [:= :doc/a 2]])]
      (is (= false (compiled {:a 1})))
      (is (= false (compiled {:a 2})))
      (is (= false (compiled {}))))))

;;; ---------------------------------------------------------------------------
;;; Residual Conversion Tests
;;; ---------------------------------------------------------------------------

(deftest residual->constraints-test
  (testing "converts residual to policy expressions"
    (let [residual    {:level [[:> 5]] :status [[:in #{"a" "b"}]]}
          constraints (engine/residual->constraints residual)]
      (is (= 2 (count constraints)))
      (is (some #(= [:> :doc/level 5] %) constraints))
      (is (some #(= [:in :doc/status #{"a" "b"}] %) constraints)))))

(deftest result->policy-test
  (testing "true returns nil"
    (is (nil? (engine/result->policy true))))
  (testing "false returns contradiction"
    (is (= [:contradiction] (engine/result->policy false))))
  (testing "single residual returns constraint"
    (is (= [:> :doc/level 5]
           (engine/result->policy {:residual {:level [[:> 5]]}})))))
