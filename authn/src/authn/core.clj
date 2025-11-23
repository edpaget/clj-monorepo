(ns authn.core
  "Core authentication functionality.

  Provides session-based authentication for first-party applications. Integrates
  with Ring middleware to handle login, logout, and session management using
  cookie-based sessions.

  ## Basic Usage

      (require '[authn.core :as authn]
               '[authn.store :as store])

      (def authenticator
        (authn/create-authenticator
          {:credential-validator my-validator
           :claims-provider my-claims-provider
           :session-store (store/create-session-store)
           :session-ttl-ms (* 24 60 60 1000)})) ; 24 hours

  ## With Ring Handler

      (require '[authn.middleware :as mw])

      (def app
        (-> handler
            (mw/wrap-authentication authenticator)
            (ring.middleware.session/wrap-session)))

  ## Login/Logout

      (require '[authn.handler :as handler])

      (defn routes [authenticator]
        [[\"POST\" \"/login\" (handler/login-handler authenticator)]
         [\"POST\" \"/logout\" (handler/logout-handler authenticator)]])"
  (:require
   [authn.protocol :as proto]
   [authn.store :as store]
   [malli.core :as m]))

(set! *warn-on-reflection* true)

(def Config
  "Malli schema for authenticator configuration."
  [:map
   [:credential-validator [:fn #(satisfies? proto/CredentialValidator %)]]
   [:claims-provider [:fn #(satisfies? proto/ClaimsProvider %)]]
   [:session-store {:optional true} [:fn #(satisfies? proto/SessionStore %)]]
   [:session-ttl-ms {:optional true} pos-int?]
   [:session-cookie-name {:optional true} :string]
   [:session-cookie-secure? {:optional true} :boolean]
   [:session-cookie-http-only? {:optional true} :boolean]
   [:session-cookie-same-site {:optional true} [:enum :strict :lax :none]]])

(def default-config
  "Default configuration values."
  {:session-ttl-ms (* 24 60 60 1000)
   :session-cookie-name "session-id"
   :session-cookie-secure? true
   :session-cookie-http-only? true
   :session-cookie-same-site :lax})

(defrecord Authenticator [credential-validator
                           claims-provider
                           session-store
                           config])

(defn create-authenticator
  "Creates an authenticator instance.

  Takes a configuration map with required keys `:credential-validator` and
  `:claims-provider`. Optional keys include `:session-store` (created in-memory
  if not provided), `:session-ttl-ms` (defaults to 24 hours), and session cookie
  configuration options.

  Session cookie options:
  - `:session-cookie-name` - Cookie name (default: \"session-id\")
  - `:session-cookie-secure?` - Require HTTPS (default: true)
  - `:session-cookie-http-only?` - HTTP only flag (default: true)
  - `:session-cookie-same-site` - SameSite attribute (default: :lax)

  Returns an Authenticator record."
  [{:keys [credential-validator
           claims-provider
           session-store
           session-ttl-ms] :as config}]
  {:pre [(m/validate Config config)]}
  (let [merged-config (merge default-config config)
        ttl (or session-ttl-ms (:session-ttl-ms merged-config))
        store (or session-store (store/create-session-store ttl))]
    (->Authenticator credential-validator
                     claims-provider
                     store
                     merged-config)))

(defn authenticate
  "Authenticates credentials and creates a session.

  Takes an Authenticator instance, credentials map, and optional scope vector.
  Validates the credentials, fetches user claims, creates a session, and returns
  the session ID. Returns nil if authentication fails.

  Example:

      (authenticate authenticator
                    {:username \"user\" :password \"pass\"}
                    [\"profile\" \"email\"])"
  ([authenticator credentials]
   (authenticate authenticator credentials []))
  ([authenticator credentials scope]
   (when-let [user-id (proto/validate-credentials
                       (:credential-validator authenticator)
                       credentials
                       nil)]
     (let [claims (proto/get-claims
                   (:claims-provider authenticator)
                   user-id
                   scope)]
       (proto/create-session
        (:session-store authenticator)
        user-id
        claims)))))

(defn get-session
  "Retrieves session data by session ID.

  Takes an Authenticator instance and session ID string. Returns the session
  data map if found and valid, or nil if the session doesn't exist or has expired."
  [authenticator session-id]
  (proto/get-session (:session-store authenticator) session-id))

(defn logout
  "Destroys a session.

  Takes an Authenticator instance and session ID string. Deletes the session
  from storage. Returns true if successful."
  [authenticator session-id]
  (proto/delete-session (:session-store authenticator) session-id))

(defn refresh-session
  "Refreshes a session by extending its expiration.

  Takes an Authenticator instance and session ID string. Updates the session's
  expiration time. Returns true if successful, false if session doesn't exist."
  [authenticator session-id]
  (when-let [session (get-session authenticator session-id)]
    (let [now (System/currentTimeMillis)
          ttl (get-in authenticator [:config :session-ttl-ms])
          expires-at (+ now ttl)]
      (proto/update-session
       (:session-store authenticator)
       session-id
       {:expires-at expires-at}))))

(defn cleanup-sessions
  "Removes expired sessions.

  Takes an Authenticator instance and removes all expired sessions from storage.
  Returns the number of sessions deleted. Should be called periodically in
  production applications."
  [authenticator]
  (proto/cleanup-expired (:session-store authenticator)))
