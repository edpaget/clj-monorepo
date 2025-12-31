(ns bashketball-editor-api.handler
  "Ring handler and routes.

  Provides HTTP endpoints for GraphQL, authentication, and health checks."
  (:require
   [authn.core :as authn]
   [authn.middleware :as authn-mw]
   [bashketball-editor-api.auth.github :as gh-auth]
   [bashketball-editor-api.context :as ctx]
   [bashketball-editor-api.models.protocol :as repo]
   [db.core :as db]
   [graphql-server.ring :as gql-ring]
   [oidc.ring :as oidc-ring]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.session :refer [wrap-session]]
   [ring.middleware.session.cookie :refer [cookie-store]]))

(defn health-handler
  "Health check endpoint."
  [_request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body {:status "ok"}})

(defn logout-handler
  "Logout handler that destroys the authn session."
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
  [authenticator]
  (fn [request]
    (let [uri    (:uri request)
          method (:request-method request)]
      (cond
        (and (= method :get) (= uri "/health"))
        (health-handler request)

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

(defn wrap-user-context
  "Middleware that binds user context for Git operations.

  Looks up the authenticated user and binds their info to [[ctx/*user-context*]]
  for the duration of the request. Git repositories use this context for
  commits and pushes."
  [handler user-repo]
  (fn [request]
    (let [user-id  (get-in request [:session :authn/user-id])
          user-ctx (when user-id
                     (when-let [user (repo/find-by user-repo {:id (parse-uuid user-id)})]
                       {:name (:name user)
                        :email (:email user)
                        :github-token (:github-token user)}))]
      (binding [ctx/*user-context* user-ctx]
        (handler request)))))

(defn wrap-cors
  "CORS middleware for cross-origin requests.

  Handles preflight OPTIONS requests and adds CORS headers to responses.
  Only allows origins specified in `allowed-origins` set."
  [handler allowed-origins]
  (fn [request]
    (let [origin   (get-in request [:headers "origin"])
          allowed? (contains? allowed-origins origin)]
      (if (= :options (:request-method request))
        {:status 204
         :headers (cond-> {"Access-Control-Allow-Methods" "GET, POST, OPTIONS"
                           "Access-Control-Allow-Headers" "Content-Type"
                           "Access-Control-Max-Age" "86400"}
                    allowed? (assoc "Access-Control-Allow-Origin" origin
                                    "Access-Control-Allow-Credentials" "true"))}
        (let [response (handler request)]
          (cond-> response
            allowed? (-> (assoc-in [:headers "Access-Control-Allow-Origin"] origin)
                         (assoc-in [:headers "Access-Control-Allow-Credentials"] "true"))))))))

(defn create-handler
  "Creates the Ring handler with all middleware.

  Wraps the routes with OIDC OAuth, GraphQL, authentication, session, JSON,
  CORS, database, and user context middleware."
  [resolver-map github-oidc-client authenticator db-pool user-repo
   git-repo card-repo set-repo branch-repo changes-repo card-service set-service
   session-config config]
  (let [success-redirect-uri (get-in config [:github :oauth :success-redirect-uri])
        required-org         (get-in config [:auth :required-org])
        allowed-origins      (set (get-in config [:cors :allowed-origins]))]
    (-> (routes authenticator)
        (gql-ring/graphql-middleware
         {:path "/graphql"
          :resolver-map resolver-map
          :context-fn (fn [request]
                        {:request request
                         :db-pool db-pool
                         :user-repo user-repo
                         :git-repo git-repo
                         :card-repo card-repo
                         :set-repo set-repo
                         :branch-repo branch-repo
                         :changes-repo changes-repo
                         :card-service card-service
                         :set-service set-service})
          :enable-graphiql? true
          :scalars {:PolicyExpr {:parse identity
                                 :serialize identity}}})
        (oidc-ring/oidc-middleware
         {:client github-oidc-client
          :login-path "/auth/github/login"
          :callback-path "/auth/github/callback"
          :callback-opts {:success-fn (gh-auth/create-success-handler
                                       {:user-repo user-repo
                                        :authenticator authenticator
                                        :success-redirect-uri success-redirect-uri
                                        :required-org required-org})
                          :verify-id-token? false}})
        (gh-auth/mock-discovery)
        (wrap-user-context user-repo)
        (authn-mw/wrap-session-refresh authenticator)
        (authn-mw/wrap-authentication authenticator)
        (wrap-session {:store (cookie-store {:key (:cookie-secret session-config)})
                       :cookie-name (:cookie-name session-config)
                       :cookie-attrs {:http-only (:cookie-http-only? session-config)
                                      :secure (:cookie-secure? session-config)
                                      :same-site (:cookie-same-site session-config)}})
        wrap-json-response
        (wrap-json-body {:keywords? true})
        wrap-params
        (wrap-cors allowed-origins)
        (wrap-db-datasource db-pool))))
