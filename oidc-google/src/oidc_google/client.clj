(ns oidc-google.client
  "Client-side Google OIDC integration.

  Provides convenience wrappers around Google's OIDC flow, leveraging the base
  [[oidc.core]] library for discovery, token exchange, and JWT validation."
  (:require
   [clojure.string :as str]
   [oidc-google.claims :as claims]
   [oidc.authorization :as auth]
   [oidc.discovery :as discovery]
   [oidc.jwt :as jwt]))

(def google-issuer
  "Google's OIDC issuer URL."
  "https://accounts.google.com")

(defn fetch-discovery-document
  "Fetches Google's OIDC discovery document.

  Returns the discovery document containing authorization_endpoint,
  token_endpoint, jwks_uri, and other OIDC metadata."
  []
  (discovery/fetch-discovery-document google-issuer))

(defn authorization-url
  "Generates a Google OAuth authorization URL.

  Takes configuration map, state string, and optional parameters map.
  Returns the URL that the user should be redirected to for Google authentication.

  The configuration should include:
  - `:client-id` - Google OAuth client ID
  - `:redirect-uri` - Where Google should redirect after authorization
  - `:scopes` - Vector of OAuth scopes (defaults to [\"openid\" \"email\" \"profile\"])

  Optional parameters in `opts`:
  - `:nonce` - Nonce for replay protection
  - `:access-type` - \"online\" (default) or \"offline\" (for refresh tokens)
  - `:prompt` - \"none\", \"consent\", \"select_account\", or combinations
  - `:login-hint` - Email address hint for account selection
  - `:additional-params` - Map of additional query parameters"
  ([config state]
   (authorization-url config state {}))
  ([{:keys [client-id redirect-uri scopes]} state opts]
   (let [discovery     (fetch-discovery-document)
         auth-endpoint (:authorization_endpoint discovery)
         scope-str     (str/join " " (or scopes ["openid" "email" "profile"]))
         additional    (cond-> {}
                         (:access-type opts)
                         (assoc "access_type" (:access-type opts))

                         (:login-hint opts)
                         (assoc "login_hint" (:login-hint opts))

                         (:additional-params opts)
                         (merge (:additional-params opts)))]
     (auth/authorization-url auth-endpoint
                             client-id
                             redirect-uri
                             (cond-> {:scope scope-str
                                      :state state}
                               (:nonce opts)
                               (assoc :nonce (:nonce opts))

                               (:prompt opts)
                               (assoc :prompt (:prompt opts))

                               (seq additional)
                               (assoc :additional-params additional))))))

(defn exchange-code
  "Exchanges an authorization code for tokens.

  Makes a POST request to Google's token endpoint with the authorization code
  received from the OAuth callback. Returns a map containing:
  - `:access_token` - The access token string
  - `:id_token` - JWT ID token (if openid scope was requested)
  - `:refresh_token` - Refresh token (if access_type=offline was used)
  - `:expires_in` - Token expiration in seconds
  - `:token_type` - Token type (typically \"Bearer\")"
  [{:keys [client-id client-secret redirect-uri]} code]
  (let [discovery      (fetch-discovery-document)
        token-endpoint (:token_endpoint discovery)]
    (auth/exchange-code token-endpoint
                        code
                        client-id
                        client-secret
                        redirect-uri
                        {})))

(defn refresh-token
  "Refreshes an access token using a refresh token.

  Takes configuration and the refresh token obtained from a previous
  authorization with access_type=offline. Returns a new token response
  containing access_token and expires_in."
  [{:keys [client-id client-secret]} refresh-token-val]
  (let [discovery      (fetch-discovery-document)
        token-endpoint (:token_endpoint discovery)]
    (auth/refresh-token token-endpoint
                        refresh-token-val
                        client-id
                        client-secret
                        {})))

(defn validate-id-token
  "Validates a Google ID token.

  Takes the ID token string, client ID (expected audience), and optional
  options map. Fetches Google's JWKS and validates the token signature,
  expiration, issuer, and audience.

  Options:
  - `:nonce` - Expected nonce value if using nonce parameter
  - `:leeway` - Clock skew leeway in seconds (default 0)

  Returns the decoded token claims on success, throws on validation failure."
  ([id-token client-id]
   (validate-id-token id-token client-id {}))
  ([id-token client-id opts]
   (let [discovery (fetch-discovery-document)
         jwks-uri  (:jwks_uri discovery)
         jwks      (jwt/fetch-jwks jwks-uri)]
     (jwt/validate-id-token id-token
                            jwks
                            google-issuer
                            client-id
                            opts))))

(defn fetch-userinfo
  "Fetches user information from Google's userinfo endpoint.

  Takes an access token and returns the user's profile data."
  [access-token]
  (claims/fetch-userinfo access-token))
