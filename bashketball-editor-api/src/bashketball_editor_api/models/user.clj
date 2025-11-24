(ns bashketball-editor-api.models.user
  "User model and repository.

  Manages user data stored in PostgreSQL. Users are created from GitHub
  OAuth authentication data."
  (:require
   [bashketball-editor-api.models.protocol :as proto]
   [db.core :as db]
   [malli.core :as m]))

(def User
  "Malli schema for user entity."
  [:map
   [:id {:optional true} :uuid]
   [:github-login :string]
   [:github-token {:optional true} [:maybe :string]]
   [:email {:optional true} [:maybe :string]]
   [:avatar-url {:optional true} [:maybe :string]]
   [:name {:optional true} [:maybe :string]]
   [:created-at {:optional true} inst?]
   [:updated-at {:optional true} inst?]])

(defn- build-where-clause
  "Builds a HoneySQL where clause from a criteria map."
  [criteria]
  (when (seq criteria)
    (into [:and]
          (map (fn [[k v]]
                 (if (= k :id)
                   [:= k [:cast v :uuid]]
                   [:= k v]))
               criteria))))

(defrecord UserRepository []
  proto/Repository
  (find-by [_this criteria]
    (when-let [where-clause (build-where-clause criteria)]
      (db/execute-one!
       {:select [:*]
        :from [:users]
        :where where-clause})))

  (find-all [_this opts]
    (let [query (cond-> {:select [:*]
                         :from [:users]
                         :order-by (or (:order-by opts) [[:created-at :desc]])}
                  (:where opts)
                  (assoc :where (build-where-clause (:where opts)))

                  (:limit opts)
                  (assoc :limit (:limit opts))

                  (:offset opts)
                  (assoc :offset (:offset opts)))]
      (vec (db/execute! query))))

  (create! [_this data]
    {:pre [(m/validate User data)]}
    (let [now       (java.time.Instant/now)
          user-data (cond-> data
                      (not (:created-at data))
                      (assoc :created-at now)

                      true
                      (assoc :updated-at now))]
      (db/execute-one!
       {:insert-into :users
        :values [user-data]
        :on-conflict :github-login
        :do-update-set (assoc (dissoc user-data :created-at)
                              :updated-at now)
        :returning [:*]})))

  (update! [_this id data]
    (let [now (java.time.Instant/now)]
      (db/execute-one!
       {:update :users
        :set (assoc data :updated-at now)
        :where [:= :id [:cast id :uuid]]
        :returning [:*]})))

  (delete! [_this id]
    (pos? (:next.jdbc/update-count
           (db/execute-one!
            {:delete-from :users
             :where [:= :id [:cast id :uuid]]})))))

(defn create-user-repository
  "Creates a new user repository instance."
  []
  (->UserRepository))
