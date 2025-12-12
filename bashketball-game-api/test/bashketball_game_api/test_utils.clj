(ns bashketball-game-api.test-utils
  "Testing utilities and fixtures.

  Provides database setup, teardown, and common test helpers."
  (:require
   [authn.protocol :as authn-proto]
   [bashketball-game-api.models.deck :as deck]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.system :as system]
   [bashketball-game-api.test-fixtures.cards :as test-cards]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as str]
   [clojure.test :refer [is]]
   [db.core :as db]
   [db.jdbc-ext :as jdbc-ext]
   [db.migrate :as migrate]
   [hato.client :as hato]
   [ring.middleware.session.cookie :refer [cookie-store]])
  (:import [java.io BufferedReader InputStreamReader]))

(def ^:dynamic *system*
  "Dynamic var holding the current test system."
  nil)

(defn start-test-system!
  "Starts a test system with test configuration.

  By default excludes the HTTP server to avoid port conflicts and uses a
  mock card catalog instead of the real one from the cards JAR.

  Options:
  - `:include-server?` - Start the HTTP server on an auto-selected port
  - `:use-real-catalog?` - Use the real card catalog from the JAR (default: false)"
  ([]
   (start-test-system! {}))
  ([{:keys [include-server? use-real-catalog?]}]
   (let [opts (cond-> {:card-catalog (when-not use-real-catalog?
                                       test-cards/mock-card-catalog)}
                (not include-server?) (assoc :exclude-keys #{::system/server})
                include-server? (assoc :port 0))
         sys  (system/start-system :test opts)]
     (binding [db/*datasource* (::system/db-pool sys)]
       (migrate/migrate)
       (jdbc-ext/refresh-enum-cache! db/*datasource*))
     sys)))

(defn stop-test-system!
  "Stops the test system."
  [sys]
  (system/stop-system sys))

(defn with-system
  "Fixture that starts and stops the test system around tests.
  Does not start the HTTP server by default."
  [f]
  (let [sys (start-test-system!)]
    (binding [*system* sys]
      (try
        (f)
        (finally
          (stop-test-system! sys))))))

(defn with-server
  "Fixture that starts and stops the test system with HTTP server.
  Uses port 0 to auto-select an available port."
  [f]
  (let [sys (start-test-system! {:include-server? true})]
    (binding [*system* sys]
      (try
        (f)
        (finally
          (stop-test-system! sys))))))

(defn server-port
  "Returns the port the test server is running on.
  Only valid when system was started with `{:include-server? true}`."
  []
  (when-let [server (::system/server *system*)]
    (-> server .getConnectors first .getLocalPort)))

(defn with-clean-db
  "Fixture that cleans the database before each test."
  [f]
  (binding [db/*datasource* (::system/db-pool *system*)]
    (db/execute! ["DELETE FROM game_events"])
    (db/execute! ["DELETE FROM games"])
    (db/execute! ["DELETE FROM claimed_starter_decks"])
    (db/execute! ["DELETE FROM decks"])
    (db/execute! ["DELETE FROM sessions"])
    (db/execute! ["DELETE FROM user_avatars"])
    (db/execute! ["DELETE FROM users"])
    (f)))

(defmacro with-db
  "Executes body with database datasource bound."
  [& body]
  `(binding [db/*datasource* (::system/db-pool *system*)]
     ~@body))

(defn create-test-user
  "Creates a test user in the database using the user repository."
  ([]
   (create-test-user "testuser"))
  ([google-id]
   (with-db
     (let [user-repo (user/create-user-repository)
           user-data {:google-id google-id
                      :email (str google-id "@example.com")
                      :avatar-url (str "https://example.com/" google-id ".png")
                      :name (str "Test " google-id)}]
       (proto/create! user-repo user-data)))))

(defn create-test-deck
  "Creates a test deck in the database."
  ([user-id]
   (create-test-deck user-id "Test Deck"))
  ([user-id deck-name]
   (create-test-deck user-id deck-name []))
  ([user-id deck-name card-slugs]
   (with-db
     (let [deck-repo (deck/create-deck-repository)]
       (proto/create! deck-repo {:user-id user-id
                                 :name deck-name
                                 :card-slugs card-slugs})))))

(def valid-deck-cards
  "Card slugs that make up a valid deck (3 players + 30 action cards)."
  (vec (concat
        ;; 3 player cards
        ["michael-jordan" "shaq" "mugsy-bogues"]
        ;; 30 action cards (using 4 copies of each to stay under limit)
        (mapcat #(repeat 4 %)
                ["basic-shot" "jump-shot" "layup" "drive-and-dish"
                 "post-up" "pick-and-roll" "alley-oop" "fast-break"]))))

(defn create-valid-test-deck
  "Creates a test deck with valid cards that passes validation."
  ([user-id]
   (create-valid-test-deck user-id "Valid Deck"))
  ([user-id deck-name]
   (with-db
     (let [deck-repo (deck/create-deck-repository)
           deck      (proto/create! deck-repo {:user-id    user-id
                                               :name       deck-name
                                               :card-slugs valid-deck-cards
                                               :is-valid   true})]
       deck))))

;; ---------------------------------------------------------------------------
;; Authentication Helpers

(defn create-authenticated-session!
  "Creates an authenticated session in the database for the given user.

  Uses the session-repo from the test system to create a session that will
  be recognized by the authentication middleware. Returns the session-id
  that can be used to create a session cookie.

  Options:
  - `:claims` - Map of claims to store with the session. Should include
                :email, :name, and :picture for the me resolver to work.
  - `:user` - User map to use for default claims (uses :email, :name, :avatar-url)"
  [user-id & {:keys [claims user]}]
  (with-db
    (let [session-repo   (::system/session-repo *system*)
          default-claims (when user
                           {:email (:email user)
                            :name (:name user)
                            :picture (:avatar-url user)})
          final-claims   (merge default-claims claims)]
      (authn-proto/create-session session-repo (str user-id) final-claims))))

(def ^:private test-cookie-secret
  "The cookie secret used in test configuration (must be 16 bytes)."
  "dev-secret-16byt")

(defn- encode-session-cookie
  "Encodes a session map into a Ring cookie-store cookie value.

  Uses the same cookie-store mechanism as the application, with the
  test cookie secret."
  [session-data]
  (let [store       (cookie-store {:key (.getBytes test-cookie-secret)})
        session-key (.write-session store nil session-data)]
    session-key))

(defn- url-encode-cookie
  "URL-encodes special characters in a cookie value.

  Ring's cookie parser URL-decodes cookie values, so we need to encode
  characters like + and = that appear in Base64 strings."
  [s]
  (-> s
      (str/replace "+" "%2B")
      (str/replace "=" "%3D")))

(defn create-session-cookie
  "Creates a Ring session cookie value for the given session-id.

  This cookie can be passed to HTTP requests to authenticate as the user
  associated with the session. The value is URL-encoded to handle special
  characters in the Base64-encoded session data."
  [session-id]
  (url-encode-cookie (encode-session-cookie {:authn/session-id session-id})))

;; ---------------------------------------------------------------------------
;; GraphQL Request Helpers

(defn graphql-url
  "Returns the GraphQL endpoint URL for the test server."
  []
  (str "http://localhost:" (server-port) "/graphql"))

(defn graphql-request
  "Executes a GraphQL request against the test server.

  Takes a query string and optional keyword arguments:
  - `:variables` - Map of GraphQL variables
  - `:operation-name` - Name of the operation to execute
  - `:session-id` - Session ID for authentication (creates session cookie)

  Returns the parsed JSON response body."
  [query & {:keys [variables operation-name session-id]}]
  (let [body         (cond-> {:query query}
                       variables (assoc :variables variables)
                       operation-name (assoc :operationName operation-name))
        cookie-value (when session-id
                       (create-session-cookie session-id))
        request-opts (cond-> {:body (json/generate-string body)
                              :content-type :json
                              :accept :json
                              :as :json
                              :coerce :always
                              :throw-exceptions false}
                       cookie-value
                       (assoc :headers {"Cookie" (str "bashketball-game-session=" cookie-value)}))]
    (:body (http/post (graphql-url) request-opts))))

;; ---------------------------------------------------------------------------
;; GraphQL Response Assertion Helpers

(defn graphql-data
  "Extracts the data from a GraphQL response, asserting no errors.

  If errors are present, fails the test with the error details."
  [response]
  (is (nil? (:errors response))
      (str "Unexpected GraphQL errors: " (pr-str (:errors response))))
  (:data response))

(defn graphql-errors
  "Extracts errors from a GraphQL response."
  [response]
  (:errors response))

(defn has-error-message?
  "Returns true if any error in the response contains the given message substring."
  [response message]
  (some #(str/includes? (get % :message "") message)
        (:errors response)))

;; ---------------------------------------------------------------------------
;; SSE Subscription Helpers

(defn subscription-url
  "Returns the subscription endpoint URL for the test server with the given query."
  [query]
  (str "http://localhost:" (server-port) "/graphql/subscriptions?query="
       (java.net.URLEncoder/encode query "UTF-8")))

(defn read-sse-event
  "Reads a single SSE event from a BufferedReader, returning a map with :event and :data.

  Skips comment-only blocks (keepalives) and returns the first real event.
  The :data value is parsed as JSON if possible."
  [^BufferedReader reader]
  (loop [event nil
         data  nil]
    (let [line (.readLine reader)]
      (cond
        (nil? line) nil
        ;; Blank line ends an event block - but only return if we have content
        (str/blank? line) (if (or event data)
                            {:event event
                             :data  (when data
                                      (try (json/parse-string data true)
                                           (catch Exception _ data)))}
                            (recur nil nil))
        (str/starts-with? line "event: ") (recur (subs line 7) data)
        (str/starts-with? line "data: ") (recur event (subs line 6))
        ;; Comments are skipped
        (str/starts-with? line ":") (recur event data)
        :else (recur event data)))))

(defn sse-request
  "Makes an SSE subscription request and returns the response with an open stream.

  Takes a GraphQL subscription query and session-id for authentication.
  Returns a map with :status, :headers, :body (InputStream), and :reader (BufferedReader).

  The caller is responsible for closing the reader when done."
  [query session-id]
  (let [cookie-value (create-session-cookie session-id)
        response     (hato/get (subscription-url query)
                               {:as      :stream
                                :timeout 10000
                                :headers {"Cookie" (str "bashketball-game-session=" cookie-value)}})
        reader       (BufferedReader. (InputStreamReader. (:body response) "UTF-8"))]
    (assoc response :reader reader)))
