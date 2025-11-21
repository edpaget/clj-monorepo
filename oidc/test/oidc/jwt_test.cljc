(ns oidc.jwt-test
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer-macros [deftest is testing]])
   #?@(:clj [[oidc.jwt.jvm :as jvm]]
       :cljs [[oidc.jwt.cljs-impl :as cljs-impl]])
   [oidc.jwt.protocol :as proto]))

(deftest decode-header-test
  (testing "decodes JWT header"
    (let [validator #?(:clj (jvm/create-validator)
                       :cljs (cljs-impl/create-validator))
          token     "eyJhbGciOiJSUzI1NiIsImtpZCI6InRlc3Qta2V5LWlkIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.invalid"
          header    (proto/decode-header validator token)]
      #?(:clj
         (do
           (is (= :rs256 (:alg header)))
           (is (= "test-key-id" (:kid header)))
           (is (= "JWT" (:typ header))))
         :cljs
         (do
           (is (= "RS256" (:alg header)))
           (is (= "test-key-id" (:kid header)))
           (is (= "JWT" (:typ header))))))))

#?(:cljs
   (deftest cljs-validator-creation-test
     (testing "creates ClojureScript validator"
       (let [validator (cljs-impl/create-validator)]
         (is (satisfies? proto/IJWTValidator validator))
         (is (satisfies? proto/IJWTParser validator))))))

#?(:clj
   (deftest jvm-validator-creation-test
     (testing "creates JVM validator"
       (let [validator (jvm/create-validator)]
         (is (satisfies? proto/IJWTValidator validator))
         (is (satisfies? proto/IJWTParser validator))))))
