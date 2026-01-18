(ns clj-jobrunr.test-utils
  "Test utilities and fixtures for integration testing.

  Provides helpers for:
  - Starting/stopping JobRunr server in tests
  - Waiting for job completion
  - Inspecting job status
  - Classloader verification
  - Multimethod reset between tests
  - Testcontainers-based PostgreSQL fixtures"
  (:require
   [clj-jobrunr.classloader :as cl]
   [clj-jobrunr.integrant :as ig-jobrunr]
   [clj-jobrunr.job :refer [handle-job]]
   [clj-jobrunr.serialization :as ser]
   [clojure.test :as t]
   [integrant.core :as ig])
  (:import
   [com.zaxxer.hikari HikariConfig HikariDataSource]
   [org.jobrunr.jobs.states StateName]
   [org.jobrunr.storage JobNotFoundException]
   [org.testcontainers.containers PostgreSQLContainer]))

(def ^:dynamic *storage-provider*
  "Storage provider for the current test context."
  nil)

(def ^:dynamic *serializer*
  "Serializer for the current test context."
  nil)

(defn job-status
  "Returns the status of a job by its ID.

  Returns one of: :enqueued, :scheduled, :processing, :succeeded, :failed, :deleted
  Throws if job not found."
  [storage-provider job-id]
  (try
    (let [job        (.getJobById storage-provider job-id)
          state-name (.getState job)]
      (condp = state-name
        StateName/ENQUEUED :enqueued
        StateName/SCHEDULED :scheduled
        StateName/PROCESSING :processing
        StateName/SUCCEEDED :succeeded
        StateName/FAILED :failed
        StateName/DELETED :deleted
        :unknown))
    (catch JobNotFoundException _
      :not-found)))

(defn wait-for-job
  "Polls until job reaches a terminal state or times out.

  Options:
    :timeout-ms - max wait time (default 5000)
    :poll-ms    - poll interval (default 100)

  Returns the final job status, or :timeout if timed out."
  [storage-provider job-id & {:keys [timeout-ms poll-ms]
                              :or {timeout-ms 5000
                                   poll-ms 100}}]
  (let [start           (System/currentTimeMillis)
        terminal-states #{:succeeded :failed :deleted}]
    (loop []
      (let [status (job-status storage-provider job-id)]
        (cond
          (terminal-states status) status
          (> (- (System/currentTimeMillis) start) timeout-ms) :timeout
          :else (do
                  (Thread/sleep poll-ms)
                  (recur)))))))

(defn with-jobrunr-fixture
  "Creates a test fixture that starts JobRunr with a datasource.

  Usage:
    (use-fixtures :each (with-jobrunr-fixture datasource))

  Within tests, use *storage-provider* and *serializer* bindings."
  [datasource]
  (fn [f]
    (let [config {::ig-jobrunr/serialization {}
                  ::ig-jobrunr/storage-provider {:datasource datasource}
                  ::ig-jobrunr/server {:storage-provider (ig/ref ::ig-jobrunr/storage-provider)
                                       :serialization (ig/ref ::ig-jobrunr/serialization)
                                       :dashboard? false}}
          system (ig/init config)]
      (try
        (binding [*storage-provider* (::ig-jobrunr/storage-provider system)
                  *serializer*       (::ig-jobrunr/serialization system)]
          (f))
        (finally
          (ig/halt! system))))))

(defmacro with-test-serializer
  "Executes body with a test serializer bound."
  [& body]
  `(let [serializer# (ser/default-serializer)]
     (binding [ser/*serializer* serializer#]
       ~@body)))

;; ---------------------------------------------------------------------------
;; Classloader utilities
;; ---------------------------------------------------------------------------

(defn verify-classloader-setup
  "Verifies that a thread with our custom classloader can load Clojure classes.
   Returns true if successful, throws if not."
  []
  (let [factory (cl/make-clojure-aware-thread-factory)
        result  (promise)
        test-fn (fn []
                  (try
                    (let [cl  (.getContextClassLoader (Thread/currentThread))
                          cls (Class/forName "clojure.lang.IFn" true cl)]
                      (deliver result {:success true :class cls}))
                    (catch Exception e
                      (deliver result {:success false :error e}))))
        thread  (.newThread factory test-fn)]
    (.start thread)
    (.join thread 5000)
    (let [{:keys [success error]} @result]
      (if success
        true
        (throw (ex-info "Classloader verification failed" {:error error}))))))

;; ---------------------------------------------------------------------------
;; Multimethod reset utilities
;; ---------------------------------------------------------------------------

(defn reset-handlers
  "Test fixture that resets the [[handle-job]] multimethod between tests.

  Removes all non-default methods to prevent test pollution.

  Usage:
    (use-fixtures :each reset-handlers)"
  [f]
  (doseq [k (keys (methods handle-job))]
    (when (not= k :default)
      (remove-method handle-job k)))
  (f))

;; ---------------------------------------------------------------------------
;; Testcontainers fixtures for integration tests
;; ---------------------------------------------------------------------------

(def ^:dynamic *datasource*
  "HikariCP datasource connected to the test PostgreSQL container."
  nil)

(def ^:dynamic *system*
  "Current Integrant system for the test context."
  nil)

(def ^:dynamic *container*
  "Current PostgreSQL Testcontainer instance."
  nil)

(def executions
  "Atom to track job executions for verification in integration tests."
  (atom []))

(defn- start-postgres-container!
  "Starts a PostgreSQL Testcontainer and returns it."
  []
  (doto (PostgreSQLContainer. "postgres:16-alpine")
    (.start)))

(defn- stop-postgres-container!
  "Stops the given PostgreSQL Testcontainer."
  [^PostgreSQLContainer container]
  (.stop container))

(defn- create-datasource
  "Creates a HikariCP datasource from the Testcontainer."
  [^PostgreSQLContainer container]
  (let [config (doto (HikariConfig.)
                 (.setJdbcUrl (.getJdbcUrl container))
                 (.setUsername (.getUsername container))
                 (.setPassword (.getPassword container))
                 (.setMaximumPoolSize 5))]
    (HikariDataSource. config)))

(defn postgres-fixture
  "Test fixture that starts a PostgreSQL Testcontainer and creates a connection pool.

  Binds `*container*` and `*datasource*` for use in tests.

  Usage:
    (use-fixtures :once postgres-fixture)"
  [f]
  (let [container (start-postgres-container!)
        ds        (create-datasource container)]
    (try
      (binding [*container*  container
                *datasource* ds]
        (f))
      (finally
        (.close ds)
        (stop-postgres-container! container)))))

(defn jobrunr-fixture
  "Test fixture that starts a JobRunr server connected to `*datasource*`.

  Uses a 5-second poll interval (minimum supported by JobRunr 8.x).
  Binds `*storage-provider*`, `*serializer*`, and `*system*`.

  Usage:
    (use-fixtures :once (t/join-fixtures [postgres-fixture jobrunr-fixture]))"
  [f]
  (let [config {::ig-jobrunr/serialization {}
                ::ig-jobrunr/storage-provider {:datasource *datasource*}
                ::ig-jobrunr/server {:storage-provider (ig/ref ::ig-jobrunr/storage-provider)
                                     :serialization (ig/ref ::ig-jobrunr/serialization)
                                     :poll-interval 5
                                     :dashboard? false}}
        system (ig/init config)]
    (try
      (binding [*storage-provider* (::ig-jobrunr/storage-provider system)
                *serializer*       (::ig-jobrunr/serialization system)
                *system*           system]
        (f))
      (finally
        (ig/halt! system)))))

(defn integration-fixture
  "Combined fixture for integration tests.

  Starts PostgreSQL container, creates connection pool, and starts JobRunr server.

  Usage:
    (use-fixtures :once (integration-fixture))"
  []
  (t/join-fixtures [postgres-fixture jobrunr-fixture]))

(defn reset-executions-fixture
  "Per-test fixture that resets the executions atom.

  Usage:
    (use-fixtures :each reset-executions-fixture)"
  [f]
  (reset! executions [])
  (f))

(defn restart-jobrunr!
  "Stops the current JobRunr server and starts a new one.

  Used for testing job persistence across server restarts.
  Updates the dynamic vars to point to the new system."
  []
  (ig/halt! *system*)
  (let [config     {::ig-jobrunr/serialization {}
                    ::ig-jobrunr/storage-provider {:datasource *datasource*}
                    ::ig-jobrunr/server {:storage-provider (ig/ref ::ig-jobrunr/storage-provider)
                                         :serialization (ig/ref ::ig-jobrunr/serialization)
                                         :poll-interval 5
                                         :dashboard? false}}
        new-system (ig/init config)]
    (alter-var-root #'*system* (constantly new-system))
    (alter-var-root #'*storage-provider* (constantly (::ig-jobrunr/storage-provider new-system)))
    (alter-var-root #'*serializer* (constantly (::ig-jobrunr/serialization new-system)))
    new-system))
