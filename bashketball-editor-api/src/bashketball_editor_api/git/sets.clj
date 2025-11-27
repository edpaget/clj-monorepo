(ns bashketball-editor-api.git.sets
  "Card set repository backed by Git via JGit.

  Stores card sets as EDN files in a Git repository under `sets/<set-id>/metadata.edn`.
  Implements the [[bashketball-editor-api.models.protocol/Repository]] protocol."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [malli.core :as m]))

(def CardSet
  "Malli schema for card set entity."
  [:map
   [:id {:optional true} :uuid]
   [:name :string]
   [:description {:optional true} [:maybe :string]]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(defn- set-path
  "Returns the file path for a card set's metadata within the repository."
  [set-id]
  (str "sets/" set-id "/metadata.edn"))

(defrecord SetRepository [git-repo lock user-ctx-fn]
  proto/Repository
  (find-by [_this criteria]
    (when-let [id (:id criteria)]
      (when-let [content (git-repo/read-file git-repo (set-path id))]
        (edn/read-string content))))

  (find-all [_this _opts]
    (let [sets-dir (io/file (:repo-path git-repo) "sets")]
      (if (.exists sets-dir)
        (->> (.listFiles sets-dir)
             (filter #(.isDirectory %))
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
            id       (or (:id data) (java.util.UUID/randomUUID))
            now      (java.time.Instant/now)
            card-set (-> data
                         (assoc :id id
                                :created-at (or (:created-at data) now)
                                :updated-at now))
            path     (set-path id)]
        (git-repo/write-file git-repo path (pr-str card-set))
        (git-repo/commit git-repo
                         (str "Create set: " (:name card-set) " [" id "]")
                         (:name user-ctx)
                         (:email user-ctx))
        (git-repo/push git-repo (:github-token user-ctx))
        card-set)))

  (update! [this id data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (user-ctx-fn)]
        (if-let [existing (proto/find-by this {:id id})]
          (let [updated (-> existing
                            (merge data)
                            (assoc :updated-at (java.time.Instant/now)))
                path    (set-path id)]
            (git-repo/write-file git-repo path (pr-str updated))
            (git-repo/commit git-repo
                             (str "Update set: " (:name updated) " [" id "]")
                             (:name user-ctx)
                             (:email user-ctx))
            (git-repo/push git-repo (:github-token user-ctx))
            updated)
          (throw (ex-info "Set not found" {:id id}))))))

  (delete! [_this id]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (user-ctx-fn)
            path     (set-path id)]
        (if (git-repo/delete-file git-repo path)
          (do
            (git-repo/commit git-repo
                             (str "Delete set: " id)
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
