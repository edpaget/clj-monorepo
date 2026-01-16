(ns oidc-apple.claims-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [oidc-apple.claims :as claims]))

(deftest extract-user-info-test
  (testing "extracts basic claims"
    (let [jwt-claims {:sub "001234.abc.5678"
                      :email "user@example.com"
                      :email_verified true
                      :real_user_status 2}
          info       (claims/extract-user-info jwt-claims {:user-provided-name "John Doe"})]
      (is (= "001234.abc.5678" (:sub info)))
      (is (= "user@example.com" (:email info)))
      (is (= true (:email-verified info)))
      (is (= "John Doe" (:name info)))
      (is (= 2 (:real-user-status info)))
      (is (= :apple (:provider info)))))

  (testing "handles missing optional fields"
    (let [jwt-claims {:sub "001234.abc.5678"}
          info       (claims/extract-user-info jwt-claims)]
      (is (= "001234.abc.5678" (:sub info)))
      (is (nil? (:email info)))
      (is (nil? (:name info)))
      (is (= :apple (:provider info)))))

  (testing "defaults email-verified to true"
    (let [jwt-claims {:sub "001234.abc.5678"
                      :email "user@example.com"}
          info       (claims/extract-user-info jwt-claims)]
      (is (= true (:email-verified info))))))

(deftest real-user-test
  (testing "identifies real users (status 2)"
    (is (true? (claims/real-user? {:real_user_status 2}))))

  (testing "returns false for unknown status (1)"
    (is (false? (claims/real-user? {:real_user_status 1}))))

  (testing "returns false for unsupported status (0)"
    (is (false? (claims/real-user? {:real_user_status 0}))))

  (testing "returns false when status is missing"
    (is (false? (claims/real-user? {:sub "123"})))))

(deftest private-relay-email-test
  (testing "detects standard private relay emails"
    (is (true? (claims/private-relay-email? "abc123@privaterelay.appleid.com"))))

  (testing "detects alternate private relay format"
    (is (true? (claims/private-relay-email? "user.privaterelay.example@icloud.com"))))

  (testing "returns false for regular emails"
    (is (false? (claims/private-relay-email? "user@gmail.com")))
    (is (false? (claims/private-relay-email? "user@icloud.com")))
    (is (false? (claims/private-relay-email? "user@example.com"))))

  (testing "handles nil email"
    (is (false? (claims/private-relay-email? nil)))))

(deftest apple->oidc-claims-test
  (testing "transforms Apple claims to OIDC format"
    (let [apple-claims {:sub "001234.abc.5678"
                        :email "user@example.com"
                        :email_verified true
                        :real_user_status 2}
          oidc-claims  (claims/apple->oidc-claims apple-claims)]
      (is (= "001234.abc.5678" (:sub oidc-claims)))
      (is (= "user@example.com" (:email oidc-claims)))
      (is (= true (:email_verified oidc-claims)))
      (is (= 2 (:apple_real_user_status oidc-claims)))))

  (testing "handles minimal claims"
    (let [apple-claims {:sub "001234.abc.5678"}
          oidc-claims  (claims/apple->oidc-claims apple-claims)]
      (is (= "001234.abc.5678" (:sub oidc-claims)))
      (is (not (contains? oidc-claims :email)))
      (is (not (contains? oidc-claims :apple_real_user_status))))))

(deftest filter-by-scope-test
  (testing "returns base claims without scopes"
    (let [claims   {:sub "123" :email "a@b.com" :apple_real_user_status 2}
          filtered (claims/filter-by-scope claims [])]
      (is (= "123" (:sub filtered)))
      (is (= 2 (:apple_real_user_status filtered)))
      (is (not (contains? filtered :email)))))

  (testing "includes email claims with email scope"
    (let [claims   {:sub "123" :email "a@b.com" :email_verified true}
          filtered (claims/filter-by-scope claims ["email"])]
      (is (= "123" (:sub filtered)))
      (is (= "a@b.com" (:email filtered)))
      (is (= true (:email_verified filtered)))))

  (testing "always includes apple-specific claims"
    (let [claims   {:sub "123" :apple_real_user_status 2}
          filtered (claims/filter-by-scope claims [])]
      (is (= 2 (:apple_real_user_status filtered))))))
