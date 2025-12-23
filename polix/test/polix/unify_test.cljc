(ns polix.unify-test
  "Tests for the unification engine.

  Tests validate that unification returns the correct result types:
  - `{}` — satisfied (empty residual)
  - `{:key [constraints]}` — residual (partial match)
  - `nil` — contradiction"
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.parser :as parser]
   [polix.residual :as res]
   [polix.result :as r]
   [polix.unify :as unify]))

;;; ---------------------------------------------------------------------------
;;; Boolean Combinator Tests
;;; ---------------------------------------------------------------------------

(deftest unify-and-test
  (testing "AND with all satisfied"
    (is (= {} (unify/unify-and [{} {} {}]))))
  (testing "AND with any contradiction"
    (is (nil? (unify/unify-and [{} nil {}]))))
  (testing "AND with residuals"
    (let [result (unify/unify-and [{} {[:a] [[1]]}])]
      (is (res/residual? result))
      (is (= {[:a] [[1]]} result))))
  (testing "AND merges multiple residuals"
    (let [result (unify/unify-and [{[:a] [[1]]} {[:b] [[2]]}])]
      (is (res/residual? result))
      (is (= {[:a] [[1]] [:b] [[2]]} result)))))

(deftest unify-or-test
  (testing "OR with any satisfied"
    (is (= {} (unify/unify-or [nil {} nil]))))
  (testing "OR with all contradictions"
    (is (nil? (unify/unify-or [nil nil nil]))))
  (testing "OR with residuals"
    (let [result (unify/unify-or [nil {[:a] [[1]]}])]
      (is (res/residual? result)))))

(deftest unify-not-test
  (testing "NOT satisfied becomes contradiction"
    (is (nil? (unify/unify-not {}))))
  (testing "NOT contradiction becomes satisfied"
    (is (= {} (unify/unify-not nil))))
  (testing "NOT residual becomes complex"
    (let [result (unify/unify-not {[:a] [[1]]})]
      (is (res/has-complex? result)))))

;;; ---------------------------------------------------------------------------
;;; Simple Equality Tests
;;; ---------------------------------------------------------------------------

(deftest simple-equality-satisfied-test
  (testing "simple equality satisfied"
    (let [ast (r/unwrap (parser/parse-policy [:= :doc/role "admin"]))]
      (is (= {} (unify/unify ast {:role "admin"}))))))

(deftest simple-equality-contradicted-test
  (testing "simple equality contradicted"
    (let [ast (r/unwrap (parser/parse-policy [:= :doc/role "admin"]))]
      (is (nil? (unify/unify ast {:role "guest"}))))))

(deftest simple-equality-residual-test
  (testing "missing key returns residual"
    (let [ast    (r/unwrap (parser/parse-policy [:= :doc/role "admin"]))
          result (unify/unify ast {})]
      (is (res/residual? result))
      (is (= {[:role] [[:= "admin"]]} result)))))

;;; ---------------------------------------------------------------------------
;;; Comparison Operator Tests
;;; ---------------------------------------------------------------------------

(deftest comparison-greater-than-test
  (testing "> satisfied"
    (is (= {} (unify/unify [:> :doc/level 5] {:level 10}))))
  (testing "> contradicted"
    (is (nil? (unify/unify [:> :doc/level 5] {:level 3}))))
  (testing "> residual"
    (let [result (unify/unify [:> :doc/level 5] {})]
      (is (res/residual? result))
      (is (= {[:level] [[:> 5]]} result)))))

(deftest comparison-less-than-test
  (testing "< satisfied"
    (is (= {} (unify/unify [:< :doc/level 10] {:level 5}))))
  (testing "< contradicted"
    (is (nil? (unify/unify [:< :doc/level 10] {:level 15})))))

(deftest comparison-greater-equal-test
  (testing ">= satisfied at boundary"
    (is (= {} (unify/unify [:>= :doc/level 5] {:level 5}))))
  (testing ">= contradicted"
    (is (nil? (unify/unify [:>= :doc/level 5] {:level 4})))))

(deftest comparison-less-equal-test
  (testing "<= satisfied at boundary"
    (is (= {} (unify/unify [:<= :doc/level 5] {:level 5}))))
  (testing "<= contradicted"
    (is (nil? (unify/unify [:<= :doc/level 5] {:level 6})))))

(deftest comparison-inequality-test
  (testing "!= satisfied"
    (is (= {} (unify/unify [:!= :doc/role "admin"] {:role "guest"}))))
  (testing "!= contradicted"
    (is (nil? (unify/unify [:!= :doc/role "admin"] {:role "admin"})))))

;;; ---------------------------------------------------------------------------
;;; Set Membership Tests
;;; ---------------------------------------------------------------------------

(deftest set-in-satisfied-test
  (testing ":in satisfied"
    (is (= {} (unify/unify [:in :doc/status #{"active" "pending"}] {:status "active"})))))

(deftest set-in-contradicted-test
  (testing ":in contradicted"
    (is (nil? (unify/unify [:in :doc/status #{"active" "pending"}] {:status "closed"})))))

(deftest set-in-residual-test
  (testing ":in residual"
    (let [result (unify/unify [:in :doc/status #{"active" "pending"}] {})]
      (is (res/residual? result))
      (is (= {[:status] [[:in #{"active" "pending"}]]} result)))))

(deftest set-not-in-test
  (testing ":not-in satisfied"
    (is (= {} (unify/unify [:not-in :doc/status #{"banned"}] {:status "active"}))))
  (testing ":not-in contradicted"
    (is (nil? (unify/unify [:not-in :doc/status #{"banned"}] {:status "banned"})))))

;;; ---------------------------------------------------------------------------
;;; Boolean Connective Tests
;;; ---------------------------------------------------------------------------

(deftest and-all-satisfied-test
  (testing "AND all satisfied"
    (is (= {} (unify/unify [:and [:= :doc/a 1] [:= :doc/b 2]] {:a 1 :b 2})))))

(deftest and-one-contradicted-test
  (testing "AND one contradicted"
    (is (nil? (unify/unify [:and [:= :doc/a 1] [:= :doc/b 2]] {:a 1 :b 999})))))

(deftest and-partial-residual-test
  (testing "AND partial residual"
    (let [result (unify/unify [:and [:= :doc/a 1] [:= :doc/b 2]] {:a 1})]
      (is (res/residual? result))
      (is (= {[:b] [[:= 2]]} result)))))

(deftest or-one-satisfied-test
  (testing "OR one satisfied"
    (is (= {} (unify/unify [:or [:= :doc/a 1] [:= :doc/a 2]] {:a 1})))))

(deftest or-all-contradicted-test
  (testing "OR all contradicted"
    (is (nil? (unify/unify [:or [:= :doc/a 1] [:= :doc/a 2]] {:a 999})))))

(deftest not-satisfied-becomes-contradiction-test
  (testing "NOT satisfied becomes contradiction"
    (is (nil? (unify/unify [:not [:= :doc/a 1]] {:a 1})))))

(deftest not-contradicted-becomes-satisfied-test
  (testing "NOT contradicted becomes satisfied"
    (is (= {} (unify/unify [:not [:= :doc/a 1]] {:a 2})))))

;;; ---------------------------------------------------------------------------
;;; Nested Path Tests
;;; ---------------------------------------------------------------------------

(deftest nested-path-satisfied-test
  (testing "nested path satisfied"
    (is (= {} (unify/unify [:= :doc/user.role "admin"] {:user {:role "admin"}})))))

(deftest nested-path-contradicted-test
  (testing "nested path contradicted"
    (is (nil? (unify/unify [:= :doc/user.role "admin"] {:user {:role "guest"}})))))

(deftest nested-path-residual-test
  (testing "nested path residual"
    (let [result (unify/unify [:= :doc/user.role "admin"] {:user {}})]
      (is (res/residual? result))
      (is (= {[:user :role] [[:= "admin"]]} result)))))

(deftest deeply-nested-path-test
  (testing "deeply nested path"
    (is (= {} (unify/unify [:= :doc/a.b.c "deep"] {:a {:b {:c "deep"}}})))))

;;; ---------------------------------------------------------------------------
;;; Path Utility Tests
;;; ---------------------------------------------------------------------------

(deftest path-exists?-test
  (testing "shallow path exists"
    (is (unify/path-exists? {:role "admin"} [:role])))
  (testing "nested path exists"
    (is (unify/path-exists? {:user {:name "Alice"}} [:user :name])))
  (testing "nil value counts as exists"
    (is (unify/path-exists? {:user {:name nil}} [:user :name])))
  (testing "false value counts as exists"
    (is (unify/path-exists? {:active false} [:active])))
  (testing "missing path"
    (is (not (unify/path-exists? {:user {}} [:user :name])))))

(deftest path->doc-accessor-test
  (testing "single element path"
    (is (= :doc/role (unify/path->doc-accessor [:role]))))
  (testing "multi element path"
    (is (= :doc/user.name (unify/path->doc-accessor [:user :name])))))

;;; ---------------------------------------------------------------------------
;;; Forall Tests
;;; ---------------------------------------------------------------------------

(deftest forall-all-satisfy-test
  (testing "all elements satisfy"
    (is (= {} (unify/unify
               [:forall [:u :doc/users] [:= :u/role "admin"]]
               {:users [{:role "admin"} {:role "admin"}]})))))

(deftest forall-one-contradicts-test
  (testing "one element contradicts"
    (is (nil? (unify/unify
               [:forall [:u :doc/users] [:= :u/role "admin"]]
               {:users [{:role "admin"} {:role "guest"}]})))))

(deftest forall-empty-collection-test
  (testing "empty collection is vacuously true"
    (is (= {} (unify/unify
               [:forall [:u :doc/users] [:= :u/role "admin"]]
               {:users []})))))

(deftest forall-missing-collection-test
  (testing "missing collection returns residual"
    (let [result (unify/unify
                  [:forall [:u :doc/users] [:= :u/role "admin"]]
                  {})]
      (is (res/residual? result))
      (is (contains? result [:users])))))

(deftest forall-partial-elements-test
  (testing "partial elements return indexed residual"
    (let [result (unify/unify
                  [:forall [:u :doc/users] [:= :u/role "admin"]]
                  {:users [{:role "admin"} {:name "Bob"}]})]
      (is (res/residual? result))
      (is (contains? result [:users 1 :role])))))

;;; ---------------------------------------------------------------------------
;;; Exists Tests
;;; ---------------------------------------------------------------------------

(deftest exists-one-satisfies-test
  (testing "one element satisfies"
    (is (= {} (unify/unify
               [:exists [:u :doc/users] [:= :u/role "admin"]]
               {:users [{:role "guest"} {:role "admin"}]})))))

(deftest exists-none-satisfy-test
  (testing "no elements satisfy"
    (is (nil? (unify/unify
               [:exists [:u :doc/users] [:= :u/role "admin"]]
               {:users [{:role "guest"} {:role "user"}]})))))

(deftest exists-empty-collection-test
  (testing "empty collection is false"
    (is (nil? (unify/unify
               [:exists [:u :doc/users] [:= :u/role "admin"]]
               {:users []})))))

(deftest exists-short-circuit-test
  (testing "short-circuits on first true"
    (is (= {} (unify/unify
               [:exists [:u :doc/users] [:= :u/role "admin"]]
               {:users [{:role "admin"} {:name "missing-role"}]})))))

;;; ---------------------------------------------------------------------------
;;; Filtered Quantifier Tests
;;; ---------------------------------------------------------------------------

(deftest forall-filter-test
  (testing "filter excludes non-matching elements"
    (is (= {} (unify/unify
               [:forall [:u :doc/users :where [:= :u/active true]]
                [:= :u/role "admin"]]
               {:users [{:active true :role "admin"}
                        {:active false :role "guest"}]})))))

(deftest exists-filter-test
  (testing "finds matching element after filter"
    (is (= {} (unify/unify
               [:exists [:u :doc/users :where [:= :u/active true]]
                [:= :u/role "admin"]]
               {:users [{:active false :role "admin"}
                        {:active true :role "admin"}]})))))

;;; ---------------------------------------------------------------------------
;;; Value Function Tests
;;; ---------------------------------------------------------------------------

(deftest fn-count-simple-test
  (testing "simple count"
    (is (= {} (unify/unify
               [:= [:fn/count :doc/users] 3]
               {:users [{} {} {}]})))))

(deftest fn-count-comparison-test
  (testing "count comparison"
    (is (= {} (unify/unify
               [:>= [:fn/count :doc/users] 2]
               {:users [{} {} {}]})))
    (is (nil? (unify/unify
               [:> [:fn/count :doc/users] 5]
               {:users [{} {} {}]})))))

;;; ---------------------------------------------------------------------------
;;; Cross-Key Constraint Tests
;;; ---------------------------------------------------------------------------

(deftest cross-key-equality-both-present-test
  (testing "both present and equal"
    (is (= {} (unify/unify [:= :doc/a :doc/b] {:a 5 :b 5}))))
  (testing "both present and unequal"
    (is (nil? (unify/unify [:= :doc/a :doc/b] {:a 5 :b 10})))))

(deftest cross-key-one-missing-test
  (testing "cross-key with left missing"
    (let [result (unify/unify [:= :doc/a :doc/b] {:b 5})]
      (is (res/residual? result))
      (is (contains? result :polix.unify/cross-key)))))

(deftest cross-key-comparison-test
  (testing "cross-key greater-than"
    (is (= {} (unify/unify [:> :doc/a :doc/b] {:a 10 :b 5})))
    (is (nil? (unify/unify [:> :doc/a :doc/b] {:a 5 :b 10})))))

;;; ---------------------------------------------------------------------------
;;; Residual Conversion Tests
;;; ---------------------------------------------------------------------------

(deftest residual->constraints-test
  (testing "converts residual to policy expressions"
    (let [residual    {[:level] [[:> 5]] [:status] [[:in #{"a" "b"}]]}
          constraints (unify/residual->constraints residual)]
      (is (= 2 (count constraints)))
      (is (some #(= [:> :doc/level 5] %) constraints))
      (is (some #(= [:in :doc/status #{"a" "b"}] %) constraints)))))

(deftest result->policy-satisfied-test
  (testing "satisfied returns nil"
    (is (nil? (unify/result->policy {})))))

(deftest result->policy-contradiction-test
  (testing "contradiction returns [:contradiction]"
    (is (= [:contradiction] (unify/result->policy nil)))))

(deftest result->policy-residual-test
  (testing "single residual returns constraint"
    (is (= [:> :doc/level 5]
           (unify/result->policy {[:level] [[:> 5]]})))))

;;; ---------------------------------------------------------------------------
;;; Constraint Set Tests
;;; ---------------------------------------------------------------------------

(deftest unify-constraint-set-satisfied-test
  (testing "constraint set satisfied"
    (let [constraint-set {[:role] [{:op := :value "admin"}]}]
      (is (= {} (unify/unify-constraint-set constraint-set {:role "admin"}))))))

(deftest unify-constraint-set-contradicted-test
  (testing "constraint set contradicted"
    (let [constraint-set {[:role] [{:op := :value "admin"}]}]
      (is (nil? (unify/unify-constraint-set constraint-set {:role "guest"}))))))

(deftest unify-constraint-set-residual-test
  (testing "constraint set residual"
    (let [constraint-set {[:role] [{:op := :value "admin"}]}
          result         (unify/unify-constraint-set constraint-set {})]
      (is (res/residual? result))
      (is (= {[:role] [[:= "admin"]]} result)))))

;;; ---------------------------------------------------------------------------
;;; Empty Document Tests
;;; ---------------------------------------------------------------------------

(deftest empty-document-simple-test
  (testing "empty document returns full residual"
    (let [result (unify/unify [:= :doc/role "admin"] {})]
      (is (res/residual? result))
      (is (= {[:role] [[:= "admin"]]} result)))))

(deftest empty-document-compound-test
  (testing "empty document with AND returns all constraints"
    (let [result (unify/unify [:and [:= :doc/a 1] [:= :doc/b 2]] {})]
      (is (res/residual? result))
      (is (= {[:a] [[:= 1]] [:b] [[:= 2]]} result)))))
