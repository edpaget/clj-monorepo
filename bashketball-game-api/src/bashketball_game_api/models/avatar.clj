(ns bashketball-game-api.models.avatar
  "User avatar model and repository.

  Manages binary avatar data stored in PostgreSQL. Avatars are downloaded from
  OAuth providers (Google) and cached locally to avoid rate limits on external
  avatar URLs. Each user has at most one avatar, keyed by their user ID."
  (:require
   [db.core :as db]
   [malli.core :as m]))

(def Avatar
  "Malli schema for avatar entity."
  [:map
   [:user-id :uuid]
   [:data bytes?]
   [:content-type :string]
   [:etag {:optional true} [:maybe :string]]
   [:fetched-at {:optional true} inst?]])

(def AvatarCreate
  "Malli schema for creating/updating an avatar."
  [:map
   [:user-id :uuid]
   [:data bytes?]
   [:content-type :string]
   [:etag {:optional true} [:maybe :string]]])

(defprotocol AvatarRepository
  "Protocol for avatar storage operations.

  Avatars are keyed by user ID with a 1:1 relationship. Operations support
  fetching, upserting, and deleting avatar binary data."

  (get-avatar [this user-id]
    "Retrieves the avatar for a user by their ID.

    Returns a map with `:data` (bytes), `:content-type`, `:etag`, and
    `:fetched-at` if found, nil otherwise.")

  (upsert! [this data]
    "Creates or updates an avatar for a user.

    Takes a map with `:user-id`, `:data` (bytes), `:content-type`, and
    optionally `:etag`. If an avatar already exists for the user, it is
    replaced. Returns the upserted avatar.")

  (delete! [this user-id]
    "Deletes the avatar for a user.

    Returns true if an avatar was deleted, false if none existed."))

(defrecord DatabaseAvatarRepository []
  AvatarRepository
  (get-avatar [_this user-id]
    (db/execute-one!
     {:select [:user-id :data :content-type :etag :fetched-at]
      :from [:user-avatars]
      :where [:= :user-id [:cast user-id :uuid]]}))

  (upsert! [_this data]
    {:pre [(m/validate AvatarCreate data)]}
    (let [now         (java.time.Instant/now)
          avatar-data (assoc data :fetched-at now)]
      (db/execute-one!
       {:insert-into :user-avatars
        :values [(-> avatar-data
                     (update :user-id #(vector :cast % :uuid)))]
        :on-conflict :user-id
        :do-update-set {:data (:data data)
                        :content-type (:content-type data)
                        :etag (:etag data)
                        :fetched-at now}
        :returning [:*]})))

  (delete! [_this user-id]
    (pos? (:next.jdbc/update-count
           (db/execute-one!
            {:delete-from :user-avatars
             :where [:= :user-id [:cast user-id :uuid]]})))))

(defn create-avatar-repository
  "Creates a new database-backed avatar repository instance."
  []
  (->DatabaseAvatarRepository))

(defn avatar-exists?
  "Returns true if an avatar exists for the given user ID."
  [repo user-id]
  (some? (get-avatar repo user-id)))
