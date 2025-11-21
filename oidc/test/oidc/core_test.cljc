(ns oidc.core-test
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer-macros [deftest is testing]])
   [oidc.core :as sut]))

(deftest create-client-test
  (testing "creates client with default scopes"
    (let [config {:issuer "https://example.com"
                  :client-id "test-client"
                  :redirect-uri "https://app.example.com/callback"}
          client (sut/create-client config)]
      (is (= "https://example.com" (:issuer client)))
      (is (= "test-client" (:client-id client)))
      (is (= ["openid"] (:scopes client)))))

  (testing "creates client with custom scopes"
    (let [config {:issuer "https://example.com"
                  :client-id "test-client"
                  :redirect-uri "https://app.example.com/callback"
                  :scopes ["openid" "profile" "email"]}
          client (sut/create-client config)]
      (is (= ["openid" "profile" "email"] (:scopes client)))))

  (testing "creates client with client secret"
    (let [config {:issuer "https://example.com"
                  :client-id "test-client"
                  :client-secret "secret123"
                  :redirect-uri "https://app.example.com/callback"}
          client (sut/create-client config)]
      (is (= "secret123" (:client-secret client))))))
