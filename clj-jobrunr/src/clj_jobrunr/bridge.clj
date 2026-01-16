(ns clj-jobrunr.bridge
  "Bridge between Clojure job handlers and JobRunr.

  Provides utilities for converting job definitions to Java class names
  and serializing/deserializing job data for JobRunr storage.

  The bridge module handles the translation between:
  - Clojure job type keywords (e.g., `:my.app.jobs/send-email`)
  - Java class names derived from the namespace (e.g., `my.app.jobs.SendEmail`)
  - EDN payloads stored in the database"
  (:require [camel-snake-kebab.core :as csk]
            [clj-jobrunr.job :refer [handle-job]]
            [clj-jobrunr.serialization :as ser]))

(defn job-class-name
  "Converts a job type keyword to a Java class name.

  Takes a namespaced keyword and returns a fully qualified Java class name
  derived from the keyword's namespace and name. The namespace becomes the
  Java package (with hyphens converted to underscores) and the name becomes
  the PascalCase class name.

  Examples:
    :my.ns/send-email -> \"my.ns.SendEmail\"
    :user.jobs/process-order -> \"user.jobs.ProcessOrder\"
    :admin-tasks.core/cleanup -> \"admin_tasks.core.Cleanup\""
  [job-kw]
  (let [ns-part    (namespace job-kw)
        job-name   (name job-kw)
        package    (csk/->snake_case ns-part)
        class-name (csk/->PascalCase job-name)]
    (str package "." class-name)))

(defn job-edn
  "Creates an EDN string containing job type and payload for storage.

  The serializer is used to write the data structure to a string.
  The resulting EDN contains:
    :job-type - the namespaced keyword identifying the job handler
    :payload - the job's payload data"
  [serializer job-type payload]
  (ser/serialize serializer {:job-type job-type
                             :payload payload}))

(defn execute!
  "Deserializes job EDN and dispatches to the appropriate handler.

  Called by the generated Java bridge class when JobRunr executes a job.
  Deserializes the EDN string, extracts the job type and payload,
  and calls [[clj-jobrunr.job/handle-job]] for dispatch.

  Returns the result of the handler function.
  Propagates any exceptions thrown by the handler."
  [serializer edn-str]
  (let [{:keys [job-type payload]} (ser/deserialize serializer edn-str)]
    (handle-job job-type payload)))
