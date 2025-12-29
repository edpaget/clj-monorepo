(ns bashketball-game-api.handler
  "HTTP handler and Ring middleware stack.

  Provides the main Ring handler with authentication, CORS, JSON processing,
  and OIDC middleware configured."
  (:require
   [authn.middleware :as authn-mw]
   [bashketball-game-api.auth.google :as google-auth]
   [bashketball-game-api.handlers.avatar :as avatar-handler]
   [cheshire.core :as json]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [db.core :as db]
   [graphql-server.ring :as gql-ring]
   [oidc.ring :as oidc-ring]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.session :refer [wrap-session]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.util.response :as response]))

(defn- json-response
  "Creates a JSON response with the given data and status."
  [data status]
  (-> (response/response (json/generate-string data))
      (response/status status)
      (response/content-type "application/json")))

(defn health-handler
  "Health check endpoint handler."
  [_request]
  (json-response {:status "ok"} 200))

(defn- parse-path-params
  "Extracts path parameters from a URI pattern match."
  [uri pattern]
  (let [pattern-parts (clojure.string/split pattern #"/")
        uri-parts     (clojure.string/split uri #"/")]
    (when (= (count pattern-parts) (count uri-parts))
      (reduce (fn [acc [p u]]
                (if (clojure.string/starts-with? p ":")
                  (assoc acc (keyword (subs p 1)) u)
                  (if (= p u) acc (reduced nil))))
              {}
              (map vector pattern-parts uri-parts)))))

(defn routes
  "Main application routes."
  [_opts]
  (fn [request]
    (let [uri (:uri request)]
      (cond
        (= uri "/health")
        (health-handler request)

        (clojure.string/starts-with? uri "/api/avatars/")
        (let [path-params (parse-path-params uri "/api/avatars/:user-id")]
          (avatar-handler/avatar-handler (assoc request :path-params path-params)))

        :else
        (json-response {:error "Not found"} 404)))))

(defn wrap-json-body
  "Middleware that parses JSON request bodies."
  [handler]
  (fn [request]
    (if-let [body (:body request)]
      (let [body-str (slurp body)
            parsed   (when (and (not (str/blank? body-str))
                                (str/includes?
                                 (get-in request [:headers "content-type"] "")
                                 "application/json"))
                       (json/parse-string body-str true))]
        (handler (assoc request :body parsed)))
      (handler request))))

(defn wrap-json-response
  "Middleware that converts map responses to JSON."
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (and (map? (:body response))
               (not (get-in response [:headers "Content-Type"])))
        (-> response
            (update :body json/generate-string)
            (response/content-type "application/json"))
        response))))

(defn wrap-cors
  "CORS middleware with configurable allowed origins."
  [handler allowed-origins]
  (fn [request]
    (let [origin       (get-in request [:headers "origin"])
          allowed?     (or (nil? allowed-origins)
                           (some #{origin} allowed-origins))
          cors-headers (when (and origin allowed?)
                         {"Access-Control-Allow-Origin" origin
                          "Access-Control-Allow-Methods" "GET, POST, PUT, DELETE, OPTIONS"
                          "Access-Control-Allow-Headers" "Content-Type, Authorization"
                          "Access-Control-Allow-Credentials" "true"})]
      (if (= :options (:request-method request))
        ;; Preflight request
        {:status 204
         :headers (merge cors-headers
                         {"Access-Control-Max-Age" "86400"})}
        ;; Regular request
        (let [response (handler request)]
          (if cors-headers
            (update response :headers merge cors-headers)
            response))))))

(defn wrap-db-datasource
  "Middleware that binds the database datasource for the request."
  [handler db-pool]
  (fn [request]
    (binding [db/*datasource* db-pool]
      (handler request))))

(defn wrap-resolver-map
  "Middleware that attaches the resolver-map to the request for GraphQL context."
  [handler resolver-map]
  (fn [request]
    (handler (assoc request :resolver-map resolver-map))))

(defn create-handler
  "Creates the main Ring handler with all middleware applied.

  Takes a config map with:
  - `:google-oidc-client` - OIDC client for Google auth
  - `:authenticator` - Authn authenticator instance
  - `:user-repo` - User repository
  - `:avatar-service` - Avatar service for cached avatar images
  - `:db-pool` - Database connection pool
  - `:resolver-map` - GraphQL resolver map with :resolvers and :card-catalog
  - `:subscription-manager` - Subscription manager for real-time updates
  - `:config` - Application configuration"
  [{:keys [google-oidc-client authenticator user-repo avatar-service db-pool resolver-map
           subscription-manager config]}]
  (let [session-config  (:session config)
        google-config   (:google config)
        cors-config     (:cors config)
        allowed-origins (:allowed-origins cors-config)
        cookie-secret   (.getBytes ^String (:cookie-secret session-config))]
    (-> (routes {})
        ;; GraphQL middleware (before auth so we can check auth in resolvers)
        (gql-ring/graphql-middleware
         {:path "/graphql"
          :resolver-map (:resolvers resolver-map)
          :context-fn (fn [req]
                        {:request req
                         :subscription-manager subscription-manager})
          :enable-graphiql? true
          :enable-subscriptions? true
          :subscription-path "/graphql/subscriptions"
          ;; HexPosition tuples are serialized as EDN strings.
          ;; Client parses "[0 1]" back to [0 1] via edn/read-string.
          ;; Note: graphql-server converts tuples to Java int arrays, so we
          ;; must convert back to vector before pr-str.
          ;; PolicyExpr is an opaque scalar for polix policy expressions.
          :scalars {:HexPosition {:parse     edn/read-string
                                  :serialize (fn [v]
                                               (pr-str (if (.isArray (class v))
                                                         (vec v)
                                                         v)))}
                    :PolicyExpr  {:parse     edn/read-string
                                  :serialize pr-str}}})
        ;; Authentication middleware
        (authn-mw/wrap-session-refresh authenticator)
        (authn-mw/wrap-authentication authenticator)
        ;; OIDC middleware for Google OAuth
        (oidc-ring/oidc-middleware
         {:client google-oidc-client
          :login-path "/auth/google/login"
          :callback-path "/auth/google/callback"
          :logout-path "/auth/logout"
          :callback-opts
          {:success-fn (google-auth/create-success-handler
                        {:user-repo user-repo
                         :avatar-service avatar-service
                         :authenticator authenticator
                         :success-redirect-uri (:success-redirect-uri google-config)
                         :db-pool db-pool})
           :error-fn (google-auth/create-error-handler
                      {:success-redirect-uri (:success-redirect-uri google-config)})}})
        ;; Session middleware
        (wrap-session {:store (cookie-store {:key cookie-secret})
                       :cookie-name (:cookie-name session-config)
                       :cookie-attrs {:http-only (:cookie-http-only? session-config)
                                      :secure (:cookie-secure? session-config)
                                      :same-site (:cookie-same-site session-config)
                                      :max-age (quot (:ttl-ms session-config) 1000)}})
        wrap-cookies
        ;; JSON handling
        wrap-json-response
        wrap-json-body
        wrap-params
        ;; Resolver map for GraphQL context
        (wrap-resolver-map resolver-map)
        ;; Avatar service
        (avatar-handler/wrap-avatar-service avatar-service)
        ;; CORS
        (wrap-cors allowed-origins)
        ;; Database binding
        (wrap-db-datasource db-pool))))
