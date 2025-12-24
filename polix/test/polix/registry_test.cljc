(ns polix.registry-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.registry :as reg]))

(use-fixtures :each
  (fn [f]
    (reg/reset-global-registry!)
    (f)))

(deftest create-registry-test
  (testing "creates registry with built-in namespaces"
    (let [registry (reg/create-registry)]
      (is (= 1 (reg/registry-version registry)))
      (is (= :document-accessor (:type (reg/resolve-namespace registry :doc))))
      (is (= :builtins (:type (reg/resolve-namespace registry :fn))))
      (is (= :self-accessor (:type (reg/resolve-namespace registry :self))))
      (is (= :param-accessor (:type (reg/resolve-namespace registry :param))))
      (is (= :event-accessor (:type (reg/resolve-namespace registry :event)))))))

(deftest register-module-test
  (testing "registers a module"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:admin [:= :doc/role "admin"]}}))]
      (is (= 2 (reg/registry-version registry)))
      (is (= :module (:type (reg/resolve-namespace registry :auth))))
      (is (= [:= :doc/role "admin"]
             (reg/resolve-policy registry :auth :admin)))))

  (testing "increments version on each registration"
    (let [r1 (reg/create-registry)
          r2 (reg/register-module r1 :auth {:policies {}})
          r3 (reg/register-module r2 :perms {:policies {}})]
      (is (= 1 (reg/registry-version r1)))
      (is (= 2 (reg/registry-version r2)))
      (is (= 3 (reg/registry-version r3)))))

  (testing "throws on reserved namespace"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                             :cljs ExceptionInfo)
                          #"reserved namespace"
                          (reg/register-module (reg/create-registry) :doc {:policies {}})))))

(deftest register-alias-test
  (testing "registers an alias to existing module"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth {:policies {:admin [:= :doc/role "admin"]}})
                       (reg/register-alias :a :auth))]
      (is (= :module (:type (reg/resolve-namespace registry :a))))
      (is (= [:= :doc/role "admin"]
             (reg/resolve-policy registry :a :admin)))))

  (testing "throws on reserved namespace"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth {:policies {}}))]
      (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                               :cljs ExceptionInfo)
                            #"reserved namespace"
                            (reg/register-alias registry :doc :auth)))))

  (testing "throws when target does not exist"
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo
                             :cljs ExceptionInfo)
                          #"target does not exist"
                          (reg/register-alias (reg/create-registry) :a :nonexistent)))))

(deftest unregister-module-test
  (testing "removes module from registry"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth {:policies {:admin [:= :doc/role "admin"]}})
                       (reg/unregister-module :auth))]
      (is (nil? (reg/resolve-namespace registry :auth)))
      (is (nil? (reg/resolve-policy registry :auth :admin))))))

(deftest resolve-namespace-test
  (testing "returns nil for unknown namespace"
    (is (nil? (reg/resolve-namespace (reg/create-registry) :unknown))))

  (testing "follows alias to target"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth {:policies {}})
                       (reg/register-alias :a :auth))]
      (is (= (reg/resolve-namespace registry :auth)
             (reg/resolve-namespace registry :a))))))

(deftest resolve-policy-test
  (testing "returns nil for non-module namespace"
    (is (nil? (reg/resolve-policy (reg/create-registry) :doc :anything))))

  (testing "returns nil for unknown policy"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth {:policies {:admin [:= :doc/role "admin"]}}))]
      (is (nil? (reg/resolve-policy registry :auth :unknown))))))

(deftest resolve-reference-test
  (testing "resolves namespaced keyword"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth {:policies {:admin [:= :doc/role "admin"]}}))]
      (let [result (reg/resolve-reference registry :auth/admin)]
        (is (= :auth (:namespace result)))
        (is (= :admin (:name result)))
        (is (= :module (:type (:entry result)))))))

  (testing "returns nil for unqualified keyword"
    (is (nil? (reg/resolve-reference (reg/create-registry) :admin))))

  (testing "returns nil for unknown namespace"
    (is (nil? (reg/resolve-reference (reg/create-registry) :unknown/policy)))))

(deftest reserved-namespace-test
  (testing "reserved-namespace? returns true for built-ins"
    (is (reg/reserved-namespace? :doc))
    (is (reg/reserved-namespace? :fn))
    (is (reg/reserved-namespace? :self))
    (is (reg/reserved-namespace? :param))
    (is (reg/reserved-namespace? :event)))

  (testing "reserved-namespace? returns false for user namespaces"
    (is (not (reg/reserved-namespace? :auth)))
    (is (not (reg/reserved-namespace? :custom)))))

(deftest global-registry-test
  (testing "init-global-registry! is idempotent"
    (let [r1 (reg/init-global-registry!)
          r2 (reg/init-global-registry!)]
      (is (= (reg/registry-version r1) (reg/registry-version r2)))))

  (testing "register-module! modifies global registry"
    (reg/register-module! :auth {:policies {:admin [:= :doc/role "admin"]}})
    (let [global (reg/get-global-registry)]
      (is (= [:= :doc/role "admin"]
             (reg/resolve-policy global :auth :admin)))))

  (testing "reset-global-registry! clears modules"
    (reg/register-module! :auth {:policies {}})
    (reg/reset-global-registry!)
    (is (nil? (reg/resolve-namespace (reg/get-global-registry) :auth)))))

(deftest module-namespaces-test
  (testing "returns module namespace keys"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth {:policies {}})
                       (reg/register-module :perms {:policies {}})
                       (reg/register-alias :a :auth))]
      (is (= #{:auth :perms} (reg/module-namespaces registry))))))

(deftest alias-namespaces-test
  (testing "returns alias keys"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth {:policies {}})
                       (reg/register-alias :a :auth)
                       (reg/register-alias :au :auth))]
      (is (= #{:a :au} (reg/alias-namespaces registry))))))

(deftest all-policies-test
  (testing "returns qualified policy map"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:admin [:= :doc/role "admin"]
                                                        :user [:= :doc/role "user"]}})
                       (reg/register-module :perms
                                            {:policies {:read [:= :doc/can-read true]}}))]
      (is (= {:auth/admin [:= :doc/role "admin"]
              :auth/user [:= :doc/role "user"]
              :perms/read [:= :doc/can-read true]}
             (reg/all-policies registry))))))

(deftest validate-module-test
  (testing "valid module returns :ok"
    (let [result (reg/validate-module {:policies {:admin [:= :doc/role "admin"]}})]
      (is (:ok result))))

  (testing "invalid module returns :error"
    (let [result (reg/validate-module {:policies "not-a-map"})]
      (is (:error result)))))

;;; ---------------------------------------------------------------------------
;;; Rich Policy Definition Tests (Phase 4)
;;; ---------------------------------------------------------------------------

(deftest rich-policy-def-backwards-compatible-test
  (testing "simple policy expression still works"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:admin [:= :doc/role "admin"]}}))]
      (is (= [:= :doc/role "admin"]
             (reg/resolve-policy registry :auth :admin))))))

(deftest rich-policy-def-with-expr-test
  (testing "rich policy def with :expr returns expression"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:admin {:expr [:= :doc/role "admin"]
                                                                :description "Admin check"}}}))]
      (is (= [:= :doc/role "admin"]
             (reg/resolve-policy registry :auth :admin))))))

(deftest policy-info-simple-test
  (testing "policy-info for simple policy"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:admin [:= :doc/role "admin"]}}))]
      (let [info (reg/policy-info registry :auth :admin)]
        (is (= [:= :doc/role "admin"] (:expr info)))
        (is (= #{} (:params info)))
        (is (= {} (:defaults info)))
        (is (false? (:parameterized? info)))))))

(deftest policy-info-parameterized-test
  (testing "policy-info for parameterized policy"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:has-role [:= :doc/role :param/role]}}))]
      (let [info (reg/policy-info registry :auth :has-role)]
        (is (= [:= :doc/role :param/role] (:expr info)))
        (is (= #{:role} (:params info)))
        (is (= {} (:defaults info)))
        (is (true? (:parameterized? info)))))))

(deftest policy-info-with-defaults-test
  (testing "policy-info extracts defaults"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:min-level {:expr [:> :doc/level :param/min]
                                                                    :params {:min {:default 0}}}}}))]
      (let [info (reg/policy-info registry :auth :min-level)]
        (is (= [:> :doc/level :param/min] (:expr info)))
        (is (= #{:min} (:params info)))
        (is (= {:min 0} (:defaults info)))
        (is (true? (:parameterized? info)))))))

(deftest policy-info-with-description-test
  (testing "policy-info includes description"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:admin {:expr [:= :doc/role "admin"]
                                                                :description "Admin check"}}}))]
      (let [info (reg/policy-info registry :auth :admin)]
        (is (= "Admin check" (:description info)))))))

(deftest param-defaults-test
  (testing "param-defaults extracts default values"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:min-level {:expr [:> :doc/level :param/min]
                                                                    :params {:min {:default 0}}}}}))]
      (is (= {:min 0} (reg/param-defaults registry :auth :min-level)))))
  (testing "param-defaults returns empty map for no defaults"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:has-role [:= :doc/role :param/role]}}))]
      (is (= {} (reg/param-defaults registry :auth :has-role))))))

(deftest parameterized-policies-test
  (testing "returns parameterized policies in module"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:admin [:= :doc/role "admin"]
                                                        :has-role [:= :doc/role :param/role]
                                                        :min-level {:expr [:> :doc/level :param/min]
                                                                    :params {:min {:default 0}}
                                                                    :description "Min level check"}}}))]
      (let [params (reg/parameterized-policies registry :auth)]
        (is (= #{:has-role :min-level} (set (keys params))))
        (is (= #{:role} (get-in params [:has-role :params])))
        (is (= #{:min} (get-in params [:min-level :params])))
        (is (= {:min 0} (get-in params [:min-level :defaults])))
        (is (= "Min level check" (get-in params [:min-level :description])))))))

(deftest all-policies-with-rich-defs-test
  (testing "all-policies extracts expressions from rich defs"
    (let [registry (-> (reg/create-registry)
                       (reg/register-module :auth
                                            {:policies {:admin {:expr [:= :doc/role "admin"]
                                                                :description "Admin check"}
                                                        :user [:= :doc/role "user"]}}))]
      (is (= {:auth/admin [:= :doc/role "admin"]
              :auth/user [:= :doc/role "user"]}
             (reg/all-policies registry))))))
