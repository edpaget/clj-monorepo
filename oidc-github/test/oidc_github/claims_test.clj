(ns oidc-github.claims-test
  (:require
   [clj-http.client :as http]
   [clojure.test :refer [deftest is testing]]
   [oidc-github.claims :as claims]))

(def ^:private sample-profile
  {:id 12345
   :login "octocat"
   :name "The Octocat"
   :email "octocat@github.com"
   :avatar_url "https://avatars.githubusercontent.com/u/12345"
   :html_url "https://github.com/octocat"
   :company "GitHub"})

(def ^:private sample-emails
  [{:email "octocat@github.com"
    :verified true
    :primary true
    :visibility "public"}
   {:email "octocat@users.noreply.github.com"
    :verified true
    :primary false
    :visibility nil}])

(def ^:private sample-orgs
  [{:login "github"
    :id 9919
    :avatar_url "https://avatars.githubusercontent.com/u/9919"}
   {:login "my-company"
    :id 1234
    :avatar_url "https://avatars.githubusercontent.com/u/1234"}])

(deftest primary-verified-email-test
  (testing "extracts primary verified email"
    (is (= "octocat@github.com"
           (claims/primary-verified-email sample-emails))))

  (testing "returns nil when no primary verified email"
    (is (nil? (claims/primary-verified-email
               [{:email "test@example.com"
                 :verified false
                 :primary true}]))))

  (testing "returns nil for empty email list"
    (is (nil? (claims/primary-verified-email [])))))

(deftest github->oidc-claims-test
  (testing "transforms GitHub data to OIDC claims"
    (let [user-data {:profile sample-profile
                     :emails sample-emails
                     :orgs sample-orgs}
          claims    (claims/github->oidc-claims user-data)]
      (is (= "12345" (:sub claims)))
      (is (= "octocat" (:preferred_username claims)))
      (is (= "octocat" (:github_login claims)))
      (is (= "The Octocat" (:name claims)))
      (is (= "octocat@github.com" (:email claims)))
      (is (true? (:email_verified claims)))
      (is (= "https://github.com/octocat" (:profile claims)))
      (is (= "https://avatars.githubusercontent.com/u/12345" (:picture claims)))
      (is (= "GitHub" (:github_company claims)))
      (is (= ["github" "my-company"] (:github_orgs claims)))))

  (testing "handles missing optional fields"
    (let [minimal-data {:profile {:id 123 :login "user"}
                        :emails []
                        :orgs []}
          claims       (claims/github->oidc-claims minimal-data)]
      (is (= "123" (:sub claims)))
      (is (= "user" (:preferred_username claims)))
      (is (nil? (:name claims)))
      (is (nil? (:email claims)))
      (is (nil? (:github_company claims)))
      (is (nil? (:github_orgs claims)))))

  (testing "handles no verified email"
    (let [user-data {:profile sample-profile
                     :emails [{:email "test@example.com"
                               :verified false
                               :primary true}]
                     :orgs []}
          claims    (claims/github->oidc-claims user-data)]
      (is (nil? (:email claims)))
      (is (nil? (:email_verified claims)))))

  (testing "handles nil emails and orgs (when scopes not granted)"
    (let [user-data {:profile sample-profile}
          claims    (claims/github->oidc-claims user-data)]
      (is (= "12345" (:sub claims)))
      (is (= "octocat" (:preferred_username claims)))
      (is (nil? (:email claims)))
      (is (nil? (:github_orgs claims))))))

(deftest filter-by-scope-test
  (let [all-claims {:sub "123"
                    :name "Test User"
                    :preferred_username "testuser"
                    :profile "https://github.com/testuser"
                    :picture "https://avatars.github.com/u/123"
                    :email "test@example.com"
                    :email_verified true
                    :github_login "testuser"
                    :github_orgs ["org1" "org2"]
                    :github_company "Test Co"}]

    (testing "includes only sub and github claims with no scopes"
      (let [filtered (claims/filter-by-scope all-claims [])]
        (is (= "123" (:sub filtered)))
        (is (= "testuser" (:github_login filtered)))
        (is (= ["org1" "org2"] (:github_orgs filtered)))
        (is (= "Test Co" (:github_company filtered)))
        (is (nil? (:name filtered)))
        (is (nil? (:email filtered)))))

    (testing "includes profile claims with profile scope"
      (let [filtered (claims/filter-by-scope all-claims ["profile"])]
        (is (= "123" (:sub filtered)))
        (is (= "Test User" (:name filtered)))
        (is (= "testuser" (:preferred_username filtered)))
        (is (= "https://github.com/testuser" (:profile filtered)))
        (is (= "https://avatars.github.com/u/123" (:picture filtered)))
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

(deftest user-in-org-test
  (let [user-data {:profile sample-profile
                   :emails sample-emails
                   :orgs sample-orgs}]

    (testing "returns true when user is in org"
      (is (true? (claims/user-in-org? user-data "github")))
      (is (true? (claims/user-in-org? user-data "my-company"))))

    (testing "returns false when user is not in org"
      (is (false? (claims/user-in-org? user-data "other-org"))))

    (testing "returns false for empty org list"
      (let [no-orgs-data {:profile sample-profile
                          :emails sample-emails
                          :orgs []}]
        (is (false? (claims/user-in-org? no-orgs-data "github")))))))

(deftest cache-test
  (testing "creates cache with TTL"
    (let [cache (claims/create-cache 1000)]
      (is (some? cache)))))

(deftest parse-oauth-scopes-test
  (testing "parses comma-separated scopes"
    (is (= #{"user:email" "read:org"}
           (claims/parse-oauth-scopes {"X-OAuth-Scopes" "user:email, read:org"}))))

  (testing "handles scopes without spaces"
    (is (= #{"user" "repo"}
           (claims/parse-oauth-scopes {"X-OAuth-Scopes" "user,repo"}))))

  (testing "handles single scope"
    (is (= #{"read:user"}
           (claims/parse-oauth-scopes {"X-OAuth-Scopes" "read:user"}))))

  (testing "returns nil when header is missing"
    (is (nil? (claims/parse-oauth-scopes {}))))

  (testing "handles empty string"
    (is (= #{} (claims/parse-oauth-scopes {"X-OAuth-Scopes" ""})))))

(deftest has-email-scope-test
  (testing "returns true for user scope"
    (is (true? (claims/has-email-scope? #{"user"}))))

  (testing "returns true for user:email scope"
    (is (true? (claims/has-email-scope? #{"user:email"}))))

  (testing "returns false when no email scope"
    (is (false? (claims/has-email-scope? #{"read:org"}))))

  (testing "returns false for nil scopes"
    (is (false? (claims/has-email-scope? nil))))

  (testing "returns false for empty scopes"
    (is (false? (claims/has-email-scope? #{})))))

(deftest has-org-scope-test
  (testing "returns true for read:org scope"
    (is (true? (claims/has-org-scope? #{"read:org"}))))

  (testing "returns true for admin:org scope"
    (is (true? (claims/has-org-scope? #{"admin:org"}))))

  (testing "returns false when no org scope"
    (is (false? (claims/has-org-scope? #{"user:email"}))))

  (testing "returns false for nil scopes"
    (is (false? (claims/has-org-scope? nil))))

  (testing "returns false for empty scopes"
    (is (false? (claims/has-org-scope? #{})))))

(deftest fetch-all-user-data-test
  (testing "fetches only profile when no email or org scopes"
    (let [calls (atom [])]
      (with-redefs [http/get (fn [url _opts]
                               (swap! calls conj url)
                               {:headers {"X-OAuth-Scopes" "read:user"}
                                :body sample-profile})]
        (let [result (claims/fetch-all-user-data "token" nil)]
          (is (= 1 (count @calls)))
          (is (= sample-profile (:profile result)))
          (is (nil? (:emails result)))
          (is (nil? (:orgs result)))))))

  (testing "fetches profile and emails with user:email scope"
    (let [calls (atom [])]
      (with-redefs [http/get (fn [url _opts]
                               (swap! calls conj url)
                               (if (re-find #"/user/emails" url)
                                 {:body sample-emails}
                                 {:headers {"X-OAuth-Scopes" "read:user, user:email"}
                                  :body sample-profile}))]
        (let [result (claims/fetch-all-user-data "token" nil)]
          (is (= 2 (count @calls)))
          (is (= sample-profile (:profile result)))
          (is (= sample-emails (:emails result)))
          (is (nil? (:orgs result)))))))

  (testing "fetches profile and orgs with read:org scope"
    (let [calls (atom [])]
      (with-redefs [http/get (fn [url _opts]
                               (swap! calls conj url)
                               (if (re-find #"/user/orgs" url)
                                 {:body sample-orgs}
                                 {:headers {"X-OAuth-Scopes" "read:user, read:org"}
                                  :body sample-profile}))]
        (let [result (claims/fetch-all-user-data "token" nil)]
          (is (= 2 (count @calls)))
          (is (= sample-profile (:profile result)))
          (is (nil? (:emails result)))
          (is (= sample-orgs (:orgs result)))))))

  (testing "fetches all data with full scopes"
    (let [calls (atom [])]
      (with-redefs [http/get (fn [url _opts]
                               (swap! calls conj url)
                               (cond
                                 (re-find #"/user/emails" url) {:body sample-emails}
                                 (re-find #"/user/orgs" url)   {:body sample-orgs}
                                 :else {:headers {"X-OAuth-Scopes" "user, read:org"}
                                        :body sample-profile}))]
        (let [result (claims/fetch-all-user-data "token" nil)]
          (is (= 3 (count @calls)))
          (is (= sample-profile (:profile result)))
          (is (= sample-emails (:emails result)))
          (is (= sample-orgs (:orgs result))))))))
