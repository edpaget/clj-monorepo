(ns polix.negate-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.ast :as ast]
   [polix.negate :as neg]
   [polix.parser :as parser]
   [polix.result :as r]))

;;; ---------------------------------------------------------------------------
;;; Test Helpers
;;; ---------------------------------------------------------------------------

(defn parse!
  "Parses a policy expression, throwing on error."
  [expr]
  (let [result (parser/parse-policy expr)]
    (if (r/error? result)
      (throw (ex-info "Parse failed" (r/unwrap result)))
      (r/unwrap result))))

;;; ---------------------------------------------------------------------------
;;; Comparison Operator Negation
;;; ---------------------------------------------------------------------------

(deftest negate-equality-test
  (testing "negating := produces :!="
    (let [ast     (parse! [:= :doc/role "admin"])
          negated (neg/negate ast)]
      (is (= ::ast/function-call (:type negated)))
      (is (= :!= (:value negated)))))

  (testing "negating :!= produces :="
    (let [ast     (parse! [:!= :doc/role "admin"])
          negated (neg/negate ast)]
      (is (= := (:value negated))))))

(deftest negate-comparison-test
  (testing "negating :> produces :<="
    (let [ast     (parse! [:> :doc/level 5])
          negated (neg/negate ast)]
      (is (= :<= (:value negated)))))

  (testing "negating :>= produces :<"
    (let [ast     (parse! [:>= :doc/level 5])
          negated (neg/negate ast)]
      (is (= :< (:value negated)))))

  (testing "negating :< produces :>="
    (let [ast     (parse! [:< :doc/level 5])
          negated (neg/negate ast)]
      (is (= :>= (:value negated)))))

  (testing "negating :<= produces :>"
    (let [ast     (parse! [:<= :doc/level 5])
          negated (neg/negate ast)]
      (is (= :> (:value negated))))))

(deftest negate-set-membership-test
  (testing "negating :in produces :not-in"
    (let [ast     (parse! [:in :doc/status #{"active" "pending"}])
          negated (neg/negate ast)]
      (is (= :not-in (:value negated)))))

  (testing "negating :not-in produces :in"
    (let [ast     (parse! [:not-in :doc/status #{"banned"}])
          negated (neg/negate ast)]
      (is (= :in (:value negated))))))

(deftest negate-pattern-test
  (testing "negating :matches produces :not-matches"
    (let [ast     (parse! [:matches :doc/email ".*@example.com"])
          negated (neg/negate ast)]
      (is (= :not-matches (:value negated)))))

  (testing "negating :not-matches produces :matches"
    (let [ast     (parse! [:not-matches :doc/email ".*@spam.com"])
          negated (neg/negate ast)]
      (is (= :matches (:value negated))))))

;;; ---------------------------------------------------------------------------
;;; De Morgan's Laws
;;; ---------------------------------------------------------------------------

(deftest negate-and-test
  (testing "negating :and produces :or with negated children"
    (let [ast     (parse! [:and [:= :doc/x 1] [:= :doc/y 2]])
          negated (neg/negate ast)]
      (is (= :or (:value negated)))
      (is (= 2 (count (:children negated))))
      (is (= :!= (:value (first (:children negated)))))
      (is (= :!= (:value (second (:children negated))))))))

(deftest negate-or-test
  (testing "negating :or produces :and with negated children"
    (let [ast     (parse! [:or [:= :doc/x 1] [:= :doc/y 2]])
          negated (neg/negate ast)]
      (is (= :and (:value negated)))
      (is (= 2 (count (:children negated))))
      (is (= :!= (:value (first (:children negated)))))
      (is (= :!= (:value (second (:children negated))))))))

(deftest negate-nested-boolean-test
  (testing "nested De Morgan: not(A and (B or C)) = (not A) or ((not B) and (not C))"
    (let [ast     (parse! [:and [:= :doc/a 1]
                           [:or [:= :doc/b 2] [:= :doc/c 3]]])
          negated (neg/negate ast)]
      ;; Should be :or
      (is (= :or (:value negated)))
      ;; First child: not(A) = [:!= :doc/a 1]
      (is (= :!= (:value (first (:children negated)))))
      ;; Second child: not(B or C) = :and with negated B and C
      (let [second-child (second (:children negated))]
        (is (= :and (:value second-child)))
        (is (= :!= (:value (first (:children second-child)))))
        (is (= :!= (:value (second (:children second-child)))))))))

;;; ---------------------------------------------------------------------------
;;; Double Negation Elimination
;;; ---------------------------------------------------------------------------

(deftest negate-not-test
  (testing "negating :not eliminates the :not (double negation)"
    (let [ast     (parse! [:not [:= :doc/x 1]])
          negated (neg/negate ast)]
      ;; Should unwrap to the inner expression
      (is (= ::ast/function-call (:type negated)))
      (is (= := (:value negated))))))

(deftest negate-double-not-test
  (testing "not(not(not A)) = not A"
    (let [ast     (parse! [:not [:not [:not [:= :doc/x 1]]]])
          negated (neg/negate ast)]
      ;; First negate removes outer :not, leaving [:not [:= ...]]
      ;; But we only negate once, so result is [:not [:= :doc/x 1]]
      (is (= :not (:value negated))))))

;;; ---------------------------------------------------------------------------
;;; Quantifier Negation
;;; ---------------------------------------------------------------------------

(deftest negate-forall-test
  (testing "negating :forall produces :exists with negated body"
    (let [ast     (parse! [:forall [:u :doc/users] [:= :u/active true]])
          negated (neg/negate ast)]
      (is (= ::ast/quantifier (:type negated)))
      (is (= :exists (:value negated)))
      ;; Body should be negated
      (let [body (first (:children negated))]
        (is (= :!= (:value body))))))

  (testing "binding is preserved"
    (let [ast     (parse! [:forall [:u :doc/users] [:= :u/active true]])
          negated (neg/negate ast)]
      (is (= :u (get-in negated [:metadata :binding :name])))
      (is (= [:users] (get-in negated [:metadata :binding :path]))))))

(deftest negate-exists-test
  (testing "negating :exists produces :forall with negated body"
    (let [ast     (parse! [:exists [:u :doc/users] [:= :u/role "admin"]])
          negated (neg/negate ast)]
      (is (= ::ast/quantifier (:type negated)))
      (is (= :forall (:value negated)))
      (let [body (first (:children negated))]
        (is (= :!= (:value body)))))))

(deftest negate-nested-quantifiers-test
  (testing "nested quantifier negation"
    (let [ast     (parse! [:forall [:t :doc/teams]
                           [:exists [:m :t/members]
                            [:= :m/role "lead"]]])
          negated (neg/negate ast)]
      ;; forall -> exists
      (is (= :exists (:value negated)))
      ;; inner exists -> forall with negated body
      (let [inner (first (:children negated))]
        (is (= :forall (:value inner)))
        (is (= :!= (:value (first (:children inner)))))))))

(deftest negate-quantifier-with-filter-test
  (testing "quantifier with :where filter is preserved"
    (let [ast     (parse! [:forall [:u :doc/users :where [:= :u/active true]]
                           [:> :u/score 50]])
          negated (neg/negate ast)]
      (is (= :exists (:value negated)))
      ;; Filter should be preserved (not negated)
      (is (some? (get-in negated [:metadata :binding :where])))
      ;; Body should be negated
      (is (= :<= (:value (first (:children negated))))))))

;;; ---------------------------------------------------------------------------
;;; Complex Markers
;;; ---------------------------------------------------------------------------

(deftest complex-marker-test
  (testing "complex marker creation"
    (let [original (parse! [:= :doc/x 1])
          marker   (neg/complex-marker original :test-reason)]
      (is (neg/complex? marker))
      (is (= :test-reason (get-in marker [:value :reason])))
      (is (= original (get-in marker [:value :original]))))))

(deftest negate-literal-produces-complex-test
  (testing "negating a bare literal produces complex marker"
    (let [ast (ast/ast-node ::ast/literal 42 [0 0])]
      (is (neg/complex? (neg/negate ast))))))

(deftest negate-accessor-produces-complex-test
  (testing "negating a bare accessor produces complex marker"
    (let [ast (ast/ast-node ::ast/doc-accessor [:role] [0 0])]
      (is (neg/complex? (neg/negate ast))))))

(deftest negate-value-fn-produces-complex-test
  (testing "negating a value function produces complex marker"
    (let [ast     (parse! [:fn/count :doc/users])
          ;; Note: [:fn/count ...] produces a value-fn node, not a function-call
          ;; Actually let me check what type this produces
          negated (neg/negate ast)]
      (is (neg/complex? negated)))))

(deftest unknown-operator-produces-complex-test
  (testing "unknown operator produces complex marker"
    (let [ast (ast/ast-node ::ast/function-call
                            :unknown-op
                            [0 0]
                            [(ast/ast-node ::ast/literal 1 [0 1])])]
      (is (neg/complex? (neg/negate ast))))))

;;; ---------------------------------------------------------------------------
;;; Utility Functions
;;; ---------------------------------------------------------------------------

(deftest has-complex?-test
  (testing "returns false for simple negatable policies"
    (let [ast (parse! [:and [:= :doc/x 1] [:> :doc/y 2]])]
      (is (false? (neg/has-complex? (neg/negate ast))))))

  (testing "returns true when complex marker present"
    (let [ast (ast/ast-node ::ast/function-call
                            :unknown-op
                            [0 0]
                            [(ast/ast-node ::ast/literal 1 [0 1])])]
      (is (true? (neg/has-complex? (neg/negate ast)))))))

(deftest double-negate-test
  (testing "double negation of simple equality"
    (let [ast            (parse! [:= :doc/x 1])
          double-negated (neg/double-negate ast)]
      ;; Should return to :=
      (is (= := (:value double-negated)))))

  (testing "double negation of :and"
    (let [ast            (parse! [:and [:= :doc/x 1] [:= :doc/y 2]])
          double-negated (neg/double-negate ast)]
      ;; and -> or -> and
      (is (= :and (:value double-negated)))))

  (testing "double negation of quantifier"
    (let [ast            (parse! [:forall [:u :doc/users] [:= :u/x 1]])
          double-negated (neg/double-negate ast)]
      ;; forall -> exists -> forall
      (is (= :forall (:value double-negated))))))

;;; ---------------------------------------------------------------------------
;;; Negation Preserves Structure
;;; ---------------------------------------------------------------------------

(deftest negation-preserves-children-count-test
  (testing ":and with 3 children produces :or with 3 children"
    (let [ast     (parse! [:and [:= :doc/a 1] [:= :doc/b 2] [:= :doc/c 3]])
          negated (neg/negate ast)]
      (is (= 3 (count (:children negated))))))

  (testing ":or with 4 children produces :and with 4 children"
    (let [ast     (parse! [:or [:= :doc/a 1] [:= :doc/b 2] [:= :doc/c 3] [:= :doc/d 4]])
          negated (neg/negate ast)]
      (is (= 4 (count (:children negated)))))))

(deftest negation-preserves-position-test
  (testing "negated node preserves position"
    (let [ast     (parse! [:= :doc/x 1])
          negated (neg/negate ast)]
      (is (= (:position ast) (:position negated))))))

(deftest negation-preserves-children-values-test
  (testing "comparison operands are preserved"
    (let [ast     (parse! [:> :doc/level 42])
          negated (neg/negate ast)]
      ;; Children should be identical (accessor and literal)
      (is (= (:children ast) (:children negated))))))
