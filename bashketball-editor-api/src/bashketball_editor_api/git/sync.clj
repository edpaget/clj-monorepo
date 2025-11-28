(ns bashketball-editor-api.git.sync
  "Git synchronization operations.

  Provides manual sync operations for pulling from and pushing to the remote
  repository. Returns structured results with status and error information."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [clojure.tools.logging :as log])
  (:import
   [org.eclipse.jgit.api.errors CheckoutConflictException]))

(defn pull-from-remote
  "Fetches and rebases local commits on top of remote changes.

  Takes a [[git-repo/GitRepo]] and GitHub token for authentication. Returns a
  map with `:status` (\"success\", \"conflict\", or \"error\") and `:message`.
  On conflict, includes `:conflicts` with affected paths."
  [git-repo github-token]
  (try
    (log/info "Pulling changes from remote (with rebase)...")
    (git-repo/fetch git-repo github-token)
    (git-repo/pull git-repo github-token)
    (log/info "Successfully pulled and rebased changes from remote")
    {:status "success"
     :message "Successfully pulled changes from remote"}

    (catch CheckoutConflictException e
      (log/warn "Rebase conflicts detected during pull")
      {:status "conflict"
       :message "Rebase conflicts detected"
       :conflicts (vec (.getConflictingPaths e))
       :error (ex-message e)})

    (catch Exception e
      (log/error e "Failed to pull from remote")
      {:status "error"
       :message "Failed to pull from remote"
       :error (ex-message e)})))

(defn push-to-remote
  "Pushes local commits to remote repository.

  Takes a [[git-repo/GitRepo]] and GitHub token for authentication. Returns a
  map with `:status` (\"success\" or \"error\") and `:message`. Fails if the
  repository is configured as read-only."
  [git-repo github-token]
  (if-not (:writer? git-repo)
    {:status "error"
     :message "Repository is read-only"}
    (try
      (log/info "Pushing changes to remote...")
      (git-repo/push git-repo github-token)
      (log/info "Successfully pushed changes to remote")
      {:status "success"
       :message "Successfully pushed changes to remote"}

      (catch Exception e
        (log/error e "Failed to push to remote")
        {:status "error"
         :message "Failed to push to remote"
         :error (ex-message e)}))))

(defn get-sync-status
  "Returns current Git sync status.

  Takes a [[git-repo/GitRepo]] and returns a map with:
  - `:ahead` - number of local commits not pushed
  - `:behind` - number of remote commits not pulled
  - `:uncommitted-changes` - count of uncommitted changes
  - `:is-clean` - true if no uncommitted changes and in sync with remote
  - `:error` - present if the directory is not a git repository"
  [git-repo]
  (let [status (git-repo/status git-repo)]
    (if (:error status)
      {:ahead 0
       :behind 0
       :uncommitted-changes 0
       :is-clean false
       :error (:error status)}
      (let [ahead-behind (git-repo/ahead-behind git-repo)
            uncommitted  (+ (count (:added status))
                            (count (:changed status))
                            (count (:removed status))
                            (count (:modified status))
                            (count (:untracked status)))]
        {:ahead (or (:ahead ahead-behind) 0)
         :behind (or (:behind ahead-behind) 0)
         :uncommitted-changes uncommitted
         :is-clean (and (zero? (or (:ahead ahead-behind) 0))
                        (zero? (or (:behind ahead-behind) 0))
                        (zero? uncommitted))}))))

(defn get-working-tree-status
  "Returns detailed working tree status.

  Takes a [[git-repo/GitRepo]] and returns a map with:
  - `:is-dirty` - true if there are any uncommitted changes, or if not a git repo
  - `:added` - vector of newly added files (staged)
  - `:modified` - vector of modified files (staged or unstaged)
  - `:deleted` - vector of deleted files
  - `:untracked` - vector of untracked files
  - `:error` - present if the directory is not a git repository"
  [git-repo]
  (let [status (git-repo/status git-repo)]
    (if (:error status)
      {:is-dirty true
       :added []
       :modified []
       :deleted []
       :untracked []
       :error (:error status)}
      (let [added     (vec (concat (:added status) []))
            modified  (vec (concat (:modified status) (:changed status)))
            deleted   (vec (:removed status))
            untracked (vec (:untracked status))
            is-dirty  (or (seq added) (seq modified) (seq deleted) (seq untracked))]
        {:is-dirty (boolean is-dirty)
         :added added
         :modified modified
         :deleted deleted
         :untracked untracked}))))
