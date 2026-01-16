(ns clj-jobrunr.bridge
  "Bridge between Clojure job handlers and JobRunr.

  Provides utilities for converting job definitions to Java class names
  and serializing/deserializing job data for JobRunr storage.

  The bridge module handles the translation between:
  - Clojure job type keywords (e.g., `::send-email`)
  - Java class names that JobRunr can serialize (e.g., `clj_jobrunr.jobs.SendEmail`)
  - EDN payloads stored in the database"
  (:require [clj-jobrunr.job :refer [handle-job]]
            [clj-jobrunr.serialization :as ser]
            [clojure.string :as str]))

(defn- kebab->pascal
  "Converts kebab-case string to PascalCase.
  Example: \"send-email\" -> \"SendEmail\""
  [s]
  (->> (str/split s #"-")
       (map str/capitalize)
       (str/join)))

(defn job-class-name
  "Converts a job type keyword to a Java class name.

  Takes a namespaced keyword and returns a fully qualified Java class name
  in the `clj_jobrunr.jobs` package with PascalCase naming.

  Examples:
    :my.ns/send-email -> \"clj_jobrunr.jobs.SendEmail\"
    :my.ns/process-user-order -> \"clj_jobrunr.jobs.ProcessUserOrder\""
  [job-kw]
  (let [job-name   (name job-kw)
        class-name (kebab->pascal job-name)]
    (str "clj_jobrunr.jobs." class-name)))

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
