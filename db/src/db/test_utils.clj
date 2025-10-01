(ns db.test-utils
  "Database test utilities providing fixtures and helpers for database testing."
  (:require
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.logging :as log]
   [db.connection-pool :as pool]
   [db.core :as db]
   [db.migrate :as migrate]
   [exclusive-initializer.core :as ei]
   [java-time.api :as t]
   [next.jdbc :as jdbc]
   [next.jdbc.transaction]
   [ragtime.next-jdbc :as ragtime.next-jdbc]))

(set! *warn-on-reflection* true)

;; --- Ragtime Configuration ---
;;
(defn- ragtime-config
  "Creates a Ragtime configuration map.

  Args:
    datasource: JDBC datasource
    migrations: Collection of migration definitions

  Returns:
    Map containing Ragtime configuration"
  [datasource migrations]
  {:datastore  (ragtime.next-jdbc/sql-database datasource)
   :migrations migrations})

;; --- Test Fixtures ---

(defn- split-db-name-from-uri
  "Splits the database name from JDBC URL.

  Args:
    db-url: JDBC URL string

  Returns:
    tuple of jdbc url without the database name and database name as string

  Raises:
    ExceptionInfo: If database name cannot be parsed from URL"
  [db-url]
  (let [path (.getPath (java.net.URI. (str/replace-first db-url #"^jdbc:" "")))
        db-name (when (and path (> (count path) 1)) (subs path 1))]

    (when-not db-name
      (throw (ex-info "Could not parse database name from JDBC URL" {:url db-url})))
    (let [base-url (-> (subs db-url 0 (inc (str/last-index-of db-url "/")))
                       (str (subs db-url (+ (count db-name) (str/index-of db-url db-name)))))]
      [base-url db-name])))

(defn- create-db-fixture
  [db-url]
  (fn [f]
    (ei/initialize! ::db
      (let [[base-url db-name] (split-db-name-from-uri db-url)
            datasource (pool/create-pool base-url)
            sql (format "CREATE DATABASE \"%s\"" db-name)]
        (log/info "Ensuring test database exists for URL:" db-url)
        (try
          (log/debug "Executing:" sql)
          (db/execute! datasource [sql])
          (log/info "Database" db-name "created successfully.")
          (catch java.sql.SQLException e
            ;; Check if the error is "database already exists" (PostgreSQL specific SQLState 42P04)
            (if (= "42P04" (.getSQLState e))
              (log/info "Database" db-name "already exists.")
              (do
                (log/error e "Failed to ensure database existence for" db-url)
                (throw (ex-info (str "Failed to ensure test database exists. Check URL, permissions, server status. Original error: " (ex-message e))
                                {:url db-url} e)))))
          (finally
            (pool/close-pool! datasource)))
        (log/info "Database" db-name "is ready.")))
    (f)))

(defn- migrate-db-fixture
  [migrations]
  (fn [f]
    (ei/initialize! ::db-migration
      (log/info "Running migrations...")
      (migrate/migrate (ragtime-config db/*datasource* migrations)))
    (f)))

(defn- pool-fixture
  [db-url]
  (fn [f]
    (let [datasource (pool/create-pool db-url)]
      (try
        (binding [db/*datasource* datasource] ; Bind dynamic var for tests using it
          (f))
        (finally
          (pool/close-pool! datasource))))))

(defn db-fixture
  "Creates a database fixture for test isolation.

  Ensures test database exists, runs migrations, and provides connection pooling.
  Uses exclusive-initializer to ensure setup runs only once across parallel tests.

  Args:
    config: Map with keys:
      :db-url - JDBC URL for test database
      :migrations - Collection of Ragtime migrations

  Returns:
    Test fixture function that accepts a test function"
  [{:keys [db-url migrations]}]
  (test/join-fixtures [(create-db-fixture db-url)
                       (pool-fixture db-url)
                       (migrate-db-fixture migrations)]))

(defn rollback-fixture
  "Transaction fixture that automatically rolls back all database changes.

  Wraps test execution in a transaction that is always rolled back,
  ensuring database state is restored after each test.

  Args:
    f: Test function to execute

  Raises:
    IllegalStateException: If no datasource is bound (db-fixture must run first)"
  [f]
  (if-let [ds db/*datasource*] ; Relies on db/*datasource* being bound by db-fixture
    (jdbc/with-transaction [tx ds {:rollback-only true}]
      (binding [db/*current-connection* tx                 ; Bind connection for tests using it
                next.jdbc.transaction/*nested-tx* :ignore] ; Set nested transaction to ignore to allow silently nested transactions
        (f)))
    (throw (IllegalStateException. "Test datasource is not initialized for transaction fixture. Ensure db-fixture runs first."))))

;; --- Helper Macros ---

(defn do-global-frozen-time
  "Internal helper for with-global-frozen-time."
  [f]
  (if-let [tx db/*current-connection*] ; Check if inside a transaction
    (let [ts-str (t/format :iso-offset-date-time (t/with-offset (t/offset-date-time) 0))] ; Ensure UTC
      (log/debug "Freezing transaction time to:" ts-str)
      ;; Set the custom session variable used by get_current_timestamp()
      (jdbc/execute! tx [(str "SET LOCAL vars.frozen_timestamp = '" ts-str "'")])
      (f)
      ;; Unset the custom session variable
      (jdbc/execute! tx ["SET LOCAL vars.frozen_timestamp = ''"])) ; Run the test code
    (throw (IllegalStateException. "freeze-time-fixture must run within a transaction (e.g., inside rollback-fixture)"))))

(defmacro with-global-frozen-time
  "Freezes time both in JVM and PostgreSQL transaction.

  Combines java-time.api/with-clock for JVM-level time freezing with
  PostgreSQL session variable setting for database-level time control.

  Args:
    time: Time instant to freeze to
    body: Code to execute with frozen time

  Raises:
    IllegalStateException: If not executed within a database transaction"
  [time & body]
  `(t/with-clock (t/mock-clock ~time)
     (do-global-frozen-time
      (fn [] ~@body))))
