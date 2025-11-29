(ns user
  "Development REPL utilities.

  Provides functions for starting, stopping, and restarting the system
  from the REPL."
  (:require
   [bashketball-game-api.config :as config]
   [bashketball-game-api.system :as system]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [clojure.tools.namespace.repl :as repl]
   [db.connection-pool :as pool]
   [db.core :as db]
   [db.migrate :as migrate]
   [db.test-utils :as db.test]))

(defonce ^:private system-state (atom nil))

(defn start
  "Starts the development system.

  Optionally takes a profile keyword (defaults to :dev)."
  ([]
   (start :dev))
  ([profile]
   (when @system-state
     (println "System already running. Call (stop) first or use (restart)."))
   (when-not @system-state
     (println "Starting system with profile:" profile)
     (reset! system-state (system/start-system profile))
     (println "System started successfully.")
     :started)))

(defn stop
  "Stops the running system."
  []
  (if @system-state
    (do
      (println "Stopping system...")
      (system/stop-system @system-state)
      (reset! system-state nil)
      (println "System stopped.")
      :stopped)
    (do
      (println "System not running.")
      :not-running)))

(defn restart
  "Restarts the system.

  Optionally takes a profile keyword (defaults to :dev)."
  ([]
   (restart :dev))
  ([profile]
   (stop)
   (start profile)))

(defn reset
  "Stops the system, reloads all changed namespaces, and restarts.

  Uses clojure.tools.namespace to refresh changed code before restarting."
  []
  (stop)
  (repl/refresh :after 'user/start))

(defn system
  "Returns the currently running system, or nil if not started."
  []
  @system-state)

;; --- Database Utilities ---

(defn- split-db-name-from-uri
  "Splits the database name from JDBC URL.

  Returns tuple of [base-url db-name]."
  [db-url]
  (let [path    (.getPath (java.net.URI. (str/replace-first db-url #"^jdbc:" "")))
        db-name (when (and path (> (count path) 1)) (subs path 1))]
    (when-not db-name
      (throw (ex-info "Could not parse database name from JDBC URL" {:url db-url})))
    (let [base-url (-> (subs db-url 0 (inc (str/last-index-of db-url "/")))
                       (str (subs db-url (+ (count db-name) (str/index-of db-url db-name)))))]
      [base-url db-name])))

(defn drop-database!
  "Drops the database specified in the configuration.

  Optionally takes a profile keyword (defaults to :dev). Stops the running
  system if it exists before dropping the database.

  WARNING: This will permanently delete all data in the database!"
  ([]
   (drop-database! :dev))
  ([profile]
   (when @system-state
     (println "Stopping system before dropping database...")
     (stop))
   (let [conf               (config/load-config profile)
         db-url             (get-in conf [:database :database-url])
         [base-url db-name] (split-db-name-from-uri db-url)
         datasource         (pool/create-pool base-url)
         sql                (format "DROP DATABASE IF EXISTS \"%s\"" db-name)]
     (try
       (println "Dropping database:" db-name)
       (db/execute! datasource [sql])
       (println "Database" db-name "dropped successfully.")
       :dropped
       (catch Exception e
         (log/error e "Failed to drop database" db-name)
         (throw (ex-info "Failed to drop database"
                         {:database db-name
                          :profile  profile}
                         e)))
       (finally
         (pool/close-pool! datasource))))))

(defn create-database!
  "Creates the database specified in the configuration.

  Optionally takes a profile keyword (defaults to :dev). Safe to call
  multiple times as it gracefully handles 'database already exists' errors."
  ([]
   (create-database! :dev))
  ([profile]
   (let [conf   (config/load-config profile)
         db-url (get-in conf [:database :database-url])]
     (println "Creating database for profile:" profile)
     (db.test/ensure-database-exists! db-url)
     :created)))

(defn recreate-database!
  "Drops and recreates the database, then runs migrations.

  Optionally takes a profile keyword (defaults to :dev). This will:
  1. Stop the running system (if any)
  2. Drop the existing database
  3. Create a new database
  4. Run all migrations

  WARNING: This will permanently delete all data in the database!"
  ([]
   (recreate-database! :dev))
  ([profile]
   (drop-database! profile)
   (create-database! profile)
   (let [conf       (config/load-config profile)
         db-url     (get-in conf [:database :database-url])
         datasource (pool/create-pool db-url)]
     (try
       (println "Running migrations...")
       (binding [db/*datasource* datasource]
         (migrate/migrate))
       (println "Database recreated and migrated successfully.")
       :recreated
       (finally
         (pool/close-pool! datasource))))))

(comment
  ;; Start the system
  (start)
  (start :dev)

  ;; Stop the system
  (stop)

  ;; Restart the system
  (restart)

  ;; Reload namespaces and restart
  (reset)

  ;; Get the running system
  (system)

  ;; Access specific components
  (::system/config (system))
  (::system/db-pool (system))
  (::system/handler (system))

  ;; Database operations
  (drop-database!)          ;; Drop dev database
  (drop-database! :dev)     ;; Drop dev database
  (drop-database! :test)    ;; Drop test database

  (create-database!)        ;; Create dev database
  (create-database! :dev)   ;; Create dev database
  (create-database! :test)  ;; Create test database

  (recreate-database!)      ;; Drop, create, and migrate dev database
  (recreate-database! :dev) ;; Drop, create, and migrate dev database

  ;; Full reset workflow
  (do
    (recreate-database!)
    (start)))
