(ns oidc-github.client-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [oidc-github.client :as client]))

(deftest authorization-url-test
  (let [config {:client-id "test-client-id"
                :redirect-uri "https://example.com/callback"
                :scopes ["user:email" "read:user"]}]

    (testing "generates authorization URL with all parameters"
      (let [url (client/authorization-url config "state-123")]
        (is (str/starts-with? url "https://github.com/login/oauth/authorize?"))
        (is (str/includes? url "client_id=test-client-id"))
        (is (str/includes? url "state=state-123"))
        (is (str/includes? url "redirect_uri=https%3A%2F%2Fexample.com%2Fcallback"))
        (is (str/includes? url "scope=user%3Aemail+read%3Auser"))))

    (testing "includes nonce when provided"
      (let [url (client/authorization-url config "state-123" "nonce-456")]
        (is (str/includes? url "nonce=nonce-456"))))

    (testing "uses default scopes when not specified"
      (let [minimal-config {:client-id "test-client-id"}
            url            (client/authorization-url minimal-config "state-123")]
        (is (str/includes? url "scope=user%3Aemail+read%3Auser+read%3Aorg"))))

    (testing "uses enterprise URL when provided"
      (let [enterprise-config (assoc config :enterprise-url "https://github.company.com")
            url               (client/authorization-url enterprise-config "state-123")]
        (is (str/starts-with? url "https://github.company.com/login/oauth/authorize?"))))))

(deftest refresh-token-test
  (testing "throws exception as GitHub OAuth Apps don't support refresh tokens"
    (let [config {:client-id "test-client-id"
                  :client-secret "test-secret"}]
      (is (thrown-with-msg? Exception
                            #"GitHub OAuth Apps do not support refresh tokens"
                            (client/refresh-token config "refresh-token-123"))))))
