(ns db.core
  "Core functionality for db"
  (:require
   [clojure.tools.logging :as log]
   [db.connection-pool :as db.pool]
   [db.jdbc-ext]
   [honey.sql :as sql]
   [integrant.core :as ig]
   [next.jdbc :as jdbc]
   [next.jdbc.date-time]
   [next.jdbc.protocols]
   [next.jdbc.result-set :as rs]))

(def ^:dynamic *debug*
  "Dynamic var for emitting debug information for database queries."
  false)

(defmacro ^:private debug
  [& body]
  `(let [result# ((fn [] ~@body))]
     (when *debug*
       (log/debug result#))
     result#))

(def ^:dynamic *datasource*
  "Dynamic var holding the application's datasource (connection pool).
   Must be bound before calling query functions without an explicit connectable."
  nil)

(def ^:dynamic *current-connection*
  "Dynamic var holding the current active JDBC connection, if any.
   Used by `with-connection` and query functions to manage transactions
   or reuse a connection."
  nil)

(defn- compile-honeysql
  "Compiles a HoneySQL map into a JDBC-compatible vector [sql-string params...].

   Takes either a HoneySQL map or a pre-compiled [sql params...] vector. When given
   a map, formats it into a SQL string with parameters. When given a vector, returns
   it unchanged. Returns a vector of [sql-string param1 param2 ...] suitable for JDBC
   execution."
  [sql-map-or-vec]
  (debug
   (if (map? sql-map-or-vec)
     (sql/format sql-map-or-vec)
     sql-map-or-vec)))

(defn do-with-connection
  "Internal helper that executes a function within a connection context.

   When `*current-connection*` is already bound and non-nil, executes the function
   directly using that connection. Otherwise, if `*datasource*` is bound and non-nil,
   obtains a new connection from the datasource, binds it to `*current-connection*`,
   executes the function, and closes the connection afterwards.

   The function `f` receives one argument (the connection) and its return value is
   returned from this function. Throws IllegalStateException when neither a current
   connection nor a datasource is available."
  [f]
  (cond
    *current-connection*
    ;; pass the current connection to the f explicitly to allow it to be used
    ;; with other JBDC interfaces
    (f *current-connection*) ; Use existing connection
    *datasource*
    (with-open [conn (jdbc/get-connection *datasource*)]
      (binding [*current-connection* conn] ; Bind connection for f
        ;; pass the current connection to the f explicitly to allow it to be used
        ;; with other JBDC interfaces
        (f *current-connection*))) ; Execute with new connection
    :else
    (throw (IllegalStateException. "No active connection (*current-connection*) or datasource (*datasource*) available."))))

(def ^:private default-next-jdbc-opts {:builder-fn rs/as-unqualified-kebab-maps})

(defprotocol Connectable
  (connectable? [this]))

(extend-protocol Connectable
  java.sql.Connection
  (connectable? [_] true)
  javax.sql.DataSource
  (connectable? [_] true)
  Object
  (connectable? [_] false)
  nil
  (connectable? [_] false))

(defn- call-jdbc-fn
  "Helper that calls a next.jdbc function while managing connections.

   Takes a next.jdbc function (like `jdbc/execute!` or `jdbc/execute-one!`) and
   flexible arguments that allow SQL to be passed with or without an explicit
   connectable. When `connectable-or-sql` is a connectable (datasource or connection),
   treats `sql-or-opts` as the SQL and `opts` as options. Otherwise, treats
   `connectable-or-sql` as SQL and `sql-or-opts` as options, obtaining a connection
   from dynamic vars.

   Compiles HoneySQL maps into SQL vectors and merges in default options. Returns
   the result of calling the jdbc function with the managed connection and compiled SQL."
  [jdbc-fn connectable-or-sql sql-or-opts & [opts]]
  (if (connectable? connectable-or-sql)
    (let [sql-vec (compile-honeysql sql-or-opts)
          opts*   (merge default-next-jdbc-opts opts)]
      (jdbc-fn connectable-or-sql sql-vec opts*))
    ;; No explicit connectable, rely on dynamic vars
    (let [sql-vec        (compile-honeysql connectable-or-sql)
          effective-opts (merge default-next-jdbc-opts
                                (if-not (nil? sql-or-opts) sql-or-opts opts))]
      (if *current-connection*
        ;; Use existing bound connection
        (jdbc-fn *current-connection* sql-vec effective-opts)
        ;; No existing connection, use do-with-connection to get one
        (do-with-connection
         (fn [conn]
           (jdbc-fn conn sql-vec effective-opts)))))))

;; Define the public query functions

(defn plan
  "Executes a query that returns a reducible collection of rows.

   Can be called with just SQL (using `*datasource*` for the connection), with
   an explicit connectable and SQL, or with a connectable, SQL, and options map.
   The SQL can be either a HoneySQL map or a standard [sql params...] vector.

   The reducible result allows efficient processing of large result sets without
   loading all rows into memory at once. Returns a reducible collection of row maps."
  ([sql]
   (call-jdbc-fn jdbc/plan sql nil))
  ([connectable-or-sql sql-or-opts]
   (call-jdbc-fn jdbc/plan connectable-or-sql sql-or-opts))
  ([connectable-or-sql sql-or-opts opts]
   (call-jdbc-fn jdbc/plan connectable-or-sql sql-or-opts opts)))

(defn execute!
  "Executes SQL commands like DDL, INSERT, UPDATE, or DELETE.

   Can be called with just SQL (using `*datasource*` for the connection), with
   an explicit connectable and SQL, or with a connectable, SQL, and options map.
   The SQL can be either a HoneySQL map or a standard [sql params...] vector.

   Returns a vector of update count integers indicating how many rows were affected
   by each statement."
  ([sql]
   (call-jdbc-fn jdbc/execute! sql nil))
  ([connectable-or-sql sql-or-opts]
   (call-jdbc-fn jdbc/execute! connectable-or-sql sql-or-opts))
  ([connectable-or-sql sql-or-opts opts]
   (call-jdbc-fn jdbc/execute! connectable-or-sql sql-or-opts opts)))

(defn execute-one!
  "Executes SQL (typically SELECT) that returns a single row.

   Can be called with just SQL (using `*datasource*` for the connection), with
   an explicit connectable and SQL, or with a connectable, SQL, and options map.
   The SQL can be either a HoneySQL map or a standard [sql params...] vector.

   Returns a single row map, or nil if the query produces no results."
  ([sql]
   (call-jdbc-fn jdbc/execute-one! sql nil))
  ([connectable-or-sql sql-or-opts]
   (call-jdbc-fn jdbc/execute-one! connectable-or-sql sql-or-opts))
  ([connectable-or-sql sql-or-opts opts]
   (call-jdbc-fn jdbc/execute-one! connectable-or-sql sql-or-opts opts)))

(defmacro with-connection
  "Ensures database operations within the body run within a connection context.
   If *current-connection* is already bound, uses it. Otherwise, obtains a
   new connection from *datasource*, binds it to *current-connection*,
   and executes the body. The new connection is closed afterwards."
  [[conn] & body]
  `(do-with-connection (fn [~conn] ~@body)))

(defmethod ig/init-key ::pool [_ {:keys [config]}]
  (db.pool/create-pool (:database-url config) (:c3p0-opts config)))

(defmethod ig/halt-key! ::pool [_ datasource]
  (db.pool/close-pool! datasource))

(defmacro with-debug
  "Set the debug dynamic var for any queries executed within the macro body"
  [& body]
  `(binding [*debug* true]
     ~@body))

(defn do-with-transaction
  "Implementation for the with-transaction macro.

   Takes an optional connectable and a thunk (function of one argument). When a
   connectable is provided, opens a transaction on it. When nil, uses the current
   connection or obtains one from the datasource. The thunk receives the transaction
   connection as its argument and its return value is returned from this function."
  [connectable-or-nil thunk]
  (if connectable-or-nil
    (next.jdbc/with-transaction [tx connectable-or-nil]
      (binding [*current-connection* tx]
        (thunk tx)))
    (do-with-connection (fn [conn]
                          (binding [*current-connection* conn]
                            (next.jdbc/with-transaction [tx conn]
                              (thunk tx)))))))

(defmacro with-transaction
  "Open a transaction from the supplied connection, *current-connection*, or a
  new connection from *datasource*."
  [[bind & [maybe-connectable]] & body]
  `(do-with-transaction ~maybe-connectable (fn [~bind] ~@body)))
