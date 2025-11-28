(ns bashketball-editor-api.git.sets
  "Card set repository backed by Git via JGit.

  Stores card sets as EDN files in a Git repository under `<slug>/metadata.edn`.

  Operations stage changes to the working tree but do not commit. Use
  [[bashketball-editor-api.git.repo/commit]] to commit staged changes.

  Implements the [[bashketball-editor-api.models.protocol/Repository]] protocol."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [bashketball-editor-api.util.edn :as edn-util]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [malli.core :as m]))

(def CardSet
  "Malli schema for card set entity.

  The `:slug` must be provided by the service layer before calling the repository."
  [:map
   [:slug :string]
   [:name :string]
   [:description {:optional true} [:maybe :string]]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(defn- set-path
  "Returns the file path for a card set's metadata within the repository.

  Set metadata is stored at `<slug>/metadata.edn`."
  [slug]
  (str slug "/metadata.edn"))

(defn- with-git-timestamps
  "Merges Git commit timestamps into a card set if not already present.

  Looks up the file's first and last commit times from Git history and uses them
  as `:created-at` and `:updated-at` if the set doesn't already have those fields."
  [git-repo path card-set]
  (if (and (:created-at card-set) (:updated-at card-set))
    card-set
    (if-let [timestamps (git-repo/file-timestamps git-repo path)]
      (-> card-set
          (update :created-at #(or % (:created-at timestamps)))
          (update :updated-at #(or % (:updated-at timestamps))))
      card-set)))

(defrecord SetRepository [git-repo lock]
  proto/Repository
  (find-by [_this criteria]
    (when-let [slug (:slug criteria)]
      (let [path (set-path slug)]
        (when-let [content (git-repo/read-file git-repo path)]
          (with-git-timestamps git-repo path (edn-util/read-edn content))))))

  (find-all [_this _opts]
    (let [repo-dir (io/file (:repo-path git-repo))]
      (if (.exists repo-dir)
        (->> (.listFiles repo-dir)
             (filter #(.isDirectory %))
             (remove #(str/starts-with? (.getName %) "."))
             (keep (fn [dir]
                     (let [metadata-file (io/file dir "metadata.edn")
                           path          (str (.getName dir) "/metadata.edn")]
                       (when (.exists metadata-file)
                         (with-git-timestamps git-repo path
                           (edn-util/read-edn (slurp metadata-file)))))))
             vec)
        [])))

  (create! [_this data]
    {:pre [(m/validate CardSet data)
           (:slug data)]}
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [slug     (:slug data)
            now      (java.time.Instant/now)
            card-set (-> data
                         (assoc :created-at (or (:created-at data) now)
                                :updated-at now))
            path     (set-path slug)]
        (when (git-repo/read-file git-repo path)
          (throw (ex-info "Set already exists" {:slug slug :name (:name data)})))
        (git-repo/write-file git-repo path (edn-util/pr-str-pretty card-set))
        card-set)))

  (update! [this slug data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (if-let [existing (proto/find-by this {:slug slug})]
        (let [updated (-> existing
                          (merge data)
                          (assoc :updated-at (java.time.Instant/now)))
              path    (set-path slug)]
          (git-repo/write-file git-repo path (edn-util/pr-str-pretty updated))
          updated)
        (throw (ex-info "Set not found" {:slug slug})))))

  (delete! [_this slug]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [path (set-path slug)]
        (boolean (git-repo/delete-file git-repo path))))))

(defn create-set-repository
  "Creates a Git-backed card set repository.

  Takes a [[git-repo/GitRepo]] instance. Operations stage changes to the
  working tree but do not commit - use [[git-repo/commit]] to commit."
  [git-repo]
  (->SetRepository git-repo (Object.)))
