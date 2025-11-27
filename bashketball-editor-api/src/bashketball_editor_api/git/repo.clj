(ns bashketball-editor-api.git.repo
  "Git repository management using JGit.

  Provides low-level Git operations for cloning, reading, writing, and syncing
  a local repository with a remote. Uses clj-jgit for JGit interop."
  (:require
   [clj-jgit.porcelain :as git]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log])
  (:import
   [java.time Instant]
   [org.eclipse.jgit.api Git]
   [org.eclipse.jgit.transport UsernamePasswordCredentialsProvider]))

(defrecord GitRepo [repo-path remote-url branch writer?]
  java.io.Closeable
  (close [_]
    nil))

(defn- credentials-provider
  "Creates a JGit credentials provider using a GitHub token."
  [github-token]
  (UsernamePasswordCredentialsProvider. "token" github-token))

(defn clone-or-open
  "Clones repository if it doesn't exist locally, otherwise opens it.

  Takes a config map with `:repo-path`, `:remote-url`, and `:branch`. If the
  repository already exists at `:repo-path`, opens it. Otherwise clones from
  `:remote-url`. Returns the JGit repository instance."
  [{:keys [repo-path remote-url branch]}]
  (let [repo-dir (io/file repo-path)]
    (if (.exists repo-dir)
      (do
        (log/info "Opening existing repository at" repo-path)
        (git/load-repo repo-path))
      (do
        (log/info "Cloning repository from" remote-url "to" repo-path)
        (git/git-clone remote-url
                       :dir repo-path
                       :branch branch)))))

(defn read-file
  "Reads a file from the working tree.

  Takes a [[GitRepo]] and a relative path within the repository. Returns the
  file contents as a string if the file exists, nil otherwise."
  [repo relative-path]
  (let [file-path (io/file (:repo-path repo) relative-path)]
    (when (.exists file-path)
      (slurp file-path))))

(defn write-file
  "Writes a file to the working tree.

  Takes a [[GitRepo]], relative path, and content string. Creates parent
  directories if needed. Does not commit the change."
  [repo relative-path content]
  (let [file-path (io/file (:repo-path repo) relative-path)]
    (io/make-parents file-path)
    (spit file-path content)))

(defn delete-file
  "Deletes a file from the working tree.

  Takes a [[GitRepo]] and relative path. Returns true if deleted, false if
  the file didn't exist. Does not commit the change."
  [repo relative-path]
  (let [file-path (io/file (:repo-path repo) relative-path)]
    (when (.exists file-path)
      (.delete file-path))))

(defn list-files
  "Lists files in a directory within the repository.

  Takes a [[GitRepo]] and a relative directory path. Returns a vector of
  File objects for files matching the optional extension filter."
  ([repo relative-dir]
   (list-files repo relative-dir nil))
  ([repo relative-dir extension]
   (let [dir (io/file (:repo-path repo) relative-dir)]
     (when (.exists dir)
       (let [files (.listFiles dir)]
         (if extension
           (filterv #(.endsWith (.getName %) extension) files)
           (vec files)))))))

(defn commit
  "Commits all changes in the working tree.

  Takes a [[GitRepo]], commit message, author name, and email. Only commits
  if the repository is configured as a writer. Returns nil if read-only."
  [repo message author-name author-email]
  (when (:writer? repo)
    (let [git-repo (git/load-repo (:repo-path repo))]
      (git/git-add git-repo ".")
      (git/git-commit git-repo message
                      :name author-name
                      :email author-email))))

(defn fetch
  "Fetches changes from remote without merging.

  Takes a [[GitRepo]] and optional GitHub token for authentication. When no
  token provided, uses system Git credentials (SSH keys, etc.)."
  ([repo]
   (let [git-repo (git/load-repo (:repo-path repo))]
     (git/git-fetch git-repo)))
  ([repo github-token]
   (let [^Git git-repo (git/load-repo (:repo-path repo))]
     (-> (.fetch git-repo)
         (.setCredentialsProvider (credentials-provider github-token))
         (.call)))))

(defn pull
  "Pulls changes from remote and merges.

  Takes a [[GitRepo]] and optional GitHub token for authentication. Returns
  the pull result."
  ([repo]
   (let [git-repo (git/load-repo (:repo-path repo))]
     (git/git-pull git-repo)))
  ([repo github-token]
   (let [^Git git-repo (git/load-repo (:repo-path repo))]
     (-> (.pull git-repo)
         (.setCredentialsProvider (credentials-provider github-token))
         (.call)))))

(defn push
  "Pushes local commits to remote.

  Takes a [[GitRepo]] and GitHub token for authentication. Only pushes if
  the repository is configured as a writer. Returns nil if read-only."
  [repo github-token]
  (when (:writer? repo)
    (let [^Git git-repo (git/load-repo (:repo-path repo))]
      (-> (.push git-repo)
          (.setCredentialsProvider (credentials-provider github-token))
          (.call)))))

(defn status
  "Gets the current repository status.

  Returns a map with keys like `:added`, `:changed`, `:removed`, `:untracked`.
  Returns an empty map if the directory is not a git repository."
  [repo]
  (try
    (let [git-repo (git/load-repo (:repo-path repo))]
      (git/git-status git-repo))
    (catch java.io.FileNotFoundException _
      {})))

(defn ahead-behind
  "Returns the number of commits ahead and behind the remote tracking branch.

  Returns a map with `:ahead` and `:behind` counts, or nil if unable to
  determine (e.g., no tracking branch configured).

  TODO(edpaget): this needs to fetch the remote first"
  [repo]
  (try
    (let [^Git git-repo   (git/load-repo (:repo-path repo))
          branch-tracking (org.eclipse.jgit.lib.BranchTrackingStatus/of
                           (.getRepository git-repo)
                           (:branch repo))]
      (when branch-tracking
        {:ahead (.getAheadCount branch-tracking)
         :behind (.getBehindCount branch-tracking)}))
    (catch Exception e
      (log/error e "Unable to get ahead/behind status")
      nil)))

(defn file-timestamps
  "Returns the creation and last modification timestamps for a file from Git history.

  Takes a [[GitRepo]] and a relative path within the repository. Queries the Git
  log for all commits that touched this file and returns a map with:
  - `:created-at` - Instant of the first commit that added the file
  - `:updated-at` - Instant of the most recent commit that modified the file

  Returns nil if the file has no Git history (e.g., untracked file)."
  [repo relative-path]
  (try
    (let [^Git git-repo (git/load-repo (:repo-path repo))
          commits       (-> (.log git-repo)
                            (.addPath relative-path)
                            (.call)
                            (.iterator)
                            iterator-seq)]
      (when (seq commits)
        (let [sorted    (sort-by #(.getCommitTime %) commits)
              first-commit (first sorted)
              last-commit  (last sorted)]
          {:created-at (Instant/ofEpochSecond (.getCommitTime first-commit))
           :updated-at (Instant/ofEpochSecond (.getCommitTime last-commit))})))
    (catch Exception e
      (log/warn e "Unable to get file timestamps for" relative-path)
      nil)))

(defn create-git-repo
  "Creates a [[GitRepo]] instance from configuration.

  Takes a map with `:repo-path`, `:remote-url`, `:branch`, and `:writer?`."
  [config]
  (map->GitRepo config))
