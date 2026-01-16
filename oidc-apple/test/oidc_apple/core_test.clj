(ns oidc-apple.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [oidc-apple.core :as apple]))

(deftest validate-config-test
  (testing "accepts valid config with client-id"
    (let [config {:client-id "com.example.app"}]
      (is (= config (apple/validate-config config)))))

  (testing "accepts config with all optional fields"
    (let [config {:client-id "com.example.app"
                  :team-id "ABCD1234"
                  :key-id "KEY123"
                  :private-key "-----BEGIN PRIVATE KEY-----\n..."}]
      (is (= config (apple/validate-config config)))))

  (testing "rejects config without client-id"
    (is (thrown? Exception (apple/validate-config {})))))

(deftest create-apple-authenticator-test
  (testing "creates authenticator with required components"
    (let [auth (apple/create-apple-authenticator {:client-id "com.example.app"})]
      (is (map? auth))
      (is (contains? auth :credential-validator))
      (is (contains? auth :claims-provider))
      (is (contains? auth :config))
      (is (some? (:credential-validator auth)))
      (is (some? (:claims-provider auth))))))

(deftest validate-id-token-test
  (testing "returns error for invalid token"
    (let [result (apple/validate-id-token
                  {:client-id "com.example.app"}
                  "invalid.token.string")]
      (is (false? (:valid? result)))
      (is (string? (:error result)))))

  (testing "returns error for unreachable issuer"
    ;; This test verifies the error handling without hitting Apple's servers
    (let [result (apple/validate-id-token
                  {:client-id "com.example.app"}
                  "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjMifQ.fake")]
      (is (false? (:valid? result)))
      (is (string? (:error result))))))

(deftest extract-user-info-test
  (testing "extracts user info from claims"
    (let [claims {:sub "001234.abc.5678"
                  :email "user@example.com"
                  :real_user_status 2}
          info   (apple/extract-user-info claims {:user-provided-name "Jane"})]
      (is (= "001234.abc.5678" (:sub info)))
      (is (= "user@example.com" (:email info)))
      (is (= "Jane" (:name info)))
      (is (= :apple (:provider info))))))

(deftest real-user-test
  (testing "delegates to claims/real-user?"
    (is (true? (apple/real-user? {:real_user_status 2})))
    (is (false? (apple/real-user? {:real_user_status 1})))))

(deftest private-relay-email-test
  (testing "delegates to claims/private-relay-email?"
    (is (true? (apple/private-relay-email? "user@privaterelay.appleid.com")))
    (is (false? (apple/private-relay-email? "user@gmail.com")))))

(deftest apple-issuer-test
  (testing "uses correct Apple issuer URL"
    (is (= "https://appleid.apple.com" apple/apple-issuer))))

;; Integration test - requires real Apple token
;; Uncomment for manual testing
#_(deftest validate-real-apple-token-test
    (testing "validates real Apple ID token"
      (let [;; Get a real token from Apple Sign-In in your app
            real-token "eyJhbG..."
            result     (apple/validate-id-token
                        {:client-id "com.yourapp.bundleid"}
                        real-token)]
        (is (:valid? result))
        (is (string? (get-in result [:claims :sub]))))))
