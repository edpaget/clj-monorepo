(ns oidc.id-token-test
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer-macros [deftest is testing async]])
   [oidc.id-token :as id-token]))

(deftest create-validator-test
  (testing "creates validator with required components"
    (let [validator (id-token/create-validator)]
      (is (map? validator))
      (is (contains? validator :discovery-client))
      (is (contains? validator :jwt-validator))
      (is (some? (:discovery-client validator)))
      (is (some? (:jwt-validator validator))))))

(deftest extract-subject-test
  (testing "extracts subject from claims"
    (is (= "user-123" (id-token/extract-subject {:sub "user-123" :email "test@example.com"}))))

  (testing "returns nil when no subject"
    (is (nil? (id-token/extract-subject {:email "test@example.com"})))))

(deftest extract-email-test
  (testing "extracts email when verified"
    (is (= "test@example.com"
           (id-token/extract-email {:email "test@example.com" :email_verified true}))))

  (testing "extracts email when email_verified not present"
    ;; Some providers don't include email_verified when it's always true
    (is (= "test@example.com"
           (id-token/extract-email {:email "test@example.com"}))))

  (testing "returns nil when email not verified"
    (is (nil? (id-token/extract-email {:email "test@example.com" :email_verified false}))))

  (testing "returns nil when no email"
    (is (nil? (id-token/extract-email {:sub "user-123"})))))

(deftest token-expired-test
  (testing "returns true for expired token"
    ;; Token expired 1 hour ago
    (let [past-exp (- #?(:clj (quot (System/currentTimeMillis) 1000)
                         :cljs (js/Math.floor (/ (.getTime (js/Date.)) 1000)))
                      3600)]
      (is (true? (id-token/token-expired? {:exp past-exp})))))

  (testing "returns false for valid token"
    ;; Token expires in 1 hour
    (let [future-exp (+ #?(:clj (quot (System/currentTimeMillis) 1000)
                           :cljs (js/Math.floor (/ (.getTime (js/Date.)) 1000)))
                        3600)]
      (is (false? (id-token/token-expired? {:exp future-exp})))))

  (testing "returns false when no exp claim"
    (is (false? (id-token/token-expired? {:sub "user-123"})))))

#?(:clj
   (deftest validate-returns-error-for-invalid-issuer-test
     (testing "returns error for unreachable issuer"
       (let [validator (id-token/create-validator)
             result    (id-token/validate validator
                                          {:id-token "eyJhbGciOiJSUzI1NiJ9.e30.invalid"
                                           :issuer "https://invalid.example.com"
                                           :audience "test-client"})]
         (is (false? (:valid? result)))
         (is (string? (:error result)))))))

#?(:cljs
   (deftest validate-returns-error-for-invalid-issuer-test
     (async done
            (let [validator (id-token/create-validator)]
              (-> (id-token/validate validator
                                     {:id-token "eyJhbGciOiJSUzI1NiJ9.e30.invalid"
                                      :issuer "https://invalid.example.com"
                                      :audience "test-client"})
                  (.then (fn [result]
                           (is (false? (:valid? result)))
                           (is (string? (:error result)))
                           (done)))
                  (.catch (fn [_]
                       ;; Network errors should still be caught and returned as error result
                            (done))))))))

;; Integration test helper - requires real provider tokens
;; Uncomment and use for manual testing with real tokens
#_(deftest validate-google-token-test
    (testing "validates real Google ID token"
      (let [validator  (id-token/create-validator)
            ;; Get a real token from Google OAuth playground or your app
            real-token "eyJhbG..."
            result     (id-token/validate validator
                                          {:id-token real-token
                                           :issuer "https://accounts.google.com"
                                           :audience "your-client-id.apps.googleusercontent.com"})]
        (is (:valid? result))
        (is (string? (get-in result [:claims :sub])))
        (is (string? (get-in result [:claims :email]))))))
