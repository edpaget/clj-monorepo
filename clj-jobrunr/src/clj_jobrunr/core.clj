(ns clj-jobrunr.core
  "Public API for enqueueing and scheduling background jobs.

  This module provides the main entry points for creating background jobs
  with JobRunr. Jobs are defined using [[clj-jobrunr.job/defjob]] and
  enqueued using the functions in this namespace.

  All functions use [[ClojureJobRequest]] to serialize job data as EDN,
  which JobRunr stores and later deserializes for execution.

  Example:
  ```clojure
  (require '[clj-jobrunr.core :as jobrunr])
  (require '[clj-jobrunr.job :refer [defjob]])

  (defjob send-email
    [{:keys [to subject body]}]
    (email/send! to subject body))

  ;; Enqueue for immediate execution
  (jobrunr/enqueue! ::send-email {:to \"user@example.com\"
                                   :subject \"Hello\"
                                   :body \"World\"})

  ;; Schedule for later
  (jobrunr/schedule! ::send-email {:to \"user@example.com\"}
                     (Duration/ofHours 1))

  ;; Create recurring job
  (jobrunr/recurring! \"daily-report\" ::generate-report {}
                      \"0 9 * * *\")
  ```"
  (:require
   [clj-jobrunr.request :as req]
   [clj-jobrunr.serialization :as ser])
  (:import
   [java.time Duration Instant]
   [java.util UUID]
   [org.jobrunr.scheduling BackgroundJob JobBuilder RecurringJobBuilder]))

;; ---------------------------------------------------------------------------
;; Utility functions
;; ---------------------------------------------------------------------------

(defn- job-type->name
  "Converts a job type keyword to a human-readable name for the dashboard.

  Examples:
    :my.app/send-email -> \"my.app/send-email\"
    ::local-job -> \"current.ns/local-job\""
  [job-type]
  (if (namespace job-type)
    (str (namespace job-type) "/" (name job-type))
    (name job-type)))

(defn- build-job
  "Creates a JobBuilder configured with name, labels, and job request."
  [job-type payload opts]
  (let [serializer (or ser/*serializer* (ser/default-serializer))
        request    (req/make-job-request serializer job-type payload)
        job-name   (or (:name opts) (job-type->name job-type))
        builder    (-> (JobBuilder/aJob)
                       (.withName job-name)
                       (.withJobRequest request))]
    ;; Add labels if provided (up to 3)
    (when-let [labels (:labels opts)]
      (.withLabels builder ^java.util.List (vec labels)))
    ;; Add custom job ID if provided
    (when-let [id (:id opts)]
      (.withId builder (if (instance? UUID id) id (UUID/fromString (str id)))))
    ;; Add retry count if provided
    (when-let [retries (:retries opts)]
      (.withAmountOfRetries builder (int retries)))
    builder))

;; ---------------------------------------------------------------------------
;; Public API
;; ---------------------------------------------------------------------------

(defn enqueue!
  "Enqueues a job for immediate background execution.

  Returns the job ID as a UUID.

  Parameters:
  - `job-type`: Namespaced keyword identifying the job (from defjob)
  - `payload`: Map of data to pass to the job handler
  - `opts`: Optional map with:
    - `:name` - Custom display name for dashboard (default: job-type as string)
    - `:labels` - Vector of up to 3 label strings for filtering
    - `:id` - Custom UUID to prevent duplicate jobs
    - `:retries` - Number of retry attempts on failure

  Example:
  ```clojure
  (enqueue! ::send-email {:to \"user@example.com\"})

  (enqueue! ::send-email {:to \"user@example.com\"}
            {:name \"Send welcome email\"
             :labels [\"email\" \"onboarding\"]})
  ```"
  ([job-type payload]
   (enqueue! job-type payload {}))
  ([job-type payload opts]
   (let [builder (build-job job-type payload opts)]
     (BackgroundJob/create builder))))

(defn schedule!
  "Schedules a job for future background execution.

  Returns the job ID as a UUID.

  Parameters:
  - `job-type`: Namespaced keyword identifying the job (from defjob)
  - `payload`: Map of data to pass to the job handler
  - `time`: When to execute - either:
    - `Duration` - relative delay from now (e.g., `(Duration/ofHours 1)`)
    - `Instant` - absolute time (e.g., `(Instant/parse \"2024-01-15T10:00:00Z\")`)
  - `opts`: Optional map (same as [[enqueue!]])

  Example:
  ```clojure
  ;; Schedule 1 hour from now
  (schedule! ::send-reminder {:user-id 123}
             (Duration/ofHours 1))

  ;; Schedule at specific time
  (schedule! ::send-reminder {:user-id 123}
             (Instant/parse \"2024-12-25T09:00:00Z\"))
  ```"
  ([job-type payload time]
   (schedule! job-type payload time {}))
  ([job-type payload time opts]
   (let [builder (build-job job-type payload opts)]
     (if (instance? Duration time)
       (.scheduleIn builder ^Duration time)
       (.scheduleAt builder ^Instant time))
     (BackgroundJob/create builder))))

(defn recurring!
  "Creates or updates a recurring job with a cron schedule.

  If a recurring job with the given ID already exists, it will be updated.
  Returns the recurring job ID as a string.

  Parameters:
  - `recurring-id`: Unique string identifier for this recurring job
  - `job-type`: Namespaced keyword identifying the job (from defjob)
  - `payload`: Map of data to pass to the job handler
  - `cron`: Cron expression string (e.g., \"0 9 * * *\" for daily at 9 AM)
  - `opts`: Optional map with:
    - `:name` - Custom display name for dashboard
    - `:labels` - Vector of up to 3 label strings
    - `:zone` - Timezone ID string (default: system default)

  Common cron patterns:
  - \"0 * * * *\" - Every hour
  - \"0 9 * * *\" - Daily at 9 AM
  - \"0 9 * * 1\" - Every Monday at 9 AM
  - \"0 0 1 * *\" - First day of each month at midnight

  Example:
  ```clojure
  (recurring! \"daily-digest\" ::send-digest {:template :daily}
              \"0 9 * * *\")

  (recurring! \"weekly-report\" ::generate-report {}
              \"0 9 * * 1\"
              {:name \"Weekly sales report\"
               :zone \"America/New_York\"})
  ```"
  ([recurring-id job-type payload cron]
   (recurring! recurring-id job-type payload cron {}))
  ([recurring-id job-type payload cron opts]
   (let [serializer (or ser/*serializer* (ser/default-serializer))
         request    (req/make-job-request serializer job-type payload)
         job-name   (or (:name opts) (job-type->name job-type))
         builder    (-> (RecurringJobBuilder/aRecurringJob)
                        (.withId recurring-id)
                        (.withName job-name)
                        (.withCron cron)
                        (.withJobRequest request))]
     ;; Add labels if provided
     (when-let [labels (:labels opts)]
       (.withLabels builder ^java.util.List (vec labels)))
     ;; Add timezone if provided
     (when-let [zone (:zone opts)]
       (.withZoneId builder (java.time.ZoneId/of zone)))
     (BackgroundJob/createRecurrently builder)
     recurring-id)))

(defn delete-recurring!
  "Deletes a recurring job by its ID.

  Does not raise an exception if the job doesn't exist.

  Parameters:
  - `recurring-id`: The unique string identifier of the recurring job

  Example:
  ```clojure
  (delete-recurring! \"daily-digest\")
  ```"
  [recurring-id]
  (BackgroundJob/deleteRecurringJob recurring-id))
