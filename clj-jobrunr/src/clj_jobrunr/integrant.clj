(ns clj-jobrunr.integrant
  "Integrant components for JobRunr integration.

  Provides lifecycle management for:
  - [[::serialization]] - EDN serializer configuration
  - [[::storage-provider]] - JobRunr storage backend (PostgreSQL)
  - [[::server]] - JobRunr background job server

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
  (:require [clj-jobrunr.serialization :as ser]
            [integrant.core :as ig])
  (:import [org.jobrunr.configuration JobRunr]
           [org.jobrunr.dashboard JobRunrDashboardWebServer]
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
             poll-interval]
      :or {dashboard? false
           dashboard-port 8000
           poll-interval 15}}]
  (let [config (-> (JobRunr/configure)
                   (.useStorageProvider storage-provider)
                   (.useBackgroundJobServer poll-interval))]
    ;; Store serialization config for bridge.execute! to use
    (alter-var-root #'ser/*serializer* (constantly serialization))
    (let [job-runr (.initialize config)
          server   {:job-runr job-runr
                    :dashboard (when dashboard?
                                 (doto (JobRunrDashboardWebServer. storage-provider (GsonJsonMapper.) dashboard-port)
                                   (.start)))}]
      server)))

(defmethod ig/halt-key! ::server
  [_ {:keys [job-runr dashboard]}]
  (when dashboard
    (.stop dashboard))
  (when-let [server (.getBackgroundJobServer job-runr)]
    (.stop server)))
