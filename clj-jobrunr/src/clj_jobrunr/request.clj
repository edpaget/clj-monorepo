(ns clj-jobrunr.request
  "JobRequest and JobRequestHandler implementations for Clojure.

  This module provides `deftype` implementations of JobRunr's JobRequest
  and JobRequestHandler interfaces, enabling background job processing
  without AOT compilation.

  [[ClojureJobRequest]] holds the EDN-serialized job data and is serialized
  as JSON by JobRunr. [[ClojureJobRequestHandler]] deserializes the EDN and
  dispatches to the appropriate Clojure handler via the multimethod.

  Note: These types are created dynamically when this namespace is loaded.
  JobRunr worker threads must use a classloader that can find them - see
  [[clj-jobrunr.classloader]] for the custom classloader setup."
  (:require
   [clj-jobrunr.bridge :as bridge]
   [clj-jobrunr.serialization :as ser])
  (:import
   [org.jobrunr.jobs.lambdas JobRequest JobRequestHandler]))

;; ---------------------------------------------------------------------------
;; ClojureJobRequestHandler (defined first - no dependencies on ClojureJobRequest at compile time)
;; ---------------------------------------------------------------------------

(deftype ClojureJobRequestHandler []
  JobRequestHandler
  (run [_ request]
    ;; Use reflection to get edn field - avoids compile-time dependency
    (let [edn        (.edn request)
          serializer (or ser/*serializer* (ser/default-serializer))]
      (bridge/execute! serializer edn))))

;; ---------------------------------------------------------------------------
;; ClojureJobRequest (can now reference ClojureJobRequestHandler)
;; ---------------------------------------------------------------------------

(deftype ClojureJobRequest [^String edn]
  JobRequest
  (getJobRequestHandler [_]
    ClojureJobRequestHandler))

(defn make-job-request
  "Creates a ClojureJobRequest from a job type and payload.

  Uses the provided serializer (or the global `*serializer*`) to serialize
  the job data as EDN."
  ([job-type payload]
   (make-job-request (or ser/*serializer* (ser/default-serializer))
                     job-type
                     payload))
  ([serializer job-type payload]
   (let [edn (bridge/job-edn serializer job-type payload)]
     (ClojureJobRequest. edn))))

;; ---------------------------------------------------------------------------
;; Utility functions
;; ---------------------------------------------------------------------------

(defn request-edn
  "Extracts the EDN string from a ClojureJobRequest."
  [^ClojureJobRequest request]
  (.edn request))

(defn handler-class
  "Returns the ClojureJobRequestHandler class.

  Useful for verifying the handler can be loaded by a classloader."
  []
  ClojureJobRequestHandler)

(defn request-class
  "Returns the ClojureJobRequest class.

  Useful for verifying the request can be loaded by a classloader."
  []
  ClojureJobRequest)
