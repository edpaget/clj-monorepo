(ns bashketball-game-api.services.auth-test
  "Tests for authentication service."
  (:require
   [authn.protocol :as authn-proto]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.services.auth :as auth]
   [bashketball-game-api.test-utils :refer [with-system with-clean-db with-db]]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-system)
(use-fixtures :each with-clean-db)

(deftest auth-service-creation-test
  (testing "Creating auth service returns valid components"
    (with-db
      (let [user-repo    (user/create-user-repository)
            auth-service (auth/create-auth-service user-repo)]
        (is (some? (:credential-validator auth-service)))
        (is (some? (:claims-provider auth-service)))
        (is (satisfies? authn-proto/CredentialValidator
                        (:credential-validator auth-service)))
        (is (satisfies? authn-proto/ClaimsProvider
                        (:claims-provider auth-service)))))))

(deftest credential-validator-creates-user-test
  (testing "Credential validator creates user from Google credentials"
    (with-db
      (let [user-repo    (user/create-user-repository)
            auth-service (auth/create-auth-service user-repo)
            validator    (:credential-validator auth-service)
            credentials  {:sub "google-123"
                          :email "test@example.com"
                          :name "Test User"
                          :picture "https://example.com/pic.jpg"}
            user-id      (authn-proto/validate-credentials
                          validator credentials nil)]
        (is (some? user-id))
        (is (uuid? (parse-uuid user-id)))))))

(deftest credential-validator-returns-nil-without-sub-test
  (testing "Credential validator returns nil when sub is missing"
    (with-db
      (let [user-repo    (user/create-user-repository)
            auth-service (auth/create-auth-service user-repo)
            validator    (:credential-validator auth-service)
            credentials  {:email "test@example.com"}
            user-id      (authn-proto/validate-credentials
                          validator credentials nil)]
        (is (nil? user-id))))))

(deftest claims-provider-returns-user-claims-test
  (testing "Claims provider returns user claims from database"
    (with-db
      (let [user-repo    (user/create-user-repository)
            auth-service (auth/create-auth-service user-repo)
            validator    (:credential-validator auth-service)
            provider     (:claims-provider auth-service)
            credentials  {:sub "google-456"
                          :email "claims@example.com"
                          :name "Claims User"
                          :picture "https://example.com/claims.jpg"}
            user-id      (authn-proto/validate-credentials
                          validator credentials nil)
            claims       (authn-proto/get-claims provider user-id [])]
        (is (some? claims))
        (is (= user-id (:sub claims)))
        (is (= "claims@example.com" (:email claims)))
        (is (= "Claims User" (:name claims)))))))

(deftest claims-provider-returns-nil-for-unknown-user-test
  (testing "Claims provider returns nil for unknown user"
    (with-db
      (let [user-repo    (user/create-user-repository)
            auth-service (auth/create-auth-service user-repo)
            provider     (:claims-provider auth-service)
            claims       (authn-proto/get-claims
                          provider
                          (str (java.util.UUID/randomUUID))
                          [])]
        (is (nil? claims))))))
