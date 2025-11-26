(ns bashketball-editor-api.graphql.resolvers.query
  "GraphQL Query resolvers.

  Contains general query resolvers. User-specific resolvers that require
  authentication are in [[bashketball-editor-api.graphql.resolvers.user]]."
  (:require
   [bashketball-editor-api.models.protocol :as repo]
   [graphql-server.core :as gql]))

(def User
  "Malli schema for GraphQL User type."
  [:map {:graphql/type :User}
   [:id :string]
   [:github-login :string]
   [:email [:maybe :string]]
   [:avatar-url [:maybe :string]]
   [:name [:maybe :string]]])

(defn- transform-user
  "Transforms a user entity to GraphQL User type.

  Converts UUID :id to string for GraphQL compatibility."
  [user]
  {:id (str (:id user))
   :github-login (:github-login user)
   :email (:email user)
   :avatar-url (:avatar-url user)
   :name (:name user)})

(gql/defresolver :Query :me
  "Returns the currently authenticated user."
  [:=> [:cat :any :any :any] [:maybe User]]
  [ctx _args _value]
  (when-let [user-id (get-in ctx [:request :authn/user-id])]
    (when-let [user (repo/find-by (:user-repo ctx) {:id (parse-uuid user-id)})]
      (transform-user user))))

(gql/def-resolver-map
  "Resolver map containing all Query resolvers.")
