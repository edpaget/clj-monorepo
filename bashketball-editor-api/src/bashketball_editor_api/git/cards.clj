(ns bashketball-editor-api.git.cards
  "Card repository backed by Git via JGit.

  Stores cards as EDN files in a Git repository under `cards/<set-id>/<card-id>.edn`.
  Implements the [[bashketball-editor-api.models.protocol/Repository]] protocol."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.edn :as edn]
   [malli.core :as m]))

(def Card
  "Malli schema for card entity."
  [:map
   [:id {:optional true} :uuid]
   [:set-id :uuid]
   [:name :string]
   [:description {:optional true} [:maybe :string]]
   [:attributes {:optional true} :map]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(defn- card-path
  "Returns the file path for a card within the repository."
  [set-id card-id]
  (str "cards/" set-id "/" card-id ".edn"))

(defrecord CardRepository [git-repo lock]
  proto/Repository
  (find-by [_this criteria]
    (when-let [id (:id criteria)]
      (when-let [set-id (:set-id criteria)]
        (when-let [content (git-repo/read-file git-repo (card-path set-id id))]
          (edn/read-string content)))))

  (find-all [_this opts]
    (if-let [set-id (get-in opts [:where :set-id])]
      (let [card-files (git-repo/list-files git-repo (str "cards/" set-id) ".edn")]
        (mapv (fn [file]
                (edn/read-string (slurp file)))
              (or card-files [])))
      []))

  (create! [_this data]
    {:pre [(m/validate Card data)]}
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (:_user data)
            _ (when-not user-ctx
                (throw (ex-info "User context required for Git operations" {})))
            id (or (:id data) (java.util.UUID/randomUUID))
            set-id (:set-id data)
            now (java.time.Instant/now)
            card (-> data
                     (dissoc :_user)
                     (assoc :id id
                            :created-at (or (:created-at data) now)
                            :updated-at now))
            path (card-path set-id id)]
        (git-repo/write-file git-repo path (pr-str card))
        (git-repo/commit git-repo
                         (str "Create card: " (:name card) " [" id "]")
                         (:name user-ctx)
                         (:email user-ctx))
        (git-repo/push git-repo (:github-token user-ctx))
        card)))

  (update! [this id data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (:_user data)
            _ (when-not user-ctx
                (throw (ex-info "User context required for Git operations" {})))
            set-id (:set-id data)
            _ (when-not set-id
                (throw (ex-info "set-id required for card update" {:id id})))]
        (if-let [existing (proto/find-by this {:id id :set-id set-id})]
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
            updated)
          (throw (ex-info "Card not found" {:id id :set-id set-id}))))))

  (delete! [_this id]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (throw (ex-info "Delete requires set-id and user context. Use delete-card! function instead." {:id id}))))

(defn delete-card!
  "Deletes a card with proper context.

  Requires `set-id` and `user-ctx` (map with `:name`, `:email`, `:github-token`)
  since Git operations need author info and the file path depends on set-id."
  [card-repo id set-id user-ctx]
  (when-not (:writer? (:git-repo card-repo))
    (throw (ex-info "Repository is read-only" {})))
  (locking (:lock card-repo)
    (let [path (card-path set-id id)
          git-repo (:git-repo card-repo)]
      (if (git-repo/delete-file git-repo path)
        (do
          (git-repo/commit git-repo
                           (str "Delete card: " id)
                           (:name user-ctx)
                           (:email user-ctx))
          (git-repo/push git-repo (:github-token user-ctx))
          true)
        false))))

(defn create-card-repository
  "Creates a Git-backed card repository.

  Takes a [[git-repo/GitRepo]] instance."
  [git-repo]
  (->CardRepository git-repo (Object.)))
