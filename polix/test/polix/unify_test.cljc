(ns polix.unify-test
  "Tests for the unification engine.

  Tests validate that unification returns the correct result types:
  - `{}` — satisfied (empty residual)
  - `{:key [constraints]}` — open residual (partial match, missing data)
  - `{:key [[:conflict ...]]}` — conflict residual (constraint violated)"
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.parser :as parser]
   [polix.registry :as registry]
   [polix.residual :as res]
   [polix.result :as r]
   [polix.unify :as unify]))

;;; ---------------------------------------------------------------------------
;;; Boolean Combinator Tests
;;; ---------------------------------------------------------------------------

(deftest unify-and-test
  (testing "AND with all satisfied"
    (is (= {} (unify/unify-and [{} {} {}]))))
  (testing "AND with legacy nil (backward compat)"
    (is (nil? (unify/unify-and [{} nil {}]))))
  (testing "AND with conflict residual"
    (let [result (unify/unify-and [{} {[:a] [[:conflict [:= 1] 2]]}])]
      (is (res/has-conflicts? result))))
  (testing "AND with residuals"
    (let [result (unify/unify-and [{} {[:a] [[1]]}])]
      (is (res/residual? result))
      (is (= {[:a] [[1]]} result))))
  (testing "AND merges multiple residuals"
    (let [result (unify/unify-and [{[:a] [[1]]} {[:b] [[2]]}])]
      (is (res/residual? result))
      (is (= {[:a] [[1]] [:b] [[2]]} result))))
  (testing "AND merges conflicts with open constraints"
    (let [result (unify/unify-and [{[:a] [[:conflict [:= 1] 2]]} {[:b] [[:> 5]]}])]
      (is (res/has-conflicts? result))
      (is (= {[:a] [[:conflict [:= 1] 2]] [:b] [[:> 5]]} result)))))

(deftest unify-or-test
  (testing "OR with any satisfied"
    (is (= {} (unify/unify-or [{[:a] [[:conflict [:= 1] 2]]} {} {[:b] [[1]]}]))))
  (testing "OR with all legacy nils produces complex"
    (let [result (unify/unify-or [nil nil nil])]
      (is (res/has-complex? result))))
  (testing "OR with all conflicts produces complex"
    (let [result (unify/unify-or [{[:a] [[:conflict [:= 1] 2]]}
                                  {[:a] [[:conflict [:= 1] 3]]}])]
      (is (res/has-complex? result))))
  (testing "OR with residuals"
    (let [result (unify/unify-or [nil {[:a] [[1]]}])]
      (is (res/residual? result)))))

(deftest unify-not-test
  (testing "NOT satisfied becomes complex (not-satisfied marker)"
    (let [result (unify/unify-not {})]
      (is (res/has-complex? result))
      (is (= :not-satisfied (get-in result [::res/complex :type])))))
  (testing "NOT legacy nil becomes satisfied"
    (is (= {} (unify/unify-not nil))))
  (testing "NOT all-conflicts becomes satisfied"
    (is (= {} (unify/unify-not {[:a] [[:conflict [:= 1] 2]]}))))
  (testing "NOT open residual becomes complex"
    (let [result (unify/unify-not {[:a] [[1]]})]
      (is (res/has-complex? result))))
  (testing "NOT mixed residual becomes complex"
    (let [result (unify/unify-not {[:a] [[:conflict [:= 1] 2]] [:b] [[:> 5]]})]
      (is (res/has-complex? result)))))

;;; ---------------------------------------------------------------------------
;;; Simple Equality Tests
;;; ---------------------------------------------------------------------------

(deftest simple-equality-satisfied-test
  (testing "simple equality satisfied"
    (let [ast (r/unwrap (parser/parse-policy [:= :doc/role "admin"]))]
      (is (= {} (unify/unify ast {:role "admin"}))))))

(deftest simple-equality-contradicted-test
  (testing "simple equality contradicted produces conflict"
    (let [ast    (r/unwrap (parser/parse-policy [:= :doc/role "admin"]))
          result (unify/unify ast {:role "guest"})]
      (is (res/has-conflicts? result))
      (is (= [[:conflict [:= "admin"] "guest"]] (get result [:role]))))))

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
  (testing "> conflict"
    (let [result (unify/unify [:> :doc/level 5] {:level 3})]
      (is (res/has-conflicts? result))
      (is (= [[:conflict [:> 5] 3]] (get result [:level])))))
  (testing "> residual"
    (let [result (unify/unify [:> :doc/level 5] {})]
      (is (res/residual? result))
      (is (= {[:level] [[:> 5]]} result)))))

(deftest comparison-less-than-test
  (testing "< satisfied"
    (is (= {} (unify/unify [:< :doc/level 10] {:level 5}))))
  (testing "< conflict"
    (let [result (unify/unify [:< :doc/level 10] {:level 15})]
      (is (res/has-conflicts? result)))))

(deftest comparison-greater-equal-test
  (testing ">= satisfied at boundary"
    (is (= {} (unify/unify [:>= :doc/level 5] {:level 5}))))
  (testing ">= conflict"
    (let [result (unify/unify [:>= :doc/level 5] {:level 4})]
      (is (res/has-conflicts? result)))))

(deftest comparison-less-equal-test
  (testing "<= satisfied at boundary"
    (is (= {} (unify/unify [:<= :doc/level 5] {:level 5}))))
  (testing "<= conflict"
    (let [result (unify/unify [:<= :doc/level 5] {:level 6})]
      (is (res/has-conflicts? result)))))

(deftest comparison-inequality-test
  (testing "!= satisfied"
    (is (= {} (unify/unify [:!= :doc/role "admin"] {:role "guest"}))))
  (testing "!= conflict"
    (let [result (unify/unify [:!= :doc/role "admin"] {:role "admin"})]
      (is (res/has-conflicts? result)))))

;;; ---------------------------------------------------------------------------
;;; Set Membership Tests
;;; ---------------------------------------------------------------------------

(deftest set-in-satisfied-test
  (testing ":in satisfied"
    (is (= {} (unify/unify [:in :doc/status #{"active" "pending"}] {:status "active"})))))

(deftest set-in-contradicted-test
  (testing ":in conflict"
    (let [result (unify/unify [:in :doc/status #{"active" "pending"}] {:status "closed"})]
      (is (res/has-conflicts? result)))))

(deftest set-in-residual-test
  (testing ":in residual"
    (let [result (unify/unify [:in :doc/status #{"active" "pending"}] {})]
      (is (res/residual? result))
      (is (= {[:status] [[:in #{"active" "pending"}]]} result)))))

(deftest set-not-in-test
  (testing ":not-in satisfied"
    (is (= {} (unify/unify [:not-in :doc/status #{"banned"}] {:status "active"}))))
  (testing ":not-in conflict"
    (let [result (unify/unify [:not-in :doc/status #{"banned"}] {:status "banned"})]
      (is (res/has-conflicts? result)))))

;;; ---------------------------------------------------------------------------
;;; Boolean Connective Tests
;;; ---------------------------------------------------------------------------

(deftest and-all-satisfied-test
  (testing "AND all satisfied"
    (is (= {} (unify/unify [:and [:= :doc/a 1] [:= :doc/b 2]] {:a 1 :b 2})))))

(deftest and-one-contradicted-test
  (testing "AND one conflict"
    (let [result (unify/unify [:and [:= :doc/a 1] [:= :doc/b 2]] {:a 1 :b 999})]
      (is (res/has-conflicts? result))
      (is (= [[:conflict [:= 2] 999]] (get result [:b]))))))

(deftest and-partial-residual-test
  (testing "AND partial residual"
    (let [result (unify/unify [:and [:= :doc/a 1] [:= :doc/b 2]] {:a 1})]
      (is (res/residual? result))
      (is (= {[:b] [[:= 2]]} result)))))

(deftest or-one-satisfied-test
  (testing "OR one satisfied"
    (is (= {} (unify/unify [:or [:= :doc/a 1] [:= :doc/a 2]] {:a 1})))))

(deftest or-all-contradicted-test
  (testing "OR all conflicts produces complex"
    (let [result (unify/unify [:or [:= :doc/a 1] [:= :doc/a 2]] {:a 999})]
      (is (res/has-complex? result)))))

(deftest not-satisfied-becomes-complex-test
  (testing "NOT satisfied becomes complex"
    (let [result (unify/unify [:not [:= :doc/a 1]] {:a 1})]
      (is (res/has-complex? result)))))

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
  (testing "nested path conflict"
    (let [result (unify/unify [:= :doc/user.role "admin"] {:user {:role "guest"}})]
      (is (res/has-conflicts? result)))))

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
  (testing "one element conflicts"
    (let [result (unify/unify
                  [:forall [:u :doc/users] [:= :u/role "admin"]]
                  {:users [{:role "admin"} {:role "guest"}]})]
      (is (res/has-conflicts? result)))))

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
    (let [result (unify/unify
                  [:exists [:u :doc/users] [:= :u/role "admin"]]
                  {:users [{:role "guest"} {:role "user"}]})]
      (is (res/has-conflicts? result)))))

(deftest exists-empty-collection-test
  (testing "empty collection produces conflict"
    (let [result (unify/unify
                  [:exists [:u :doc/users] [:= :u/role "admin"]]
                  {:users []})]
      (is (res/has-conflicts? result)))))

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
  (testing "count comparison satisfied"
    (is (= {} (unify/unify
               [:>= [:fn/count :doc/users] 2]
               {:users [{} {} {}]}))))
  (testing "count comparison conflict"
    (let [result (unify/unify
                  [:> [:fn/count :doc/users] 5]
                  {:users [{} {} {}]})]
      (is (res/has-complex? result)))))

;;; ---------------------------------------------------------------------------
;;; Cross-Key Constraint Tests
;;; ---------------------------------------------------------------------------

(deftest cross-key-equality-both-present-test
  (testing "both present and equal"
    (is (= {} (unify/unify [:= :doc/a :doc/b] {:a 5 :b 5}))))
  (testing "both present and unequal produces conflict"
    (let [result (unify/unify [:= :doc/a :doc/b] {:a 5 :b 10})]
      (is (res/has-conflicts? result)))))

(deftest cross-key-one-missing-test
  (testing "cross-key with left missing"
    (let [result (unify/unify [:= :doc/a :doc/b] {:b 5})]
      (is (res/residual? result))
      (is (res/has-cross-key? result)))))

(deftest cross-key-comparison-test
  (testing "cross-key greater-than satisfied"
    (is (= {} (unify/unify [:> :doc/a :doc/b] {:a 10 :b 5}))))
  (testing "cross-key greater-than conflict"
    (let [result (unify/unify [:> :doc/a :doc/b] {:a 5 :b 10})]
      (is (res/has-conflicts? result)))))

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
  (testing "legacy nil returns [:contradiction]"
    (is (= [:contradiction] (unify/result->policy nil))))
  (testing "conflict residual returns [:contradiction]"
    (is (= [:contradiction]
           (unify/result->policy {[:a] [[:conflict [:= 1] 2]]})))))

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
  (testing "constraint set produces conflict"
    (let [constraint-set {[:role] [{:op := :value "admin"}]}
          result         (unify/unify-constraint-set constraint-set {:role "guest"})]
      (is (res/has-conflicts? result))
      (is (= [[:conflict [:= "admin"] "guest"]] (get result [:role]))))))

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

;;; ---------------------------------------------------------------------------
;;; Self Accessor Tests
;;; ---------------------------------------------------------------------------

(deftest self-accessor-found-test
  (testing "self accessor resolves from context"
    (let [result (unify/unify [:= :self/value 5] {}
                              {:self {:value 5}})]
      (is (= {} result)))))

(deftest self-accessor-nested-test
  (testing "self accessor resolves nested path"
    (let [result (unify/unify [:= :self/computed.result 42] {}
                              {:self {:computed {:result 42}}})]
      (is (= {} result)))))

(deftest self-accessor-missing-test
  (testing "self accessor returns complex when key missing"
    (let [result (unify/unify [:= :self/value 5] {} {:self {}})]
      (is (res/has-complex? result))
      (is (= {[:value] [[:self :missing]]} (first (get-in result [::res/complex :args])))))))

(deftest self-accessor-no-context-test
  (testing "self accessor returns complex when no self context"
    (let [result (unify/unify [:= :self/value 5] {} {})]
      (is (res/has-complex? result))
      (is (= {[:value] [[:self :missing]]} (first (get-in result [::res/complex :args])))))))

;;; ---------------------------------------------------------------------------
;;; Param Accessor Tests
;;; ---------------------------------------------------------------------------

(deftest param-accessor-found-test
  (testing "param accessor resolves from context"
    (let [result (unify/unify [:= :doc/role :param/role] {:role "admin"}
                              {:params {:role "admin"}})]
      (is (= {} result)))))

(deftest param-accessor-missing-test
  (testing "param accessor returns complex when param missing"
    (let [result (unify/unify [:= :doc/role :param/role] {:role "admin"}
                              {:params {}})]
      (is (res/has-complex? result)))))

(deftest param-accessor-no-params-test
  (testing "param accessor returns complex when no params context"
    (let [result (unify/unify [:= :doc/role :param/role] {:role "admin"} {})]
      (is (res/has-complex? result)))))

;;; ---------------------------------------------------------------------------
;;; Event Accessor Tests
;;; ---------------------------------------------------------------------------

(deftest event-accessor-found-test
  (testing "event accessor resolves from context"
    (let [result (unify/unify [:= :event/target-id "entity-1"] {}
                              {:event {:target-id "entity-1"}})]
      (is (= {} result)))))

(deftest event-accessor-nested-test
  (testing "event accessor resolves nested path"
    (let [result (unify/unify [:= :event/damage.amount 10] {}
                              {:event {:damage {:amount 10}}})]
      (is (= {} result)))))

(deftest event-accessor-missing-test
  (testing "event accessor returns complex when missing"
    (let [result (unify/unify [:= :event/target-id "x"] {} {:event {}})]
      (is (res/has-complex? result)))))

(deftest event-accessor-no-context-test
  (testing "event accessor returns complex when no event context"
    (let [result (unify/unify [:= :event/target-id "x"] {} {})]
      (is (res/has-complex? result)))))

;;; ---------------------------------------------------------------------------
;;; Let Binding Tests
;;; ---------------------------------------------------------------------------

(deftest let-binding-simple-test
  (testing "let binding evaluates body with self context"
    (let [result (unify/unify [:let [:x 5] [:= :self/x 5]] {} {})]
      (is (= {} result)))))

(deftest let-binding-from-doc-test
  (testing "let binding captures doc value"
    (let [result (unify/unify [:let [:val :doc/amount] [:> :self/val 10]]
                              {:amount 15} {})]
      (is (= {} result)))))

(deftest let-binding-conflict-test
  (testing "let binding conflict when body fails"
    (let [result (unify/unify [:let [:x 5] [:= :self/x 10]] {} {})]
      (is (res/has-complex? result)))))

(deftest let-binding-multiple-test
  (testing "let with multiple bindings"
    (let [result (unify/unify [:let [:x 1 :y 2]
                               [:and [:= :self/x 1] [:= :self/y 2]]]
                              {} {})]
      (is (= {} result)))))

(deftest let-binding-residual-test
  (testing "let binding residual when expr is residual"
    (let [result (unify/unify [:let [:val :doc/missing] [:> :self/val 10]]
                              {} {})]
      (is (res/has-complex? result))
      (is (= :let-binding-residual (get-in result [::res/complex :type]))))))

;;; ---------------------------------------------------------------------------
;;; Policy Reference Tests
;;; ---------------------------------------------------------------------------

(deftest policy-reference-no-registry-test
  (testing "policy reference returns complex when no registry"
    (let [result (unify/unify [:auth/admin] {:role "admin"} {})]
      (is (res/has-complex? result))
      (is (= :no-registry (get-in result [::res/complex :type]))))))

(deftest policy-reference-found-test
  (testing "policy reference resolves and evaluates"
    (let [registry (-> (registry/create-registry)
                       (registry/register-module :auth
                                                 {:policies {:admin [:= :doc/role "admin"]}}))
          result   (unify/unify [:auth/admin] {:role "admin"} {:registry registry})]
      (is (= {} result)))))

(deftest policy-reference-conflict-test
  (testing "policy reference conflict when policy fails"
    (let [registry (-> (registry/create-registry)
                       (registry/register-module :auth
                                                 {:policies {:admin [:= :doc/role "admin"]}}))
          result   (unify/unify [:auth/admin] {:role "guest"} {:registry registry})]
      (is (res/has-conflicts? result)))))

(deftest policy-reference-with-params-test
  (testing "policy reference with params"
    (let [registry (-> (registry/create-registry)
                       (registry/register-module :auth
                                                 {:policies {:has-role [:= :doc/role :param/role]}}))
          result   (unify/unify [:auth/has-role {:role "editor"}]
                                {:role "editor"}
                                {:registry registry})]
      (is (= {} result)))))

(deftest policy-reference-unknown-test
  (testing "policy reference returns complex when policy unknown"
    (let [registry (registry/create-registry)
          result   (unify/unify [:auth/admin] {:role "admin"} {:registry registry})]
      (is (res/has-complex? result))
      (is (= :unknown-policy (get-in result [::res/complex :type]))))))

;;; ---------------------------------------------------------------------------
;;; Phase 3: Let Binding with Collection Results
;;; ---------------------------------------------------------------------------

(deftest let-binding-stores-concrete-values-test
  (testing "let binding stores number"
    (let [result (unify/unify [:let [:x 42] [:= :self/x 42]] {})]
      (is (= {} result))))

  (testing "let binding stores string"
    (let [result (unify/unify [:let [:s "hello"] [:= :self/s "hello"]] {})]
      (is (= {} result))))

  (testing "let binding stores keyword"
    (let [result (unify/unify [:let [:k :active] [:= :self/k :active]] {})]
      (is (= {} result)))))

(deftest let-binding-extracts-doc-value-test
  (testing "let binding extracts nested doc value"
    (let [result (unify/unify
                  [:let [:count :doc/stats.total]
                   [:> :self/count 0]]
                  {:stats {:total 5}})]
      (is (= {} result)))))

(deftest let-binding-conflict-propagates-test
  (testing "let binding conflict when body fails"
    (let [result (unify/unify
                  [:let [:val :doc/amount] [:> :self/val 100]]
                  {:amount 50})]
      (is (res/has-complex? result)))))

(deftest let-binding-chained-test
  (testing "let bindings can reference previous bindings"
    (let [result (unify/unify
                  [:let [:a 10 :b :self/a]
                   [:= :self/b 10]]
                  {})]
      (is (= {} result)))))

;;; ---------------------------------------------------------------------------
;;; Phase 3: Document Computed Fields
;;; ---------------------------------------------------------------------------

(deftest computed-field-equality-test
  (testing "computed field is evaluated (even if result conflicts)"
    (let [result (unify/unify
                  [:= :doc/derived "ADMIN"]
                  {:role "admin"
                   :derived [:fn/upper :doc/role]})]
      ;; The computed field [:fn/upper :doc/role] is evaluated.
      ;; :fn/upper is unknown so produces complex marker.
      ;; Comparing that to "ADMIN" produces a conflict.
      ;; Key point: we got a conflict, not a residual - the field was evaluated.
      (is (res/has-conflicts? result)))))

(deftest computed-field-accessed-test
  (testing "static document field still works"
    (let [result (unify/unify
                  [:= :doc/role "admin"]
                  {:role "admin"})]
      (is (= {} result)))))

(deftest circular-computed-field-test
  (testing "circular dependency throws"
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error)
                          #"Circular dependency"
                          (unify/unify
                           [:= :doc/a 1]
                           {:a [:= :doc/b 1]
                            :b [:= :doc/a 1]})))))

(deftest computed-field-single-level-test
  (testing "computed field that references static field"
    (let [result (unify/unify
                  [:= :doc/check true]
                  {:base 5
                   :check [:> :doc/base 0]})]
      ;; The computed field [:> :doc/base 0] evaluates to {} (satisfied).
      ;; Comparing {} to true produces a conflict (different types).
      ;; Key point: the computed field was evaluated.
      (is (res/has-conflicts? result))))

  (testing "computed field with matching comparison"
    (let [result (unify/unify
                  [:= :doc/level 10]
                  {:base 5
                   :level [:+ :doc/base 5]})]
      ;; :+ is unknown, so this will have conflicts
      (is (res/has-conflicts? result)))))

;;; ---------------------------------------------------------------------------
;;; Phase 3: Self-Accessor Consistency
;;; ---------------------------------------------------------------------------

(deftest self-accessor-missing-returns-residual-test
  (testing "missing self key returns residual with :missing marker"
    (let [ast    (r/unwrap (parser/parse-policy :self/missing-key))
          result (unify/unify-ast ast {} {:self {:other-key 1}})]
      (is (res/residual? result))
      (is (= {[:missing-key] [[:self :missing]]} result))))

  (testing "missing self context returns residual with :missing marker"
    (let [ast    (r/unwrap (parser/parse-policy :self/any-key))
          result (unify/unify-ast ast {} {})]
      (is (res/residual? result))
      (is (= {[:any-key] [[:self :missing]]} result)))))

;;; ---------------------------------------------------------------------------
;;; Phase 4: Parameterized Policies with Defaults
;;; ---------------------------------------------------------------------------

(deftest param-accessor-unbound-test
  (testing "unbound param returns residual with :unbound marker"
    (let [ast    (r/unwrap (parser/parse-policy :param/role))
          result (unify/unify-ast ast {} {:params {} :unbound-params #{:role}})]
      (is (res/residual? result))
      (is (= {[:role] [[:param :unbound]]} result)))))

(deftest policy-reference-defaults-applied-test
  (testing "defaults applied when param not provided"
    (let [registry (-> (registry/create-registry)
                       (registry/register-module :auth
                                                 {:policies {:min-level {:expr [:> :doc/level :param/min]
                                                                         :params {:min {:default 0}}}}}))
          result   (unify/unify [:auth/min-level] {:level 5} {:registry registry})]
      (is (= {} result)))))

(deftest policy-reference-provided-overrides-default-test
  (testing "provided params override defaults"
    (let [registry (-> (registry/create-registry)
                       (registry/register-module :auth
                                                 {:policies {:min-level {:expr [:> :doc/level :param/min]
                                                                         :params {:min {:default 0}}}}}))
          result   (unify/unify [:auth/min-level {:min 10}] {:level 5} {:registry registry})]
      ;; 5 > 10 fails, so we get an op-failed complex marker
      (is (res/has-complex? result))
      (is (= :op-failed (get-in result [::res/complex :type]))))))

(deftest policy-reference-context-overrides-default-test
  (testing "context params override defaults"
    (let [registry (-> (registry/create-registry)
                       (registry/register-module :auth
                                                 {:policies {:min-level {:expr [:> :doc/level :param/min]
                                                                         :params {:min {:default 0}}}}}))
          result   (unify/unify [:auth/min-level] {:level 5}
                                {:registry registry :params {:min 10}})]
      ;; 5 > 10 fails, so we get an op-failed complex marker
      (is (res/has-complex? result))
      (is (= :op-failed (get-in result [::res/complex :type]))))))

(deftest policy-reference-provided-overrides-context-test
  (testing "provided params override context params"
    (let [registry (-> (registry/create-registry)
                       (registry/register-module :auth
                                                 {:policies {:has-role [:= :doc/role :param/role]}}))
          result   (unify/unify [:auth/has-role {:role "editor"}] {:role "editor"}
                                {:registry registry :params {:role "admin"}})]
      (is (= {} result)))))

(deftest policy-reference-nested-inherits-params-test
  (testing "nested policy references inherit params"
    (let [registry (-> (registry/create-registry)
                       (registry/register-module :auth
                                                 {:policies {:has-role [:= :doc/role :param/role]
                                                             :check [:auth/has-role]}}))
          result   (unify/unify [:auth/check] {:role "admin"}
                                {:registry registry :params {:role "admin"}})]
      (is (= {} result)))))

;;; ---------------------------------------------------------------------------
;;; Literal Wrapper Tests
;;; ---------------------------------------------------------------------------

(deftest literal-wrapper-equality-satisfied-test
  (testing "literal wrapper returns value unchanged"
    (let [result (unify/unify [:= :doc/phase [:literal :phase/ACTIONS]]
                              {:phase :phase/ACTIONS})]
      (is (= {} result)))))

(deftest literal-wrapper-equality-conflict-test
  (testing "literal wrapper creates conflict on mismatch"
    (let [result (unify/unify [:= :doc/phase [:literal :phase/ACTIONS]]
                              {:phase :phase/SETUP})]
      (is (res/has-conflicts? result)))))

(deftest literal-wrapper-residual-test
  (testing "literal wrapper returns residual when doc key missing"
    (let [result (unify/unify [:= :doc/phase [:literal :phase/ACTIONS]] {})]
      (is (res/residual? result))
      (is (= {[:phase] [[:= :phase/ACTIONS]]} result)))))

(deftest literal-wrapper-in-condition-test
  (testing "literal wrapper in AND condition"
    (let [result (unify/unify [:and [:= :doc/phase [:literal :phase/ACTIONS]]
                               [:= :doc/active true]]
                              {:phase :phase/ACTIONS :active true})]
      (is (= {} result)))))

;;; ---------------------------------------------------------------------------
;;; Document Constraint Composition Tests
;;; ---------------------------------------------------------------------------

(deftest document-constraint-composition-test
  (testing "document with constraint composes with policy"
    (let [doc    {[:level] [[:> 5]]}
          policy [:< :doc/level 10]
          result (unify/unify policy doc)]
      (is (res/residual? result))
      (is (= {[:level] [[:> 5] [:< 10]]} result))))

  (testing "multiple document constraints compose with policy"
    (let [doc    {[:level] [[:> 0] [:< 100]]}
          policy [:> :doc/level 5]
          result (unify/unify policy doc)]
      (is (res/residual? result))
      (is (= {[:level] [[:> 0] [:< 100] [:> 5]]} result)))))

(deftest document-literal-evaluates-test
  (testing "document with literal value evaluates directly"
    (let [doc    {:level 7}
          policy [:> :doc/level 5]
          result (unify/unify policy doc)]
      (is (= {} result))))

  (testing "document with literal violating policy produces conflict"
    (let [doc    {:level 3}
          policy [:> :doc/level 5]
          result (unify/unify policy doc)]
      (is (res/has-conflicts? result)))))

(deftest document-literal-and-constraint-test
  (testing "document with literal and constraint - all satisfied"
    (let [doc    {:level 7 [:level] [[:> 0]]}
          policy [:< :doc/level 10]
          result (unify/unify policy doc)]
      (is (= {} result))))

  (testing "document with literal violating doc constraint"
    (let [doc    {:level 3 [:level] [[:> 5]]}
          policy [:< :doc/level 10]
          result (unify/unify policy doc)]
      (is (res/has-conflicts? result))))

  (testing "document with literal violating policy constraint"
    (let [doc    {:level 15 [:level] [[:> 0]]}
          policy [:< :doc/level 10]
          result (unify/unify policy doc)]
      (is (res/has-conflicts? result)))))

(deftest document-constraint-nested-path-test
  (testing "nested path constraint composes"
    (let [doc    {[:user :level] [[:> 5]]}
          policy [:< :doc/user.level 10]
          result (unify/unify policy doc)]
      (is (res/residual? result))
      (is (= {[:user :level] [[:> 5] [:< 10]]} result)))))
