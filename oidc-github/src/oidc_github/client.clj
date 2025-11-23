(ns oidc-github.client
  "Client-side GitHub OAuth integration.

  Provides convenience wrappers around GitHub's OAuth flow with sensible defaults
  for scopes and endpoints. Wraps the lower-level oidc client library."
  (:require
   [clj-http.client :as http]
   [clojure.string :as str]
   [oidc-github.claims :as claims]))

(def ^:private github-auth-url "https://github.com/login/oauth/authorize")
(def ^:private github-token-url "https://github.com/login/oauth/access_token")

(defn- enterprise-auth-url
  "Constructs authorization URL for GitHub Enterprise."
  [base-url]
  (str base-url "/login/oauth/authorize"))

(defn- enterprise-token-url
  "Constructs token URL for GitHub Enterprise."
  [base-url]
  (str base-url "/login/oauth/access_token"))

(defn- build-query-string
  "Builds a URL query string from a map of parameters."
  [params]
  (->> params
       (map (fn [[k v]]
              (str (name k) "=" (java.net.URLEncoder/encode (str v) "UTF-8"))))
       (str/join "&")))

(defn authorization-url
  "Generates a GitHub OAuth authorization URL.

  Takes configuration map, state string, and optional nonce. Returns the URL
  that the user should be redirected to for GitHub authentication.

  The configuration should include:
  - `:client-id` - GitHub OAuth App client ID
  - `:redirect-uri` - Where GitHub should redirect after authorization
  - `:scopes` - Vector of OAuth scopes (defaults to [\"user:email\" \"read:user\" \"read:org\"])
  - `:enterprise-url` - Base URL for GitHub Enterprise (optional)

  The `state` parameter should be a unique, unguessable string to prevent CSRF attacks.

  Example:

      (authorization-url
        {:client-id \"abc123\"
         :redirect-uri \"https://app.com/callback\"
         :scopes [\"user:email\"]}
        \"random-state-value\")"
  ([config state]
   (authorization-url config state nil))
  ([{:keys [client-id redirect-uri scopes enterprise-url]} state nonce]
   (let [base-url (if enterprise-url
                   (enterprise-auth-url enterprise-url)
                   github-auth-url)
         params (cond-> {:client_id client-id
                        :state state
                        :scope (str/join " " (or scopes ["user:email" "read:user" "read:org"]))}
                  redirect-uri
                  (assoc :redirect_uri redirect-uri)

                  nonce
                  (assoc :nonce nonce))]
     (str base-url "?" (build-query-string params)))))

(defn exchange-code
  "Exchanges an authorization code for an access token.

  Makes a POST request to GitHub's token endpoint with the authorization code
  received from the OAuth callback. Returns a map containing:
  - `:access_token` - The access token string
  - `:token_type` - Token type (typically \"bearer\")
  - `:scope` - Space-separated string of granted scopes

  Example:

      (exchange-code
        {:client-id \"abc123\"
         :client-secret \"secret\"
         :redirect-uri \"https://app.com/callback\"}
        \"code-from-github-callback\")"
  [{:keys [client-id client-secret redirect-uri enterprise-url]} code]
  (let [token-url (if enterprise-url
                   (enterprise-token-url enterprise-url)
                   github-token-url)
        response (http/post token-url
                           {:form-params (cond-> {:client_id client-id
                                                 :client_secret client-secret
                                                 :code code}
                                           redirect-uri
                                           (assoc :redirect_uri redirect-uri))
                            :headers {"Accept" "application/json"}
                            :as :json
                            :throw-exceptions true})]
    (:body response)))

(defn fetch-user
  "Fetches GitHub user information using an access token.

  Returns a map containing all available GitHub user data including profile,
  emails, and organization memberships. This data can be transformed into
  OIDC claims using [[oidc-github.claims/github->oidc-claims]].

  Example:

      (def user-data (fetch-user \"ghp_abc123\"))
      (def claims (claims/github->oidc-claims user-data))"
  ([access-token]
   (fetch-user access-token nil))
  ([access-token enterprise-url]
   (claims/fetch-all-user-data access-token enterprise-url)))

(defn refresh-token
  "Refreshes an access token using a refresh token.

  Note: GitHub OAuth Apps do not support refresh tokens, so this function will
  throw an exception. This is provided for API completeness. If you need refresh
  tokens, you must use a GitHub App (not OAuth App) which has different
  authentication flows."
  [_config _refresh-token]
  (throw (ex-info "GitHub OAuth Apps do not support refresh tokens"
                  {:type :unsupported-operation
                   :message "Use GitHub Apps (not OAuth Apps) if you need refresh token support"})))
