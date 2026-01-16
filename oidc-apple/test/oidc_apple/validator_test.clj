(ns oidc-apple.validator-test
  (:require
   [authn.protocol :as proto]
   [clojure.test :refer [deftest is testing]]
   [oidc-apple.validator :as v]))

(deftest apple-credential-validator-creation-test
  (testing "creates validator that implements CredentialValidator"
    (let [validator (v/create-credential-validator "com.example.app")]
      (is (satisfies? proto/CredentialValidator validator)))))

(deftest apple-credential-validator-test
  (testing "returns nil for missing id-token"
    (let [validator (v/create-credential-validator "com.example.app")]
      (is (nil? (proto/validate-credentials validator {} nil)))
      (is (nil? (proto/validate-credentials validator {:access-token "foo"} nil)))))

  (testing "returns nil for invalid token"
    (let [validator (v/create-credential-validator "com.example.app")]
      ;; Invalid token should return nil, not throw
      (is (nil? (proto/validate-credentials
                 validator
                 {:id-token "invalid.token.here"}
                 nil))))))

(deftest apple-claims-provider-creation-test
  (testing "creates provider that implements ClaimsProvider"
    (let [provider (v/create-claims-provider "com.example.app")]
      (is (satisfies? proto/ClaimsProvider provider)))))

(deftest apple-claims-provider-test
  (testing "returns base claims for user-id"
    (let [provider (v/create-claims-provider "com.example.app")
          claims   (proto/get-claims provider "apple-user-123" ["email"])]
      (is (= "apple-user-123" (:sub claims)))
      (is (true? (:apple_provider claims))))))
