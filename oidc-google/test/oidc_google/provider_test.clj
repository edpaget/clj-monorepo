(ns oidc-google.provider-test
  (:require
   [authn.protocol :as proto]
   [clojure.test :refer [deftest is testing]]
   [oidc-google.claims :as claims]
   [oidc-google.client :as client]
   [oidc-google.provider :as provider]))

(def ^:private sample-userinfo
  {:sub "123456789"
   :name "John Doe"
   :email "john@example.com"
   :email_verified true
   :picture "https://example.com/photo.jpg"})

(deftest credential-validator-test
  (testing "validates with id-token"
    (with-redefs [client/validate-id-token (fn [token client-id]
                                             (when (= "valid-token" token)
                                               {:sub "123" :email "test@example.com"}))]
      (let [config    {:client-id "test-id" :client-secret "test-secret"}
            validator (provider/create-credential-validator config 300000)]
        (is (= "123" (proto/validate-credentials validator {:id-token "valid-token"} nil)))
        (is (nil? (proto/validate-credentials validator {:id-token "invalid"} nil))))))

  (testing "validates with access-token"
    (with-redefs [claims/fetch-userinfo (fn [token]
                                          (when (= "valid-access-token" token)
                                            sample-userinfo))]
      (let [config    {:client-id "test-id" :client-secret "test-secret"}
            validator (provider/create-credential-validator config 300000)]
        (is (= "123456789" (proto/validate-credentials validator {:access-token "valid-access-token"} nil))))))

  (testing "validates with code"
    (with-redefs [client/exchange-code     (fn [_config code]
                                             (when (= "valid-code" code)
                                               {:access_token "token"
                                                :id_token "id-token"}))
                  client/validate-id-token (fn [_token _client-id]
                                             {:sub "123"})]
      (let [config    {:client-id "test-id" :client-secret "test-secret"}
            validator (provider/create-credential-validator config 300000)]
        (is (= "123" (proto/validate-credentials validator {:code "valid-code"} nil))))))

  (testing "returns nil for invalid credentials"
    (let [config    {:client-id "test-id" :client-secret "test-secret"}
          validator (provider/create-credential-validator config 300000)]
      (is (nil? (proto/validate-credentials validator {} nil)))
      (is (nil? (proto/validate-credentials validator {:unknown "value"} nil))))))

(deftest claims-provider-test
  (testing "provides claims filtered by scope"
    (with-redefs [claims/fetch-userinfo (constantly sample-userinfo)]
      (let [provider (provider/create-claims-provider 300000)
            claims   (proto/get-claims provider "access-token" ["profile" "email"])]
        (is (= "123456789" (:sub claims)))
        (is (= "John Doe" (:name claims)))
        (is (= "john@example.com" (:email claims))))))

  (testing "filters claims based on scope"
    (with-redefs [claims/fetch-userinfo (constantly sample-userinfo)]
      (let [provider     (provider/create-claims-provider 300000)
            profile-only (proto/get-claims provider "access-token" ["profile"])
            email-only   (proto/get-claims provider "access-token" ["email"])]
        (is (= "John Doe" (:name profile-only)))
        (is (nil? (:email profile-only)))
        (is (= "john@example.com" (:email email-only)))
        (is (nil? (:name email-only)))))))

(deftest create-credential-validator-test
  (testing "creates validator record"
    (let [config    {:client-id "test-id" :client-secret "test-secret"}
          validator (provider/create-credential-validator config 300000)]
      (is (instance? oidc_google.provider.GoogleCredentialValidator validator)))))

(deftest create-claims-provider-test
  (testing "creates claims provider record"
    (let [provider (provider/create-claims-provider 300000)]
      (is (instance? oidc_google.provider.GoogleClaimsProvider provider)))))
