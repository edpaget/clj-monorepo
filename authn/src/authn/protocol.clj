(ns authn.protocol
  "Core authentication protocols.

  Defines extensibility points for credential validation, claims provisioning,
  and session storage. These protocols enable pluggable authentication backends
  and storage implementations.")

(set! *warn-on-reflection* true)

(def CredentialHash
  "Malli schema for credential hash.

  Can be any map containing authentication data. Common examples:
  - `{:username \"user\" :password \"pass\"}` - Username/password
  - `{:api-key \"key\"}` - API key
  - `{:certificate cert-data}` - Certificate-based auth
  - `{:access-token \"token\"}` - Token-based auth"
  :map)

(def Claims
  "Malli schema for user claims.

  A map of keyword keys to arbitrary values representing user information."
  [:map-of :keyword :any])

(def Session
  "Malli schema for session data.

  Contains user identifier, claims, and metadata."
  [:map
   [:user-id :string]
   [:claims {:optional true} Claims]
   [:created-at {:optional true} pos-int?]
   [:expires-at {:optional true} pos-int?]])

(defprotocol CredentialValidator
  "Protocol for validating credentials and returning user identity.

  Implementations validate credentials using their specific authentication
  mechanism (database lookup, LDAP, OAuth, etc.) and return a user identifier
  if the credentials are valid.

  The credential-hash can contain any authentication data in map form,
  allowing for flexible authentication mechanisms."
  (validate-credentials [this credential-hash client-id]
    "Validates credentials and returns user identifier.

    Takes a map containing authentication credentials and an optional client
    identifier (used in OAuth/OIDC flows, can be nil for first-party authentication).
    The structure of the credentials map depends on the authentication mechanism
    (username/password, API key, token, etc.). Returns the user identifier (string)
    if credentials are valid, or nil if authentication fails.

    For first-party applications, the client-id parameter can be ignored."))

(defprotocol ClaimsProvider
  "Protocol for providing user claims.

  Implementations fetch user information and return it as a map of claims.
  Claims typically include standard attributes like name, email, roles, etc."
  (get-claims [this user-id scope]
    "Retrieves claims for a user based on requested scope.

    Takes a user identifier (from credential validation) and a vector of scope
    strings (like `[\"profile\" \"email\"]`). Returns a map of claims appropriate
    for the requested scopes, such as `{:sub \"user-id\" :email \"user@example.com\"
    :name \"User Name\"}`. The `sub` claim should always be included."))

(defprotocol SessionStore
  "Protocol for managing user sessions.

  Implementations provide storage and retrieval of session data, enabling
  session-based authentication. Sessions are identified by a session ID and
  contain user information and metadata."
  (create-session [this user-id claims]
    "Creates a new session for a user.

    Takes a user identifier and claims map. Generates a unique session ID,
    stores the session data, and returns the session ID string.")

  (get-session [this session-id]
    "Retrieves session data by session ID.

    Takes a session ID string and looks up the associated session. Returns
    a map containing `:user-id`, `:claims`, `:created-at`, and `:expires-at`
    if found, or nil if the session doesn't exist or has expired.")

  (update-session [this session-id session-data]
    "Updates an existing session.

    Takes a session ID string and a map of session data to update. Returns
    true if the session was updated successfully, false otherwise.")

  (delete-session [this session-id]
    "Deletes a session.

    Takes a session ID string and removes it from storage. Used for logout.
    Returns true if the session was deleted successfully.")

  (cleanup-expired [this]
    "Removes expired sessions from storage.

    Implementations should periodically call this to prevent storage from
    growing indefinitely. Returns the number of sessions deleted."))
