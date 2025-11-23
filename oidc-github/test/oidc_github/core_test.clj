(ns oidc-github.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [oidc-github.core :as core]))

(deftest validate-config-test
  (testing "validates valid config"
    (let [config {:client-id "test-id"
                  :client-secret "test-secret"}]
      (is (some? (core/validate-config config)))))

  (testing "includes default values"
    (let [config {:client-id "test-id"
                  :client-secret "test-secret"}]
      (core/validate-config config)
      (is (= ["user:email" "read:user" "read:org"] (:scopes core/default-config)))))

  (testing "throws on invalid config"
    (is (thrown? Exception
                 (core/validate-config {:client-id 123})))))

(deftest create-github-authenticator-test
  (testing "creates authenticator with credential validator and claims provider"
    (let [config {:client-id "test-id"
                  :client-secret "test-secret"}
          auth   (core/create-github-authenticator config)]
      (is (some? (:credential-validator auth)))
      (is (some? (:claims-provider auth)))))

  (testing "merges with default config"
    (let [config {:client-id "test-id"
                  :client-secret "test-secret"
                  :required-org "my-org"}
          auth   (core/create-github-authenticator config)]
      (is (some? auth)))))

(deftest authorization-url-test
  (testing "generates authorization URL"
    (let [config {:client-id "test-id"
                  :client-secret "test-secret"
                  :redirect-uri "https://example.com/callback"}
          url    (core/authorization-url config "state-123")]
      (is (string? url))
      (is (str/starts-with? url "https://github.com/login/oauth/authorize?"))))

  (testing "includes nonce when provided"
    (let [config {:client-id "test-id"
                  :client-secret "test-secret"}
          url    (core/authorization-url config "state-123" "nonce-456")]
      (is (str/includes? url "nonce=nonce-456")))))
