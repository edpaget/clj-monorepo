(ns bashketball-editor-api.graphql.resolvers.query
  "GraphQL Query resolvers."
  (:require
   [bashketball-editor-api.models.protocol :as repo]
   [graphql-server.core :as gql]))

(def User
  "Malli schema for GraphQL User type."
  [:map
   [:id :string]
   [:github-login :string]
   [:email [:maybe :string]]
   [:avatar-url [:maybe :string]]
   [:name [:maybe :string]]])

(gql/defresolver :Query :me
  "Returns the currently authenticated user."
  [:=> [:cat :any :any :any] [:maybe User]]
  [ctx _args _value]
  (when-let [user-id (get-in ctx [:request :authn/user-id])]
    (when-let [user (repo/find-by (:user-repo ctx) {:id (parse-uuid user-id)})]
      {:id (str (:id user))
       :github-login (:github-login user)
       :email (:email user)
       :avatar-url (:avatar-url user)
       :name (:name user)})))

(gql/def-resolver-map)
