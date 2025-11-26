(ns bashketball-editor-api.graphql.resolvers.mutation
  "GraphQL Mutation resolvers.

  Contains mutations for Git sync operations and card/set management."
  (:require
   [bashketball-editor-api.git.sync :as git-sync]
   [bashketball-editor-api.models.protocol :as repo]
   [bashketball-editor-api.graphql.middleware :as middleware]
   [graphql-server.core :as gql]))

(def SyncResult
  "Malli schema for sync operation result."
  [:map {:graphql/type :SyncResult}
   [:status :string]
   [:message :string]
   [:error {:optional true} [:maybe :string]]
   [:conflicts {:optional true} [:maybe [:vector :string]]]])

(defn- get-current-user
  "Gets the current authenticated user from context."
  [ctx]
  (let [user-id (get-in ctx [:request :authn/user-id])]
    (repo/find-by (:user-repo ctx) {:id (parse-uuid user-id)})))

(gql/defresolver :Mutation :pullFromRemote
  "Pulls changes from remote Git repository.

  Fetches and merges changes from the remote. Returns status with any conflicts."
  [:=> [:cat :any :any :any] SyncResult]
  [ctx _args _value]
  (let [user (get-current-user ctx)]
    (git-sync/pull-from-remote (:git-repo ctx) (:github-token user))))

(gql/defresolver :Mutation :pushToRemote
  "Pushes local changes to remote Git repository.

  Pushes all committed changes to the remote. Fails if repository is read-only."
  [:=> [:cat :any :any :any] SyncResult]
  [ctx _args _value]
  (let [user (get-current-user ctx)]
    (git-sync/push-to-remote (:git-repo ctx) (:github-token user))))

(gql/def-resolver-map
  "Resolver map containing all Mutation resolvers."
  [middleware/require-authentication])
