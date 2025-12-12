(ns bashketball-editor-api.git.branches
  "Branch repository backed by Git via JGit.

  Provides branch listing, creation, and switching operations through the
  standard [[bashketball-editor-api.models.protocol/Repository]] protocol.

  Operations:
  - `find-by` - Get branch by name or current branch
  - `find-all` - List all local branches
  - `create!` - Create new branch and check it out
  - `update!` - Switch to existing branch
  - `delete!` - Not implemented"
  (:require
   [bashketball-editor-api.models.protocol :as proto]
   [clj-jgit.porcelain :as git]
   [clojure.string :as str]
   [clojure.tools.logging :as log])
  (:import
   [java.io File]
   [org.eclipse.jgit.api Git]
   [org.eclipse.jgit.lib Ref]))

(defn- ref->branch-name
  "Extracts branch name from a JGit Ref object."
  [^Ref ref]
  (let [full-name (.getName ref)]
    (if (str/starts-with? full-name "refs/heads/")
      (subs full-name (count "refs/heads/"))
      full-name)))

(defn- get-current-branch-name
  "Returns the name of the currently checked out branch."
  [^Git jgit]
  (try
    (.. jgit getRepository getBranch)
    (catch Exception e
      (log/error e "Failed to get current branch")
      nil)))

(defn- list-local-branches
  "Lists all local branches as Ref objects."
  [^Git jgit]
  (try
    (-> (.branchList jgit)
        (.call))
    (catch Exception e
      (log/error e "Failed to list branches")
      [])))

(defrecord BranchRepository [git-repo]
  proto/Repository

  (find-by [_this criteria]
    (let [git-dir (File. (:repo-path git-repo) ".git")]
      (when (.exists git-dir)
        (let [^Git jgit   (git/load-repo (:repo-path git-repo))
              current     (get-current-branch-name jgit)
              branch-name (or (:name criteria)
                              (when (:current criteria) current))]
          (when branch-name
            (let [refs    (list-local-branches jgit)
                  names   (map ref->branch-name refs)
                  exists? (some #(= branch-name %) names)]
              (when exists?
                {:name    branch-name
                 :current (= branch-name current)})))))))

  (find-all [_this _opts]
    (let [git-dir (File. (:repo-path git-repo) ".git")]
      (if (.exists git-dir)
        (let [^Git jgit (git/load-repo (:repo-path git-repo))
              current   (get-current-branch-name jgit)
              refs      (list-local-branches jgit)]
          (mapv (fn [ref]
                  (let [name (ref->branch-name ref)]
                    {:name    name
                     :current (= name current)}))
                refs))
        [])))

  (create! [_this data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (let [branch-name (:name data)
          _           (when-not branch-name
                        (throw (ex-info "Branch name is required" {:data data})))
          git-dir     (File. (:repo-path git-repo) ".git")]
      (if (.exists git-dir)
        (try
          (let [^Git jgit (git/load-repo (:repo-path git-repo))]
            ;; Create the branch
            (-> (.branchCreate jgit)
                (.setName branch-name)
                (.call))
            ;; Checkout the new branch
            (-> (.checkout jgit)
                (.setName branch-name)
                (.call))
            {:status "success"
             :message (str "Created and switched to branch " branch-name)
             :branch branch-name})
          (catch org.eclipse.jgit.api.errors.RefAlreadyExistsException _
            {:status "error"
             :message (str "Branch '" branch-name "' already exists")})
          (catch Exception e
            (log/error e "Failed to create branch" branch-name)
            {:status "error"
             :message (str "Failed to create branch: " (.getMessage e))}))
        {:status "error"
         :message "Not a git repository"})))

  (update! [_this id _data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (let [branch-name (:name id)
          _           (when-not branch-name
                        (throw (ex-info "Branch name is required" {:id id})))
          git-dir     (File. (:repo-path git-repo) ".git")]
      (if (.exists git-dir)
        (try
          (let [^Git jgit (git/load-repo (:repo-path git-repo))]
            (-> (.checkout jgit)
                (.setName branch-name)
                (.call))
            {:status "success"
             :message (str "Switched to branch " branch-name)
             :branch branch-name})
          (catch org.eclipse.jgit.api.errors.RefNotFoundException _
            {:status "error"
             :message (str "Branch '" branch-name "' not found")})
          (catch Exception e
            (log/error e "Failed to switch branch" branch-name)
            {:status "error"
             :message (str "Failed to switch branch: " (.getMessage e))}))
        {:status "error"
         :message "Not a git repository"})))

  (delete! [_this _id]
    (throw (ex-info "Not implemented" {:operation :delete!}))))

(defn create-branch-repository
  "Creates a Git-backed branch repository.

  Takes a [[git-repo/GitRepo]] instance."
  [git-repo]
  (->BranchRepository git-repo))
