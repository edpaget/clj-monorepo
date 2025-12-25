(ns polix.bytecode-test
  "Tests for JVM bytecode generation."
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.bytecode.class-generator :as cg]
   [polix.compiler :as c]
   [polix.optimized.evaluator :as opt]))

(defn- compile-policy
  "Helper to compile a policy expression to constraint set."
  [expr]
  (:simplified (c/merge-policies [expr])))

(deftest bytecode-eligible?-test
  (testing "simple equality is bytecode-eligible"
    (is (cg/bytecode-eligible? (compile-policy [:= :doc/role "admin"]))))

  (testing "comparison operators are bytecode-eligible"
    (is (cg/bytecode-eligible? (compile-policy [:> :doc/age 18])))
    (is (cg/bytecode-eligible? (compile-policy [:< :doc/count 100])))
    (is (cg/bytecode-eligible? (compile-policy [:>= :doc/level 5])))
    (is (cg/bytecode-eligible? (compile-policy [:<= :doc/score 10]))))

  (testing "in/not-in operators are bytecode-eligible"
    (is (cg/bytecode-eligible? (compile-policy [:in :doc/status #{"active" "pending"}]))))

  (testing "matches/not-matches are bytecode-eligible"
    (is (cg/bytecode-eligible? (compile-policy [:matches :doc/email ".*@example\\.com"])))))

(deftest generate-policy-class-equality-test
  (testing "string equality - satisfied"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:= :doc/role "admin"]))]
      (is (= {} (policy-fn {:role "admin"})))))

  (testing "string equality - conflict"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:= :doc/role "admin"]))]
      (is (= {[:role] [[:conflict [:= "admin"] "guest"]]}
             (policy-fn {:role "guest"})))))

  (testing "string equality - open"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:= :doc/role "admin"]))]
      (is (= {[:role] [[:= "admin"]]}
             (policy-fn {}))))))

(deftest generate-policy-class-comparison-test
  (testing "greater than - satisfied"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:> :doc/age 18]))]
      (is (= {} (policy-fn {:age 21})))))

  (testing "greater than - conflict"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:> :doc/age 18]))]
      (is (contains? (policy-fn {:age 15}) [:age]))))

  (testing "less than - satisfied"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:< :doc/count 100]))]
      (is (= {} (policy-fn {:count 50})))))

  (testing "greater-or-equal - boundary"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:>= :doc/level 5]))]
      (is (= {} (policy-fn {:level 5})))
      (is (= {} (policy-fn {:level 6})))
      (is (contains? (policy-fn {:level 4}) [:level]))))

  (testing "less-or-equal - boundary"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:<= :doc/score 10]))]
      (is (= {} (policy-fn {:score 10})))
      (is (= {} (policy-fn {:score 5})))
      (is (contains? (policy-fn {:score 11}) [:score])))))

(deftest generate-policy-class-in-test
  (testing "in set - satisfied"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:in :doc/status #{"active" "pending"}]))]
      (is (= {} (policy-fn {:status "active"})))
      (is (= {} (policy-fn {:status "pending"})))))

  (testing "in set - conflict"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:in :doc/status #{"active" "pending"}]))]
      (is (contains? (policy-fn {:status "inactive"}) [:status]))))

  (testing "not-in set - satisfied"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:not-in :doc/role #{"guest" "banned"}]))]
      (is (= {} (policy-fn {:role "admin"})))))

  (testing "not-in set - conflict"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:not-in :doc/role #{"guest" "banned"}]))]
      (is (contains? (policy-fn {:role "guest"}) [:role])))))

(deftest generate-policy-class-matches-test
  (testing "regex matches - satisfied"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:matches :doc/email ".*@example\\.com"]))]
      (is (= {} (policy-fn {:email "user@example.com"})))))

  (testing "regex matches - conflict"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:matches :doc/email ".*@example\\.com"]))]
      (is (contains? (policy-fn {:email "user@other.com"}) [:email]))))

  (testing "regex not-matches - satisfied"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:not-matches :doc/name "^admin.*"]))]
      (is (= {} (policy-fn {:name "user123"})))))

  (testing "regex not-matches - conflict"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:not-matches :doc/name "^admin.*"]))]
      (is (contains? (policy-fn {:name "admin_user"}) [:name])))))

(deftest generate-policy-class-nested-path-test
  (testing "nested path - satisfied"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:= :doc/user.role "admin"]))]
      ;; :doc/user.role becomes path [:user :role] - nested document access
      (is (= {} (policy-fn {:user {:role "admin"}})))))

  (testing "nested path - conflict"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:= :doc/user.role "admin"]))]
      (is (contains? (policy-fn {:user {:role "guest"}}) [:user :role]))))

  (testing "nested path - open"
    (let [policy-fn (cg/generate-policy-class (compile-policy [:= :doc/user.role "admin"]))]
      (is (contains? (policy-fn {}) [:user :role])))))

(deftest compile-policy-tier-selection-test
  (testing "auto-selects T3 for eligible policies"
    (let [compiled (opt/compile-policy (compile-policy [:= :doc/role "admin"]))]
      (is (= :t3 (opt/compilation-tier compiled)))))

  (testing "can force T2 tier"
    (let [compiled (opt/compile-policy (compile-policy [:= :doc/role "admin"]) {:tier :t2})]
      (is (= :t2 (opt/compilation-tier compiled)))))

  (testing "bytecode disabled falls back to T2"
    (let [compiled (opt/compile-policy (compile-policy [:= :doc/role "admin"]) {:bytecode false})]
      (is (= :t2 (opt/compilation-tier compiled))))))

(deftest bytecode-vs-closure-equivalence-test
  (testing "bytecode and closure produce identical results"
    (let [constraint-set (compile-policy [:and [:= :doc/role "admin"]
                                          [:> :doc/level 5]])
          t3             (opt/compile-policy constraint-set {:tier :t3})
          t2             (opt/compile-policy constraint-set {:tier :t2})
          docs           [{:role "admin" :level 10}
                          {:role "admin" :level 3}
                          {:role "guest" :level 10}
                          {:role "admin"}
                          {:level 10}
                          {}]]
      (doseq [doc docs]
        (is (= (opt/evaluate t2 doc) (opt/evaluate t3 doc))
            (str "Mismatch for doc: " doc))))))
