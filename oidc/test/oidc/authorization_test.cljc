(ns oidc.authorization-test
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer-macros [deftest is testing]])
   [clojure.string :as str]
   [oidc.authorization :as sut]))

(deftest generate-state-test
  (testing "generates random state"
    (let [state1 (sut/generate-state)
          state2 (sut/generate-state)]
      (is (string? state1))
      (is (pos? (count state1)))
      (is (not= state1 state2)))))

(deftest generate-nonce-test
  (testing "generates random nonce"
    (let [nonce1 (sut/generate-nonce)
          nonce2 (sut/generate-nonce)]
      (is (string? nonce1))
      (is (pos? (count nonce1)))
      (is (not= nonce1 nonce2)))))

(deftest authorization-url-test
  (testing "constructs basic authorization URL"
    (let [url (sut/authorization-url
               "https://provider.com/authorize"
               "client123"
               "https://app.com/callback"
               {})]
      (is (str/starts-with? url "https://provider.com/authorize?"))
      (is (str/includes? url "response_type=code"))
      (is (str/includes? url "client_id=client123"))
      (is (str/includes? url "redirect_uri=https%3A%2F%2Fapp.com%2Fcallback"))
      (is (str/includes? url "scope=openid"))))

  (testing "includes optional parameters"
    (let [url (sut/authorization-url
               "https://provider.com/authorize"
               "client123"
               "https://app.com/callback"
               {:state "abc123"
                :nonce "xyz789"
                :prompt "consent"})]
      (is (str/includes? url "state=abc123"))
      (is (str/includes? url "nonce=xyz789"))
      (is (str/includes? url "prompt=consent"))))

  (testing "includes custom scope"
    (let [url (sut/authorization-url
               "https://provider.com/authorize"
               "client123"
               "https://app.com/callback"
               {:scope "openid profile email"})]
      #?(:clj
         (is (str/includes? url "scope=openid+profile+email"))
         :cljs
         (is (str/includes? url "scope=openid%20profile%20email"))))))
