(ns bashketball-game-api.test-utils
  "Testing utilities and fixtures.

  Provides database setup, teardown, and common test helpers."
  (:require
   [authn.protocol :as authn-proto]
   [bashketball-game-api.models.deck :as deck]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.system :as system]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as str]
   [clojure.test :refer [is]]
   [db.core :as db]
   [db.jdbc-ext :as jdbc-ext]
   [db.migrate :as migrate]
   [ring.middleware.session.cookie :refer [cookie-store]]))

(def ^:dynamic *system*
  "Dynamic var holding the current test system."
  nil)

(defn start-test-system!
  "Starts a test system with test configuration.

  By default excludes the HTTP server to avoid port conflicts.
  Pass `{:include-server? true}` to start the server on an auto-selected port."
  ([]
   (start-test-system! {}))
  ([{:keys [include-server?]}]
   (let [opts (if include-server?
                {:port 0}
                {:exclude-keys #{::system/server}})
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
    (db/execute! ["DELETE FROM decks"])
    (db/execute! ["DELETE FROM sessions"])
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
