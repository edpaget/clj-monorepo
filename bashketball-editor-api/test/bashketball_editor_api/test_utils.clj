(ns bashketball-editor-api.test-utils
  "Testing utilities and fixtures.

  Provides database setup, teardown, and common test helpers."
  (:require
   [bashketball-editor-api.system :as system]
   [clojure.test :refer [use-fixtures]]
   [db.core :as db]
   [db.migrate :as migrate]
   [integrant.core :as ig]))

(def ^:dynamic *system* nil)

(defn start-test-system!
  "Starts a test system with test configuration."
  []
  (let [sys (system/start-system :test)]
    (binding [db/*datasource* (::system/db-pool sys)]
      (migrate/migrate! "migrations"))
    sys))

(defn stop-test-system!
  "Stops the test system."
  [sys]
  (system/stop-system sys))

(defn with-system
  "Fixture that starts and stops the test system around tests."
  [f]
  (let [sys (start-test-system!)]
    (binding [*system* sys]
      (try
        (f)
        (finally
          (stop-test-system! sys))))))

(defn with-clean-db
  "Fixture that cleans the database before each test."
  [f]
  (binding [db/*datasource* (::system/db-pool *system*)]
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
  ([github-login]
   (with-db
     (let [user-repo (bashketball-editor-api.models.user/create-user-repository)
           user-data {:github-login github-login
                      :email (str github-login "@example.com")
                      :avatar-url (str "https://github.com/" github-login ".png")
                      :name (str "Test " github-login)}]
       (bashketball-editor-api.models.protocol/create! user-repo user-data)))))
