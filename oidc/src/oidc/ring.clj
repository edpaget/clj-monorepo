(ns oidc.ring
  "Ring middleware for OIDC client authentication flows.

  Provides middleware that handles OIDC authentication routes automatically,
  including login initiation, callback handling, and logout. Uses Ring sessions
  to store state, nonce, and tokens."
  (:require
   [cheshire.core :as json]
   [clojure.string :as str]
   [oidc.authorization :as auth]
   [oidc.discovery :as discovery]
   [oidc.jwt :as jwt]
   [ring.util.response :as response]))

(set! *warn-on-reflection* true)

(defn- json-response
  "Creates a JSON response with the given data and status code."
  [data status]
  (-> (response/response (json/generate-string data))
      (response/status status)
      (response/content-type "application/json")))

(defn- fetch-discovery-cached
  "Fetches discovery document with caching in the request.

  Caches the discovery document in the request under ::discovery to avoid
  repeated fetches during a single request."
  [request issuer]
  (if-let [cached (::discovery request)]
    cached
    (discovery/fetch-discovery-document issuer)))

(defn login-handler
  "Handles the login initiation route.

  Generates state and nonce parameters, stores them in the session, fetches
  the discovery document to get the authorization endpoint, and redirects the
  user to the OIDC provider's authorization URL."
  [client {:keys [prompt max-age ui-locales additional-params]
           :or {prompt nil
                max-age nil
                ui-locales nil
                additional-params {}}}]
  (fn [request]
    (let [state         (auth/generate-state)
          nonce         (auth/generate-nonce)
          discovery-doc (fetch-discovery-cached request (:issuer client))
          auth-url      (auth/authorization-url
                         (:authorization_endpoint discovery-doc)
                         (:client-id client)
                         (:redirect-uri client)
                         {:scope (str/join " " (:scopes client))
                          :state state
                          :nonce nonce
                          :prompt prompt
                          :max-age max-age
                          :ui-locales ui-locales
                          :additional-params additional-params})]
      (-> (response/redirect auth-url)
          (assoc :session (assoc (:session request)
                                 ::state state
                                 ::nonce nonce))))))

(defn callback-handler
  "Handles the OAuth callback route.

  Validates the state parameter against the session, exchanges the authorization
  code for tokens, optionally verifies the ID token, and stores the tokens in
  the session. On success, calls the success-fn with the request and token
  response. On error, calls the error-fn with the request and error information."
  [client {:keys [success-fn error-fn verify-id-token?]
           :or {success-fn (fn [_req _tokens]
                             (-> (response/redirect "/")
                                 (response/status 302)))
                error-fn (fn [_req error]
                           (json-response {:error error} 401))
                verify-id-token? true}}]
  (fn [request]
    (let [params        (or (:params request) (:query-params request))
          code          (get params "code")
          state         (get params "state")
          error-param   (get params "error")
          session-state (get-in request [:session ::state])]
      (cond
        ;; Error from provider
        error-param
        (error-fn request error-param)

        ;; Missing code
        (not code)
        (error-fn request "Missing authorization code")

        ;; State mismatch
        (not= state session-state)
        (error-fn request "State mismatch")

        ;; Exchange code for tokens
        :else
        (try
          (let [discovery-doc  (fetch-discovery-cached request (:issuer client))
                token-response (auth/exchange-code
                                (:token_endpoint discovery-doc)
                                code
                                (:client-id client)
                                (:client-secret client)
                                (:redirect-uri client)
                                {})
                session-nonce  (get-in request [:session ::nonce])]
            ;; Optionally verify ID token
            (when (and verify-id-token? (:id_token token-response))
              (let [jwks-uri (:jwks_uri discovery-doc)
                    jwks     (jwt/fetch-jwks jwks-uri)
                    claims   (jwt/validate-id-token
                              (:id_token token-response)
                              jwks
                              (:issuer discovery-doc)
                              (:client-id client)
                              {:nonce session-nonce})]
                ;; Verify nonce if present (validate-id-token should handle this)
                (when (and session-nonce (not= (:nonce claims) session-nonce))
                  (throw (ex-info "Nonce mismatch" {:type :nonce-mismatch})))))

            ;; Store tokens in session and call success-fn
            (let [response (success-fn request token-response)]
              (assoc response :session (-> (:session request)
                                           (dissoc ::state ::nonce)
                                           (assoc ::tokens token-response)))))
          (catch Exception e
            (error-fn request (or (ex-message e) "Token exchange failed"))))))))

(defn logout-handler
  "Handles the logout route.

  Clears the session tokens. Optionally supports OIDC RP-Initiated Logout by
  redirecting to the provider's end_session_endpoint if configured."
  [client {:keys [post-logout-redirect-uri end-session-redirect?]
           :or {post-logout-redirect-uri nil
                end-session-redirect? false}}]
  (fn [request]
    (if (and end-session-redirect? post-logout-redirect-uri)
      ;; OIDC RP-Initiated Logout
      (let [discovery-doc        (fetch-discovery-cached request (:issuer client))
            end-session-endpoint (:end_session_endpoint discovery-doc)
            id-token             (get-in request [:session ::tokens :id_token])]
        (if end-session-endpoint
          (let [logout-url (str end-session-endpoint
                                "?post_logout_redirect_uri="
                                (java.net.URLEncoder/encode
                                 ^String post-logout-redirect-uri "UTF-8")
                                (when id-token
                                  (str "&id_token_hint="
                                       (java.net.URLEncoder/encode ^String id-token "UTF-8"))))]
            (-> (response/redirect logout-url)
                (assoc :session nil)))
          ;; Fallback if no end_session_endpoint
          (-> (response/redirect post-logout-redirect-uri)
              (assoc :session nil))))
      ;; Simple logout - just clear session
      (-> (json-response {:success true} 200)
          (assoc :session nil)))))

(defn oidc-middleware
  "Ring middleware that adds OIDC authentication routes.

  Intercepts requests to the configured routes and handles OIDC authentication
  flows automatically. Uses Ring sessions to store state, nonce, and tokens.

  Options:
  - `:client` - OIDC client configuration from [[oidc.core/create-client]]
  - `:login-path` - Path for login initiation (default: `/auth/login`)
  - `:callback-path` - Path for OAuth callback (default: `/auth/callback`)
  - `:logout-path` - Path for logout (default: `/auth/logout`)
  - `:login-opts` - Options passed to [[login-handler]] (prompt, max-age, etc.)
  - `:callback-opts` - Options passed to [[callback-handler]] (success-fn, error-fn, etc.)
  - `:logout-opts` - Options passed to [[logout-handler]] (post-logout-redirect-uri, etc.)

  Example:

      (def client
        (oidc/create-client
          {:issuer \"https://accounts.google.com\"
           :client-id \"your-client-id\"
           :client-secret \"your-client-secret\"
           :redirect-uri \"http://localhost:3000/auth/callback\"
           :scopes [\"openid\" \"email\" \"profile\"]}))

      (def app
        (-> handler
            (oidc-middleware
              {:client client
               :callback-opts {:success-fn (fn [req tokens]
                                             (response/redirect \"/dashboard\"))}})))"
  [handler {:keys [client
                   login-path
                   callback-path
                   logout-path
                   login-opts
                   callback-opts
                   logout-opts]
            :or {login-path "/auth/login"
                 callback-path "/auth/callback"
                 logout-path "/auth/logout"
                 login-opts {}
                 callback-opts {}
                 logout-opts {}}}]
  (fn [request]
    (let [uri    (:uri request)
          method (:request-method request)]
      (cond
        ;; Login initiation
        (and (= method :get) (= uri login-path))
        ((login-handler client login-opts) request)

        ;; OAuth callback
        (and (= method :get) (= uri callback-path))
        ((callback-handler client callback-opts) request)

        ;; Logout
        (and (#{:get :post} method) (= uri logout-path))
        ((logout-handler client logout-opts) request)

        ;; Other requests - pass through
        :else
        (handler request)))))

(defn wrap-oidc-tokens
  "Middleware that adds OIDC tokens from session to the request.

  Adds `:oidc/tokens` key to the request containing the token response from
  the session if present. This allows downstream handlers to access tokens
  without directly accessing the session.

  Example:

      (defn my-handler [request]
        (if-let [access-token (get-in request [:oidc/tokens :access_token])]
          {:status 200 :body (str \"Authenticated with token: \" access-token)}
          {:status 401 :body \"Not authenticated\"}))"
  [handler]
  (fn [request]
    (let [tokens (get-in request [:session ::tokens])]
      (handler (cond-> request
                 tokens (assoc :oidc/tokens tokens))))))
