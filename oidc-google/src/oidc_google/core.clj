(ns oidc-google.core
  "Google OIDC integration.

  Provides both provider-side authentication (for use with [[oidc-provider.core]])
  and client-side OIDC flow helpers using the base [[oidc.core]] library with
  Google-specific defaults and configurations.

  ## Provider Usage

  Use Google as an authenticator for your OIDC provider:

      (require '[oidc-google.core :as google]
               '[oidc-provider.core :as provider])

      (def config
        {:client-id \"your-google-client-id.apps.googleusercontent.com\"
         :client-secret \"your-google-client-secret\"})

      (def authenticator (google/create-google-authenticator config))

      (def provider
        (provider/create-provider
         {:issuer \"https://your-app.com\"
          :credential-validator (:credential-validator authenticator)
          :claims-provider (:claims-provider authenticator)
          ...}))

  ## Client Usage

  Use Google as an OIDC provider directly:

      (require '[oidc-google.core :as google])

      (def config
        {:client-id \"your-google-client-id.apps.googleusercontent.com\"
         :client-secret \"your-google-client-secret\"
         :redirect-uri \"https://your-app.com/callback\"})

      (def auth-url (google/authorization-url config \"state-123\"))
      (def tokens (google/exchange-code config \"code-from-callback\"))
      (def user (google/fetch-userinfo (:access_token tokens)))"
  (:require
   [malli.core :as m]
   [oidc-google.client :as client]
   [oidc-google.provider :as provider]))

(def Config
  "Configuration schema for Google OIDC integration."
  [:map
   [:client-id :string]
   [:client-secret :string]
   [:redirect-uri {:optional true} :string]
   [:scopes {:optional true} [:vector :string]]
   [:cache-ttl-ms {:optional true} :int]])

(def default-config
  "Default configuration values for Google OIDC integration."
  {:scopes ["openid" "email" "profile"]
   :cache-ttl-ms (* 5 60 1000)})

(defn validate-config
  "Validates configuration against the Config schema.

  Returns the config if valid, throws an exception otherwise."
  [config]
  (if (m/validate Config config)
    config
    (throw (ex-info "Invalid Google OIDC configuration"
                    {:config config
                     :errors (m/explain Config config)}))))

(defn create-google-authenticator
  "Creates a Google authenticator for use with [[oidc-provider.core/create-provider]].

  Takes a configuration map with `:client-id`, `:client-secret`, and optional settings.
  Returns a map containing `:credential-validator` and `:claims-provider` that implement
  the required oidc-provider protocols.

  Configuration options:

  - `:client-id` - Google OAuth client ID (required)
  - `:client-secret` - Google OAuth client secret (required)
  - `:cache-ttl-ms` - Cache TTL for userinfo responses in milliseconds (default: 5 minutes)"
  [config]
  (let [config (validate-config (merge default-config config))]
    {:credential-validator (provider/create-credential-validator
                            config
                            (:cache-ttl-ms config))
     :claims-provider (provider/create-claims-provider
                       (:cache-ttl-ms config))}))

(defn authorization-url
  "Generates a Google OAuth authorization URL.

  Takes configuration, state, and optional parameters map. Returns a URL string
  that the user should be redirected to for Google authentication. Uses the scopes
  specified in config or defaults to `[\"openid\" \"email\" \"profile\"]`.

  Optional parameters:
  - `:nonce` - Nonce for replay protection
  - `:access-type` - \"offline\" to get refresh tokens
  - `:prompt` - \"consent\" to force consent screen, \"select_account\" for account picker
  - `:login-hint` - Email address to pre-fill"
  ([config state]
   (authorization-url config state {}))
  ([config state opts]
   (let [config (validate-config (merge default-config config))]
     (client/authorization-url config state opts))))

(defn exchange-code
  "Exchanges an authorization code for tokens.

  Takes configuration and the authorization code received from Google's callback.
  Returns a map containing `:access_token`, `:id_token`, `:token_type`, `:expires_in`,
  and optionally `:refresh_token` (if access_type=offline was used)."
  [config code]
  (let [config (validate-config (merge default-config config))]
    (client/exchange-code config code)))

(defn refresh-token
  "Refreshes an access token using a refresh token.

  Takes configuration and the refresh token obtained from a previous authorization
  with access_type=offline. Returns a new token response."
  [config refresh-token-val]
  (let [config (validate-config (merge default-config config))]
    (client/refresh-token config refresh-token-val)))

(defn fetch-userinfo
  "Fetches Google user information using an access token.

  Returns a map containing Google user profile data including sub, name, email,
  picture, and locale."
  [access-token]
  (client/fetch-userinfo access-token))

(defn validate-id-token
  "Validates a Google ID token.

  Takes an ID token string, client ID, and optional options map. Validates the
  token signature using Google's JWKS and checks issuer, audience, and expiration.

  Options:
  - `:nonce` - Expected nonce value
  - `:leeway` - Clock skew leeway in seconds

  Returns the decoded token claims on success, throws on validation failure."
  ([id-token client-id]
   (client/validate-id-token id-token client-id))
  ([id-token client-id opts]
   (client/validate-id-token id-token client-id opts)))
