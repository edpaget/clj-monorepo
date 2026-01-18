(ns clj-jobrunr.integrant
  "Integrant components for JobRunr integration.

  Provides lifecycle management for:
  - [[::serialization]] - EDN serializer configuration
  - [[::storage-provider]] - JobRunr storage backend (PostgreSQL)
  - [[::server]] - JobRunr background job server with virtual threads

  The server component configures JobRunr with a custom worker policy that:
  - Uses Java 21+ virtual threads for lightweight, high-throughput job execution
  - Sets the correct context classloader so workers can find Clojure deftype classes
  - Binds the EDN serializer for job deserialization

  Example Integrant configuration:

      {::serialization
       {:readers {'time/instant #(java.time.Instant/parse %)}}

       ::storage-provider
       {:datasource #ig/ref :datasource/postgres}

       ::server
       {:storage-provider #ig/ref ::storage-provider
        :serialization #ig/ref ::serialization
        :dashboard? true
        :dashboard-port 8080
        :poll-interval 15
        :worker-count 4}}"
  (:require
   [clj-jobrunr.classloader :as cl]
   [clj-jobrunr.request :as req]
   [clj-jobrunr.serialization :as ser]
   [clj-jobrunr.worker-policy :as wp]
   [integrant.core :as ig])
  (:import
   [org.jobrunr.configuration JobRunr]
   [org.jobrunr.dashboard JobRunrDashboardWebServer]
   [org.jobrunr.server BackgroundJobServerConfiguration]
   [org.jobrunr.storage.sql.postgres PostgresStorageProvider]
   [org.jobrunr.utils.mapper.gson GsonJsonMapper]))

;; ---------------------------------------------------------------------------
;; Serialization Component
;; ---------------------------------------------------------------------------

(defmethod ig/init-key ::serialization
  [_ {:keys [readers read-fn write-fn]}]
  (ser/make-serializer {:readers readers
                        :read-fn read-fn
                        :write-fn write-fn}))

;; Serializer has no resources to clean up
(defmethod ig/halt-key! ::serialization [_ _serializer])

;; Expose for testing
(def storage-provider-init-key ::storage-provider)
(def server-init-key ::server)

;; ---------------------------------------------------------------------------
;; Storage Provider Component
;; ---------------------------------------------------------------------------

(defmethod ig/init-key ::storage-provider
  [_ {:keys [datasource]}]
  (PostgresStorageProvider. datasource))

;; Storage provider doesn't need explicit cleanup - JobRunr handles it
(defmethod ig/halt-key! ::storage-provider [_ _storage-provider])

;; ---------------------------------------------------------------------------
;; Server Component
;; ---------------------------------------------------------------------------

(defmethod ig/init-key ::server
  [_ {:keys [storage-provider serialization dashboard? dashboard-port
             poll-interval worker-count]
      :or {dashboard? false
           dashboard-port 8000
           poll-interval 15}}]
  ;; Store serialization config for bridge.execute! to use
  ;; This must happen before workers start so they can access it
  (alter-var-root #'ser/*serializer* (constantly serialization))

  (let [;; Get classloader from our deftype (where Clojure's DynamicClassLoader lives)
        source-cl     (.getClassLoader (req/request-class))
        ;; Create composite classloader for both worker and polling threads
        composite-cl  (cl/make-composite-classloader source-cl)
        ;; Create worker policy with virtual threads and correct classloader
        worker-count  (or worker-count (wp/default-worker-count))
        worker-policy (wp/make-clojure-worker-policy worker-count source-cl)]
    ;; Set context classloader before initializing JobRunr. This classloader
    ;; is needed for:
    ;; 1. JobRunr's internal threads to find our deftype classes
    ;; 2. Any thread that calls enqueue!/schedule! to create JobDetails
    ;; We intentionally don't restore the original classloader because our
    ;; composite delegates to both DynamicClassLoader and the parent, so it's
    ;; a superset of what the original could see.
    (.setContextClassLoader (Thread/currentThread) composite-cl)
    (let [;; Configure background job server with our policy
          server-config (-> (BackgroundJobServerConfiguration/usingStandardBackgroundJobServerConfiguration)
                            (.andPollIntervalInSeconds poll-interval)
                            (.andBackgroundJobServerWorkerPolicy worker-policy))
          config        (-> (JobRunr/configure)
                            (.useStorageProvider storage-provider)
                            (.useBackgroundJobServer server-config))
          job-runr      (.initialize config)
          dashboard     (when dashboard?
                          (doto (JobRunrDashboardWebServer. storage-provider (GsonJsonMapper.) dashboard-port)
                            (.start)))]
      {:job-runr job-runr
       :dashboard dashboard
       :composite-classloader composite-cl})))

(defmethod ig/halt-key! ::server
  [_ {:keys [dashboard]}]
  (when dashboard
    (.stop dashboard))
  ;; In JobRunr 8.x, getBackgroundJobServer is a static method on JobRunr
  (when-let [server (JobRunr/getBackgroundJobServer)]
    (.stop server)))
