(ns clj-jobrunr.java-bridge
  "Java bridge class for JobRunr integration.

  This namespace generates a Java class that JobRunr can serialize and invoke.
  The generated class has a static `run` method that deserializes EDN and
  dispatches to the appropriate Clojure job handler.

  The class is generated via gen-class and requires AOT compilation.

  Generated class: `clj_jobrunr.ClojureBridge`
  Static method: `run(String edn)` - executes a job from its EDN representation"
  (:require [clj-jobrunr.bridge :as bridge]
            [clj-jobrunr.serialization :as ser])
  (:gen-class
   :name clj_jobrunr.ClojureBridge
   :methods [^:static [run [String] Object]]))

(defn -run
  "Static method implementation called by JobRunr.

  Deserializes the EDN string and dispatches to the appropriate handler
  via [[clj-jobrunr.bridge/execute!]]. Uses the globally configured
  serializer from [[clj-jobrunr.serialization/*serializer*]].

  Returns the result of the job handler."
  [^String edn]
  (let [serializer (or ser/*serializer* (ser/default-serializer))]
    (bridge/execute! serializer edn)))
