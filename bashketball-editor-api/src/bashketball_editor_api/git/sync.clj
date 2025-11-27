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
  "Fetches and merges changes from remote repository.

  Takes a [[git-repo/GitRepo]] and GitHub token for authentication. Returns a
  map with `:status` (\"success\", \"conflict\", or \"error\") and `:message`.
  On conflict, includes `:conflicts` with affected paths."
  [git-repo github-token]
  (try
    (log/info "Pulling changes from remote...")
    (git-repo/fetch git-repo github-token)
    (git-repo/pull git-repo github-token)
    (log/info "Successfully pulled changes from remote")
    {:status "success"
     :message "Successfully pulled changes from remote"}

    (catch CheckoutConflictException e
      (log/warn "Merge conflicts detected during pull")
      {:status "conflict"
       :message "Merge conflicts detected"
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
  - `:is-clean` - true if no uncommitted changes and in sync with remote"
  [git-repo]
  (let [status       (git-repo/status git-repo)
        ahead-behind (git-repo/ahead-behind git-repo)
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
                    (zero? uncommitted))}))
