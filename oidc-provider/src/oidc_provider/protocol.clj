(ns oidc-provider.protocol
  "Core protocols for OIDC provider extensibility."
  (:require
   [malli.core :as m]))

(set! *warn-on-reflection* true)

(def CredentialHash
  "Malli schema for credential hash - can be any map."
  :map)

(def ClientConfig
  "Malli schema for OAuth2/OIDC client configuration."
  [:map
   [:client-id :string]
   [:client-secret {:optional true} :string]
   [:redirect-uris [:vector :string]]
   [:grant-types [:vector [:enum "authorization_code" "refresh_token" "client_credentials"]]]
   [:response-types [:vector [:enum "code" "token" "id_token"]]]
   [:scopes [:vector :string]]
   [:token-endpoint-auth-method {:optional true}
    [:enum "client_secret_basic" "client_secret_post" "none"]]])

(def Claims
  "Malli schema for OIDC claims."
  [:map-of :keyword :any])

(defprotocol CredentialValidator
  "Protocol for validating credentials and returning user identity.

  The credential-hash can contain any authentication data:
  - {:username \"user\" :password \"pass\"}
  - {:api-key \"key\"}
  - {:certificate cert-data}
  - {:bearer-token \"token\"}

  This allows for flexible authentication mechanisms."
  (validate-credentials [this credential-hash client-id]
    "Validates credentials for the given client.

    Args:
      credential-hash: Map containing authentication credentials
      client-id: OAuth2 client identifier

    Returns:
      User identifier (string) if valid, nil otherwise"))

(defprotocol ClaimsProvider
  "Protocol for providing user claims based on scope."
  (get-claims [this user-id scope]
    "Retrieves claims for a user based on requested scope.

    Args:
      user-id: User identifier from credential validation
      scope: Vector of scope strings (e.g., [\"openid\" \"profile\" \"email\"])

    Returns:
      Map of claims (e.g., {:sub \"user-id\" :email \"user@example.com\" :name \"User Name\"})"))

(defprotocol ClientStore
  "Protocol for managing OAuth2/OIDC client registrations."
  (get-client [this client-id]
    "Retrieves client configuration by client-id.

    Args:
      client-id: OAuth2 client identifier

    Returns:
      Client configuration map matching ClientConfig schema, or nil if not found")

  (register-client [this client-config]
    "Registers a new client.

    Args:
      client-config: Client configuration map matching ClientConfig schema

    Returns:
      Registered client configuration with generated client-id if not provided"))

(defprotocol AuthorizationCodeStore
  "Protocol for storing and retrieving authorization codes."
  (save-authorization-code [this code user-id client-id redirect-uri scope nonce expiry]
    "Saves an authorization code with associated metadata.

    Args:
      code: Authorization code string
      user-id: User identifier
      client-id: OAuth2 client identifier
      redirect-uri: Redirect URI used in authorization request
      scope: Vector of scope strings
      nonce: Optional nonce for replay protection
      expiry: Expiration timestamp (milliseconds since epoch)

    Returns:
      true if saved successfully")

  (get-authorization-code [this code]
    "Retrieves authorization code metadata.

    Args:
      code: Authorization code string

    Returns:
      Map with keys [:user-id :client-id :redirect-uri :scope :nonce :expiry], or nil if not found")

  (delete-authorization-code [this code]
    "Deletes an authorization code (codes are single-use).

    Args:
      code: Authorization code string

    Returns:
      true if deleted successfully"))

(defprotocol TokenStore
  "Protocol for managing access and refresh tokens."
  (save-access-token [this token user-id client-id scope expiry]
    "Saves an access token.

    Args:
      token: Access token string
      user-id: User identifier
      client-id: OAuth2 client identifier
      scope: Vector of scope strings
      expiry: Expiration timestamp (milliseconds since epoch)

    Returns:
      true if saved successfully")

  (get-access-token [this token]
    "Retrieves access token metadata.

    Args:
      token: Access token string

    Returns:
      Map with keys [:user-id :client-id :scope :expiry], or nil if not found")

  (save-refresh-token [this token user-id client-id scope]
    "Saves a refresh token.

    Args:
      token: Refresh token string
      user-id: User identifier
      client-id: OAuth2 client identifier
      scope: Vector of scope strings

    Returns:
      true if saved successfully")

  (get-refresh-token [this token]
    "Retrieves refresh token metadata.

    Args:
      token: Refresh token string

    Returns:
      Map with keys [:user-id :client-id :scope], or nil if not found")

  (revoke-token [this token]
    "Revokes a token (access or refresh).

    Args:
      token: Token string to revoke

    Returns:
      true if revoked successfully"))
