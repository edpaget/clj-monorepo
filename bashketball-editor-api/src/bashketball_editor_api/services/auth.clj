(ns bashketball-editor-api.services.auth
  "Authentication service.

  Coordinates authentication flow between GitHub OAuth, user repository,
  and session management."
  (:require
   [authn.protocol :as proto]
   [bashketball-editor-api.models.protocol :as repo-proto]
   [bashketball-editor-api.models.user :as user]))

(defn github-data->user-data
  "Transforms GitHub API user data to user repository data format."
  [github-data]
  {:github-login (:login github-data)
   :email (:email github-data)
   :avatar-url (:avatar_url github-data)
   :name (:name github-data)})

(defrecord AuthService [user-repo credential-validator claims-provider])

(defrecord GitHubCredentialValidator [github-validator user-repo]
  proto/CredentialValidator
  (validate-credentials [_this credentials client-id]
    (when-let [github-login (proto/validate-credentials
                             github-validator credentials client-id)]
      (if-let [user (repo-proto/find-by user-repo {:github-login github-login})]
        (str (:id user))
        nil))))

(defrecord GitHubClaimsProvider [github-claims-provider user-repo]
  proto/ClaimsProvider
  (get-claims [_this user-id scope]
    (let [github-claims (proto/get-claims github-claims-provider user-id scope)
          github-login (:login github-claims)
          ;; Upsert user from GitHub data
          user-data (github-data->user-data github-claims)
          user (repo-proto/create! user-repo user-data)]
      (assoc github-claims
             :user-id (str (:id user))
             :sub (str (:id user))))))

(defn create-auth-service
  "Creates an authentication service.

  Wraps GitHub OAuth validators to manage user creation and lookup."
  [user-repo github-credential-validator github-claims-provider]
  (->AuthService user-repo
                 (->GitHubCredentialValidator github-credential-validator user-repo)
                 (->GitHubClaimsProvider github-claims-provider user-repo)))
