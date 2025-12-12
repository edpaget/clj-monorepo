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
  "Returns the currently authenticated user, or null if not authenticated.

  The `avatarUrl` field returns a local cached avatar endpoint instead of the
  original Google picture URL, to avoid rate limits on Google's avatar service."
  [:=> [:cat :any :any :any] [:maybe User]]
  [ctx _args _value]
  (let [request (:request ctx)]
    (when (:authn/authenticated? request)
      (let [user-id (:authn/user-id request)]
        {:id (parse-uuid user-id)
         :email (get-in request [:authn/claims :email])
         :name (get-in request [:authn/claims :name])
         :avatarUrl (str "/api/avatars/" user-id)}))))

(def-resolver-map)
