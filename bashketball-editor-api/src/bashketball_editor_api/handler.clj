(ns bashketball-editor-api.handler
  "Ring handler and routes.

  Provides HTTP endpoints for GraphQL, authentication, and health checks."
  (:require
   [authn.core :as authn]
   [authn.middleware :as authn-mw]
   [bashketball-editor-api.auth.github :as gh-auth]
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

(defn create-handler
  "Creates the Ring handler with all middleware.

  Wraps the routes with OIDC OAuth, GraphQL, authentication, session, JSON,
  and database middleware."
  [resolver-map github-oidc-client authenticator db-pool user-repo
   git-repo card-repo set-repo session-config config]
  (let [success-redirect-uri (get-in config [:github :oauth :success-redirect-uri])]
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
                         :set-repo set-repo})
          :enable-graphiql? true})
        (oidc-ring/oidc-middleware
         {:client github-oidc-client
          :login-path "/auth/github/login"
          :callback-path "/auth/github/callback"
          :callback-opts {:success-fn (gh-auth/create-success-handler
                                       user-repo
                                       authenticator
                                       success-redirect-uri)
                          :verify-id-token? false}})
        (gh-auth/mock-discovery)
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
        (wrap-db-datasource db-pool))))
