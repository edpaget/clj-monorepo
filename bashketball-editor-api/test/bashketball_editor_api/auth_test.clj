(ns bashketball-editor-api.auth-test
  "Tests for authentication flow."
  (:require
   [authn.core :as authn]
   [authn.protocol :as authn-proto]
   [bashketball-editor-api.auth.github :as gh-auth]
   [bashketball-editor-api.handler :as handler]
   [bashketball-editor-api.models.protocol :as proto]
   [bashketball-editor-api.models.user :as user]
   [bashketball-editor-api.services.auth :as auth-svc]
   [bashketball-editor-api.test-utils :refer [with-clean-db with-db with-system]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [oidc-github.claims :as gh-claims]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(defn- create-test-authenticator
  "Creates a test authenticator with mock GitHub provider."
  []
  (let [user-repo      (user/create-user-repository)
        ;; Mock credential validator that accepts any code and returns access token
        mock-validator (reify authn-proto/CredentialValidator
                         (validate-credentials [_this credentials _client-id]
                           (when (:code credentials)
                             "test-access-token")))
        ;; Mock claims provider that returns test user data and upserts user
        mock-claims    (reify authn-proto/ClaimsProvider
                         (get-claims [_this access-token _scope]
                           (let [github-claims {:login "testuser"
                                                :email "testuser@example.com"
                                                :avatar_url "https://github.com/testuser.png"
                                                :name "Test User"
                                                :id 12345}
                                 user-data     (auth-svc/github-data->user-data github-claims access-token)
                                 user          (proto/create! user-repo user-data)]
                             (assoc github-claims
                                    :user-id (str (:id user))
                                    :sub (str (:id user))))))]
    (authn/create-authenticator
     {:credential-validator mock-validator
      :claims-provider mock-claims
      :session-ttl-ms 86400000})))

(deftest authenticate-with-code-test
  (testing "Authenticating with GitHub code creates user and session"
    (with-db
      (let [authenticator (create-test-authenticator)
            session-id    (authn/authenticate authenticator {:code "test-code"})]
        (is (some? session-id) "Session ID should be returned")

        ;; Verify session was created
        (let [session (authn/get-session authenticator session-id)]
          (is (some? session))
          (is (some? (:user-id session)))
          (is (map? (:claims session))))

        ;; Verify user was created in database
        (let [user-repo (user/create-user-repository)
              user      (proto/find-by user-repo {:github-login "testuser"})]
          (is (some? user))
          (is (= "testuser" (:github-login user)))
          (is (= "testuser@example.com" (:email user)))
          (is (= "Test User" (:name user)))))))

  (testing "GitHub access token is stored with user for Git operations"
    (with-db
      (let [authenticator (create-test-authenticator)
            _session-id   (authn/authenticate authenticator {:code "test-code"})
            user-repo     (user/create-user-repository)
            user          (proto/find-by user-repo {:github-login "testuser"})]
        (is (= "test-access-token" (:github-token user))
            "GitHub access token should be stored in user record")))))

(deftest authenticate-with-invalid-code-test
  (testing "Authenticating with invalid code returns nil"
    (with-db
      (let [authenticator (create-test-authenticator)
            session-id    (authn/authenticate authenticator {})]
        (is (nil? session-id))))))

(deftest logout-test
  (testing "Logging out destroys the session"
    (with-db
      (let [authenticator (create-test-authenticator)
            session-id    (authn/authenticate authenticator {:code "test-code"})]
        (is (some? session-id))

        ;; Verify session exists
        (is (some? (authn/get-session authenticator session-id)))

        ;; Logout
        (authn/logout authenticator session-id)

        ;; Verify session is gone
        (is (nil? (authn/get-session authenticator session-id)))))))

(deftest refresh-session-test
  (testing "Refreshing a session extends its expiration"
    (with-db
      (let [authenticator  (create-test-authenticator)
            session-id     (authn/authenticate authenticator {:code "test-code"})
            session-before (authn/get-session authenticator session-id)
            expires-before (:expires-at session-before)]

        ;; Wait a moment
        (Thread/sleep 100)

        ;; Refresh session
        (authn/refresh-session authenticator session-id)

        ;; Verify expiration was extended
        (let [session-after (authn/get-session authenticator session-id)
              expires-after (:expires-at session-after)]
          (is (> expires-after expires-before)))))))

(deftest user-upsert-on-login-test
  (testing "Logging in again with same GitHub account updates user data"
    (with-db
      (let [user-repo           (user/create-user-repository)
            ;; First login
            authenticator-1     (create-test-authenticator)
            session-id-1        (authn/authenticate authenticator-1 {:code "test-code"})
            user-1              (proto/find-by user-repo {:github-login "testuser"})

            ;; Mock claims provider with updated data
            mock-claims-updated (reify authn-proto/ClaimsProvider
                                  (get-claims [_this access-token _scope]
                                    (let [github-claims {:login "testuser"
                                                         :email "newemail@example.com"
                                                         :avatar_url "https://github.com/testuser-new.png"
                                                         :name "Updated Test User"
                                                         :id 12345}
                                          user-data     (auth-svc/github-data->user-data github-claims access-token)
                                          user          (proto/create! user-repo user-data)]
                                      (assoc github-claims
                                             :user-id (str (:id user))
                                             :sub (str (:id user))))))
            mock-validator      (reify authn-proto/CredentialValidator
                                  (validate-credentials [_this credentials _client-id]
                                    (when (:code credentials)
                                      "test-access-token")))
            authenticator-2     (authn/create-authenticator
                                 {:credential-validator mock-validator
                                  :claims-provider mock-claims-updated
                                  :session-ttl-ms 86400000})

            ;; Second login with updated data
            session-id-2        (authn/authenticate authenticator-2 {:code "test-code"})
            user-2              (proto/find-by user-repo {:github-login "testuser"})]

        (is (some? session-id-1))
        (is (some? session-id-2))

        ;; Verify same user was updated, not a new one created
        (is (= (:id user-1) (:id user-2)))
        (is (= "testuser" (:github-login user-2)))
        (is (= "newemail@example.com" (:email user-2)))
        (is (= "Updated Test User" (:name user-2)))
        (is (= (:created-at user-1) (:created-at user-2)) "Created-at should not change")
        (is (.after (:updated-at user-2) (:updated-at user-1)) "Updated-at should be newer")))))

(deftest logout-handler-test
  (testing "Logout handler destroys session and returns success"
    (with-db
      (let [authenticator (create-test-authenticator)
            session-id    (authn/authenticate authenticator {:code "test-code"})
            handler       (handler/logout-handler authenticator)
            request       {:session {:authn/session-id session-id}}
            response      (handler request)]
        (is (= 200 (:status response)))
        (is (true? (get-in response [:body :success])))
        (is (nil? (:session response)))

        ;; Verify session was deleted
        (is (nil? (authn/get-session authenticator session-id)))))))

(deftest create-success-handler-saves-github-token-test
  (testing "OAuth success handler stores GitHub access token in user record"
    (with-db
      (let [user-repo       (user/create-user-repository)
            authenticator   (create-test-authenticator)
            success-handler (gh-auth/create-success-handler
                             user-repo
                             authenticator
                             "http://localhost:3001/")]
        ;; Simulate OAuth callback with token response
        ;; fetch-all-user-data returns {:profile ... :emails ... :orgs ...}
        (with-redefs [gh-claims/fetch-all-user-data
                      (fn [_token _opts]
                        {:profile {:login "oauth-test-user"
                                   :email "oauth@example.com"
                                   :avatar_url "https://github.com/oauth-test.png"
                                   :name "OAuth Test User"
                                   :id 99999}
                         :emails [{:email "oauth@example.com"
                                   :primary true
                                   :verified true}]
                         :orgs []})]
          (let [token-response {:access_token "github-oauth-token-12345"}
                response       (success-handler {} token-response)]
            ;; Verify redirect response
            (is (= 302 (:status response)))

            ;; Verify user was created with GitHub token
            (let [user (proto/find-by user-repo {:github-login "oauth-test-user"})]
              (is (some? user))
              (is (= "oauth-test-user" (:github-login user)))
              (is (= "github-oauth-token-12345" (:github-token user))
                  "GitHub access token should be stored for Git operations"))))))))
