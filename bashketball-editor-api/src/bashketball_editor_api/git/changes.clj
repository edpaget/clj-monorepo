(ns bashketball-editor-api.git.changes
  "Working tree changes repository backed by Git via JGit.

  Provides operations for viewing and discarding uncommitted changes through
  the standard [[bashketball-editor-api.models.protocol/Repository]] protocol.

  Operations:
  - `find-all` - Returns working tree status (added, modified, deleted, untracked)
  - `delete!` - Discards all uncommitted changes (hard reset + clean)
  - Other operations throw NotImplementedError"
  (:require
   [bashketball-editor-api.models.protocol :as proto]
   [clj-jgit.porcelain :as git]
   [clojure.tools.logging :as log])
  (:import
   [java.io File]
   [org.eclipse.jgit.api Git ResetCommand$ResetType]))

(defn- get-working-tree-status
  "Returns the working tree status from JGit."
  [^Git jgit]
  (let [status (git/git-status jgit)]
    {:added     (vec (:added status))
     :modified  (vec (concat (:changed status) (:modified status)))
     :deleted   (vec (:removed status))
     :untracked (vec (:untracked status))
     :is-dirty  (or (seq (:added status))
                    (seq (:changed status))
                    (seq (:modified status))
                    (seq (:removed status))
                    (seq (:untracked status)))}))

(defrecord ChangesRepository [git-repo]
  proto/Repository

  (find-by [_this _criteria]
    (throw (ex-info "Not implemented" {:operation :find-by})))

  (find-all [_this _opts]
    (let [git-dir (File. (:repo-path git-repo) ".git")]
      (if (.exists git-dir)
        (let [^Git jgit (git/load-repo (:repo-path git-repo))]
          (get-working-tree-status jgit))
        {:added     []
         :modified  []
         :deleted   []
         :untracked []
         :is-dirty  false})))

  (create! [_this _data]
    (throw (ex-info "Not implemented" {:operation :create!})))

  (update! [_this _id _data]
    (throw (ex-info "Not implemented" {:operation :update!})))

  (delete! [_this _id]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (let [git-dir (File. (:repo-path git-repo) ".git")]
      (if (.exists git-dir)
        (try
          (let [^Git jgit (git/load-repo (:repo-path git-repo))]
            ;; Hard reset to discard staged and modified tracked files
            (-> (.reset jgit)
                (.setMode ResetCommand$ResetType/HARD)
                (.call))
            ;; Clean to remove untracked files
            (-> (.clean jgit)
                (.setCleanDirectories true)
                (.setForce true)
                (.call))
            {:status "success"
             :message "All uncommitted changes have been discarded"})
          (catch Exception e
            (log/error e "Failed to discard changes")
            {:status "error"
             :message (str "Failed to discard changes: " (.getMessage e))}))
        {:status "error"
         :message "Not a git repository"}))))

(defn create-changes-repository
  "Creates a Git-backed changes repository.

  Takes a [[git-repo/GitRepo]] instance."
  [git-repo]
  (->ChangesRepository git-repo))
