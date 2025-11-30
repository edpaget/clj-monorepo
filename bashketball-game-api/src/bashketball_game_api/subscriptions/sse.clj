(ns bashketball-game-api.subscriptions.sse
  "Server-Sent Events handler for GraphQL subscriptions.

  Provides Ring handlers that stream messages from core.async channels
  as SSE events. Handles authentication, connection lifecycle, and
  proper HTTP streaming."
  (:require [bashketball-game-api.subscriptions.core :as subs]
            [cheshire.core :as json]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [ring.util.io :as ring-io])
  (:import [java.io OutputStream]))

(def ^:private sse-headers
  "Standard headers for SSE responses."
  {"Content-Type"      "text/event-stream"
   "Cache-Control"     "no-cache, no-store, must-revalidate"
   "Connection"        "keep-alive"
   "X-Accel-Buffering" "no"})

(defn- write-sse-event
  "Writes a single SSE event to the output stream."
  [^OutputStream out event-type data]
  (let [event-str (str "event: " (name event-type) "\n"
                       "data: " (json/generate-string data) "\n\n")]
    (.write out (.getBytes event-str "UTF-8"))
    (.flush out)))

(defn- write-sse-keepalive
  "Writes an SSE comment as a keepalive."
  [^OutputStream out]
  (.write out (.getBytes ": keepalive\n\n" "UTF-8"))
  (.flush out))

(defn sse-response
  "Creates a Ring streaming response from a core.async channel.

  The channel should emit maps with `:type` and `:data` keys.
  Automatically sends keepalive comments every 15 seconds.
  Closes cleanly when the channel closes or client disconnects."
  [channel & {:keys [cors-origin] :or {cors-origin "*"}}]
  {:status  200
   :headers (merge sse-headers
                   {"Access-Control-Allow-Origin"      cors-origin
                    "Access-Control-Allow-Credentials" "true"})
   :body    (ring-io/piped-input-stream
             (fn [out]
               (let [keepalive-ch        (async/chan)
                     _keepalive-interval (async/go-loop []
                                           (async/<! (async/timeout 15000))
                                           (when (async/>! keepalive-ch :keepalive)
                                             (recur)))]
                 (try
                   (loop []
                     (let [[val _port] (async/alts!! [channel keepalive-ch]
                                                     :priority true)]
                       (cond
                         (nil? val)
                         (log/debug "SSE channel closed, ending stream")

                         (= val :keepalive)
                         (do
                           (write-sse-keepalive out)
                           (recur))

                         :else
                         (do
                           (write-sse-event out
                                            (get val :type :message)
                                            (get val :data val))
                           (recur)))))
                   (catch Exception e
                     (log/debug e "SSE stream error (likely client disconnect)"))
                   (finally
                     (async/close! keepalive-ch)
                     (async/close! channel))))))})

(defn- authenticated?
  "Checks if request is authenticated."
  [request]
  (get request :authn/authenticated?))

(defn- get-user-id
  "Extracts user ID from authenticated request."
  [request]
  (when-let [id (get request :authn/user-id)]
    (parse-uuid id)))

(defn- json-error-response
  "Creates a JSON error response."
  [status message]
  {:status  status
   :headers {"Content-Type" "application/json"}
   :body    (json/generate-string {:error message})})

(defn game-subscription-handler
  "Ring handler for game subscription endpoint.

  GET /subscriptions/game/:game-id

  Subscribes to real-time updates for a specific game. Requires authentication.
  Returns SSE stream with game state updates."
  [subscription-manager]
  (fn [{:keys [path-params] :as request}]
    (if-not (authenticated? request)
      (json-error-response 401 "Authentication required")

      (let [game-id (some-> (:game-id path-params) parse-uuid)]
        (if-not game-id
          (json-error-response 400 "Invalid game ID")

          (let [user-id (get-user-id request)
                channel (subs/subscribe! subscription-manager [:game game-id])]
            (log/info "User" user-id "subscribed to game" game-id)
            (async/put! channel {:type :connected
                                 :data {:game-id (str game-id)
                                        :user-id (str user-id)}})
            (sse-response channel)))))))

(defn lobby-subscription-handler
  "Ring handler for lobby subscription endpoint.

  GET /subscriptions/lobby

  Subscribes to lobby updates (new games available, games filled, etc.).
  Requires authentication."
  [subscription-manager]
  (fn [request]
    (if-not (authenticated? request)
      (json-error-response 401 "Authentication required")

      (let [user-id (get-user-id request)
            channel (subs/subscribe! subscription-manager [:lobby])]
        (log/info "User" user-id "subscribed to lobby")
        (async/put! channel {:type :connected
                             :data {:user-id (str user-id)}})
        (sse-response channel)))))

(defn wrap-subscription-routes
  "Middleware that handles subscription routes before other middleware.

  Must be placed early in the middleware stack (after auth but before JSON body
  parsing) to avoid response body transformation."
  [handler subscription-manager]
  (fn [request]
    (let [uri    (:uri request)
          method (:request-method request)]
      (cond
        ;; Game subscription
        (and (= method :get)
             (re-matches #"/subscriptions/game/([^/]+)" uri))
        (let [game-id (second (re-matches #"/subscriptions/game/([^/]+)" uri))]
          ((game-subscription-handler subscription-manager)
           (assoc-in request [:path-params :game-id] game-id)))

        ;; Lobby subscription
        (and (= method :get) (= uri "/subscriptions/lobby"))
        ((lobby-subscription-handler subscription-manager) request)

        ;; SSE OPTIONS preflight
        (and (= method :options)
             (re-matches #"/subscriptions/.*" uri))
        {:status  204
         :headers {"Access-Control-Allow-Origin"  "*"
                   "Access-Control-Allow-Methods" "GET, OPTIONS"
                   "Access-Control-Allow-Headers" "Content-Type, Authorization"
                   "Access-Control-Max-Age"       "86400"}}

        ;; Pass through to next handler
        :else
        (handler request)))))
