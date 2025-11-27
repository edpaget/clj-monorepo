(ns bashketball-editor-api.git.cards
  "Card repository backed by Git via JGit.

  Stores cards as EDN files in a Git repository under `cards/<set-slug>/<slug>.edn`.
  Cards use `:slug` as the primary key within a set (version history is handled by Git).
  Implements the [[bashketball-editor-api.models.protocol/Repository]] protocol."
  (:require
   [bashketball-editor-api.git.repo :as git-repo]
   [bashketball-editor-api.models.protocol :as proto]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
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

  Cards are stored at `<set-slug>/<slug>.edn`."
  [set-slug slug]
  (str set-slug "/" slug ".edn"))

(defn- with-git-timestamps
  "Merges Git commit timestamps into a card if not already present.

  Looks up the file's first and last commit times from Git history and uses them
  as `:created-at` and `:updated-at` if the card doesn't already have those fields."
  [git-repo path card]
  (if (and (:created-at card) (:updated-at card))
    card
    (if-let [timestamps (git-repo/file-timestamps git-repo path)]
      (-> card
          (update :created-at #(or % (:created-at timestamps)))
          (update :updated-at #(or % (:updated-at timestamps))))
      card)))

(defrecord CardRepository [git-repo lock user-ctx-fn]
  proto/Repository
  (find-by [_this criteria]
    (when-let [slug (:slug criteria)]
      (when-let [set-slug (:set-slug criteria)]
        (let [path (card-path set-slug slug)]
          (when-let [content (git-repo/read-file git-repo path)]
            (with-git-timestamps git-repo path (edn/read-string content)))))))

  (find-all [_this opts]
    (let [set-slug         (get-in opts [:where :set-slug])
          card-type-filter (get-in opts [:where :card-type])
          repo-dir         (io/file (:repo-path git-repo))
          set-dirs         (if set-slug
                             [(io/file repo-dir set-slug)]
                             (when (.exists repo-dir)
                               (->> (.listFiles repo-dir)
                                    (filter #(.isDirectory %))
                                 (remove #(str/starts-with? (.getName %) ".")))))]
      (->> set-dirs
           (mapcat (fn [dir]
                     (let [dir-name   (.getName dir)
                           card-files (git-repo/list-files git-repo dir-name ".edn")
                           card-files (remove #(str/ends-with? (.getName %) "metadata.edn")
                                              (or card-files []))]
                       (map (fn [file]
                              (let [card (edn/read-string (slurp file))
                                    path (str dir-name "/" (.getName file))]
                                (with-git-timestamps git-repo path card)))
                            card-files))))
           (filter #(if card-type-filter
                      (= card-type-filter (:card-type %))
                      true))
           vec)))

  (create! [_this data]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (user-ctx-fn)
            set-slug (:set-slug data)
            slug     (:slug data)
            _        (when-not slug
                       (throw (ex-info "slug is required" {:data data})))
            now      (java.time.Instant/now)
            card     (-> data
                         (assoc :created-at (or (:created-at data) now)
                                :updated-at now))
            path     (card-path set-slug slug)]
        (when (git-repo/read-file git-repo path)
          (throw (ex-info "Card already exists" {:slug slug :set-slug set-slug})))
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
      (let [user-ctx (user-ctx-fn)
            slug     (:slug criteria)
            set-slug (:set-slug criteria)
            _        (when-not (and slug set-slug)
                       (throw (ex-info "slug and set-slug required for card update"
                                       {:criteria criteria})))]
        (if-let [existing (proto/find-by this {:slug slug :set-slug set-slug})]
          (let [updated (-> existing
                            (merge (dissoc data :set-slug :slug))
                            (assoc :updated-at (java.time.Instant/now)))
                path    (card-path set-slug slug)]
            (git-repo/write-file git-repo path (pr-str updated))
            (git-repo/commit git-repo
                             (str "Update card: " slug)
                             (:name user-ctx)
                             (:email user-ctx))
            (git-repo/push git-repo (:github-token user-ctx))
            updated)
          (throw (ex-info "Card not found" {:slug slug :set-slug set-slug}))))))

  (delete! [_this id]
    (when-not (:writer? git-repo)
      (throw (ex-info "Repository is read-only" {})))
    (locking lock
      (let [user-ctx (user-ctx-fn)
            slug     (:slug id)
            set-slug (:set-slug id)
            _        (when-not (and slug set-slug)
                       (throw (ex-info "slug and set-slug required for card delete" {:id id})))
            path     (card-path set-slug slug)]
        (if (git-repo/delete-file git-repo path)
          (do
            (git-repo/commit git-repo
                             (str "Delete card: " slug)
                             (:name user-ctx)
                             (:email user-ctx))
            (git-repo/push git-repo (:github-token user-ctx))
            true)
          false)))))

(defn create-card-repository
  "Creates a Git-backed card repository.

  Takes a [[git-repo/GitRepo]] instance and a `user-ctx-fn` - a zero-argument
  function that returns the current user context map with `:name`, `:email`,
  and `:github-token` keys. Use [[bashketball-editor-api.context/current-user-context]]
  as the accessor for request-scoped context."
  [git-repo user-ctx-fn]
  (->CardRepository git-repo (Object.) user-ctx-fn))
