(ns oidc-apple.core
  "Apple Sign-In integration.

   Provides ID token validation and credential validation for Apple Sign-In.
   Unlike Google, Apple does not have a userinfo endpoint - all user data comes
   from the ID token.

   ## Key Differences from Other Providers

   1. **No userinfo endpoint**: All claims come from the ID token
   2. **First-time only data**: Email and name are only provided on first authorization
   3. **Private relay emails**: Users can hide their real email
   4. **Fraud detection**: `real_user_status` indicates confidence level

   ## Provider Usage

   Use Apple as an authenticator for your OIDC provider:

       (require '[oidc-apple.core :as apple]
                '[oidc-provider.core :as provider])

       (def authenticator (apple/create-apple-authenticator
                           {:client-id \"com.yourapp.service\"}))

       (def provider
         (provider/create-provider
          {:issuer \"https://your-app.com\"
           :credential-validator (:credential-validator authenticator)
           :claims-provider (:claims-provider authenticator)
           ...}))

   ## Direct Token Validation

   Validate Apple ID tokens directly:

       (require '[oidc-apple.core :as apple])

       (def result (apple/validate-id-token
                    {:client-id \"com.yourapp.service\"}
                    id-token-string))

       (when (:valid? result)
         (println \"User:\" (get-in result [:claims :sub])))"
  (:require
   [malli.core :as m]
   [oidc-apple.claims :as claims]
   [oidc-apple.client :as client]
   [oidc-apple.validator :as validator]
   [oidc.id-token :as id-token]))

(set! *warn-on-reflection* true)

(def apple-issuer
  "Apple's OIDC issuer URL."
  "https://appleid.apple.com")

(def Config
  "Configuration schema for Apple Sign-In integration."
  [:map
   [:client-id :string]  ;; Your Apple App ID or Services ID
   [:team-id {:optional true} :string]  ;; For client secret JWT generation (web flow)
   [:key-id {:optional true} :string]   ;; For client secret JWT generation (web flow)
   [:private-key {:optional true} :string]])  ;; For client secret JWT generation (web flow)

(defn validate-config
  "Validates configuration against the Config schema.

   Returns the config if valid, throws an exception otherwise."
  [config]
  (if (m/validate Config config)
    config
    (throw (ex-info "Invalid Apple Sign-In configuration"
                    {:config config
                     :errors (m/explain Config config)}))))

(defn create-apple-authenticator
  "Creates an Apple authenticator for use with [[oidc-provider.core/create-provider]].

   Takes a configuration map with `:client-id` (required) and optional settings.
   Returns a map containing `:credential-validator` and `:claims-provider` that
   implement the required oidc-provider protocols.

   Configuration options:

   - `:client-id` - Apple App ID or Services ID (required)
   - `:team-id` - Apple Developer Team ID (optional, for web flow)
   - `:key-id` - Sign in with Apple key ID (optional, for web flow)
   - `:private-key` - Private key contents (optional, for web flow)

   Note: Apple's ClaimsProvider has limited functionality since there's no
   userinfo endpoint. Your application should store user data from the initial
   sign-in and retrieve it from your database."
  [config]
  (let [config    (validate-config config)
        client-id (:client-id config)]
    {:credential-validator (validator/create-credential-validator client-id)
     :claims-provider (validator/create-claims-provider client-id)
     :config config}))

(defn validate-id-token
  "Validates an Apple ID token.

   Takes a configuration map and the ID token string. Validates the token
   signature using Apple's JWKS and checks issuer, audience, and expiration.

   Arguments:
   - config: Map with :client-id
   - id-token: JWT ID token string from Apple Sign-In

   Returns:
   - {:valid? true :claims {...}} on success
   - {:valid? false :error \"...\"} on failure

   The claims include standard OIDC claims plus Apple-specific claims:
   - :sub - Apple's unique user identifier
   - :email - User's email (may be private relay, only on first auth)
   - :email_verified - Always true for Apple
   - :real_user_status - Fraud detection (0=unsupported, 1=unknown, 2=likely real)"
  [config id-token]
  (let [config    (validate-config config)
        validator (id-token/create-validator)]
    (id-token/validate validator
                       {:id-token id-token
                        :issuer apple-issuer
                        :audience (:client-id config)})))

(defn extract-user-info
  "Extracts user info from Apple ID token claims.

   Arguments:
   - claims: Decoded JWT claims from a validated Apple ID token
   - opts: Optional map with:
     - :user-provided-name - Name provided separately (for first-time users)

   Returns a map with normalized user info including :sub, :email, :name, etc.

   Note: Apple only provides email/name on the FIRST authorization. For
   subsequent logins, you must retrieve stored user data from your database."
  ([claims]
   (extract-user-info claims nil))
  ([claims opts]
   (claims/extract-user-info claims opts)))

(defn real-user?
  "Checks if Apple's fraud detection indicates a likely real user.

   Returns true only if `real_user_status` is 2 (high confidence).
   Consider additional verification for status 0 (unsupported) or 1 (unknown)
   in high-security scenarios."
  [claims]
  (claims/real-user? claims))

(defn private-relay-email?
  "Checks if the email is an Apple Private Relay address.

   Users can choose to hide their real email during Sign in with Apple.
   Private relay emails forward to the user's real address but can be
   disabled by the user at any time."
  [email]
  (claims/private-relay-email? email))

;; ---------------------------------------------------------------------------
;; Web OAuth Flow Functions

(defn generate-client-secret
  "Generates a JWT client secret for Apple Sign-In web flow.

   Apple requires a JWT signed with ES256 containing team-id, client-id,
   and expiration. This secret is used in place of a static client secret.

   Arguments:
   - config: Map with :team-id, :key-id, :client-id, :private-key
   - opts: Optional map with :expires-in-seconds (default 86400)

   Returns a JWT string."
  [config & opts]
  (apply client/generate-client-secret config opts))

(defn authorization-url
  "Generates Apple Sign-In authorization URL.

   Arguments:
   - config: Map with :client-id and :redirect-uri
   - state: Random state string for CSRF protection
   - opts: Optional map with :response-mode and :scope

   Returns the authorization URL string."
  [config state & opts]
  (apply client/authorization-url config state opts))

(defn exchange-code
  "Exchanges authorization code for tokens.

   Generates a client secret JWT and exchanges the code at Apple's token
   endpoint.

   Arguments:
   - config: Map with :client-id, :team-id, :key-id, :private-key, :redirect-uri
   - code: Authorization code from Apple callback

   Returns token response map with :access_token, :id_token, etc."
  [config code]
  (client/exchange-code config code))
