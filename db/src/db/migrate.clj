(ns db.migrate
  "Database migration utilities using Ragtime."
  (:require
   [clojure.tools.logging :as log]
   [db.core :as db]
   [ragtime.next-jdbc :as next-jdbc]
   [ragtime.repl :as repl]))

(def ^:private ^:dynamic *ragtime-config* nil)

(defn do-with-config
  "Execute function with Ragtime configuration bound to *ragtime-config*.

  Args:
    thunk: Function of zero arguments to execute with config

  Returns:
    Return value of thunk, or nil if error occurs"
  [thunk]
  (try
    (binding [*ragtime-config* {:datastore  (next-jdbc/sql-database db/*datasource*)
                                :migrations (next-jdbc/load-resources "migrations")}]
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

  Args:
    config: the ragtime configuration to use (optional)

  Returns:
    nil"
  ([]
   (with-config
     (repl/migrate *ragtime-config*)))
  ([config]
   (repl/migrate config)))

(defn rollback
  "Rolls back the last applied migration.

  Args:
    config: the ragtime configuration to use (optional)

  Returns:
    nil"
  ([]
   (with-config
     (repl/rollback *ragtime-config*)))
  ([config]
   (repl/rollback config)))
