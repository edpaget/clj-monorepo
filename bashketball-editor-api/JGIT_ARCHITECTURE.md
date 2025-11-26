# JGit-Based Architecture for Card Storage

> **Status**: ✅ IMPLEMENTED (Phase 3 Complete)
>
> This document served as the design specification for the Git-backed storage system.
> The implementation is complete in `src/bashketball_editor_api/git/`. Key differences
> from the original design:
> - Manual sync (UI-initiated pull/push) instead of automatic background sync
> - Per-user GitHub token for push/pull credentials
> - User attribution in Git commits (name/email from authenticated user)

## Overview

Use JGit to manage a local clone of the card repository, with manual sync to remote (GitHub/GitLab/etc).

## Architecture

```
┌─────────────────────────────────────────────────────┐
│ Application Instance                                │
│                                                     │
│  ┌──────────────┐      ┌─────────────────────┐    │
│  │  GraphQL API │─────▶│  Card Repository    │    │
│  └──────────────┘      │  (JGit-backed)      │    │
│                        └──────────┬──────────┘    │
│                                   │               │
│  ┌──────────────┐      ┌──────────▼──────────┐    │
│  │  Background  │─────▶│  Local Git Repo     │    │
│  │  Sync Job    │      │  /data/cards-repo   │    │
│  └──────────────┘      └──────────┬──────────┘    │
│                                   │               │
└───────────────────────────────────┼───────────────┘
                                    │ push/pull
                                    ▼
                        ┌─────────────────────┐
                        │  Remote Git Server  │
                        │  (GitHub/GitLab)    │
                        └─────────────────────┘
```

## Component Design

### 1. Git Repository Component

**File**: `src/bashketball_editor_api/git/repo.clj`

```clojure
(ns bashketball-editor-api.git.repo
  "Git repository management with JGit."
  (:require
   [clj-jgit.porcelain :as git]
   [clojure.java.io :as io]))

(defrecord GitRepo [repo-path remote-url branch writer?]
  java.io.Closeable
  (close [_]
    ;; Clean up resources if needed
    nil))

(defn clone-or-open
  "Clones repository if it doesn't exist locally, otherwise opens it."
  [{:keys [repo-path remote-url branch] :as config}]
  (let [repo-dir (io/file repo-path)]
    (if (.exists repo-dir)
      (do
        (println "Opening existing repository at" repo-path)
        (git/load-repo repo-path))
      (do
        (println "Cloning repository from" remote-url "to" repo-path)
        (git/git-clone remote-url
                       :dir repo-path
                       :branch branch)))))

(defn read-file
  "Reads a file from the working tree."
  [repo relative-path]
  (let [repo-path (:repo-path repo)
        file-path (io/file repo-path relative-path)]
    (when (.exists file-path)
      (slurp file-path))))

(defn write-file
  "Writes a file to the working tree (does not commit)."
  [repo relative-path content]
  (let [repo-path (:repo-path repo)
        file-path (io/file repo-path relative-path)]
    (io/make-parents file-path)
    (spit file-path content)))

(defn delete-file
  "Deletes a file from the working tree (does not commit)."
  [repo relative-path]
  (let [repo-path (:repo-path repo)
        file-path (io/file repo-path relative-path)]
    (when (.exists file-path)
      (.delete file-path))))

(defn commit
  "Commits all changes in the working tree.

  Uses the authenticated user's name and email for commit author."
  [repo message author-name author-email]
  (when (:writer? repo)
    (let [git-repo (git/load-repo (:repo-path repo))]
      ;; Add all changes
      (git/git-add git-repo ".")
      ;; Commit
      (git/git-commit git-repo message
                      :name author-name
                      :email author-email))))

(defn fetch
  "Fetches changes from remote without merging.

  The github-token is optional for initial clone operations that use SSH keys."
  ([repo]
   (let [git-repo (git/load-repo (:repo-path repo))]
     (git/git-fetch git-repo)))
  ([repo github-token]
   (let [git-repo (git/load-repo (:repo-path repo))]
     (git/git-fetch git-repo
                    :credentials {:username "token"
                                  :password github-token}))))

(defn pull
  "Pulls changes from remote and merges.

  The github-token is optional for initial operations that use SSH keys."
  ([repo]
   (let [git-repo (git/load-repo (:repo-path repo))]
     (git/git-pull git-repo)))
  ([repo github-token]
   (let [git-repo (git/load-repo (:repo-path repo))]
     (git/git-pull git-repo
                   :credentials {:username "token"
                                 :password github-token}))))

(defn push
  "Pushes local commits to remote.

  Uses the authenticated user's GitHub token for credentials."
  [repo github-token]
  (when (:writer? repo)
    (let [git-repo (git/load-repo (:repo-path repo))]
      (git/git-push git-repo
                    :credentials {:username "token"
                                  :password github-token}))))

(defn status
  "Gets repository status."
  [repo]
  (let [git-repo (git/load-repo (:repo-path repo))]
    (git/git-status git-repo)))

(defn create-git-repo
  "Creates a GitRepo instance."
  [config]
  (map->GitRepo config))
```

### 2. Card Repository Implementation

**File**: `src/bashketball_editor_api/github/cards.clj`

```clojure
(ns bashketball-editor-api.github.cards
  "Card repository backed by Git (via JGit)."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [malli.core :as m]))

(def Card
  [:map
   [:id {:optional true} :uuid]
   [:set-id :uuid]
   [:name :string]
   [:description {:optional true} [:maybe :string]]
   [:attributes {:optional true} :map]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(defn- card-path [set-id card-id]
  (str "cards/" set-id "/" card-id ".edn"))

(defn- list-cards-in-set [git-repo set-id]
  (let [set-dir (io/file (:repo-path git-repo) "cards" (str set-id))
        card-files (when (.exists set-dir)
                     (.listFiles set-dir))]
    (when card-files
      (filterv #(.endsWith (.getName %) ".edn") card-files))))

(defrecord CardRepository [git-repo lock]
  proto/Repository
  (find-by [_this criteria]
    (when-let [id (:id criteria)]
      (when-let [set-id (:set-id criteria)]
        (when-let [content (git-repo/read-file git-repo (card-path set-id id))]
          (edn/read-string content)))))

  (find-all [_this opts]
    (if-let [set-id (get-in opts [:where :set-id])]
      (let [card-files (list-cards-in-set git-repo set-id)]
        (mapv (fn [file]
                (edn/read-string (slurp file)))
              card-files))
      []))

  (create! [this data]
    {:pre [(m/validate Card data)]}
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      ;; Extract user context from data (passed by GraphQL resolver)
      (let [user-ctx (:_user data)
            _ (when-not user-ctx
                (throw (ex-info "User context required for Git operations" {})))
            id (or (:id data) (java.util.UUID/randomUUID))
            set-id (:set-id data)
            now (java.time.Instant/now)
            ;; Remove user context before storing
            card (-> data
                     (dissoc :_user)
                     (assoc :id id
                            :created-at (or (:created-at data) now)
                            :updated-at now))
            path (card-path set-id id)]
        ;; Write to working tree
        (git-repo/write-file git-repo path (pr-str card))
        ;; Commit with user's credentials
        (git-repo/commit git-repo
                        (str "Create card: " (:name card) " [" id "]")
                        (:name user-ctx)
                        (:email user-ctx))
        ;; Push with user's GitHub token
        (git-repo/push git-repo (:github-token user-ctx))
        card)))

  (update! [this id data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (:_user data)
            _ (when-not user-ctx
                (throw (ex-info "User context required for Git operations" {})))]
        (when-let [set-id (:set-id data)]
          (when-let [existing (proto/find-by this {:id id :set-id set-id})]
            (let [updated (-> existing
                              (merge (dissoc data :_user))
                              (assoc :updated-at (java.time.Instant/now)))
                  path (card-path set-id id)]
              (git-repo/write-file git-repo path (pr-str updated))
              (git-repo/commit git-repo
                              (str "Update card: " (:name updated) " [" id "]")
                              (:name user-ctx)
                              (:email user-ctx))
              (git-repo/push git-repo (:github-token user-ctx))
              updated))))))

  (delete! [this id]
    (locking lock
      ;; This is tricky - need to search for the card across all sets
      ;; or require set-id in the delete operation
      ;; For now, throw error requiring set-id
      (throw (ex-info "Delete requires set-id in criteria" {:id id})))))

(defn create-card-repository
  "Creates a Git-backed card repository."
  [git-repo]
  (->CardRepository git-repo (Object.)))
```

### 3. Background Sync Job

**File**: `src/bashketball_editor_api/git/sync.clj`

```clojure
(ns bashketball-editor-api.git.sync
  "Background job for syncing Git repository with remote."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [clojure.tools.logging :as log]))

(defn sync-repository
  "Fetches from remote, merges changes, and pushes local commits.

  Returns:
  - :success if sync completed
  - :conflicts if merge conflicts detected
  - :error with exception if sync failed"
  [git-repo]
  (try
    (log/info "Starting repository sync...")

    ;; Fetch remote changes
    (log/debug "Fetching from remote...")
    (git-repo/fetch git-repo)

    ;; Check status
    (let [status (git-repo/status git-repo)]
      (when (seq (:uncommitted-changes status))
        (log/warn "Uncommitted changes detected before pull")))

    ;; Pull and merge
    (log/debug "Pulling changes...")
    (git-repo/pull git-repo)

    ;; Push local commits
    (log/debug "Pushing local commits...")
    (git-repo/push git-repo)

    (log/info "Repository sync completed successfully")
    {:status :success}

    (catch org.eclipse.jgit.api.errors.CheckoutConflictException e
      (log/error e "Merge conflicts detected during sync")
      {:status :conflicts
       :error (ex-message e)})

    (catch Exception e
      (log/error e "Error during repository sync")
      {:status :error
       :error (ex-message e)})))

(defn start-sync-job
  "Starts a background thread that syncs repository periodically.

  Returns a future that can be cancelled."
  [git-repo interval-ms]
  (let [running (atom true)]
    {:future (future
               (while @running
                 (try
                   (Thread/sleep interval-ms)
                   (sync-repository git-repo)
                   (catch InterruptedException e
                     (log/info "Sync job interrupted")
                     (reset! running false))
                   (catch Exception e
                     (log/error e "Unexpected error in sync job")))))
     :stop-fn #(reset! running false)}))

(defn stop-sync-job
  "Stops a running sync job."
  [{:keys [future stop-fn]}]
  (stop-fn)
  (future-cancel future))
```

### 4. Integrant Configuration

**File**: `src/bashketball_editor_api/system.clj`

```clojure
(defmethod ig/init-key ::git-repo [_ {:keys [config]}]
  (let [git-config {:repo-path (get-in config [:git :repo-path])
                    :remote-url (get-in config [:git :remote-url])
                    :branch (get-in config [:git :branch])
                    :writer? (get-in config [:git :writer?])}
        repo (git-repo/create-git-repo git-config)]
    ;; Clone or open repository (uses SSH keys or system git config)
    (git-repo/clone-or-open git-config)
    (log/info "Git repository initialized at" (:repo-path git-config))
    repo))

(defmethod ig/halt-key! ::git-repo [_ repo]
  (.close repo))

(defmethod ig/init-key ::card-repo [_ {:keys [git-repo]}]
  (cards/create-card-repository git-repo))
```

### 5. Configuration

**File**: `resources/config.edn`

```clojure
{:git
 {:repo-path #or [#env GIT_REPO_PATH "/data/bashketball-cards"]
  :remote-url #env GIT_REMOTE_URL
  :branch #or [#env GIT_BRANCH "main"]
  :writer? #profile {:dev true
                     :prod #or [#env GIT_WRITER "false"]}}}
```

**Note**: Author name and email are no longer in configuration. Each Git commit is authored by the authenticated user who made the change, using their name/email from their user record. Push/pull operations use the authenticated user's GitHub OAuth token, providing proper attribution and credentials per operation.

## Handling Multi-Instance Deployments

### Option 1: Single Writer Pattern (Recommended)

```clojure
;; Only one instance handles Git writes
;; Other instances read from shared storage (S3, NFS) or database cache

(defmethod ig/init-key ::git-sync-job [_ {:keys [git-repo config]}]
  (if (= "true" (System/getenv "GIT_WRITER_INSTANCE"))
    (git-sync/start-sync-job git-repo interval-ms)
    (do
      (log/info "Not a Git writer instance, skipping sync job")
      nil)))
```

### Option 2: Distributed Lock

```clojure
(ns bashketball-editor-api.git.lock
  (:require
   [db.core :as db]))

(defn acquire-lock!
  "Acquires distributed lock for Git operations."
  [lock-name timeout-ms]
  (db/execute-one!
   {:insert-into :git_locks
    :values [{:lock-name lock-name
              :acquired-at (java.time.Instant/now)
              :expires-at (+ (System/currentTimeMillis) timeout-ms)
              :instance-id (System/getenv "INSTANCE_ID")}]
    :on-conflict :lock-name
    :do-nothing true
    :returning [:*]}))

(defn release-lock!
  "Releases distributed lock."
  [lock-name]
  (db/execute!
   {:delete-from :git_locks
    :where [:= :lock-name lock-name]}))

(defmacro with-distributed-lock
  "Executes body with distributed lock."
  [lock-name timeout-ms & body]
  `(when (acquire-lock! ~lock-name ~timeout-ms)
     (try
       ~@body
       (finally
         (release-lock! ~lock-name)))))
```

### Option 3: Read-Only Replicas

```clojure
;; Most instances are read-only
;; Mutations go through a dedicated writer service

(defn read-only-repo?
  "Check if this instance should only read."
  []
  (not= "true" (System/getenv "GIT_WRITER")))

(defrecord CardRepository [git-repo lock read-only?]
  proto/Repository
  (create! [this data]
    (if read-only?
      (throw (ex-info "Repository is read-only" {}))
      ;; ... normal create logic
      )))
```

## Conflict Resolution Strategies

### Strategy 1: Last-Write-Wins with Timestamp

```clojure
(defn merge-cards
  "Merges two card versions, preferring newer."
  [local-card remote-card]
  (if (> (.toEpochMilli (:updated-at local-card))
         (.toEpochMilli (:updated-at remote-card)))
    local-card
    remote-card))
```

### Strategy 2: Automatic Merge with Custom Logic

```clojure
(defn merge-cards
  "Intelligently merges card fields."
  [local-card remote-card]
  {:id (:id local-card)
   :set-id (:set-id local-card)
   :name (if (newer? local-card remote-card)
           (:name local-card)
           (:name remote-card))
   :attributes (merge (:attributes remote-card)
                      (:attributes local-card))
   :updated-at (max (:updated-at local-card)
                    (:updated-at remote-card))})
```

### Strategy 3: Conflict Detection with User Resolution

```clojure
(defn detect-conflict
  "Returns conflict data if merge cannot be automatic."
  [local-card remote-card]
  (when (and (not= local-card remote-card)
             (= (:updated-at local-card)
                (:updated-at remote-card)))
    {:local local-card
     :remote remote-card
     :fields-changed (different-fields local-card remote-card)}))
```

## Benefits Summary

1. ✅ **Performance**: 10-100x faster than API
2. ✅ **No rate limits**: Unlimited operations
3. ✅ **Atomic commits**: Multiple changes in one commit
4. ✅ **Full Git features**: Branches, history, diffs
5. ✅ **Offline capable**: Queue sync for later
6. ✅ **Platform independent**: Works with any Git server
7. ✅ **Bulk operations**: Import/export hundreds of cards easily

## Challenges Summary

1. ⚠️ **Local state**: Need disk space and lifecycle management
2. ⚠️ **Multi-instance**: Requires coordination strategy
3. ⚠️ **Deployment**: More complex setup
4. ⚠️ **Sync complexity**: Background jobs, conflict handling
5. ⚠️ **Authentication**: Git credential management

## Recommendation

**Use JGit** - The benefits significantly outweigh the challenges for this use case:

- Trading card editors involve **bulk operations** (import sets, batch updates)
- Users want **rich history** (who changed what, when)
- **Performance matters** for good UX (instant card loading)
- Can use **single writer pattern** to avoid multi-instance complexity
- Git naturally supports **branching workflows** (drafts, reviews)

The main complexity (multi-instance coordination) can be solved with a simple "single writer" pattern where most instances are read-only and mutations go through one designated instance or a queue.
