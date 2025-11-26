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

(defrecord SetRepository [git-repo lock]
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
      (let [user-ctx (:_user data)
            _ (when-not user-ctx
                (throw (ex-info "User context required for Git operations" {})))
            id (or (:id data) (java.util.UUID/randomUUID))
            now (java.time.Instant/now)
            card-set (-> data
                         (dissoc :_user)
                         (assoc :id id
                                :created-at (or (:created-at data) now)
                                :updated-at now))
            path (set-path id)]
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
      (let [user-ctx (:_user data)
            _ (when-not user-ctx
                (throw (ex-info "User context required for Git operations" {})))]
        (if-let [existing (proto/find-by this {:id id})]
          (let [updated (-> existing
                            (merge (dissoc data :_user))
                            (assoc :updated-at (java.time.Instant/now)))
                path (set-path id)]
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
    (throw (ex-info "Delete requires user context. Use delete-set! function instead." {:id id}))))

(defn delete-set!
  "Deletes a card set with proper context.

  Requires `user-ctx` (map with `:name`, `:email`, `:github-token`). Deletes
  only the set metadata; cards within the set are not automatically deleted."
  [set-repo id user-ctx]
  (when-not (:writer? (:git-repo set-repo))
    (throw (ex-info "Repository is read-only" {})))
  (locking (:lock set-repo)
    (let [path (set-path id)
          git-repo (:git-repo set-repo)]
      (if (git-repo/delete-file git-repo path)
        (do
          (git-repo/commit git-repo
                           (str "Delete set: " id)
                           (:name user-ctx)
                           (:email user-ctx))
          (git-repo/push git-repo (:github-token user-ctx))
          true)
        false))))

(defn create-set-repository
  "Creates a Git-backed card set repository.

  Takes a [[git-repo/GitRepo]] instance."
  [git-repo]
  (->SetRepository git-repo (Object.)))
