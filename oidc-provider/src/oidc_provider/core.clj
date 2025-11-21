(ns oidc-provider.core
  "Core OIDC provider setup and configuration."
  (:require
   [malli.core :as m]
   [oidc-provider.authorization :as authz]
   [oidc-provider.discovery :as disco]
   [oidc-provider.protocol :as proto]
   [oidc-provider.store :as store]
   [oidc-provider.token :as token]
   [oidc-provider.token-endpoint :as token-ep]))

(set! *warn-on-reflection* true)

(def ProviderSetup
  "Malli schema for provider setup configuration."
  [:map
   [:issuer :string]
   [:authorization-endpoint :string]
   [:token-endpoint :string]
   [:jwks-uri :string]
   [:signing-key {:optional true} [:fn (fn [k] (instance? com.nimbusds.jose.jwk.RSAKey k))]]
   [:access-token-ttl-seconds {:optional true} pos-int?]
   [:id-token-ttl-seconds {:optional true} pos-int?]
   [:authorization-code-ttl-seconds {:optional true} pos-int?]
   [:client-store {:optional true} [:fn #(satisfies? proto/ClientStore %)]]
   [:code-store {:optional true} [:fn #(satisfies? proto/AuthorizationCodeStore %)]]
   [:token-store {:optional true} [:fn #(satisfies? proto/TokenStore %)]]
   [:credential-validator {:optional true} [:fn #(satisfies? proto/CredentialValidator %)]]
   [:claims-provider {:optional true} [:fn #(satisfies? proto/ClaimsProvider %)]]])

(defrecord Provider [config
                     provider-config
                     client-store
                     code-store
                     token-store
                     credential-validator
                     claims-provider])

(defn create-provider
  "Creates an OIDC provider instance.

  Args:
    config: Provider configuration map
      Required:
        - :issuer - Provider issuer URL
        - :authorization-endpoint - Authorization endpoint URL
        - :token-endpoint - Token endpoint URL
        - :jwks-uri - JWKS endpoint URL
      Optional:
        - :signing-key - RSAKey for signing tokens (generated if not provided)
        - :access-token-ttl-seconds - Access token TTL (default 3600)
        - :id-token-ttl-seconds - ID token TTL (default 3600)
        - :authorization-code-ttl-seconds - Auth code TTL (default 600)
        - :client-store - ClientStore implementation (in-memory created if not provided)
        - :code-store - AuthorizationCodeStore implementation (in-memory created if not provided)
        - :token-store - TokenStore implementation (in-memory created if not provided)
        - :credential-validator - CredentialValidator implementation (required for auth)
        - :claims-provider - ClaimsProvider implementation (required for claims)

  Returns:
    Provider instance"
  [{:keys [issuer
           signing-key
           access-token-ttl-seconds
           id-token-ttl-seconds
           authorization-code-ttl-seconds
           client-store
           code-store
           token-store
           credential-validator
           claims-provider] :as config}]
  {:pre [(m/validate ProviderSetup config)]}
  (let [key             (or signing-key (token/generate-rsa-key))
        provider-config {:issuer issuer
                         :signing-key key
                         :access-token-ttl-seconds (or access-token-ttl-seconds 3600)
                         :id-token-ttl-seconds (or id-token-ttl-seconds 3600)
                         :authorization-code-ttl-seconds (or authorization-code-ttl-seconds 600)}]
    (->Provider config
                provider-config
                (or client-store (store/create-client-store))
                (or code-store (store/create-authorization-code-store))
                (or token-store (store/create-token-store))
                credential-validator
                claims-provider)))

(defn discovery-metadata
  "Returns OpenID Connect Discovery metadata for the provider.

  Args:
    provider: Provider instance

  Returns:
    Discovery metadata map"
  [provider]
  (disco/openid-configuration
   (select-keys (:config provider)
                [:issuer
                 :authorization-endpoint
                 :token-endpoint
                 :jwks-uri
                 :userinfo-endpoint
                 :scopes-supported
                 :response-types-supported
                 :grant-types-supported
                 :subject-types-supported
                 :id-token-signing-alg-values-supported
                 :token-endpoint-auth-methods-supported
                 :claims-supported])))

(defn jwks
  "Returns JWKS for the provider.

  Args:
    provider: Provider instance

  Returns:
    JWKS map"
  [provider]
  (disco/jwks-endpoint (:provider-config provider)))

(defn parse-authorization-request
  "Parses and validates an authorization request.

  Args:
    provider: Provider instance
    query-string: Query string from authorization request

  Returns:
    Validated authorization request map

  Throws:
    ex-info on validation errors"
  [provider query-string]
  (authz/parse-authorization-request query-string (:client-store provider)))

(defn authorize
  "Handles authorization approval after user authentication.

  Args:
    provider: Provider instance
    request: Parsed authorization request
    user-id: User identifier who approved the request

  Returns:
    Redirect URL string"
  [provider request user-id]
  (let [response (authz/handle-authorization-approval
                  request
                  user-id
                  (:provider-config provider)
                  (:code-store provider))]
    (authz/build-redirect-url response)))

(defn deny-authorization
  "Handles authorization denial.

  Args:
    provider: Provider instance
    request: Parsed authorization request
    error-code: OAuth2 error code
    error-description: Error description

  Returns:
    Redirect URL string"
  [_provider request error-code error-description]
  (let [response (authz/handle-authorization-denial request error-code error-description)]
    (authz/build-redirect-url response)))

(defn token-request
  "Handles token endpoint request.

  Args:
    provider: Provider instance
    params: Token request parameters (from form body)
    authorization-header: Authorization header value (optional, for client auth)

  Returns:
    Token response map

  Throws:
    ex-info on validation or processing errors"
  [provider params authorization-header]
  (token-ep/handle-token-request
   params
   authorization-header
   (:provider-config provider)
   (:client-store provider)
   (:code-store provider)
   (:token-store provider)
   (:claims-provider provider)))

(defn register-client
  "Registers a new OAuth2/OIDC client.

  Args:
    provider: Provider instance
    client-config: Client configuration map

  Returns:
    Registered client configuration with client-id"
  [provider client-config]
  (proto/register-client (:client-store provider) client-config))

(defn get-client
  "Retrieves a client configuration.

  Args:
    provider: Provider instance
    client-id: Client identifier

  Returns:
    Client configuration map or nil"
  [provider client-id]
  (proto/get-client (:client-store provider) client-id))
