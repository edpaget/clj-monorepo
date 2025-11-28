(ns oidc-google.claims-test
  (:require
   [clj-http.client :as http]
   [clojure.test :refer [deftest is testing]]
   [oidc-google.claims :as claims]))

(def ^:private sample-userinfo
  {:sub "123456789"
   :name "John Doe"
   :given_name "John"
   :family_name "Doe"
   :email "john@example.com"
   :email_verified true
   :picture "https://lh3.googleusercontent.com/a/photo"
   :locale "en"
   :hd "example.com"})

(deftest google->oidc-claims-test
  (testing "transforms Google userinfo to OIDC claims"
    (let [claims (claims/google->oidc-claims sample-userinfo)]
      (is (= "123456789" (:sub claims)))
      (is (= "John Doe" (:name claims)))
      (is (= "John" (:given_name claims)))
      (is (= "Doe" (:family_name claims)))
      (is (= "john@example.com" (:email claims)))
      (is (true? (:email_verified claims)))
      (is (= "https://lh3.googleusercontent.com/a/photo" (:picture claims)))
      (is (= "en" (:locale claims)))
      (is (= "example.com" (:google_hd claims)))))

  (testing "handles minimal userinfo"
    (let [minimal {:sub "123"}
          claims  (claims/google->oidc-claims minimal)]
      (is (= "123" (:sub claims)))
      (is (nil? (:name claims)))
      (is (nil? (:email claims)))
      (is (nil? (:google_hd claims)))))

  (testing "handles email_verified false"
    (let [userinfo {:sub "123" :email "test@example.com" :email_verified false}
          claims   (claims/google->oidc-claims userinfo)]
      (is (= "test@example.com" (:email claims)))
      (is (false? (:email_verified claims)))))

  (testing "excludes nil values"
    (let [userinfo {:sub "123" :name nil :email nil}
          claims   (claims/google->oidc-claims userinfo)]
      (is (= "123" (:sub claims)))
      (is (not (contains? claims :name)))
      (is (not (contains? claims :email))))))

(deftest filter-by-scope-test
  (let [all-claims {:sub "123"
                    :name "Test User"
                    :given_name "Test"
                    :family_name "User"
                    :picture "https://example.com/photo.jpg"
                    :locale "en"
                    :email "test@example.com"
                    :email_verified true
                    :google_hd "example.com"}]

    (testing "includes only sub and google claims with no scopes"
      (let [filtered (claims/filter-by-scope all-claims [])]
        (is (= "123" (:sub filtered)))
        (is (= "example.com" (:google_hd filtered)))
        (is (nil? (:name filtered)))
        (is (nil? (:email filtered)))))

    (testing "includes profile claims with profile scope"
      (let [filtered (claims/filter-by-scope all-claims ["profile"])]
        (is (= "123" (:sub filtered)))
        (is (= "Test User" (:name filtered)))
        (is (= "Test" (:given_name filtered)))
        (is (= "User" (:family_name filtered)))
        (is (= "https://example.com/photo.jpg" (:picture filtered)))
        (is (= "en" (:locale filtered)))
        (is (nil? (:email filtered)))))

    (testing "includes email claims with email scope"
      (let [filtered (claims/filter-by-scope all-claims ["email"])]
        (is (= "123" (:sub filtered)))
        (is (= "test@example.com" (:email filtered)))
        (is (true? (:email_verified filtered)))
        (is (nil? (:name filtered)))))

    (testing "includes all claims with both scopes"
      (let [filtered (claims/filter-by-scope all-claims ["profile" "email"])]
        (is (= all-claims filtered))))))

(deftest cache-test
  (testing "creates cache with TTL"
    (let [cache (claims/create-cache 1000)]
      (is (some? cache)))))

(deftest fetch-userinfo-test
  (testing "fetches userinfo from Google"
    (with-redefs [http/get (fn [_url _opts]
                             {:body sample-userinfo})]
      (let [result (claims/fetch-userinfo "access-token")]
        (is (= "123456789" (:sub result)))
        (is (= "john@example.com" (:email result))))))

  (testing "throws on error"
    (with-redefs [http/get (fn [_url _opts]
                             (throw (ex-info "Not found" {:status 404})))]
      (is (thrown? Exception (claims/fetch-userinfo "invalid-token"))))))
