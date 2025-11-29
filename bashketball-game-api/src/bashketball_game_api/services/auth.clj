(ns bashketball-game-api.services.auth
  "Authentication service for Google OIDC integration.

  Implements authn protocols for credential validation and claims provisioning.
  Handles user upsert on successful Google authentication."
  (:require
   [authn.protocol :as authn-proto]
   [bashketball-game-api.models.protocol :as repo]
   [bashketball-game-api.models.user :as user]
   [clojure.tools.logging :as log]
   [db.core :as db]))

(defrecord GoogleCredentialValidator [user-repo]
  authn-proto/CredentialValidator
  (validate-credentials [_this credentials _client-id]
    ;; For Google OIDC, credentials contain the validated Google claims
    ;; The user-id is the google-id from the ID token
    (when (:sub credentials)
      (binding [db/*datasource* db/*datasource*]
        (let [user (user/upsert-from-google! user-repo credentials)]
          (str (:id user)))))))

(defrecord DatabaseClaimsProvider [user-repo]
  authn-proto/ClaimsProvider
  (get-claims [_this user-id _scope]
    ;; Fetch user from database and return claims
    (binding [db/*datasource* db/*datasource*]
      (when-let [user (repo/find-by user-repo {:id (parse-uuid user-id)})]
        {:sub user-id
         :email (:email user)
         :name (:name user)
         :picture (:avatar-url user)}))))

(defn create-auth-service
  "Creates the authentication service components.

  Returns a map with:
  - `:credential-validator` - Validates Google credentials and upserts users
  - `:claims-provider` - Provides user claims from database"
  [user-repo]
  {:credential-validator (->GoogleCredentialValidator user-repo)
   :claims-provider (->DatabaseClaimsProvider user-repo)})

(defn upsert-user-from-google!
  "Upserts a user from Google OIDC claims.

  Takes the user repository and Google claims (from ID token or userinfo).
  Creates a new user or updates an existing one based on google-id."
  [user-repo google-claims]
  (log/info "Upserting user from Google claims:" (:sub google-claims))
  (user/upsert-from-google! user-repo google-claims))
