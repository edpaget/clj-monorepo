(ns polix.loader-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.loader :as loader]
   [polix.registry :as reg]))

;;; ---------------------------------------------------------------------------
;;; Cycle Detection Tests
;;; ---------------------------------------------------------------------------

(deftest detect-cycle-no-cycle-test
  (testing "no cycle in simple graph"
    (is (nil? (loader/detect-cycle {:a #{:b} :b #{:c} :c #{}})))))

(deftest detect-cycle-self-loop-test
  (testing "self loop detected"
    (is (some? (loader/detect-cycle {:a #{:a}})))))

(deftest detect-cycle-two-node-test
  (testing "two node cycle detected"
    (let [cycle (loader/detect-cycle {:a #{:b} :b #{:a}})]
      (is (some? cycle))
      (is (= (first cycle) (last cycle))))))

(deftest detect-cycle-three-node-test
  (testing "three node cycle detected"
    (let [cycle (loader/detect-cycle {:a #{:b} :b #{:c} :c #{:a}})]
      (is (some? cycle))
      (is (= (first cycle) (last cycle))))))

(deftest detect-cycle-disconnected-test
  (testing "disconnected graph with no cycle"
    (is (nil? (loader/detect-cycle {:a #{:b} :b #{} :c #{:d} :d #{}})))))

;;; ---------------------------------------------------------------------------
;;; Topological Sort Tests
;;; ---------------------------------------------------------------------------

(deftest topological-sort-simple-test
  (testing "simple linear dependency"
    (let [result (loader/topological-sort {:a #{:b} :b #{:c} :c #{}})]
      (is (some? result))
      ;; :c before :b before :a
      (is (< (.indexOf result :c) (.indexOf result :b)))
      (is (< (.indexOf result :b) (.indexOf result :a))))))

(deftest topological-sort-no-deps-test
  (testing "no dependencies"
    (let [result (loader/topological-sort {:a #{} :b #{} :c #{}})]
      (is (= 3 (count result)))
      (is (= #{:a :b :c} (set result))))))

(deftest topological-sort-diamond-test
  (testing "diamond dependency"
    (let [result (loader/topological-sort {:a #{:b :c} :b #{:d} :c #{:d} :d #{}})]
      (is (some? result))
      ;; :d must come before :b and :c
      (is (< (.indexOf result :d) (.indexOf result :b)))
      (is (< (.indexOf result :d) (.indexOf result :c)))
      ;; :b and :c must come before :a
      (is (< (.indexOf result :b) (.indexOf result :a)))
      (is (< (.indexOf result :c) (.indexOf result :a))))))

;;; ---------------------------------------------------------------------------
;;; Load Module Tests
;;; ---------------------------------------------------------------------------

(deftest load-module-single-test
  (testing "loads single module"
    (let [registry (loader/load-module (reg/create-registry)
                                       {:namespace :auth
                                        :policies {:admin [:= :doc/role "admin"]}})]
      (is (= [:= :doc/role "admin"]
             (reg/resolve-policy registry :auth :admin))))))

(deftest load-module-with-imports-test
  (testing "loads module with imports metadata"
    (let [registry (loader/load-module (reg/create-registry)
                                       {:namespace :auth
                                        :imports [:common]
                                        :policies {:admin [:= :doc/role "admin"]}})]
      (is (= [:common] (get-in (:entries registry) [:auth :imports]))))))

;;; ---------------------------------------------------------------------------
;;; Load Modules Tests
;;; ---------------------------------------------------------------------------

(deftest load-modules-simple-test
  (testing "loads multiple modules"
    (let [modules            [{:namespace :common
                               :policies {:active [:= :doc/status "active"]}}
                              {:namespace :auth
                               :policies {:admin [:= :doc/role "admin"]}}]
          {:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
      (is (nil? error))
      (is (some? ok))
      (is (= #{:common :auth} (reg/module-namespaces ok))))))

(deftest load-modules-with-imports-test
  (testing "loads modules with dependencies in order"
    (let [modules            [{:namespace :auth
                               :imports [:common]
                               :policies {:admin-active [:and [:common/active]
                                                         [:= :doc/role "admin"]]}}
                              {:namespace :common
                               :policies {:active [:= :doc/status "active"]}}]
          {:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
      (is (nil? error))
      (is (some? ok))
      (is (= [:and [:common/active] [:= :doc/role "admin"]]
             (reg/resolve-policy ok :auth :admin-active))))))

(deftest load-modules-circular-test
  (testing "rejects circular imports"
    (let [modules            [{:namespace :a :imports [:b] :policies {}}
                              {:namespace :b :imports [:c] :policies {}}
                              {:namespace :c :imports [:a] :policies {}}]
          {:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
      (is (nil? ok))
      (is (= :circular-import (:type error))))))

(deftest load-modules-missing-import-test
  (testing "rejects missing imports"
    (let [modules            [{:namespace :auth
                               :imports [:common]
                               :policies {:admin [:= :doc/role "admin"]}}]
          {:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
      (is (nil? ok))
      (is (= :missing-imports (:type error)))
      (is (= [{:module :auth :missing :common}] (:details error))))))

(deftest load-modules-duplicate-namespace-test
  (testing "rejects duplicate namespaces"
    (let [modules            [{:namespace :auth :policies {:a 1}}
                              {:namespace :auth :policies {:b 2}}]
          {:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
      (is (nil? ok))
      (is (= :duplicate-namespaces (:type error))))))

(deftest load-modules-invalid-module-test
  (testing "rejects invalid module definition"
    (let [modules            [{:namespace "not-a-keyword" :policies {}}]
          {:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
      (is (nil? ok))
      (is (= :invalid-namespace (:error error))))))

(deftest load-modules-invalid-imports-test
  (testing "rejects invalid imports"
    (let [modules            [{:namespace :auth :imports "not-a-vector" :policies {}}]
          {:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
      (is (nil? ok))
      (is (= :invalid-imports (:error error))))))

(deftest load-modules-invalid-policies-test
  (testing "rejects invalid policies"
    (let [modules            [{:namespace :auth :policies "not-a-map"}]
          {:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
      (is (nil? ok))
      (is (= :invalid-policies (:error error))))))

;;; ---------------------------------------------------------------------------
;;; Integration Tests
;;; ---------------------------------------------------------------------------

(deftest integration-policy-reference-test
  (testing "loaded modules work with policy references"
    (let [modules      [{:namespace :common
                         :policies {:active [:= :doc/status "active"]}}
                        {:namespace :auth
                         :imports [:common]
                         :policies {:admin-active [:and [:common/active]
                                                   [:= :doc/role "admin"]]}}]
          {:keys [ok]} (loader/load-modules (reg/create-registry) modules)]
      (is (some? ok))
      ;; The policy references other policies - this is the structure test
      (is (= [:and [:common/active] [:= :doc/role "admin"]]
             (reg/resolve-policy ok :auth :admin-active))))))

(deftest integration-diamond-deps-test
  (testing "diamond dependencies load correctly"
    (let [modules            [{:namespace :d
                               :policies {:base [:= :doc/x 1]}}
                              {:namespace :b
                               :imports [:d]
                               :policies {:b-policy [:and [:d/base] [:= :doc/y 2]]}}
                              {:namespace :c
                               :imports [:d]
                               :policies {:c-policy [:and [:d/base] [:= :doc/z 3]]}}
                              {:namespace :a
                               :imports [:b :c]
                               :policies {:a-policy [:and [:b/b-policy] [:c/c-policy]]}}]
          {:keys [ok error]} (loader/load-modules (reg/create-registry) modules)]
      (is (nil? error))
      (is (= #{:a :b :c :d} (reg/module-namespaces ok))))))
