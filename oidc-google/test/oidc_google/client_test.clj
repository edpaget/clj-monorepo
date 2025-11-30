(ns oidc-google.client-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [oidc-google.client :as client]
   [oidc.authorization :as auth]
   [oidc.discovery :as discovery]
   [oidc.jwt :as jwt]))

(def ^:private mock-discovery
  {:authorization_endpoint "https://accounts.google.com/o/oauth2/v2/auth"
   :token_endpoint "https://oauth2.googleapis.com/token"
   :jwks_uri "https://www.googleapis.com/oauth2/v3/certs"
   :userinfo_endpoint "https://openidconnect.googleapis.com/v1/userinfo"
   :issuer "https://accounts.google.com"
   :response_types_supported ["code"]
   :subject_types_supported ["public"]
   :id_token_signing_alg_values_supported ["RS256"]})

(deftest google-issuer-test
  (testing "has correct Google issuer"
    (is (= "https://accounts.google.com" client/google-issuer))))

(deftest authorization-url-test
  (testing "generates URL with default scopes"
    (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)]
      (let [config {:client-id "test-id"
                    :redirect-uri "https://example.com/callback"}
            url    (client/authorization-url config "state-123")]
        (is (str/starts-with? url "https://accounts.google.com/o/oauth2/v2/auth?"))
        (is (str/includes? url "client_id=test-id"))
        (is (str/includes? url "redirect_uri=https"))
        (is (str/includes? url "state=state-123"))
        (is (str/includes? url "scope=openid")))))

  (testing "uses custom scopes"
    (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)]
      (let [config {:client-id "test-id"
                    :redirect-uri "https://example.com/callback"
                    :scopes ["openid" "email"]}
            url    (client/authorization-url config "state")]
        (is (str/includes? url "scope=openid+email")))))

  (testing "includes login_hint when provided"
    (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)]
      (let [config {:client-id "test-id"
                    :redirect-uri "https://example.com/callback"}
            url    (client/authorization-url config "state" {:login-hint "user@example.com"})]
        (is (str/includes? url "login_hint=user"))))))

(deftest exchange-code-test
  (testing "exchanges code using oidc.authorization"
    (let [exchanged? (atom false)]
      (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)
                    auth/exchange-code                 (fn [endpoint code client-id secret _redirect _opts]
                                                         (reset! exchanged? true)
                                                         (is (= "https://oauth2.googleapis.com/token" endpoint))
                                                         (is (= "auth-code" code))
                                                         (is (= "test-id" client-id))
                                                         (is (= "test-secret" secret))
                                                         {:access_token "token"
                                                          :token_type "Bearer"})]
        (let [config {:client-id "test-id"
                      :client-secret "test-secret"
                      :redirect-uri "https://example.com/callback"}
              result (client/exchange-code config "auth-code")]
          (is @exchanged?)
          (is (= "token" (:access_token result))))))))

(deftest refresh-token-test
  (testing "refreshes token using oidc.authorization"
    (let [refreshed? (atom false)]
      (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)
                    auth/refresh-token                 (fn [endpoint refresh-token client-id secret _opts]
                                                         (reset! refreshed? true)
                                                         (is (= "https://oauth2.googleapis.com/token" endpoint))
                                                         (is (= "refresh-token" refresh-token))
                                                         (is (= "test-id" client-id))
                                                         (is (= "test-secret" secret))
                                                         {:access_token "new-token"
                                                          :token_type "Bearer"
                                                          :expires_in 3600})]
        (let [config {:client-id "test-id"
                      :client-secret "test-secret"}
              result (client/refresh-token config "refresh-token")]
          (is @refreshed?)
          (is (= "new-token" (:access_token result))))))))

(deftest validate-id-token-test
  (testing "validates token using Google's JWKS"
    (let [validated? (atom false)]
      (with-redefs [discovery/fetch-discovery-document (constantly mock-discovery)
                    jwt/fetch-jwks                     (fn [uri]
                                                         (is (= "https://www.googleapis.com/oauth2/v3/certs" uri))
                                                         {:keys []})
                    jwt/validate-id-token              (fn [token _jwks issuer audience _opts]
                                                         (reset! validated? true)
                                                         (is (= "id-token" token))
                                                         (is (= "https://accounts.google.com" issuer))
                                                         (is (= "client-id" audience))
                                                         {:sub "123" :email "test@example.com"})]
        (let [result (client/validate-id-token "id-token" "client-id")]
          (is @validated?)
          (is (= "123" (:sub result))))))))
