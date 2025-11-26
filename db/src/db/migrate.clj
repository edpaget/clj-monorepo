(ns db.migrate
  "Database migration utilities using Ragtime."
  (:require
   [clojure.tools.logging :as log]
   [db.core :as db]
   [ragtime.next-jdbc :as next-jdbc]
   [ragtime.repl :as repl]))

(def ^:private ^:dynamic *ragtime-config* nil)

(def ^:dynamic *migrations-path*
  "Resource path for migrations. Defaults to `\"migrations\"`. Bind this to use
   a different path, e.g., `\"bashketball-editor-api/migrations\"`."
  "migrations")

(defn do-with-config
  "Executes a function with Ragtime configuration bound to `*ragtime-config*`.

   Takes a thunk (function of zero arguments) and executes it with a Ragtime
   configuration that uses `db/*datasource*` and loads migrations from the
   path specified by [[*migrations-path*]]. Returns the return value of the
   thunk, or nil if an error occurs (errors are logged)."
  [thunk]
  (try
    (binding [*ragtime-config* {:datastore  (next-jdbc/sql-database db/*datasource*)
                                :migrations (next-jdbc/load-resources *migrations-path*)}]
      (thunk))
    (catch Throwable e
      (log/error e "Migration failed with error"))))

(defmacro with-config
  "Execute body with Ragtime configuration bound to *ragtime-config*."
  [& body]
  `(do-with-config (fn [] ~@body)))

;; --- Migration Functions ---

(defn migrate
  "Applies all pending migrations.

   When called with no arguments, uses the default configuration from
   `*datasource*` and the `migrations` resource directory. When called with
   a Ragtime config map, uses that configuration instead. Returns nil."
  ([]
   (with-config
     (repl/migrate *ragtime-config*)))
  ([config]
   (repl/migrate config)))

(defn rollback
  "Rolls back the last applied migration.

   When called with no arguments, uses the default configuration from
   `*datasource*` and the `migrations` resource directory. When called with
   a Ragtime config map, uses that configuration instead. Returns nil."
  ([]
   (with-config
     (repl/rollback *ragtime-config*)))
  ([config]
   (repl/rollback config)))
