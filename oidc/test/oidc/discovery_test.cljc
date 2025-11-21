(ns oidc.discovery-test
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer-macros [deftest is testing]])
   [oidc.discovery :as sut]))

(deftest well-known-url-test
  (testing "constructs well-known URL without trailing slash"
    (is (= "https://example.com/.well-known/openid-configuration"
           (sut/well-known-url "https://example.com"))))

  (testing "constructs well-known URL with trailing slash"
    (is (= "https://example.com/.well-known/openid-configuration"
           (sut/well-known-url "https://example.com/")))))
