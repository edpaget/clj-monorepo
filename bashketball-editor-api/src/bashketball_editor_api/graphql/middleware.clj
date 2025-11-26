(ns bashketball-editor-api.graphql.middleware
  "GraphQL middleware for cross-cutting concerns.

  Provides middleware for authentication, error handling, and logging."
  (:require
   [com.walmartlabs.lacinia.resolve :as resolve]))

(defn wrap-db-context
  "Middleware that adds database datasource to the GraphQL context."
  [resolver db-pool]
  (fn [ctx args value]
    (let [ctx* (assoc ctx :db-pool db-pool)]
      (resolver ctx* args value))))

(defn wrap-repositories
  "Middleware that adds repository instances to the GraphQL context."
  [resolver repos]
  (fn [ctx args value]
    (let [ctx* (merge ctx repos)]
      (resolver ctx* args value))))

(defn require-authentication
  "Middleware that requires authentication to access a resolver.

  Returns a Lacinia error if the request is not authenticated."
  [resolver]
  (fn [ctx args value]
    (if (get-in ctx [:request :authn/authenticated?])
      (resolver ctx args value)
      (resolve/resolve-as nil {:message "Authentication required"}))))
