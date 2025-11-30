(ns bashketball-game-api.graphql.resolvers.user
  "GraphQL resolvers for user queries.

  Provides Query resolvers for authenticated user information."
  (:require
   [graphql-server.core :refer [defresolver def-resolver-map]]))

(def User
  "GraphQL schema for User type."
  [:map {:graphql/type :User}
   [:id :uuid]
   [:email :string]
   [:name {:optional true} [:maybe :string]]
   [:avatarUrl {:optional true} [:maybe :string]]])

(defresolver :Query :me
  "Returns the currently authenticated user, or null if not authenticated."
  [:=> [:cat :any :any :any] [:maybe User]]
  [ctx _args _value]
  (let [request (:request ctx)]
    (when (:authn/authenticated? request)
      {:id (parse-uuid (:authn/user-id request))
       :email (get-in request [:authn/claims :email])
       :name (get-in request [:authn/claims :name])
       :avatarUrl (get-in request [:authn/claims :picture])})))

(def-resolver-map)
