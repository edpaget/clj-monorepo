(ns authn.middleware-test
  (:require
   [authn.core :as core]
   [authn.middleware :as mw]
   [authn.protocol :as proto]
   [clojure.test :refer [deftest is testing]]))

(defrecord TestValidator []
  proto/CredentialValidator
  (validate-credentials [_this credentials _client-id]
    (when (and (= (:username credentials) "testuser")
               (= (:password credentials) "testpass"))
      "user-123")))

(defrecord TestClaimsProvider []
  proto/ClaimsProvider
  (get-claims [_this user-id _scope]
    {:sub user-id :name "Test User"}))

(defn- test-handler [request]
  {:status 200
   :body {:authenticated? (:authn/authenticated? request)
          :user-id (:authn/user-id request)}})

(deftest wrap-authentication-test
  (let [auth (core/create-authenticator
              {:credential-validator (->TestValidator)
               :claims-provider (->TestClaimsProvider)})
        handler (mw/wrap-authentication test-handler auth)
        session-id (core/authenticate auth
                                      {:username "testuser"
                                       :password "testpass"})]

    (testing "adds user info for valid session"
      (let [request {:cookies {"session-id" {:value session-id}}}
            response (handler request)]
        (is (= 200 (:status response)))
        (is (true? (get-in response [:body :authenticated?])))
        (is (= "user-123" (get-in response [:body :user-id])))))

    (testing "marks request as unauthenticated for missing session"
      (let [request {:cookies {}}
            response (handler request)]
        (is (= 200 (:status response)))
        (is (false? (get-in response [:body :authenticated?])))
        (is (nil? (get-in response [:body :user-id])))))))

(deftest wrap-require-authentication-test
  (let [auth (core/create-authenticator
              {:credential-validator (->TestValidator)
               :claims-provider (->TestClaimsProvider)})
        handler (-> test-handler
                    mw/wrap-require-authentication
                    (mw/wrap-authentication auth))]

    (testing "allows authenticated requests"
      (let [session-id (core/authenticate auth
                                          {:username "testuser"
                                           :password "testpass"})
            request {:cookies {"session-id" {:value session-id}}}
            response (handler request)]
        (is (= 200 (:status response)))))

    (testing "blocks unauthenticated requests"
      (let [request {:cookies {}}
            response (handler request)]
        (is (= 401 (:status response)))))))
