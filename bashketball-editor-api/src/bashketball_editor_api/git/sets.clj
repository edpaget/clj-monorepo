(ns bashketball-editor-api.git.sets
  "Card set repository backed by Git via JGit.

  Stores card sets as EDN files in a Git repository under `sets/<slug>/metadata.edn`.
  Set slugs are derived from the set name.
  Implements the [[bashketball-editor-api.models.protocol/Repository]] protocol."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [malli.core :as m]))

(def CardSet
  "Malli schema for card set entity.

  The `:slug` is derived from the set name."
  [:map
   [:slug {:optional true} :string]
   [:name :string]
   [:description {:optional true} [:maybe :string]]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(defn slugify
  "Converts a string to a URL-safe slug.

  Converts to lowercase and replaces non-alphanumeric characters with hyphens.
  Removes leading/trailing hyphens and collapses multiple hyphens."
  [s]
  (-> s
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"^-|-$" "")))

(defn- set-path
  "Returns the file path for a card set's metadata within the repository.

  Set metadata is stored at `<slug>/metadata.edn`."
  [slug]
  (str slug "/metadata.edn"))

(defrecord SetRepository [git-repo lock user-ctx-fn]
  proto/Repository
  (find-by [_this criteria]
    (when-let [slug (:slug criteria)]
      (when-let [content (git-repo/read-file git-repo (set-path slug))]
        (edn/read-string content))))

  (find-all [_this _opts]
    (let [repo-dir (io/file (:repo-path git-repo))]
      (if (.exists repo-dir)
        (->> (.listFiles repo-dir)
             (filter #(.isDirectory %))
             (remove #(str/starts-with? (.getName %) "."))
             (keep (fn [dir]
                     (let [metadata-file (io/file dir "metadata.edn")]
                       (when (.exists metadata-file)
                         (edn/read-string (slurp metadata-file))))))
             vec)
        [])))

  (create! [_this data]
    {:pre [(m/validate CardSet data)]}
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (user-ctx-fn)
            slug     (or (:slug data) (slugify (:name data)))
            now      (java.time.Instant/now)
            card-set (-> data
                         (assoc :slug slug
                                :created-at (or (:created-at data) now)
                                :updated-at now))
            path     (set-path slug)]
        (when (git-repo/read-file git-repo path)
          (throw (ex-info "Set already exists" {:slug slug :name (:name data)})))
        (git-repo/write-file git-repo path (pr-str card-set))
        (git-repo/commit git-repo
                         (str "Create set: " (:name card-set) " [" slug "]")
                         (:name user-ctx)
                         (:email user-ctx))
        (git-repo/push git-repo (:github-token user-ctx))
        card-set)))

  (update! [this slug data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (user-ctx-fn)]
        (if-let [existing (proto/find-by this {:slug slug})]
          (let [updated (-> existing
                            (merge data)
                            (assoc :updated-at (java.time.Instant/now)))
                path    (set-path slug)]
            (git-repo/write-file git-repo path (pr-str updated))
            (git-repo/commit git-repo
                             (str "Update set: " (:name updated) " [" slug "]")
                             (:name user-ctx)
                             (:email user-ctx))
            (git-repo/push git-repo (:github-token user-ctx))
            updated)
          (throw (ex-info "Set not found" {:slug slug}))))))

  (delete! [_this slug]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (user-ctx-fn)
            path     (set-path slug)]
        (if (git-repo/delete-file git-repo path)
          (do
            (git-repo/commit git-repo
                             (str "Delete set: " slug)
                             (:name user-ctx)
                             (:email user-ctx))
            (git-repo/push git-repo (:github-token user-ctx))
            true)
          false)))))

(defn create-set-repository
  "Creates a Git-backed card set repository.

  Takes a [[git-repo/GitRepo]] instance and a `user-ctx-fn` - a zero-argument
  function that returns the current user context map with `:name`, `:email`,
  and `:github-token` keys. Use [[bashketball-editor-api.context/current-user-context]]
  as the accessor for request-scoped context."
  [git-repo user-ctx-fn]
  (->SetRepository git-repo (Object.) user-ctx-fn))
