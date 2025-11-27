(ns bashketball-editor-api.auth.github
  "GitHub OAuth integration using OIDC middleware.

  GitHub doesn't have a standard OIDC discovery endpoint, so we manually
  configure the OAuth endpoints and provide a mock discovery document."
  (:require
   [authn.protocol :as authn-proto]
   [bashketball-editor-api.models.protocol :as repo]
   [clojure.tools.logging :as log]
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
  2. Validates organization membership (if `required-org` is configured)
  3. Converts to OIDC claims
  4. Upserts user in database
  5. Creates authn session
  6. Redirects to success URI

  Takes a config map with:
  - `:user-repo` - User repository for upserting users
  - `:authenticator` - Authn authenticator for session management
  - `:success-redirect-uri` - URI to redirect after successful auth
  - `:required-org` - (optional) GitHub org login that users must belong to
  - `:failure-redirect-uri` - (optional) URI to redirect on org validation failure"
  [{:keys [user-repo authenticator success-redirect-uri required-org failure-redirect-uri]}]
  (fn [_request token-response]
    (let [access-token (:access_token token-response)
          user-data    (gh-claims/fetch-all-user-data access-token nil)
          github-login (get-in user-data [:profile :login])]
      (if (and required-org (not (gh-claims/user-in-org? user-data required-org)))
        (do
          (log/warn "User" github-login "denied access: not a member of org" required-org)
          (-> (response/redirect (or failure-redirect-uri
                                     (str success-redirect-uri "?error=org_required")))
              (assoc :session nil)))
        (let [claims     (gh-claims/github->oidc-claims user-data)
              user       (repo/create! user-repo
                                       {:github-login github-login
                                        :github-token access-token
                                        :email (:email claims)
                                        :avatar-url (:picture claims)
                                        :name (:name claims)})
              user-id    (str (:id user))
              session-id (authn-proto/create-session
                          (:session-store authenticator)
                          user-id
                          claims)]
          (log/info "User" github-login "authenticated successfully")
          (-> (response/redirect success-redirect-uri)
              (assoc :session {:authn/session-id session-id})))))))

(defn mock-discovery
  "Middleware that mocks GitHub OIDC discovery responses.

  Since GitHub doesn't have a standard OIDC discovery endpoint, we intercept
  discovery requests and return a manually constructed discovery document."
  [handler]
  (fn [request]
    ;; Store mock discovery in request for oidc.ring to use
    (handler (assoc request :oidc.ring/discovery github-discovery-doc))))
