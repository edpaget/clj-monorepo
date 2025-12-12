(ns bashketball-game-api.auth.google
  "Google OIDC integration for authentication.

  Provides OIDC client configuration and success/error handlers for
  the OAuth callback flow."
  (:require
   [authn.protocol :as authn-proto]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.services.avatar :as avatar-svc]
   [clojure.tools.logging :as log]
   [db.core :as db]
   [oidc-google.core :as google]
   [oidc.core :as oidc]
   [ring.util.response :as response]))

(def google-issuer "https://accounts.google.com")

(defn create-google-oidc-client
  "Creates an OIDC client configured for Google OAuth.

  Takes Google OAuth configuration with `:client-id`, `:client-secret`,
  `:redirect-uri`, and optionally `:scopes`."
  [{:keys [client-id client-secret redirect-uri scopes]}]
  (oidc/create-client
   {:issuer google-issuer
    :client-id client-id
    :client-secret client-secret
    :redirect-uri redirect-uri
    :scopes (or scopes ["openid" "email" "profile"])}))

(defn create-success-handler
  "Creates a success callback that integrates Google OAuth with authn sessions.

  When OAuth succeeds:
  1. Fetches Google user info using the access token
  2. Upserts user in database
  3. Triggers async avatar download
  4. Creates authn session
  5. Redirects to success URI

  Takes a config map with:
  - `:user-repo` - User repository for upserting users
  - `:avatar-service` - Avatar service for caching profile pictures
  - `:authenticator` - Authn authenticator for session management
  - `:success-redirect-uri` - URI to redirect after successful auth
  - `:db-pool` - Database connection pool"
  [{:keys [user-repo avatar-service authenticator success-redirect-uri db-pool]}]
  (fn [_request token-response]
    (binding [db/*datasource* db-pool]
      (let [access-token (:access_token token-response)
            ;; Fetch Google user info
            userinfo     (google/fetch-userinfo access-token)
            google-id    (:sub userinfo)
            email        (:email userinfo)
            name         (:name userinfo)
            picture      (:picture userinfo)]
        (log/info "Google OAuth success for user:" email)
        ;; Upsert user in database
        (let [user       (user/upsert-from-google!
                          user-repo
                          {:sub google-id
                           :email email
                           :name name
                           :picture picture})
              user-id    (:id user)
              claims     {:sub (str user-id)
                          :email email
                          :name name
                          :picture picture}
              session-id (authn-proto/create-session
                          (:session-store authenticator)
                          (str user-id)
                          claims)]
          ;; Trigger async avatar download (non-blocking)
          (when (and avatar-service picture)
            (avatar-svc/fetch-avatar-async! avatar-service user-id picture))
          (log/info "Created session for user:" user-id)
          (-> (response/redirect success-redirect-uri)
              (assoc :session {:authn/session-id session-id})))))))

(defn create-error-handler
  "Creates an error callback for OAuth failures.

  Takes a config map with:
  - `:error-redirect-uri` - URI to redirect on auth failure (optional)
  - `:success-redirect-uri` - Fallback redirect with error param"
  [{:keys [error-redirect-uri success-redirect-uri]}]
  (fn [_request error]
    (log/warn "Google OAuth error:" error)
    (let [redirect-uri (or error-redirect-uri
                           (str success-redirect-uri "?error=" error))]
      (-> (response/redirect redirect-uri)
          (assoc :session nil)))))
