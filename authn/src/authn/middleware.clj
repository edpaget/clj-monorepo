(ns authn.middleware
  "Ring middleware for authentication.

  Provides middleware that integrates session-based authentication into Ring
  applications. Reads session IDs from Ring's session middleware, validates
  sessions against the session store, and adds user information to requests."
  (:require
   [authn.core :as core]
   [ring.util.response :as response]))

(set! *warn-on-reflection* true)

(defn- get-session-id
  "Extracts session ID from Ring's session map."
  [request]
  (get-in request [:session :authn/session-id]))

(defn- add-user-to-request
  "Adds authenticated user information to the request."
  [request session-data]
  (assoc request
         :authn/user-id (:user-id session-data)
         :authn/claims (:claims session-data)
         :authn/authenticated? true))

(defn wrap-authentication
  "Middleware that authenticates requests using Ring sessions.

  Takes a Ring handler and an Authenticator instance. Reads the session ID from
  Ring's `:session` map (under `:authn/session-id`), validates the session against
  the session store, and adds user information to the request under the
  `:authn/user-id`, `:authn/claims`, and `:authn/authenticated?` keys.

  If no valid session is found, adds `:authn/authenticated?` false to the request
  and allows the request to proceed (for public routes). Use [[wrap-require-authentication]]
  to enforce authentication.

  Must be applied after `ring.middleware.session/wrap-session` so that the
  `:session` map is available in the request.

  Example:

      (def app
        (-> handler
            (wrap-authentication authenticator)
            (ring.middleware.session/wrap-session)))"
  [handler authenticator]
  (fn [request]
    (let [session-id (get-session-id request)]
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
    (when-let [session-id (get-session-id request)]
      (when (:authn/authenticated? request)
        (core/refresh-session authenticator session-id)))
    (handler request)))
