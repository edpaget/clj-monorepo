(ns authn.middleware
  "Ring middleware for authentication.

  Provides middleware that integrates session-based authentication into Ring
  applications. Checks for session cookies, validates sessions, and adds user
  information to the request."
  (:require
   [authn.core :as core]
   [ring.util.response :as response]))

(set! *warn-on-reflection* true)

(defn- get-session-id-from-cookie
  "Extracts session ID from request cookies."
  [request cookie-name]
  (get-in request [:cookies cookie-name :value]))

(defn- add-user-to-request
  "Adds authenticated user information to the request."
  [request session-data]
  (assoc request
         :authn/user-id (:user-id session-data)
         :authn/claims (:claims session-data)
         :authn/authenticated? true))

(defn wrap-authentication
  "Middleware that authenticates requests using session cookies.

  Takes a Ring handler and an Authenticator instance. Checks for a session cookie,
  validates the session, and adds user information to the request under the
  `:authn/user-id`, `:authn/claims`, and `:authn/authenticated?` keys.

  If no valid session is found, adds `:authn/authenticated?` false to the request
  and allows the request to proceed (for public routes). Use [[wrap-require-authentication]]
  to enforce authentication.

  Example:

      (def app
        (-> handler
            (wrap-authentication authenticator)
            (ring.middleware.session/wrap-session)))"
  [handler authenticator]
  (fn [request]
    (let [cookie-name (get-in authenticator [:config :session-cookie-name])
          session-id  (get-session-id-from-cookie request cookie-name)]
      (if-let [session-data (and session-id
                                 (core/get-session authenticator session-id))]
        (handler (add-user-to-request request session-data))
        (handler (assoc request :authn/authenticated? false))))))

(defn wrap-require-authentication
  "Middleware that requires authentication for all requests.

  Takes a Ring handler and returns unauthorized (401) response for unauthenticated
  requests. Should be applied after [[wrap-authentication]].

  Example:

      (def protected-app
        (-> handler
            wrap-require-authentication
            (wrap-authentication authenticator)
            (ring.middleware.session/wrap-session)))"
  [handler]
  (fn [request]
    (if (:authn/authenticated? request)
      (handler request)
      (-> (response/response {:error "Unauthorized"})
          (response/status 401)
          (response/content-type "application/json")))))

(defn wrap-session-refresh
  "Middleware that refreshes session expiration on each request.

  Takes a Ring handler and an Authenticator instance. For authenticated requests,
  extends the session's expiration time. This keeps active sessions alive.

  Should be applied after [[wrap-authentication]].

  Example:

      (def app
        (-> handler
            (wrap-session-refresh authenticator)
            (wrap-authentication authenticator)
            (ring.middleware.session/wrap-session)))"
  [handler authenticator]
  (fn [request]
    (let [cookie-name (get-in authenticator [:config :session-cookie-name])
          session-id  (get-session-id-from-cookie request cookie-name)]
      (when (and session-id (:authn/authenticated? request))
        (core/refresh-session authenticator session-id))
      (handler request))))
