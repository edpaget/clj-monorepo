(ns bashketball-editor-api.handler
  "Ring handler and routes.

  Provides HTTP endpoints for GraphQL, authentication, and health checks."
  (:require
   [authn.core :as authn]
   [authn.middleware :as authn-mw]
   [db.core :as db]
   [graphql-server.ring :as gql-ring]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.session :refer [wrap-session]]))

(defn health-handler
  "Health check endpoint."
  [_request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body {:status "ok"}})

(defn login-handler
  "GitHub OAuth callback handler.

  Exchanges authorization code for access token, creates user session.
  The authn package handles:
  1. Validating the code via CredentialValidator
  2. Fetching user claims via ClaimsProvider (which upserts the user)
  3. Creating a session with the user data"
  [authenticator config]
  (fn [request]
    (let [code (get-in request [:params "code"])]
      (if code
        (if-let [session-id (authn/authenticate authenticator {:code code})]
          {:status 302
           :headers {"Location" (get-in config [:github :oauth :success-redirect-uri])}
           :session {:authn/session-id session-id}}
          {:status 401
           :headers {"Content-Type" "application/json"}
           :body {:error "Authentication failed"}})
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body {:error "Missing authorization code"}}))))

(defn logout-handler
  "Logout handler that destroys the session."
  [authenticator]
  (fn [request]
    (when-let [session-id (get-in request [:session :authn/session-id])]
      (authn/logout authenticator session-id))
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body {:success true}
     :session nil}))

(defn not-found-handler
  "404 handler."
  [_request]
  {:status 404
   :headers {"Content-Type" "application/json"}
   :body {:error "Not found"}})

(defn routes
  "Application routes."
  [authenticator config]
  (fn [request]
    (let [uri    (:uri request)
          method (:request-method request)]
      (cond
        (and (= method :get) (= uri "/health"))
        (health-handler request)

        (and (= method :get) (= uri "/auth/github/callback"))
        ((login-handler authenticator config) request)

        (and (= method :post) (= uri "/auth/logout"))
        ((logout-handler authenticator) request)

        :else
        (not-found-handler request)))))

(defn wrap-db-datasource
  "Middleware that binds the database datasource for the request."
  [handler db-pool]
  (fn [request]
    (binding [db/*datasource* db-pool]
      (handler request))))

(defn wrap-session-refresh
  "Middleware that refreshes the session on each request.

  Extends the session expiration time to keep active users logged in."
  [handler authenticator]
  (fn [request]
    (when-let [session-id (get-in request [:session :authn/session-id])]
      (authn/refresh-session authenticator session-id))
    (handler request)))

(defn create-handler
  "Creates the Ring handler with all middleware.

  Wraps the routes with GraphQL, authentication, session, JSON, and database middleware."
  [resolver-map authenticator db-pool user-repo session-config config]
  (-> (routes authenticator config)
      (gql-ring/graphql-middleware
       {:path "/graphql"
        :resolver-map resolver-map
        :context-fn (fn [request]
                      {:request request
                       :db-pool db-pool
                       :user-repo user-repo})
        :enable-graphiql? true})
      (wrap-session-refresh authenticator)
      (authn-mw/wrap-authentication authenticator)
      (wrap-session {:cookie-name (:cookie-name session-config)
                     :cookie-attrs {:http-only (:cookie-http-only? session-config)
                                    :secure (:cookie-secure? session-config)
                                    :same-site (:cookie-same-site session-config)}})
      wrap-json-response
      (wrap-json-body {:keywords? true})
      wrap-params
      (wrap-db-datasource db-pool)))
