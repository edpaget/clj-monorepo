(ns bashketball-editor-api.git.cards
  "Card repository backed by Git via JGit.

  Stores cards as EDN files in a Git repository under `cards/<set-id>/<slug>.edn`.
  Cards use `:slug` as the primary key within a set (version history is handled by Git).
  Implements the [[bashketball-editor-api.models.protocol/Repository]] protocol."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(defn slugify
  "Converts a string to a URL-safe slug.

  Converts to lowercase and replaces non-alphanumeric characters with hyphens.
  Removes leading/trailing hyphens and collapses multiple hyphens."
  [s]
  (-> s
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")
      (str/replace #"^-|-$" "")))

(defn- card-path
  "Returns the file path for a card within the repository.

  Cards are stored at `cards/<set-id>/<slug>.edn`."
  [set-id slug]
  (str "cards/" set-id "/" slug ".edn"))

(defrecord CardRepository [git-repo lock]
  proto/Repository
  (find-by [_this criteria]
    (when-let [slug (:slug criteria)]
      (when-let [set-id (:set-id criteria)]
        (let [path (card-path set-id slug)]
          (when-let [content (git-repo/read-file git-repo path)]
            (edn/read-string content))))))

  (find-all [_this opts]
    (let [set-id           (get-in opts [:where :set-id])
          card-type-filter (get-in opts [:where :card-type])]
      (if set-id
        (let [card-files (git-repo/list-files git-repo (str "cards/" set-id) ".edn")
              cards      (mapv (fn [file]
                                 (edn/read-string (slurp file)))
                               (or card-files []))]
          (cond->> cards
            card-type-filter (filterv #(= card-type-filter (:card-type %)))))
        [])))

  (create! [_this data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (:_user data)
            _        (when-not user-ctx
                       (throw (ex-info "User context required for Git operations" {})))
            set-id   (:set-id data)
            slug     (:slug data)
            _        (when-not slug
                       (throw (ex-info "slug is required" {:data data})))
            now      (java.time.Instant/now)
            card     (-> data
                         (dissoc :_user)
                         (assoc :created-at (or (:created-at data) now)
                                :updated-at now))
            path     (card-path set-id slug)]
        (when (git-repo/read-file git-repo path)
          (throw (ex-info "Card already exists" {:slug slug :set-id set-id})))
        (git-repo/write-file git-repo path (pr-str card))
        (git-repo/commit git-repo
                         (str "Create card: " slug)
                         (:name user-ctx)
                         (:email user-ctx))
        (git-repo/push git-repo (:github-token user-ctx))
        card)))

  (update! [this criteria data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (:_user data)
            _        (when-not user-ctx
                       (throw (ex-info "User context required for Git operations" {})))
            slug     (:slug criteria)
            set-id   (:set-id data)
            _        (when-not (and slug set-id)
                       (throw (ex-info "slug and set-id required for card update"
                                       {:criteria criteria :set-id set-id})))]
        (if-let [existing (proto/find-by this {:slug slug :set-id set-id})]
          (let [updated (-> existing
                            (merge (dissoc data :_user :set-id :slug))
                            (assoc :updated-at (java.time.Instant/now)))
                path    (card-path set-id slug)]
            (git-repo/write-file git-repo path (pr-str updated))
            (git-repo/commit git-repo
                             (str "Update card: " slug)
                             (:name user-ctx)
                             (:email user-ctx))
            (git-repo/push git-repo (:github-token user-ctx))
            updated)
          (throw (ex-info "Card not found" {:slug slug :set-id set-id}))))))

  (delete! [_this id]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (throw (ex-info "Delete requires slug, set-id, and user context. Use delete-card! function instead."
                    {:id id}))))

(defn delete-card!
  "Deletes a card with proper context.

  Requires card `slug`, `set-id`, and `user-ctx` (map with `:name`, `:email`,
  `:github-token`) since Git operations need author info."
  [card-repo slug set-id user-ctx]
  (when-not (:writer? (:git-repo card-repo))
    (throw (ex-info "Repository is read-only" {})))
  (locking (:lock card-repo)
    (let [path     (card-path set-id slug)
          git-repo (:git-repo card-repo)]
      (if (git-repo/delete-file git-repo path)
        (do
          (git-repo/commit git-repo
                           (str "Delete card: " slug)
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
