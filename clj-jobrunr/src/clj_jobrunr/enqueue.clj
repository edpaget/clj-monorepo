(ns clj-jobrunr.enqueue
  "Job enqueueing API for scheduling background jobs.

  This module provides functions to create job request structures that can
  be passed to JobRunr. The actual JobRunr API calls are handled by the
  integrant components; these functions prepare the data structures.

  All functions take a serializer as the first argument to support
  custom EDN tagged literals in payloads."
  (:require [clj-jobrunr.bridge :as bridge])
  (:import [java.time Instant Duration]))

(defn make-job-request
  "Creates a job request map for immediate execution.

  Returns a map containing:
    :job-type   - the namespaced keyword for the job
    :payload    - the job payload data
    :edn        - serialized EDN string for storage
    :class-name - Java class name for JobRunr"
  [serializer job-type payload]
  {:job-type job-type
   :payload payload
   :edn (bridge/job-edn serializer job-type payload)
   :class-name (bridge/job-class-name job-type)})

(defn make-scheduled-request
  "Creates a job request map for scheduled execution.

  The `time` parameter can be either:
    - An `Instant` for absolute scheduling
    - A `Duration` for relative scheduling (from now)

  Returns a map containing:
    :job-type     - the namespaced keyword for the job
    :payload      - the job payload data
    :edn          - serialized EDN string for storage
    :class-name   - Java class name for JobRunr
    :scheduled-at - the Instant when the job should run"
  [serializer job-type payload time]
  (let [scheduled-at (if (instance? Duration time)
                       (.plus (Instant/now) ^Duration time)
                       time)]
    (assoc (make-job-request serializer job-type payload)
           :scheduled-at scheduled-at)))

(defn make-recurring-request
  "Creates a job request map for recurring execution.

  Parameters:
    serializer - the EDN serializer to use
    job-id     - unique identifier for the recurring job (for updates/deletion)
    job-type   - the namespaced keyword for the job
    cron       - cron expression (e.g., \"0 9 * * *\" for daily at 9 AM)
    payload    - the job payload data

  Returns a map containing:
    :job-id     - the unique recurring job identifier
    :job-type   - the namespaced keyword for the job
    :payload    - the job payload data
    :edn        - serialized EDN string for storage
    :class-name - Java class name for JobRunr
    :cron       - the cron expression"
  [serializer job-id job-type cron payload]
  (assoc (make-job-request serializer job-type payload)
         :job-id job-id
         :cron cron))

(defn make-delete-recurring-request
  "Creates a request to delete a recurring job.

  Returns a map containing:
    :job-id - the unique recurring job identifier to delete"
  [job-id]
  {:job-id job-id})
