(ns bashketball-game-api.handlers.avatar
  "HTTP handler for serving cached user avatars.

  Provides an endpoint to serve avatar images stored in the database,
  with proper HTTP caching headers (ETag, Cache-Control)."
  (:require
   [bashketball-game-api.services.avatar :as avatar-svc]
   [ring.util.response :as response])
  (:import
   [java.io ByteArrayInputStream]))

(defn avatar-handler
  "Ring handler that serves a user's avatar image.

  Expects the request to have:
  - `:avatar-service` - The avatar service instance
  - `:path-params` with `:user-id` - The user's UUID

  Response behavior:
  - 200 OK with image data if avatar exists
  - 304 Not Modified if client's If-None-Match matches ETag
  - 404 Not Found if no avatar exists for the user

  Includes caching headers:
  - `ETag` for conditional requests
  - `Cache-Control: public, max-age=86400` (24 hours)"
  [{:keys [avatar-service path-params headers]}]
  (let [user-id-str (:user-id path-params)
        user-id     (try (parse-uuid user-id-str)
                         (catch Exception _ nil))]
    (if-not user-id
      (-> (response/response "Invalid user ID")
          (response/status 400))
      (if-let [{:keys [data content-type etag]} (avatar-svc/get-user-avatar avatar-service user-id)]
        (let [client-etag (get headers "if-none-match")]
          (if (and etag (= client-etag etag))
            {:status 304
             :headers {"ETag" etag}}
            {:status 200
             :headers (cond-> {"Content-Type"  content-type
                               "Cache-Control" "public, max-age=86400"}
                        etag (assoc "ETag" etag))
             :body (ByteArrayInputStream. data)}))
        (-> (response/response nil)
            (response/status 404))))))

(defn wrap-avatar-service
  "Middleware that attaches the avatar service to requests."
  [handler avatar-service]
  (fn [request]
    (handler (assoc request :avatar-service avatar-service))))
