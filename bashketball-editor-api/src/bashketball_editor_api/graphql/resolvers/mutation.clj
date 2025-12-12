(ns bashketball-editor-api.graphql.resolvers.mutation
  "GraphQL Mutation resolvers.

  Contains mutations for Git sync operations and card/set management."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.git.sync :as git-sync]
   [bashketball-editor-api.graphql.middleware :as middleware]
   [bashketball-editor-api.models.protocol :as repo]
   [graphql-server.core :as gql]))

(def SyncResult
  "Malli schema for sync operation result."
  [:map {:graphql/type :SyncResult}
   [:status :string]
   [:message :string]
   [:error {:optional true} [:maybe :string]]
   [:conflicts {:optional true} [:maybe [:vector :string]]]])

(def CommitResult
  "Malli schema for commit operation result."
  [:map {:graphql/type :CommitResult}
   [:success :boolean]
   [:message :string]
   [:commit-id {:optional true} [:maybe :string]]])

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

(gql/defresolver :Mutation :commitChanges
  "Commits all staged changes in the working tree.

  Takes an optional commit message. If not provided, uses a default message.
  Returns the commit result with success status and commit ID."
  [:=> [:cat :any [:map {:optional true} [:message {:optional true} :string]] :any]
   CommitResult]
  [ctx args _value]
  (let [user    (get-current-user ctx)
        message (or (:message args) "Update cards and sets")
        repo    (:git-repo ctx)]
    (if-not (:writer? repo)
      {:success false
       :message "Repository is read-only"}
      (try
        (let [result (git-repo/commit repo message (:name user) (:email user))]
          {:success true
           :message (str "Changes committed: " message)
           :commit-id (when result (.getName result))})
        (catch Exception e
          {:success false
           :message (str "Commit failed: " (.getMessage e))})))))

(def BranchResult
  "Malli schema for branch operation result."
  [:map {:graphql/type :BranchResult}
   [:status :string]
   [:message :string]
   [:branch {:optional true} [:maybe :string]]])

(gql/defresolver :Mutation :switchBranch
  "Switches to an existing branch.

  Fails if there are uncommitted changes in the working tree."
  [:=> [:cat :any [:map [:branch :string]] :any] BranchResult]
  [ctx {:keys [branch]} _value]
  (let [changes-repo (:changes-repo ctx)
        changes      (repo/find-all changes-repo {})]
    (if (:is-dirty changes)
      {:status "error" :message "Commit or discard changes first"}
      (repo/update! (:branch-repo ctx) {:name branch} {}))))

(gql/defresolver :Mutation :createBranch
  "Creates a new branch and switches to it.

  Fails if there are uncommitted changes in the working tree."
  [:=> [:cat :any [:map [:branch :string]] :any] BranchResult]
  [ctx {:keys [branch]} _value]
  (let [changes-repo (:changes-repo ctx)
        changes      (repo/find-all changes-repo {})]
    (if (:is-dirty changes)
      {:status "error" :message "Commit or discard changes first"}
      (repo/create! (:branch-repo ctx) {:name branch}))))

(gql/defresolver :Mutation :discardChanges
  "Discards all uncommitted changes in the working tree.

  Performs a hard reset and removes untracked files."
  [:=> [:cat :any :any :any] SyncResult]
  [ctx _args _value]
  (repo/delete! (:changes-repo ctx) :all))

(gql/def-resolver-map
  "Resolver map containing all Mutation resolvers."
  [middleware/require-authentication])
