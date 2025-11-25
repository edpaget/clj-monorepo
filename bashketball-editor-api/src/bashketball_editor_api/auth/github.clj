(ns bashketball-editor-api.auth.github
  "GitHub OAuth integration using OIDC middleware.

  GitHub doesn't have a standard OIDC discovery endpoint, so we manually
  configure the OAuth endpoints and provide a mock discovery document."
  (:require
   [authn.protocol :as authn-proto]
   [bashketball-editor-api.models.protocol :as repo]
   [oidc-github.claims :as gh-claims]
   [oidc.core :as oidc]
   [ring.util.response :as response]))

(set! *warn-on-reflection* true)

(def ^:private github-discovery-doc
  "Mock OIDC discovery document for GitHub OAuth.

  GitHub doesn't provide a standard OIDC discovery endpoint, but we can
  create a compatible discovery document with GitHub's OAuth endpoints."
  {:issuer "https://github.com"
   :authorization_endpoint "https://github.com/login/oauth/authorize"
   :token_endpoint "https://github.com/login/oauth/access_token"
   :jwks_uri "https://token.actions.githubusercontent.com/.well-known/jwks"
   :response_types_supported ["code"]
   :subject_types_supported ["public"]
   :id_token_signing_alg_values_supported ["RS256"]})

(defn create-github-oidc-client
  "Creates an OIDC client configured for GitHub OAuth.

  Takes GitHub OAuth configuration with `:client-id`, `:client-secret`,
  `:redirect-uri`, and optionally `:scopes`. Returns an OIDC client
  configuration compatible with oidc.ring middleware."
  [{:keys [client-id client-secret redirect-uri scopes]}]
  (oidc/create-client
   {:issuer "https://github.com"
    :client-id client-id
    :client-secret client-secret
    :redirect-uri redirect-uri
    :scopes (or scopes ["user:email" "read:user"])}))

(defn create-success-handler
  "Creates a success callback that integrates GitHub OAuth with authn sessions.

  When OAuth succeeds:
  1. Fetches GitHub user data using the access token
  2. Converts to OIDC claims
  3. Upserts user in database
  4. Creates authn session
  5. Redirects to success URI"
  [user-repo authenticator success-redirect-uri]
  (fn [_request token-response]
    (let [access-token (:access_token token-response)
          ;; Fetch GitHub user data
          user-data    (gh-claims/fetch-all-user-data access-token nil)
          ;; Convert to OIDC claims
          claims       (gh-claims/github->oidc-claims user-data)
          github-login (:preferred_username claims)
          ;; Upsert user in database
          user         (repo/create! user-repo
                                     {:github-login github-login
                                      :email (:email claims)
                                      :avatar-url (:picture claims)
                                      :name (:name claims)})
          user-id      (str (:id user))
          ;; Create authn session
          session-id   (authn-proto/create-session
                        (:session-store authenticator)
                        user-id
                        claims)]
      (-> (response/redirect success-redirect-uri)
          (assoc :session {:authn/session-id session-id})))))

(defn mock-discovery
  "Middleware that mocks GitHub OIDC discovery responses.

  Since GitHub doesn't have a standard OIDC discovery endpoint, we intercept
  discovery requests and return a manually constructed discovery document."
  [handler]
  (fn [request]
    ;; Store mock discovery in request for oidc.ring to use
    (handler (assoc request ::discovery github-discovery-doc))))
