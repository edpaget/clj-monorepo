(ns authn.store
  "In-memory implementations of storage protocols.

  Provides simple in-memory stores for development and testing. For production
  use, implement the protocols with persistent storage (database, Redis, etc.)."
  (:require
   [authn.protocol :as proto]))

(set! *warn-on-reflection* true)

(defn- generate-session-id
  "Generates a random session ID."
  []
  (str (java.util.UUID/randomUUID)))

(defrecord InMemorySessionStore [sessions session-ttl-ms]
  proto/SessionStore
  (create-session [_this user-id claims]
    (let [session-id (generate-session-id)
          now (System/currentTimeMillis)
          expires-at (+ now session-ttl-ms)
          session-data {:user-id user-id
                        :claims claims
                        :created-at now
                        :expires-at expires-at}]
      (swap! sessions assoc session-id session-data)
      session-id))

  (get-session [_this session-id]
    (when-let [session-data (get @sessions session-id)]
      (let [now (System/currentTimeMillis)]
        (if (< now (:expires-at session-data))
          session-data
          (do
            (swap! sessions dissoc session-id)
            nil)))))

  (update-session [_this session-id session-data]
    (if (contains? @sessions session-id)
      (do
        (swap! sessions update session-id merge session-data)
        true)
      false))

  (delete-session [_this session-id]
    (swap! sessions dissoc session-id)
    true)

  (cleanup-expired [_this]
    (let [now (System/currentTimeMillis)
          expired-keys (filter (fn [[_id session]]
                                 (>= now (:expires-at session)))
                               @sessions)
          count (count expired-keys)]
      (swap! sessions #(apply dissoc % (map first expired-keys)))
      count)))

(defn create-session-store
  "Creates an in-memory session store.

  Takes an optional session TTL in milliseconds (defaults to 24 hours).
  Returns a SessionStore implementation backed by an atom."
  ([]
   (create-session-store (* 24 60 60 1000)))
  ([session-ttl-ms]
   (->InMemorySessionStore (atom {}) session-ttl-ms)))
