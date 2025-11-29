(ns bashketball-game-api.test-utils
  "Testing utilities and fixtures.

  Provides database setup, teardown, and common test helpers."
  (:require
   [bashketball-game-api.models.deck :as deck]
   [bashketball-game-api.models.protocol :as proto]
   [bashketball-game-api.models.user :as user]
   [bashketball-game-api.system :as system]
   [db.core :as db]
   [db.jdbc-ext :as jdbc-ext]
   [db.migrate :as migrate]))

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
