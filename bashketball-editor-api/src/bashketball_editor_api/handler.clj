(ns bashketball-editor-api.handler
  "Ring handler and routes.

  Provides HTTP endpoints for GraphQL, authentication, and health checks."
  (:require
   [authn.middleware :as authn-mw]
   [bashketball-editor-api.graphql.schema :as gql-schema]
   [cheshire.core :as json]
   [clojure.string :as str]
   [db.core :as db]
   [graphql-server.ring :as gql-ring]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.session :refer [wrap-session]]
   [ring.util.response :as response]))

(defn health-handler
  "Health check endpoint."
  [_request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body {:status "ok"}})

(defn login-handler
  "GitHub OAuth callback handler.

  Exchanges authorization code for access token, creates user session."
  [authenticator user-repo]
  (fn [request]
    (let [code (get-in request [:params "code"])
          state (get-in request [:params "state"])]
      (if code
        ;; TODO: Exchange code for access token, create user, create session
        {:status 501
         :headers {"Content-Type" "application/json"}
         :body {:error "Not implemented yet"}}
        {:status 400
         :headers {"Content-Type" "application/json"}
         :body {:error "Missing authorization code"}}))))

(defn logout-handler
  "Logout handler that destroys the session."
  [authenticator]
  (fn [request]
    (when-let [session-id (get-in request [:session :authn/session-id])]
      (authn.core/logout authenticator session-id))
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body {:success true}
     :session nil}))

(defn graphql-handler
  "GraphQL endpoint handler."
  [graphql-schema db-pool user-repo]
  (fn [request]
    (let [context {:request request
                   :db-pool db-pool
                   :user-repo user-repo}]
      (gql-ring/graphql-request graphql-schema context request))))

(defn not-found-handler
  "404 handler."
  [_request]
  {:status 404
   :headers {"Content-Type" "application/json"}
   :body {:error "Not found"}})

(defn routes
  "Application routes."
  [graphql-schema authenticator db-pool user-repo]
  (fn [request]
    (let [uri (:uri request)
          method (:request-method request)]
      (cond
        (and (= method :get) (= uri "/health"))
        (health-handler request)

        (and (= method :get) (= uri "/auth/github/callback"))
        ((login-handler authenticator user-repo) request)

        (and (= method :post) (= uri "/auth/logout"))
        ((logout-handler authenticator) request)

        (and (#{:get :post} method) (= uri "/graphql"))
        ((graphql-handler graphql-schema db-pool user-repo) request)

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

  Wraps the routes with authentication, session, JSON, and database middleware."
  [graphql-schema authenticator db-pool user-repo session-config]
  (-> (routes graphql-schema authenticator db-pool user-repo)
      (authn-mw/wrap-authentication authenticator)
      (wrap-session {:cookie-name (:cookie-name session-config)
                     :cookie-attrs {:http-only (:cookie-http-only? session-config)
                                    :secure (:cookie-secure? session-config)
                                    :same-site (:cookie-same-site session-config)}})
      wrap-json-response
      (wrap-json-body {:keywords? true})
      wrap-params
      (wrap-db-datasource db-pool)))
