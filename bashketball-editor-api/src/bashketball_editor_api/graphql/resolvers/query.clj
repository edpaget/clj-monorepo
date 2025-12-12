(ns bashketball-editor-api.graphql.resolvers.query
  "GraphQL Query resolvers.

  Contains general query resolvers. User-specific resolvers that require
  authentication are in [[bashketball-editor-api.graphql.resolvers.user]]."
  (:require
   [bashketball-editor-api.git.sync :as git-sync]
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

(def SyncStatus
  "Malli schema for Git sync status."
  [:map {:graphql/type :SyncStatus}
   [:ahead :int]
   [:behind :int]
   [:uncommitted-changes :int]
   [:is-clean :boolean]])

(gql/defresolver :Query :syncStatus
  "Returns current Git repository sync status.

  Shows commits ahead/behind remote and uncommitted changes count."
  [:=> [:cat :any :any :any] SyncStatus]
  [ctx _args _value]
  (git-sync/get-sync-status (:git-repo ctx)))

(def WorkingTreeStatus
  "Malli schema for working tree status with file details."
  [:map {:graphql/type :WorkingTreeStatus}
   [:is-dirty :boolean]
   [:added [:vector :string]]
   [:modified [:vector :string]]
   [:deleted [:vector :string]]
   [:untracked [:vector :string]]])

(gql/defresolver :Query :workingTreeStatus
  "Returns detailed working tree status.

  Shows whether there are uncommitted changes and lists affected files by category:
  added (new files), modified, deleted, and untracked files."
  [:=> [:cat :any :any :any] WorkingTreeStatus]
  [ctx _args _value]
  (git-sync/get-working-tree-status (:git-repo ctx)))

(def BranchInfo
  "Malli schema for branch information."
  [:map {:graphql/type :BranchInfo}
   [:current-branch [:maybe :string]]
   [:branches [:vector :string]]])

(gql/defresolver :Query :branchInfo
  "Returns current branch and list of all local branches."
  [:=> [:cat :any :any :any] BranchInfo]
  [ctx _args _value]
  (let [branch-repo (:branch-repo ctx)
        branches    (repo/find-all branch-repo {})
        current     (first (filter :current branches))]
    {:current-branch (:name current)
     :branches (mapv :name branches)}))

(gql/def-resolver-map
  "Resolver map containing all Query resolvers.")
