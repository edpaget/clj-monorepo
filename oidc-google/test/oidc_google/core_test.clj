(ns oidc-google.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [oidc-google.core :as core]
   [oidc.discovery :as discovery]))

(deftest validate-config-test
  (testing "validates valid config"
    (let [config {:client-id "test-id.apps.googleusercontent.com"
                  :client-secret "test-secret"}]
      (is (some? (core/validate-config config)))))

  (testing "includes default values"
    (is (= ["openid" "email" "profile"] (:scopes core/default-config)))
    (is (= (* 5 60 1000) (:cache-ttl-ms core/default-config))))

  (testing "throws on invalid config"
    (is (thrown? Exception
                 (core/validate-config {:client-id 123}))))

  (testing "throws on missing required fields"
    (is (thrown? Exception
                 (core/validate-config {:client-id "test-id"})))))

(deftest create-google-authenticator-test
  (testing "creates authenticator with credential validator and claims provider"
    (let [config {:client-id "test-id"
                  :client-secret "test-secret"}
          auth   (core/create-google-authenticator config)]
      (is (some? (:credential-validator auth)))
      (is (some? (:claims-provider auth)))))

  (testing "accepts custom cache TTL"
    (let [config {:client-id "test-id"
                  :client-secret "test-secret"
                  :cache-ttl-ms 60000}
          auth   (core/create-google-authenticator config)]
      (is (some? auth)))))

(def ^:private mock-discovery
  {:authorization_endpoint "https://accounts.google.com/o/oauth2/v2/auth"
   :token_endpoint "https://oauth2.googleapis.com/token"
   :jwks_uri "https://www.googleapis.com/oauth2/v3/certs"
   :userinfo_endpoint "https://openidconnect.googleapis.com/v1/userinfo"
   :issuer "https://accounts.google.com"
   :response_types_supported ["code"]
   :subject_types_supported ["public"]
   :id_token_signing_alg_values_supported ["RS256"]})

(deftest authorization-url-test
  (testing "generates authorization URL"
    (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)]
      (let [config {:client-id "test-id.apps.googleusercontent.com"
                    :client-secret "test-secret"
                    :redirect-uri "https://example.com/callback"}
            url    (core/authorization-url config "state-123")]
        (is (string? url))
        (is (str/starts-with? url "https://accounts.google.com/o/oauth2/v2/auth?")))))

  (testing "includes state parameter"
    (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)]
      (let [config {:client-id "test-id"
                    :client-secret "test-secret"
                    :redirect-uri "https://example.com/callback"}
            url    (core/authorization-url config "my-state")]
        (is (str/includes? url "state=my-state")))))

  (testing "includes nonce when provided"
    (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)]
      (let [config {:client-id "test-id"
                    :client-secret "test-secret"
                    :redirect-uri "https://example.com/callback"}
            url    (core/authorization-url config "state-123" {:nonce "nonce-456"})]
        (is (str/includes? url "nonce=nonce-456")))))

  (testing "includes access_type for offline access"
    (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)]
      (let [config {:client-id "test-id"
                    :client-secret "test-secret"
                    :redirect-uri "https://example.com/callback"}
            url    (core/authorization-url config "state" {:access-type "offline"})]
        (is (str/includes? url "access_type=offline")))))

  (testing "includes prompt parameter"
    (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)]
      (let [config {:client-id "test-id"
                    :client-secret "test-secret"
                    :redirect-uri "https://example.com/callback"}
            url    (core/authorization-url config "state" {:prompt "consent"})]
        (is (str/includes? url "prompt=consent"))))))
