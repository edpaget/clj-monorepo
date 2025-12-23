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
    (let [constraint-set {[:role] [{:op := :value "admin"}]}]
      (is (= true (engine/evaluate-constraint-set constraint-set {:role "admin"})))))
  (testing "simple equality contradicted"
    (let [constraint-set {[:role] [{:op := :value "admin"}]}]
      (is (= false (engine/evaluate-constraint-set constraint-set {:role "guest"})))))
  (testing "missing key returns residual"
    (let [constraint-set {[:role] [{:op := :value "admin"}]}
          result         (engine/evaluate-constraint-set constraint-set {})]
      (is (engine/residual? result))
      (is (= {[:role] [[:= "admin"]]} (:residual result))))))

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
;;; Note: These tests use compile-policies-legacy to compare with engine,
;;; since engine uses the old true/false/{:residual} format.

(deftest equivalence-simple-equality-test
  (testing "simple equality produces same results"
    (let [expr     [:= :doc/role "admin"]
          compiled (compiler/compile-policies-legacy [expr])
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
            compiled (compiler/compile-policies-legacy [expr])
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
          compiled (compiler/compile-policies-legacy [expr])
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
          compiled (compiler/compile-policies-legacy [expr1 expr2])
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
    (let [compiled (compiler/compile-policies-legacy [[:= :doc/a 1] [:= :doc/a 2]])]
      (is (= false (compiled {:a 1})))
      (is (= false (compiled {:a 2})))
      (is (= false (compiled {}))))))

;;; ---------------------------------------------------------------------------
;;; Residual Conversion Tests
;;; ---------------------------------------------------------------------------

(deftest residual->constraints-test
  (testing "converts residual to policy expressions"
    (let [residual    {[:level] [[:> 5]] [:status] [[:in #{"a" "b"}]]}
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
           (engine/result->policy {:residual {[:level] [[:> 5]]}})))))

;;; ---------------------------------------------------------------------------
;;; Path Utility Tests
;;; ---------------------------------------------------------------------------

(deftest path-exists?-test
  (testing "shallow path exists"
    (is (engine/path-exists? {:role "admin"} [:role])))
  (testing "nested path exists"
    (is (engine/path-exists? {:user {:name "Alice"}} [:user :name])))
  (testing "deeply nested path exists"
    (is (engine/path-exists? {:a {:b {:c "value"}}} [:a :b :c])))
  (testing "partial path - missing leaf"
    (is (not (engine/path-exists? {:user {}} [:user :name]))))
  (testing "missing root"
    (is (not (engine/path-exists? {} [:user :name]))))
  (testing "nil value counts as exists"
    (is (engine/path-exists? {:user {:name nil}} [:user :name])))
  (testing "false value counts as exists"
    (is (engine/path-exists? {:active false} [:active])))
  (testing "non-map at intermediate path"
    (is (not (engine/path-exists? {:user "string"} [:user :name])))))

(deftest path->doc-accessor-test
  (testing "single element path"
    (is (= :doc/role (engine/path->doc-accessor [:role]))))
  (testing "two element path"
    (is (= :doc/user.name (engine/path->doc-accessor [:user :name]))))
  (testing "deep path"
    (is (= :doc/a.b.c.d (engine/path->doc-accessor [:a :b :c :d])))))

;;; ---------------------------------------------------------------------------
;;; Nested Path Evaluation Tests
;;; ---------------------------------------------------------------------------

(deftest eval-nested-doc-accessor-test
  (testing "nested access returns value"
    (let [ast (r/unwrap (parser/parse-policy :doc/user.name))]
      (is (= "Alice" (engine/evaluate ast {:user {:name "Alice"}})))))
  (testing "deeply nested access"
    (let [ast (r/unwrap (parser/parse-policy :doc/a.b.c))]
      (is (= "deep" (engine/evaluate ast {:a {:b {:c "deep"}}})))))
  (testing "partial path returns residual"
    (let [ast    (r/unwrap (parser/parse-policy :doc/user.name))
          result (engine/evaluate ast {:user {}})]
      (is (engine/residual? result))
      (is (= {[:user :name] [[:any]]} (:residual result)))))
  (testing "missing root returns residual"
    (let [ast    (r/unwrap (parser/parse-policy :doc/user.name))
          result (engine/evaluate ast {})]
      (is (engine/residual? result)))))

(deftest eval-nested-comparison-test
  (testing "nested equality satisfied"
    (let [ast (r/unwrap (parser/parse-policy [:= :doc/user.role "admin"]))]
      (is (= true (engine/evaluate ast {:user {:role "admin"}})))))
  (testing "nested equality contradicted"
    (let [ast (r/unwrap (parser/parse-policy [:= :doc/user.role "admin"]))]
      (is (= false (engine/evaluate ast {:user {:role "guest"}})))))
  (testing "nested equality residual"
    (let [ast    (r/unwrap (parser/parse-policy [:= :doc/user.role "admin"]))
          result (engine/evaluate ast {:user {}})]
      (is (engine/residual? result))
      (is (= {[:user :role] [[:= "admin"]]} (:residual result)))))
  (testing "nested comparison with missing root"
    (let [ast    (r/unwrap (parser/parse-policy [:> :doc/user.level 5]))
          result (engine/evaluate ast {})]
      (is (engine/residual? result)))))

(deftest eval-nested-and-test
  (testing "AND with multiple nested paths"
    (let [ast (r/unwrap (parser/parse-policy
                         [:and
                          [:= :doc/user.role "admin"]
                          [:= :doc/user.active true]]))]
      (is (= true (engine/evaluate ast {:user {:role "admin" :active true}})))
      (is (= false (engine/evaluate ast {:user {:role "guest" :active true}})))))
  (testing "AND with partial nested data"
    (let [ast    (r/unwrap (parser/parse-policy
                            [:and
                             [:= :doc/user.role "admin"]
                             [:= :doc/user.level 5]]))
          result (engine/evaluate ast {:user {:role "admin"}})]
      (is (engine/residual? result)))))

;;; ---------------------------------------------------------------------------
;;; Residual Conversion with Paths
;;; ---------------------------------------------------------------------------

(deftest residual->constraints-nested-test
  (testing "converts nested path residual"
    (let [residual    {[:user :role] [[:= "admin"]]}
          constraints (engine/residual->constraints residual)]
      (is (= [[:= :doc/user.role "admin"]] constraints))))
  (testing "converts multiple nested paths"
    (let [residual    {[:user :role] [[:= "admin"]]
                       [:user :level] [[:> 5]]}
          constraints (engine/residual->constraints residual)]
      (is (= 2 (count constraints)))
      (is (some #(= [:= :doc/user.role "admin"] %) constraints))
      (is (some #(= [:> :doc/user.level 5] %) constraints)))))

;;; ---------------------------------------------------------------------------
;;; Binding Context Tests
;;; ---------------------------------------------------------------------------

(deftest with-binding-test
  (testing "adds binding to context"
    (let [ctx  {}
          ctx' (engine/with-binding ctx :u {:name "Alice"})]
      (is (= {:name "Alice"} (engine/get-binding ctx' :u))))))

(deftest get-binding-test
  (testing "returns nil for missing binding"
    (is (nil? (engine/get-binding {} :u))))
  (testing "returns bound value"
    (let [ctx (engine/with-binding {} :u {:role "admin"})]
      (is (= {:role "admin"} (engine/get-binding ctx :u))))))

;;; ---------------------------------------------------------------------------
;;; Forall Evaluation Tests
;;; ---------------------------------------------------------------------------

(deftest forall-all-satisfy-test
  (testing "all elements satisfy - returns true"
    (is (= true (engine/evaluate
                 [:forall [:u :doc/users] [:= :u/role "admin"]]
                 {:users [{:role "admin"} {:role "admin"}]})))))

(deftest forall-one-contradicts-test
  (testing "one element contradicts - returns false"
    (is (= false (engine/evaluate
                  [:forall [:u :doc/users] [:= :u/role "admin"]]
                  {:users [{:role "admin"} {:role "guest"}]})))))

(deftest forall-empty-collection-test
  (testing "empty collection - returns true (vacuous truth)"
    (is (= true (engine/evaluate
                 [:forall [:u :doc/users] [:= :u/role "admin"]]
                 {:users []})))))

(deftest forall-missing-collection-test
  (testing "missing collection - returns residual"
    (let [result (engine/evaluate
                  [:forall [:u :doc/users] [:= :u/role "admin"]]
                  {})]
      (is (engine/residual? result))
      (is (contains? (:residual result) [:users])))))

(deftest forall-non-sequential-test
  (testing "non-sequential collection - returns false"
    (is (= false (engine/evaluate
                  [:forall [:u :doc/users] [:= :u/role "admin"]]
                  {:users "not-a-list"})))))

(deftest forall-partial-elements-test
  (testing "partial elements - returns residual with indices"
    (let [result (engine/evaluate
                  [:forall [:u :doc/users] [:= :u/role "admin"]]
                  {:users [{:role "admin"} {:name "Bob"}]})]
      (is (engine/residual? result))
      (is (contains? (:residual result) [:users 1 :role])))))

(deftest forall-nested-path-test
  (testing "forall with nested path access in body"
    (is (= true (engine/evaluate
                 [:forall [:u :doc/users] [:= :u/profile.verified true]]
                 {:users [{:profile {:verified true}}
                          {:profile {:verified true}}]})))))

;;; ---------------------------------------------------------------------------
;;; Exists Evaluation Tests
;;; ---------------------------------------------------------------------------

(deftest exists-one-satisfies-test
  (testing "one element satisfies - returns true"
    (is (= true (engine/evaluate
                 [:exists [:u :doc/users] [:= :u/role "admin"]]
                 {:users [{:role "guest"} {:role "admin"}]})))))

(deftest exists-none-satisfy-test
  (testing "no elements satisfy - returns false"
    (is (= false (engine/evaluate
                  [:exists [:u :doc/users] [:= :u/role "admin"]]
                  {:users [{:role "guest"} {:role "user"}]})))))

(deftest exists-empty-collection-test
  (testing "empty collection - returns false"
    (is (= false (engine/evaluate
                  [:exists [:u :doc/users] [:= :u/role "admin"]]
                  {:users []})))))

(deftest exists-missing-collection-test
  (testing "missing collection - returns residual"
    (let [result (engine/evaluate
                  [:exists [:u :doc/users] [:= :u/role "admin"]]
                  {})]
      (is (engine/residual? result)))))

(deftest exists-non-sequential-test
  (testing "non-sequential collection - returns false"
    (is (= false (engine/evaluate
                  [:exists [:u :doc/users] [:= :u/role "admin"]]
                  {:users "not-a-list"})))))

(deftest exists-partial-elements-test
  (testing "some residual, none true - returns residual"
    (let [result (engine/evaluate
                  [:exists [:u :doc/users] [:= :u/role "admin"]]
                  {:users [{:name "Bob"} {:name "Alice"}]})]
      (is (engine/residual? result)))))

(deftest exists-short-circuit-test
  (testing "short-circuits on first true"
    (is (= true (engine/evaluate
                 [:exists [:u :doc/users] [:= :u/role "admin"]]
                 {:users [{:role "admin"} {:name "missing-role"}]})))))

;;; ---------------------------------------------------------------------------
;;; Nested Quantifiers Tests
;;; ---------------------------------------------------------------------------

(deftest nested-forall-exists-satisfied-test
  (testing "every team has at least one lead - satisfied"
    (is (= true (engine/evaluate
                 [:forall [:team :doc/teams]
                  [:exists [:member :team/members]
                   [:= :member/role "lead"]]]
                 {:teams [{:members [{:role "lead"}]}
                          {:members [{:role "dev"} {:role "lead"}]}]})))))

(deftest nested-forall-exists-contradicted-test
  (testing "one team has no lead - contradicted"
    (is (= false (engine/evaluate
                  [:forall [:team :doc/teams]
                   [:exists [:member :team/members]
                    [:= :member/role "lead"]]]
                  {:teams [{:members [{:role "lead"}]}
                           {:members [{:role "dev"}]}]})))))

(deftest nested-missing-inner-collection-test
  (testing "nested with missing inner collection - residual"
    (let [result (engine/evaluate
                  [:forall [:team :doc/teams]
                   [:exists [:member :team/members]
                    [:= :member/role "lead"]]]
                  {:teams [{:members [{:role "lead"}]}
                           {:name "B"}]})]
      (is (engine/residual? result)))))

(deftest nested-exists-forall-test
  (testing "exists team where all members are certified"
    (is (= true (engine/evaluate
                 [:exists [:team :doc/teams]
                  [:forall [:member :team/members]
                   [:= :member/certified true]]]
                 {:teams [{:members [{:certified false}]}
                          {:members [{:certified true} {:certified true}]}]})))))

;;; ---------------------------------------------------------------------------
;;; Quantifier with Comparison Operators
;;; ---------------------------------------------------------------------------

(deftest forall-with-greater-than-test
  (testing "forall with > operator"
    (is (= true (engine/evaluate
                 [:forall [:u :doc/users] [:> :u/score 50]]
                 {:users [{:score 75} {:score 100}]})))
    (is (= false (engine/evaluate
                  [:forall [:u :doc/users] [:> :u/score 50]]
                  {:users [{:score 75} {:score 30}]})))))

(deftest exists-with-in-operator-test
  (testing "exists with :in operator"
    (is (= true (engine/evaluate
                 [:exists [:u :doc/users] [:in :u/status #{"active" "pending"}]]
                 {:users [{:status "closed"} {:status "active"}]})))
    (is (= false (engine/evaluate
                  [:exists [:u :doc/users] [:in :u/status #{"active" "pending"}]]
                  {:users [{:status "closed"} {:status "expired"}]})))))

;;; ---------------------------------------------------------------------------
;;; Filtered Quantifier Tests
;;; ---------------------------------------------------------------------------

(deftest forall-filter-excludes-test
  (testing "filter excludes non-matching elements"
    (is (= true (engine/evaluate
                 [:forall [:u :doc/users :where [:= :u/active true]]
                  [:= :u/role "admin"]]
                 {:users [{:active true :role "admin"}
                          {:active false :role "guest"}]})))))

(deftest forall-filter-all-must-satisfy-test
  (testing "all filtered elements must satisfy body"
    (is (= false (engine/evaluate
                  [:forall [:u :doc/users :where [:= :u/active true]]
                   [:= :u/role "admin"]]
                  {:users [{:active true :role "admin"}
                           {:active true :role "guest"}]})))))

(deftest forall-filter-empty-after-filter-test
  (testing "empty after filter is vacuously true"
    (is (= true (engine/evaluate
                 [:forall [:u :doc/users :where [:= :u/active true]]
                  [:= :u/role "admin"]]
                 {:users [{:active false :role "guest"}
                          {:active false :role "user"}]})))))

(deftest forall-filter-residual-body-fails-test
  (testing "filter residual with body that would fail"
    (let [result (engine/evaluate
                  [:forall [:u :doc/users :where [:= :u/active true]]
                   [:= :u/role "admin"]]
                  {:users [{:active true :role "admin"}
                           {:role "guest"}]})]
      (is (engine/residual? result)))))

(deftest forall-filter-residual-body-passes-test
  (testing "filter residual with body that passes - safe for forall"
    (is (= true (engine/evaluate
                 [:forall [:u :doc/users :where [:= :u/active true]]
                  [:= :u/role "admin"]]
                 {:users [{:active true :role "admin"}
                          {:role "admin"}]})))))

(deftest exists-filter-finds-match-test
  (testing "finds matching element after filter"
    (is (= true (engine/evaluate
                 [:exists [:u :doc/users :where [:= :u/active true]]
                  [:= :u/role "admin"]]
                 {:users [{:active false :role "admin"}
                          {:active true :role "admin"}]})))))

(deftest exists-filter-no-match-test
  (testing "no matching element after filter"
    (is (= false (engine/evaluate
                  [:exists [:u :doc/users :where [:= :u/active true]]
                   [:= :u/role "admin"]]
                  {:users [{:active true :role "guest"}
                           {:active false :role "admin"}]})))))

(deftest exists-filter-empty-after-filter-test
  (testing "empty after filter - false"
    (is (= false (engine/evaluate
                  [:exists [:u :doc/users :where [:= :u/active true]]
                   [:= :u/role "admin"]]
                  {:users [{:active false :role "admin"}]})))))

(deftest nested-quantifiers-with-filters-test
  (testing "forall with nested exists, both filtered"
    (is (= true (engine/evaluate
                 [:forall [:team :doc/teams :where [:= :team/active true]]
                  [:exists [:m :team/members :where [:> :m/level 3]]
                   [:= :m/role "lead"]]]
                 {:teams [{:active true
                           :members [{:level 5 :role "lead"}
                                     {:level 2 :role "dev"}]}
                          {:active false
                           :members [{:level 1 :role "intern"}]}]})))))

;;; ---------------------------------------------------------------------------
;;; Value Function Tests
;;; ---------------------------------------------------------------------------

(deftest fn-count-simple-test
  (testing "simple count"
    (is (= true (engine/evaluate
                 [:= [:fn/count :doc/users] 3]
                 {:users [{} {} {}]})))))

(deftest fn-count-comparison-test
  (testing "count in comparison"
    (is (= true (engine/evaluate
                 [:>= [:fn/count :doc/users] 2]
                 {:users [{} {} {}]})))
    (is (= false (engine/evaluate
                  [:> [:fn/count :doc/users] 5]
                  {:users [{} {} {}]})))))

(deftest fn-count-with-filter-test
  (testing "count with filter"
    (is (= true (engine/evaluate
                 [:= [:fn/count [:u :doc/users :where [:= :u/active true]]] 2]
                 {:users [{:active true}
                          {:active false}
                          {:active true}]})))))

(deftest fn-count-missing-collection-test
  (testing "count with missing collection - residual"
    (let [result (engine/evaluate [:fn/count :doc/users] {})]
      (is (engine/residual? result)))))

(deftest fn-count-non-sequential-test
  (testing "count of non-sequential is 0"
    (is (= true (engine/evaluate
                 [:= [:fn/count :doc/users] 0]
                 {:users "not-a-list"})))))

(deftest fn-count-filter-residual-test
  (testing "count with filter residual"
    (let [result (engine/evaluate
                  [:fn/count [:u :doc/users :where [:= :u/active true]]]
                  {:users [{:active true} {}]})]
      (is (engine/residual? result))
      (is (= 1 (:partial-count result))))))

;;; ---------------------------------------------------------------------------
;;; Implied Function Tests
;;; ---------------------------------------------------------------------------

(deftest implied-equality-true-test
  (testing "implied for equality with true result"
    (is (= {[:role] [[:= "admin"]]}
           (engine/implied [:= :doc/role "admin"] true)))))

(deftest implied-equality-false-test
  (testing "implied for equality with false result"
    (is (= {[:role] [[:!= "admin"]]}
           (engine/implied [:= :doc/role "admin"] false)))))

(deftest implied-comparison-true-test
  (testing "implied for comparison with true result"
    (is (= {[:level] [[:> 5]]}
           (engine/implied [:> :doc/level 5] true)))))

(deftest implied-comparison-false-test
  (testing "implied for comparison with false result"
    (is (= {[:level] [[:<= 5]]}
           (engine/implied [:> :doc/level 5] false)))))

(deftest implied-and-true-test
  (testing "implied for AND with true result merges constraints"
    (is (= {[:role] [[:= "admin"]] [:level] [[:> 5]]}
           (engine/implied [:and [:= :doc/role "admin"] [:> :doc/level 5]] true)))))

(deftest implied-and-false-test
  (testing "implied for AND with false result negates all constraints"
    ;; To make AND false, we negate all constraints (make everything fail).
    ;; This is one valid solution - semantically, only ONE needs to be false,
    ;; but negating all is a concrete answer rather than returning complex.
    (is (= {[:a] [[:!= 1]] [:b] [[:!= 2]]}
           (engine/implied [:and [:= :doc/a 1] [:= :doc/b 2]] false)))))

(deftest implied-or-true-test
  (testing "implied for OR with true result is complex"
    (let [result (engine/implied [:or [:= :doc/a 1] [:= :doc/b 2]] true)]
      (is (engine/complex? result)))))

(deftest implied-or-false-test
  (testing "implied for OR with false result merges negated constraints"
    (is (= {[:a] [[:!= 1]] [:b] [[:!= 2]]}
           (engine/implied [:or [:= :doc/a 1] [:= :doc/b 2]] false)))))

(deftest implied-not-true-test
  (testing "implied for NOT with true result negates"
    (is (= {[:role] [[:!= "admin"]]}
           (engine/implied [:not [:= :doc/role "admin"]] true)))))

(deftest implied-not-false-test
  (testing "implied for NOT with false result keeps original"
    (is (= {[:role] [[:= "admin"]]}
           (engine/implied [:not [:= :doc/role "admin"]] false)))))

(deftest implied-from-residual-test
  (testing "residual as input returns remaining constraints"
    (let [residual {:residual {[:level] [[:> 5]]}}]
      (is (= {[:level] [[:> 5]]}
             (engine/implied nil residual))))))

(deftest implied-from-residual-negate-test
  (testing "residual with negate option"
    (let [residual {:residual {[:level] [[:> 5]]}}]
      (is (= {[:level] [[:<= 5]]}
             (engine/implied nil residual {:negate? true}))))))

(deftest implied-chained-evaluation-test
  (testing "chained evaluation and implied"
    (let [policy [:and [:= :doc/role "admin"] [:> :doc/level 5]]
          result (engine/evaluate policy {:role "admin"})]
      (is (engine/residual? result))
      (is (= {[:level] [[:> 5]]}
             (engine/implied policy result))))))

;;; ---------------------------------------------------------------------------
;;; Implied/Evaluate Equivalence Tests
;;; ---------------------------------------------------------------------------

(deftest implied-evaluate-equivalence-test
  (testing "implied true equals evaluate residual for simple policies"
    (doseq [policy [[:= :doc/a 1]
                    [:> :doc/level 5]
                    [:and [:= :doc/a 1] [:= :doc/b 2]]
                    [:in :doc/status #{"a" "b"}]]]
      (let [eval-result (engine/evaluate policy {})
            impl-result (engine/implied policy true)]
        (is (= (:residual eval-result) impl-result)
            (str "Mismatch for " policy))))))

(deftest implied-negate-test
  (testing "implied false negates simple constraints"
    (is (= {[:a] [[:!= 1]]}
           (engine/implied [:= :doc/a 1] false))))
  (testing "implied false negates comparison"
    (is (= {[:level] [[:<= 5]]}
           (engine/implied [:> :doc/level 5] false))))
  (testing "implied false negates AND (all constraints)"
    (is (= {[:a] [[:!= 1]] [:b] [[:!= 2]]}
           (engine/implied [:and [:= :doc/a 1] [:= :doc/b 2]] false)))))

;;; ---------------------------------------------------------------------------
;;; Cross-Key Constraint Tests
;;; ---------------------------------------------------------------------------

(deftest cross-key-equality-both-present-test
  (testing "cross-key equality with both values present and equal"
    (is (= true (engine/evaluate [:= :doc/a :doc/b] {:a 5 :b 5}))))
  (testing "cross-key equality with both values present and unequal"
    (is (= false (engine/evaluate [:= :doc/a :doc/b] {:a 5 :b 10})))))

(deftest cross-key-equality-one-missing-test
  (testing "cross-key equality with left value missing"
    (let [result (engine/evaluate [:= :doc/a :doc/b] {:b 5})]
      (is (engine/residual? result))
      (is (contains? (:residual result) :polix.engine/cross-key))))
  (testing "cross-key equality with right value missing"
    (let [result (engine/evaluate [:= :doc/a :doc/b] {:a 5})]
      (is (engine/residual? result))
      (is (contains? (:residual result) :polix.engine/cross-key)))))

(deftest cross-key-comparison-test
  (testing "cross-key greater-than"
    (is (= true (engine/evaluate [:> :doc/a :doc/b] {:a 10 :b 5})))
    (is (= false (engine/evaluate [:> :doc/a :doc/b] {:a 5 :b 10}))))
  (testing "cross-key less-than"
    (is (= true (engine/evaluate [:< :doc/a :doc/b] {:a 5 :b 10})))
    (is (= false (engine/evaluate [:< :doc/a :doc/b] {:a 10 :b 5})))))

(deftest cross-key-inequality-test
  (testing "cross-key inequality"
    (is (= true (engine/evaluate [:!= :doc/a :doc/b] {:a 5 :b 10})))
    (is (= false (engine/evaluate [:!= :doc/a :doc/b] {:a 5 :b 5})))))

(deftest cross-key-implied-test
  (testing "implied for cross-key constraint"
    (let [result (engine/implied [:= :doc/a :doc/b] true)]
      (is (contains? result :polix.engine/cross-key))
      (is (= := (:op (first (:polix.engine/cross-key result))))))))

(deftest cross-key-implied-negated-test
  (testing "implied for cross-key with false result"
    (let [result (engine/implied [:= :doc/a :doc/b] false)]
      (is (contains? result :polix.engine/cross-key))
      (is (= :!= (:op (first (:polix.engine/cross-key result))))))))

(deftest residual->constraints-cross-key-test
  (testing "converts cross-key residual to constraints"
    (let [residual    {:polix.engine/cross-key [{:op := :left-path [:a] :right-path [:b]}]}
          constraints (engine/residual->constraints residual)]
      (is (= 1 (count constraints)))
      (is (= [:= :doc/a :doc/b] (first constraints))))))
