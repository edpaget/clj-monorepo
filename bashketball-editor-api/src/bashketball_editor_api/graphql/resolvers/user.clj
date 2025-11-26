(ns bashketball-editor-api.graphql.resolvers.user
  "GraphQL User resolvers.

  Provides resolvers for user queries that require authentication.
  All resolvers in this namespace are wrapped with [[bashketball-editor-api.graphql.middleware/require-authentication]]."
  (:require
   [bashketball-editor-api.graphql.middleware :as middleware]
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

(gql/defresolver :Query :user
  "Fetches a user by ID or GitHub login. Requires authentication."
  [:=> [:cat :any [:map [:id {:optional true} :string]
                   [:github-login {:optional true} :string]] :any]
   [:maybe User]]
  [ctx args _value]
  (let [criteria (cond
                   (:id args) {:id (parse-uuid (:id args))}
                   (:github-login args) {:github-login (:github-login args)}
                   :else nil)]
    (when criteria
      (when-let [user (repo/find-by (:user-repo ctx) criteria)]
        (transform-user user)))))

(gql/defresolver :Query :users
  "Lists all users with optional filtering. Requires authentication."
  [:=> [:cat :any [:map {:optional true} [:limit {:optional true} :int]] :any]
   [:vector User]]
  [ctx args _value]
  (let [users (repo/find-all (:user-repo ctx) args)]
    (mapv transform-user users)))

(gql/def-resolver-map
  "Resolver map containing all User Query resolvers with authentication middleware."
  [middleware/require-authentication])
