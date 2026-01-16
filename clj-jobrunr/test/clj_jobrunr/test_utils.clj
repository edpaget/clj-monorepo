(ns clj-jobrunr.test-utils
  "Test utilities and fixtures for integration testing.

  Provides helpers for:
  - Starting/stopping JobRunr server in tests
  - Waiting for job completion
  - Inspecting job status"
  (:require [clj-jobrunr.integrant :as ig-jobrunr]
            [clj-jobrunr.serialization :as ser]
            [integrant.core :as ig])
  (:import [org.jobrunr.jobs.states StateName]
           [org.jobrunr.storage JobNotFoundException]))

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
          state-name (.getStateName (.getJobState job))]
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
