(ns oidc-github.core
  "GitHub OAuth and OIDC integration.

  Provides both provider-side authentication (for use with [[oidc-provider.core]])
  and client-side OAuth flow helpers (wrapping [[oidc.core]]) with GitHub-specific
  defaults and configurations.

  ## Provider Usage

  Use GitHub as an authenticator for your OIDC provider:

      (require '[oidc-github.core :as github]
               '[oidc-provider.core :as provider])

      (def config
        {:client-id \"your-github-app-id\"
         :client-secret \"your-github-app-secret\"
         :required-org \"your-org\"})

      (def authenticator (github/create-github-authenticator config))

      (def provider
        (provider/create-provider
         {:issuer \"https://your-app.com\"
          :credential-validator (:credential-validator authenticator)
          :claims-provider (:claims-provider authenticator)
          ...}))

  ## Client Usage

  Use GitHub as an OIDC provider:

      (require '[oidc-github.core :as github])

      (def config
        {:client-id \"your-github-app-id\"
         :client-secret \"your-github-app-secret\"
         :redirect-uri \"https://your-app.com/callback\"})

      (def auth-url (github/authorization-url config \"state-123\" \"nonce-456\"))
      (def token (github/exchange-code config \"code-from-callback\"))
      (def user (github/fetch-user (:access_token token)))"
  (:require
   [malli.core :as m]
   [oidc-github.client :as client]
   [oidc-github.provider :as provider]))

(def Config
  "Configuration schema for GitHub OAuth/OIDC integration."
  [:map
   [:client-id :string]
   [:client-secret :string]
   [:redirect-uri {:optional true} :string]
   [:scopes {:optional true} [:vector :string]]
   [:enterprise-url {:optional true} :string]
   [:required-org {:optional true} :string]
   [:validate-org? {:optional true} :boolean]
   [:cache-ttl-ms {:optional true} :int]])

(def default-config
  "Default configuration values for GitHub OAuth/OIDC integration."
  {:scopes ["user:email" "read:user" "read:org"]
   :validate-org? false
   :cache-ttl-ms (* 5 60 1000)})

(defn validate-config
  "Validates configuration against the Config schema.

  Returns the config if valid, throws an exception otherwise."
  [config]
  (if (m/validate Config config)
    config
    (throw (ex-info "Invalid GitHub OIDC configuration"
                    {:config config
                     :errors (m/explain Config config)}))))

(defn create-github-authenticator
  "Creates a GitHub authenticator for use with [[oidc-provider.core/create-provider]].

  Takes a configuration map with `:client-id`, `:client-secret`, and optional settings.
  Returns a map containing `:credential-validator` and `:claims-provider` that implement
  the required oidc-provider protocols.

  Configuration options:

  - `:client-id` - GitHub OAuth App client ID (required)
  - `:client-secret` - GitHub OAuth App client secret (required)
  - `:required-org` - GitHub organization that users must belong to (optional)
  - `:validate-org?` - Whether to validate org membership (default: false)
  - `:enterprise-url` - Base URL for GitHub Enterprise Server (optional)
  - `:cache-ttl-ms` - Cache TTL for GitHub API responses in milliseconds (default: 5 minutes)

  Example:

      (def auth (create-github-authenticator
                  {:client-id \"abc123\"
                   :client-secret \"secret\"
                   :required-org \"my-company\"
                   :validate-org? true}))"
  [config]
  (let [config (validate-config (merge default-config config))]
    {:credential-validator (provider/->GitHubCredentialValidator
                            (:client-id config)
                            (:client-secret config)
                            (:required-org config)
                            (:validate-org? config)
                            (:enterprise-url config)
                            (:cache-ttl-ms config))
     :claims-provider (provider/->GitHubClaimsProvider
                       (:enterprise-url config)
                       (:cache-ttl-ms config))}))

(defn authorization-url
  "Generates a GitHub OAuth authorization URL.

  Takes configuration, state, and optional nonce. Returns a URL string that the user
  should be redirected to for GitHub authentication. Uses the scopes specified in config
  or defaults to `[\"user:email\" \"read:user\" \"read:org\"]`.

  Example:

      (authorization-url
        {:client-id \"abc123\"
         :redirect-uri \"https://app.com/callback\"
         :scopes [\"user:email\"]}
        \"state-123\")"
  ([config state]
   (authorization-url config state nil))
  ([config state nonce]
   (let [config (validate-config (merge default-config config))]
     (client/authorization-url config state nonce))))

(defn exchange-code
  "Exchanges an authorization code for an access token.

  Takes configuration and the authorization code received from GitHub's callback.
  Returns a map containing `:access_token`, `:token_type`, and `:scope`.

  Example:

      (exchange-code
        {:client-id \"abc123\"
         :client-secret \"secret\"
         :redirect-uri \"https://app.com/callback\"}
        \"code-from-github\")"
  [config code]
  (let [config (validate-config (merge default-config config))]
    (client/exchange-code config code)))

(defn fetch-user
  "Fetches GitHub user information using an access token.

  Returns a map containing GitHub user profile data including login, name, email,
  avatar URL, and organization memberships.

  Example:

      (fetch-user \"ghp_abc123xyz\")"
  [access-token]
  (client/fetch-user access-token))

(defn refresh-token
  "Refreshes an access token.

  Note: GitHub OAuth Apps do not support refresh tokens. This is provided for
  completeness but will throw an exception if called with a GitHub OAuth App.
  GitHub Apps (not OAuth Apps) do support refresh tokens."
  [config refresh-token]
  (let [config (validate-config (merge default-config config))]
    (client/refresh-token config refresh-token)))
