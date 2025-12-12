(ns bashketball-game-api.services.avatar
  "Avatar download and caching service.

  Downloads avatar images from external URLs (e.g., Google profile pictures)
  and stores them locally in the database to avoid rate limits on external
  avatar services."
  (:require
   [bashketball-game-api.models.avatar :as avatar-model]
   [clj-http.client :as http]
   [clojure.tools.logging :as log])
  (:import
   [java.security MessageDigest]
   [java.util Base64]))

(defn- bytes->etag
  "Generates an ETag from image bytes using MD5 hash."
  [^bytes data]
  (let [md     (MessageDigest/getInstance "MD5")
        digest (.digest md data)]
    (.encodeToString (Base64/getEncoder) digest)))

(defn fetch-image
  "Downloads an image from a URL.

  Returns a map with `:data` (byte array), `:content-type`, and `:etag` on
  success. Returns nil if the download fails or the URL is invalid."
  [url]
  (try
    (let [response (http/get url {:as               :byte-array
                                  :socket-timeout   5000
                                  :connection-timeout 5000
                                  :throw-exceptions false})]
      (when (= 200 (:status response))
        (let [data         (:body response)
              content-type (get-in response [:headers "Content-Type"] "image/jpeg")]
          {:data         data
           :content-type content-type
           :etag         (bytes->etag data)})))
    (catch Exception e
      (log/warn e "Failed to fetch image from" url)
      nil)))

(defn store-avatar!
  "Stores avatar data for a user.

  Takes the avatar repository, user ID, and avatar data map with `:data`,
  `:content-type`, and optionally `:etag`. Returns the stored avatar or nil
  on failure."
  [repo user-id {:keys [data content-type etag]}]
  (try
    (avatar-model/upsert! repo {:user-id      user-id
                                :data         data
                                :content-type content-type
                                :etag         etag})
    (catch Exception e
      (log/error e "Failed to store avatar for user" user-id)
      nil)))

(defn fetch-and-store!
  "Downloads an avatar from a URL and stores it for a user.

  Combines [[fetch-image]] and [[store-avatar!]] into a single operation.
  Returns the stored avatar on success, nil on failure. Logs errors but
  does not throw exceptions."
  [repo user-id url]
  (when-let [image-data (fetch-image url)]
    (store-avatar! repo user-id image-data)))

(defn get-avatar
  "Retrieves a cached avatar for a user.

  Returns a map with `:data`, `:content-type`, `:etag`, and `:fetched-at`
  if found, nil otherwise."
  [repo user-id]
  (avatar-model/get-avatar repo user-id))

(defprotocol AvatarService
  "Protocol for avatar service operations."

  (fetch-avatar-async! [this user-id url]
    "Asynchronously fetches and stores an avatar for a user.

    Returns immediately. The download happens in a background thread.
    Does not block the caller.")

  (get-user-avatar [this user-id]
    "Retrieves the cached avatar for a user.

    Returns a map with `:data`, `:content-type`, `:etag`, and `:fetched-at`
    if found, nil otherwise."))

(defrecord DatabaseAvatarService [avatar-repo]
  AvatarService
  (fetch-avatar-async! [_this user-id url]
    (future
      (try
        (fetch-and-store! avatar-repo user-id url)
        (catch Exception e
          (log/error e "Async avatar fetch failed for user" user-id)))))

  (get-user-avatar [_this user-id]
    (get-avatar avatar-repo user-id)))

(defn create-avatar-service
  "Creates a new avatar service instance.

  Takes an avatar repository for storage operations."
  [avatar-repo]
  (->DatabaseAvatarService avatar-repo))
