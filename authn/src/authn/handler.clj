(ns authn.handler
  "Ring request handlers for login and logout.

  Provides ready-to-use Ring handlers for common authentication operations.
  These handlers accept JSON request bodies and return JSON responses."
  (:require
   [authn.core :as core]
   [cheshire.core :as json]
   [clojure.string :as str]
   [ring.util.response :as response]))

(set! *warn-on-reflection* true)

(defn- parse-json-body
  "Parses JSON request body."
  [request]
  (when-let [body (:body request)]
    (json/parse-stream (clojure.java.io/reader body) true)))

(defn- json-response
  "Creates a JSON response."
  ([data]
   (json-response data 200))
  ([data status]
   (-> (response/response (json/generate-string data))
       (response/status status)
       (response/content-type "application/json"))))

(defn- set-session-cookie
  "Adds a session cookie to the response."
  [response session-id authenticator]
  (let [config      (:config authenticator)
        cookie-name (:session-cookie-name config)
        ttl-seconds (/ (:session-ttl-ms config) 1000)
        cookie-opts {:value session-id
                     :path "/"
                     :http-only (:session-cookie-http-only? config)
                     :secure (:session-cookie-secure? config)
                     :same-site (:session-cookie-same-site config)
                     :max-age (int ttl-seconds)}]
    (response/set-cookie response cookie-name cookie-opts)))

(defn- clear-session-cookie
  "Removes the session cookie from the response."
  [response authenticator]
  (let [cookie-name (get-in authenticator [:config :session-cookie-name])]
    (response/set-cookie response cookie-name "" {:max-age 0 :path "/"})))

(defn- get-session-id-from-cookie
  "Extracts session ID from request cookies."
  [request cookie-name]
  (get-in request [:cookies cookie-name :value]))

(defn login-handler
  "Ring handler for login requests.

  Accepts a POST request with JSON body containing credentials. The structure
  of the credentials depends on your CredentialValidator implementation.

  Common examples:
  - `{\"username\": \"user\", \"password\": \"pass\"}` - Username/password
  - `{\"api-key\": \"key\"}` - API key authentication
  - `{\"access-token\": \"token\"}` - Token-based authentication

  On success, returns 200 with session information and sets a session cookie.
  On failure, returns 401 with error message.

  Example:

      (defn routes [authenticator]
        [[\"POST\" \"/login\" (login-handler authenticator)]])"
  [authenticator]
  (fn [request]
    (let [credentials (parse-json-body request)]
      (if-let [session-id (core/authenticate authenticator credentials)]
        (let [session-data (core/get-session authenticator session-id)]
          (-> (json-response {:success true
                              :user-id (:user-id session-data)
                              :claims (:claims session-data)})
              (set-session-cookie session-id authenticator)))
        (json-response {:success false
                        :error "Invalid credentials"}
                       401)))))

(defn logout-handler
  "Ring handler for logout requests.

  Accepts a POST request. Reads the session cookie, destroys the session,
  and clears the session cookie. Returns 200 on success.

  Example:

      (defn routes [authenticator]
        [[\"POST\" \"/logout\" (logout-handler authenticator)]])"
  [authenticator]
  (fn [request]
    (let [cookie-name (get-in authenticator [:config :session-cookie-name])
          session-id  (get-session-id-from-cookie request cookie-name)]
      (when session-id
        (core/logout authenticator session-id))
      (-> (json-response {:success true})
          (clear-session-cookie authenticator)))))

(defn whoami-handler
  "Ring handler that returns current user information.

  Accepts a GET request. Returns the authenticated user's information if
  authenticated, or 401 if not authenticated.

  This handler should be used after the authentication middleware has been applied.

  Example:

      (defn routes [authenticator]
        [[\"GET\" \"/whoami\" (whoami-handler)]])"
  []
  (fn [request]
    (if (:authn/authenticated? request)
      (json-response {:authenticated true
                      :user-id (:authn/user-id request)
                      :claims (:authn/claims request)})
      (json-response {:authenticated false
                      :error "Not authenticated"}
                     401))))
