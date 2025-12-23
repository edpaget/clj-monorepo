(ns polix.compiler-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [polix.compiler :as compiler]
   [polix.operators :as op]
   [polix.residual :as res]))

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
      (is (= [:role] (get-in result [:constraint :key])))
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
      (is (= {} (check {:role "admin"})))))

  (testing "simple equality - conflict"
    (let [check  (compiler/compile-policies [[:= :doc/role "admin"]])
          result (check {:role "guest"})]
      (is (res/has-conflicts? result))
      (is (= [[:conflict [:= "admin"] "guest"]] (get result [:role])))))

  (testing "simple equality - missing key gives residual"
    (let [check  (compiler/compile-policies [[:= :doc/role "admin"]])
          result (check {})]
      (is (res/residual? result))
      (is (= {[:role] [[:= "admin"]]} result)))))

(deftest compile-multiple-constraints-test
  (testing "AND of multiple constraints - all satisfied"
    (let [check (compiler/compile-policies
                 [[:= :doc/role "admin"]
                  [:> :doc/level 5]])]
      (is (= {} (check {:role "admin" :level 10})))))

  (testing "AND of multiple constraints - one conflict"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]
                   [:> :doc/level 5]])
          result (check {:role "admin" :level 3})]
      (is (res/has-conflicts? result))))

  (testing "AND of multiple constraints - partial"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]
                   [:> :doc/level 5]])
          result (check {:role "admin"})]
      (is (res/residual? result))
      (is (contains? result [:level])))))

(deftest compile-range-constraints-test
  (testing "range constraint - satisfied"
    (let [check (compiler/compile-policies [[:> :doc/age 18]])]
      (is (= {} (check {:age 25})))))

  (testing "range constraint - conflict"
    (let [check  (compiler/compile-policies [[:> :doc/age 18]])
          result (check {:age 16})]
      (is (res/has-conflicts? result))))

  (testing "range constraint - boundary"
    (let [check (compiler/compile-policies [[:>= :doc/age 18]])]
      (is (= {} (check {:age 18})))
      (is (res/has-conflicts? (check {:age 17}))))))

(deftest simplify-range-constraints-test
  (testing "merging range constraints keeps tightest bounds"
    (let [check (compiler/compile-policies
                 [[:> :doc/x 3]
                  [:> :doc/x 5]
                  [:< :doc/x 10]])]
      (is (= {} (check {:x 7})))
      (is (res/has-conflicts? (check {:x 4})))
      (is (res/has-conflicts? (check {:x 11}))))))

(deftest contradictory-policies-test
  (testing "contradictory equality constraints"
    (let [check (compiler/compile-policies
                 [[:= :doc/role "admin"]
                  [:= :doc/role "guest"]])]
      (is (nil? (check {:role "admin"})))
      (is (nil? (check {:role "guest"})))
      (is (nil? (check {}))))))

(deftest in-constraint-test
  (testing "in constraint - satisfied"
    (let [check (compiler/compile-policies
                 [[:in :doc/status #{"active" "pending"}]])]
      (is (= {} (check {:status "active"})))
      (is (= {} (check {:status "pending"})))))

  (testing "in constraint - conflict"
    (let [check  (compiler/compile-policies
                  [[:in :doc/status #{"active" "pending"}]])
          result (check {:status "closed"})]
      (is (res/has-conflicts? result)))))

(deftest residual-to-constraints-test
  (testing "converting residual back to policy"
    (let [residual    {[:level] [[:> 5]] [:status] [[:in #{"a" "b"}]]}
          constraints (compiler/residual->constraints residual)]
      (is (= 2 (count constraints)))
      (is (some #(= [:> :doc/level 5] %) constraints))
      (is (some #(= [:in :doc/status #{"a" "b"}] %) constraints)))))

(deftest result-to-policy-test
  (testing "satisfied result gives nil"
    (is (nil? (compiler/result->policy {}))))

  (testing "legacy nil gives [:contradiction]"
    (is (= [:contradiction] (compiler/result->policy nil))))

  (testing "conflict residual gives [:contradiction]"
    (is (= [:contradiction]
           (compiler/result->policy {[:x] [[:conflict [:= 1] 2]]}))))

  (testing "residual gives simplified constraints"
    (let [result {[:level] [[:> 5]]}
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
      (is (= {} (check {:name "HELLO"})))
      (is (res/has-conflicts? (check {:name "hello"}))))))

(deftest context-strict-mode-test
  (testing "strict mode throws on unknown operator"
    (let [check (compiler/compile-policies
                 [[:= :doc/role "admin"]]
                 {:strict? true})]
      ;; Known operator works
      (is (= {} (check {:role "admin"}))))))

(deftest context-tracing-test
  (testing "tracing on fully satisfied policy"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]]
                  {:trace? true})
          result (check {:role "admin"})]
      (is (map? result))
      (is (= {} (:result result)))
      (is (contains? result :trace))
      (is (vector? (:trace result)))
      (is (= 1 (count (:trace result))))))

  (testing "tracing on conflict policy"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]]
                  {:trace? true})
          result (check {:role "guest"})]
      (is (map? result))
      (is (res/has-conflicts? (:result result)))
      (is (contains? result :trace))
      (is (vector? (:trace result)))))

  (testing "tracing on partial evaluation"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]
                   [:> :doc/level 5]]
                  {:trace? true})
          result (check {:role "admin"})]
      (is (map? result))
      (is (res/residual? (:result result)))
      (is (contains? result :trace))
      (is (vector? (:trace result)))))

  (testing "per-evaluation trace override"
    (let [check  (compiler/compile-policies
                  [[:= :doc/role "admin"]
                   [:> :doc/level 5]])
          result (check {:role "admin"} {:trace? true})]
      (is (map? result))
      (is (contains? result :trace))
      (is (contains? result :result)))))

;;; ---------------------------------------------------------------------------
;;; Nested Path Tests
;;; ---------------------------------------------------------------------------

(deftest compile-nested-path-test
  (testing "compiled policy with nested path - satisfied"
    (let [checker (compiler/compile-policies [[:= :doc/user.role "admin"]])]
      (is (= {} (checker {:user {:role "admin"}})))))
  (testing "compiled policy with nested path - conflict"
    (let [checker (compiler/compile-policies [[:= :doc/user.role "admin"]])
          result  (checker {:user {:role "guest"}})]
      (is (res/has-conflicts? result))))
  (testing "compiled policy with nested path - residual"
    (let [checker (compiler/compile-policies [[:= :doc/user.role "admin"]])
          result  (checker {:user {}})]
      (is (res/residual? result)))))

(deftest nested-path-constraint-merging-test
  (testing "constraints on same nested path merge"
    (let [checker (compiler/compile-policies
                   [[:> :doc/user.level 5]
                    [:< :doc/user.level 10]])]
      (is (= {} (checker {:user {:level 7}})))
      (is (res/has-conflicts? (checker {:user {:level 3}})))
      (is (res/has-conflicts? (checker {:user {:level 12}})))))
  (testing "constraints on different nested paths"
    (let [checker (compiler/compile-policies
                   [[:= :doc/user.role "admin"]
                    [:> :doc/user.level 5]])]
      (is (= {} (checker {:user {:role "admin" :level 10}})))
      (is (res/has-conflicts? (checker {:user {:role "guest" :level 10}}))))))

(deftest deeply-nested-path-test
  (testing "deeply nested paths work"
    (let [checker (compiler/compile-policies
                   [[:= :doc/org.team.member.role "lead"]])]
      (is (= {} (checker {:org {:team {:member {:role "lead"}}}})))
      (is (res/has-conflicts? (checker {:org {:team {:member {:role "member"}}}}))))))

(deftest mixed-path-depths-test
  (testing "mix of shallow and nested paths"
    (let [checker (compiler/compile-policies
                   [[:= :doc/type "user"]
                    [:= :doc/user.role "admin"]])]
      (is (= {} (checker {:type "user" :user {:role "admin"}})))
      (is (res/has-conflicts? (checker {:type "order" :user {:role "admin"}}))))))

;;; ---------------------------------------------------------------------------
;;; Quantifier Compilation Tests
;;; ---------------------------------------------------------------------------

(deftest compile-forall-policy-test
  (testing "compiled forall - all satisfy"
    (let [checker (compiler/compile-policies
                   [[:forall [:u :doc/users] [:= :u/role "admin"]]])]
      (is (= {} (checker {:users [{:role "admin"} {:role "admin"}]})))))
  (testing "compiled forall - one conflict"
    (let [checker (compiler/compile-policies
                   [[:forall [:u :doc/users] [:= :u/role "admin"]]])
          result  (checker {:users [{:role "admin"} {:role "guest"}]})]
      (is (res/has-conflicts? result))))
  (testing "compiled forall - empty collection (vacuous)"
    (let [checker (compiler/compile-policies
                   [[:forall [:u :doc/users] [:= :u/role "admin"]]])]
      (is (= {} (checker {:users []}))))))

(deftest compile-exists-policy-test
  (testing "compiled exists - one satisfies"
    (let [checker (compiler/compile-policies
                   [[:exists [:u :doc/users] [:= :u/role "admin"]]])]
      (is (= {} (checker {:users [{:role "guest"} {:role "admin"}]})))))
  (testing "compiled exists - none satisfy"
    (let [checker (compiler/compile-policies
                   [[:exists [:u :doc/users] [:= :u/role "admin"]]])
          result  (checker {:users [{:role "guest"} {:role "user"}]})]
      (is (res/has-conflicts? result))))
  (testing "compiled exists - empty collection"
    (let [checker (compiler/compile-policies
                   [[:exists [:u :doc/users] [:= :u/role "admin"]]])
          result  (checker {:users []})]
      (is (res/has-conflicts? result)))))

(deftest compile-quantifier-with-constraints-test
  (testing "quantifier combined with simple constraints"
    (let [checker (compiler/compile-policies
                   [[:= :doc/status "active"]
                    [:forall [:u :doc/users] [:= :u/role "member"]]])]
      (is (= {} (checker {:status "active"
                          :users [{:role "member"}]})))
      (is (res/has-conflicts? (checker {:status "inactive"
                                        :users [{:role "member"}]})))
      (is (res/has-conflicts? (checker {:status "active"
                                        :users [{:role "admin"}]}))))))

(deftest compile-quantifier-residual-test
  (testing "quantifier with missing collection returns residual"
    (let [checker (compiler/compile-policies
                   [[:forall [:u :doc/users] [:= :u/role "admin"]]])
          result  (checker {})]
      (is (res/residual? result))))
  (testing "mixed constraints with quantifier residual"
    (let [checker (compiler/compile-policies
                   [[:= :doc/status "active"]
                    [:forall [:u :doc/users] [:= :u/role "admin"]]])
          result  (checker {:status "active"})]
      (is (res/residual? result)))))

(deftest compile-nested-quantifiers-test
  (testing "nested quantifiers through compiler"
    (let [checker (compiler/compile-policies
                   [[:forall [:team :doc/teams]
                     [:exists [:member :team/members]
                      [:= :member/role "lead"]]]])]
      (is (= {} (checker {:teams [{:members [{:role "lead"}]}
                                  {:members [{:role "dev"} {:role "lead"}]}]})))
      (is (res/has-conflicts?
           (checker {:teams [{:members [{:role "lead"}]}
                             {:members [{:role "dev"}]}]}))))))
