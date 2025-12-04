(ns bashketball-editor-api.services.auth
  "Authentication service.

  Coordinates authentication flow between GitHub OAuth, user repository,
  and session management."
  (:require
   [authn.protocol :as proto]
   [bashketball-editor-api.models.protocol :as repo-proto]
   [clj-http.client :as http]))

(defn github-data->user-data
  "Transforms GitHub API user data to user repository data format.

  The access-token is stored to enable Git operations with the user's credentials."
  [github-data access-token]
  {:github-login (:login github-data)
   :github-token access-token
   :email (:email github-data)
   :avatar-url (:avatar_url github-data)
   :name (:name github-data)})

(defrecord AuthService [user-repo credential-validator claims-provider])

(defn- exchange-code-for-token
  "Exchanges a GitHub OAuth authorization code for an access token."
  [client-id client-secret code]
  (try
    (let [response (http/post "https://github.com/login/oauth/access_token"
                              {:form-params {:client_id client-id
                                             :client_secret client-secret
                                             :code code}
                               :headers {"Accept" "application/json"}
                               :as :json
                               :throw-exceptions false})]
      (when (= 200 (:status response))
        (get-in response [:body :access_token])))
    (catch Exception _e
      nil)))

(defrecord GitHubCredentialValidator [client-id client-secret]
  proto/CredentialValidator
  (validate-credentials [_this credentials _client-id]
    (cond
      ;; If access token provided, validate it by calling GitHub API
      (:access-token credentials)
      (:access-token credentials)

      ;; If code provided, exchange it for access token
      (:code credentials)
      (exchange-code-for-token client-id client-secret (:code credentials))

      :else
      nil)))

(defrecord GitHubClaimsProvider [github-claims-provider user-repo]
  proto/ClaimsProvider
  (get-claims [_this user-id scope]
    (let [access-token  user-id
          github-claims (proto/get-claims github-claims-provider access-token scope)
          ;; Upsert user from GitHub data, storing the access token for Git operations
          user-data     (github-data->user-data github-claims access-token)
          user          (repo-proto/create! user-repo user-data)]
      (assoc github-claims
             :user-id (str (:id user))
             :sub (str (:id user))))))

(defn create-auth-service
  "Creates an authentication service.

  Wraps GitHub OAuth to manage user creation and lookup."
  [user-repo client-id client-secret github-claims-provider]
  (->AuthService user-repo
                 (->GitHubCredentialValidator client-id client-secret)
                 (->GitHubClaimsProvider github-claims-provider user-repo)))
