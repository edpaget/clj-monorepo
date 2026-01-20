(ns oidc-apple.client-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [oidc-apple.client :as client]))

;; Test EC key pair for signing (P-256 curve, generated for testing only)
(def ^:private test-private-key
  "-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQghQwnzmnRQkwutBsJ
b5y4vZZGyK6te5RifGiyYQE+sAKhRANCAAS5K/1VyY7M4EIC/oppak65jddZ8+0U
CszmnqCIdWVIATVSa3zf9EkkjkJs1S//xUXK1rA4ha6NQ6TAh8ggrDF5
-----END PRIVATE KEY-----")

(def ^:private test-config
  {:client-id    "net.example.app.web"
   :team-id      "ABCD1234EF"
   :key-id       "KEY123456"
   :private-key  test-private-key
   :redirect-uri "https://example.com/auth/apple/callback"})

;; ---------------------------------------------------------------------------
;; generate-client-secret tests

(defn- decode-jwt-payload
  "Decodes JWT payload without verification (for testing claim structure)."
  [token]
  (let [payload (second (str/split token #"\."))]
    (-> payload
        (->> (.decode (java.util.Base64/getUrlDecoder)))
        (String. "UTF-8")
        (json/parse-string true))))

(defn- decode-jwt-header
  "Decodes JWT header without verification (for testing header structure)."
  [token]
  (let [header (first (str/split token #"\."))]
    (-> header
        (->> (.decode (java.util.Base64/getUrlDecoder)))
        (String. "UTF-8")
        (json/parse-string true))))

(deftest generate-client-secret-test
  (testing "generates valid JWT with correct claims"
    (let [secret  (client/generate-client-secret test-config)
          decoded (decode-jwt-payload secret)]
      (is (string? secret))
      (is (= 3 (count (str/split secret #"\."))) "Should be a valid JWT with 3 parts")
      (is (= "ABCD1234EF" (:iss decoded)))
      (is (= "net.example.app.web" (:sub decoded)))
      (is (= "https://appleid.apple.com" (:aud decoded)))
      (is (number? (:iat decoded)))
      (is (number? (:exp decoded)))
      (is (> (:exp decoded) (:iat decoded)))))

  (testing "includes key-id in JWT header"
    (let [secret (client/generate-client-secret test-config)
          header (decode-jwt-header secret)]
      (is (= "KEY123456" (:kid header)))
      (is (= "ES256" (:alg header)))))

  (testing "respects expires-in-seconds option"
    (let [secret  (client/generate-client-secret test-config {:expires-in-seconds 3600})
          decoded (decode-jwt-payload secret)
          diff    (- (:exp decoded) (:iat decoded))]
      (is (= 3600 diff))))

  (testing "uses default 24 hour expiration"
    (let [secret  (client/generate-client-secret test-config)
          decoded (decode-jwt-payload secret)
          diff    (- (:exp decoded) (:iat decoded))]
      (is (= 86400 diff)))))

;; ---------------------------------------------------------------------------
;; authorization-url tests

(deftest authorization-url-test
  (testing "generates URL with required params"
    (let [url (client/authorization-url test-config "random-state")]
      (is (str/starts-with? url "https://appleid.apple.com/auth/authorize?"))
      (is (str/includes? url "client_id=net.example.app.web"))
      (is (str/includes? url "redirect_uri=https%3A%2F%2Fexample.com%2Fauth%2Fapple%2Fcallback"))
      (is (str/includes? url "state=random-state"))
      (is (str/includes? url "response_type=code"))))

  (testing "uses form_post response mode by default"
    (let [url (client/authorization-url test-config "state")]
      (is (str/includes? url "response_mode=form_post"))))

  (testing "includes openid email name scopes by default"
    (let [url (client/authorization-url test-config "state")]
      ;; URL encoded "openid email name"
      (is (str/includes? url "scope=openid+email+name"))))

  (testing "allows custom response mode"
    (let [url (client/authorization-url test-config "state" {:response-mode "query"})]
      (is (str/includes? url "response_mode=query"))))

  (testing "allows custom scope"
    (let [url (client/authorization-url test-config "state" {:scope "openid email"})]
      (is (str/includes? url "scope=openid+email"))
      (is (not (str/includes? url "scope=openid+email+name"))))))

;; ---------------------------------------------------------------------------
;; exchange-code tests

(deftest exchange-code-test
  (testing "calls token endpoint with generated client secret"
    (let [captured-request (atom nil)]
      (with-redefs [clj-http.client/post
                    (fn [url opts]
                      (reset! captured-request {:url url :opts opts})
                      {:status 200
                       :body   "{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"id_token\":\"test-id-token\"}"})]
        (let [result (client/exchange-code test-config "auth-code-123")]
          ;; Check URL
          (is (= "https://appleid.apple.com/auth/token" (:url @captured-request)))
          ;; Check form params
          (let [params (get-in @captured-request [:opts :form-params])]
            (is (= "authorization_code" (get params "grant_type")))
            (is (= "auth-code-123" (get params "code")))
            (is (= "net.example.app.web" (get params "client_id")))
            (is (= "https://example.com/auth/apple/callback" (get params "redirect_uri")))
            ;; Client secret should be a JWT
            (is (string? (get params "client_secret")))
            (is (= 3 (count (str/split (get params "client_secret") #"\.")))))
          ;; Check result
          (is (= "test-token" (:access_token result)))
          (is (= "Bearer" (:token_type result)))
          (is (= "test-id-token" (:id_token result))))))))

;; ---------------------------------------------------------------------------
;; Constants tests

(deftest constants-test
  (testing "apple-issuer is correct"
    (is (= "https://appleid.apple.com" client/apple-issuer)))

  (testing "apple-auth-endpoint is correct"
    (is (= "https://appleid.apple.com/auth/authorize" client/apple-auth-endpoint)))

  (testing "apple-token-endpoint is correct"
    (is (= "https://appleid.apple.com/auth/token" client/apple-token-endpoint))))
