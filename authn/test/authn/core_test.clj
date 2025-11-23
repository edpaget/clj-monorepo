(ns authn.core-test
  (:require
   [authn.core :as core]
   [authn.protocol :as proto]
   [authn.store :as store]
   [clojure.test :refer [deftest is testing]]))

(defrecord TestValidator []
  proto/CredentialValidator
  (validate-credentials [_this credentials _client-id]
    (when (and (= (:username credentials) "testuser")
               (= (:password credentials) "testpass"))
      "user-123")))

(defrecord TestClaimsProvider []
  proto/ClaimsProvider
  (get-claims [_this user-id scope]
    (cond-> {:sub user-id}
      (some #{"profile"} scope)
      (assoc :name "Test User")

      (some #{"email"} scope)
      (assoc :email "test@example.com"))))

(deftest create-authenticator-test
  (testing "creates authenticator with required config"
    (let [auth (core/create-authenticator
                {:credential-validator (->TestValidator)
                 :claims-provider (->TestClaimsProvider)})]
      (is (some? auth))
      (is (some? (:session-store auth)))))

  (testing "uses provided session store"
    (let [custom-store (store/create-session-store)
          auth (core/create-authenticator
                {:credential-validator (->TestValidator)
                 :claims-provider (->TestClaimsProvider)
                 :session-store custom-store})]
      (is (= custom-store (:session-store auth))))))

(deftest authenticate-test
  (let [auth (core/create-authenticator
              {:credential-validator (->TestValidator)
               :claims-provider (->TestClaimsProvider)})]

    (testing "authenticates valid credentials"
      (let [session-id (core/authenticate auth
                                          {:username "testuser"
                                           :password "testpass"}
                                          ["profile" "email"])]
        (is (string? session-id))
        (let [session (core/get-session auth session-id)]
          (is (= "user-123" (:user-id session)))
          (is (= "Test User" (get-in session [:claims :name])))
          (is (= "test@example.com" (get-in session [:claims :email]))))))

    (testing "rejects invalid credentials"
      (is (nil? (core/authenticate auth
                                   {:username "testuser"
                                    :password "wrongpass"}))))))

(deftest logout-test
  (let [auth (core/create-authenticator
              {:credential-validator (->TestValidator)
               :claims-provider (->TestClaimsProvider)})
        session-id (core/authenticate auth
                                      {:username "testuser"
                                       :password "testpass"})]

    (testing "destroys session"
      (is (some? (core/get-session auth session-id)))
      (is (true? (core/logout auth session-id)))
      (is (nil? (core/get-session auth session-id))))))

(deftest refresh-session-test
  (let [auth (core/create-authenticator
              {:credential-validator (->TestValidator)
               :claims-provider (->TestClaimsProvider)
               :session-ttl-ms 1000})
        session-id (core/authenticate auth
                                      {:username "testuser"
                                       :password "testpass"})
        original-session (core/get-session auth session-id)
        original-expires (:expires-at original-session)]

    (testing "refreshes session expiration"
      (Thread/sleep 100)
      (is (true? (core/refresh-session auth session-id)))
      (let [refreshed-session (core/get-session auth session-id)]
        (is (> (:expires-at refreshed-session) original-expires))))))
