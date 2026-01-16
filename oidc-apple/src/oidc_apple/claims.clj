(ns oidc-apple.claims
  "Apple ID token claims extraction and mapping.

   Unlike Google, Apple does not have a userinfo endpoint. All user data comes
   from the ID token claims. Additionally, Apple only provides email and name
   on the FIRST authorization - subsequent logins only include the `sub` claim.

   Key Apple-specific behaviors:
   - Email may be a Private Relay address (@privaterelay.appleid.com)
   - Name is only provided on first authorization
   - `real_user_status` indicates fraud detection confidence (2 = likely real)")

(set! *warn-on-reflection* true)

(defn extract-user-info
  "Extracts user info from Apple ID token claims.

   Arguments:
   - claims: Decoded JWT claims from Apple ID token
   - user-provided-name: Optional name provided separately (e.g., from app UI)

   Returns map with:
   - :sub - Apple's unique user identifier (stable across logins)
   - :email - User's email (may be private relay address)
   - :email-verified - Whether email is verified (Apple emails are always verified)
   - :name - User's name (only on first auth, otherwise from user-provided-name)
   - :real-user-status - Apple's fraud detection indicator
   - :provider - Always :apple"
  [claims & [{:keys [user-provided-name]}]]
  {:sub (:sub claims)
   :email (:email claims)
   :email-verified (:email_verified claims true)  ;; Apple emails are always verified
   :name user-provided-name
   :real-user-status (:real_user_status claims)
   :provider :apple})

(defn real-user?
  "Returns true if Apple's fraud detection indicates a likely real user.

   Apple's `real_user_status` values:
   - 0: Unsupported (older devices/OS versions)
   - 1: Unknown (system couldn't determine)
   - 2: Likely real (high confidence this is a real person)

   For high-security scenarios, you may want additional verification
   for status 0 or 1."
  [claims]
  (= 2 (:real_user_status claims)))

(defn private-relay-email?
  "Returns true if the email is an Apple Private Relay address.

   When users choose 'Hide My Email' during Sign in with Apple, Apple
   provides a unique @privaterelay.appleid.com address that forwards
   to their real email.

   These relay addresses:
   - Are unique per app (user gets different address for each app)
   - Forward to the user's real email
   - Can be disabled by the user at any time

   Your app should handle email delivery failures gracefully."
  [email]
  (boolean
   (and email
        (or (.endsWith ^String email "@privaterelay.appleid.com")
            (.contains ^String email ".privaterelay.")))))

(defn apple->oidc-claims
  "Transforms Apple ID token claims into standard OIDC claims format.

   Apple's ID token already uses OIDC-compatible claim names, so this
   primarily normalizes the data and adds Apple-specific claims under
   the `apple_` prefix.

   Standard claims returned:
   - `sub` - Apple user ID
   - `email` - Email address (may be private relay)
   - `email_verified` - Always true for Apple

   Custom Apple claims:
   - `apple_real_user_status` - Fraud detection indicator"
  [claims]
  (cond-> {:sub (:sub claims)}
    (:email claims)
    (assoc :email (:email claims))

    (contains? claims :email_verified)
    (assoc :email_verified (:email_verified claims))

    (contains? claims :real_user_status)
    (assoc :apple_real_user_status (:real_user_status claims))))

(defn filter-by-scope
  "Filters OIDC claims based on requested scopes.

   For Apple Sign-In:
   - `email` scope includes: email, email_verified
   - `profile` scope is not applicable (Apple doesn't provide profile via ID token)

   Apple-specific claims (apple_*) are always included regardless of scope.
   Note: Apple's name is provided separately in the authorization response,
   not in the ID token, so it's not included here."
  [claims scope]
  (let [scope-set    (set scope)
        email-claims [:email :email_verified]
        apple-claims [:apple_real_user_status]
        base-claims  [:sub]]
    (select-keys claims
                 (concat base-claims
                         apple-claims
                         (when (scope-set "email") email-claims)))))
