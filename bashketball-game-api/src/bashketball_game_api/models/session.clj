(ns bashketball-game-api.models.session
  "Session model and repository.

  Implements database-backed session storage for authentication. Sessions
  are stored in PostgreSQL with JSON claims and expiration timestamps.
  Implements the [[authn.protocol/SessionStore]] protocol."
  (:require
   [authn.protocol :as proto]
   [db.core :as db]
   [db.transform :as db-transform]))

(defrecord SessionRepository [ttl-ms]
  proto/SessionStore
  (create-session [_this user-id claims]
    (let [session-id (str (java.util.UUID/randomUUID))
          now        (System/currentTimeMillis)
          expires-at (+ now ttl-ms)]
      (db/execute-one!
       {:insert-into :sessions
        :values [{:user-id [:cast user-id :uuid]
                  :session-id session-id
                  :claims [:lift claims]
                  :expires-at (java.time.Instant/ofEpochMilli expires-at)
                  :created-at (java.time.Instant/ofEpochMilli now)}]})
      session-id))

  (get-session [_this session-id]
    (when-let [session (db/execute-one!
                        {:select [:user-id :claims :created-at :expires-at]
                         :from [:sessions]
                         :where [:and
                                 [:= :session-id session-id]
                                 [:> :expires-at [:now]]]})]
      {:user-id (str (:user-id session))
       :claims (db-transform/keywordize-keys (:claims session))
       :created-at (.toEpochMilli (.toInstant (:created-at session)))
       :expires-at (.toEpochMilli (.toInstant (:expires-at session)))}))

  (update-session [_this session-id session-data]
    (let [result (db/execute-one!
                  {:update :sessions
                   :set (cond-> {}
                          (:expires-at session-data)
                          (assoc :expires-at
                                 (java.time.Instant/ofEpochMilli
                                  (:expires-at session-data)))

                          (:claims session-data)
                          (assoc :claims [:lift (:claims session-data)]))
                   :where [:= :session-id session-id]
                   :returning [:id]})]
      (some? result)))

  (delete-session [_this session-id]
    (-> (db/execute-one!
         {:delete-from :sessions
          :where [:= :session-id session-id]})
        :next.jdbc/update-count
        pos?))

  (cleanup-expired [_this]
    (-> (db/execute-one!
         {:delete-from :sessions
          :where [:< :expires-at [:now]]})
        :next.jdbc/update-count)))

(defn create-session-repository
  "Creates a new database-backed session repository.

  Takes a TTL in milliseconds for session expiration. Sessions older than
  this TTL will be considered expired and will not be returned by `get-session`."
  [ttl-ms]
  (->SessionRepository ttl-ms))
