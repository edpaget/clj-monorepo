(ns clj-jobrunr.worker-policy
  "Custom BackgroundJobServerWorkerPolicy for Clojure integration.

  This module provides a worker policy that configures JobRunr worker threads
  to use a classloader capable of finding Clojure's dynamically-generated
  classes (deftype, defrecord).

  The key insight is that JobRunr uses `Class.forName()` with the thread's
  context classloader to load job request and handler classes. By default,
  worker threads use the system classloader which can't see Clojure's
  `DynamicClassLoader` classes. Our custom policy creates an executor with
  threads that have the correct context classloader set.

  Usage with Integrant:
  ```clojure
  (let [policy (make-clojure-worker-policy worker-count source-classloader)]
    (-> (BackgroundJobServerConfiguration/usingStandardBackgroundJobServerConfiguration)
        (.andBackgroundJobServerWorkerPolicy policy)))
  ```"
  (:require [clj-jobrunr.classloader :as cl])
  (:import [java.util.concurrent ScheduledThreadPoolExecutor ThreadFactory TimeUnit]
           [org.jobrunr.server.configuration BackgroundJobServerWorkerPolicy]
           [org.jobrunr.server.strategy BasicWorkDistributionStrategy]
           [org.jobrunr.server.threadpool JobRunrExecutor]))

;; ---------------------------------------------------------------------------
;; ClojureJobRunrExecutor
;; ---------------------------------------------------------------------------

(defn- make-clojure-thread-factory
  "Creates a ThreadFactory that sets the context classloader on new threads.

  The composite classloader delegates to Clojure's DynamicClassLoader,
  allowing worker threads to load deftype/defrecord classes."
  [composite-classloader thread-name-prefix]
  (let [counter (atom 0)]
    (reify ThreadFactory
      (newThread [_ runnable]
        (let [thread (Thread. runnable)
              n      (swap! counter inc)]
          (.setName thread (str thread-name-prefix "-" n))
          (.setContextClassLoader thread composite-classloader)
          thread)))))

(defn make-clojure-executor
  "Creates a JobRunrExecutor with threads using a composite classloader.

  The executor extends ScheduledThreadPoolExecutor (required by JobRunr)
  and uses a custom ThreadFactory that sets the context classloader to
  include Clojure's DynamicClassLoader.

  Parameters:
  - `worker-count`: Number of worker threads
  - `composite-classloader`: The classloader to set on worker threads
  - `thread-name-prefix`: Optional prefix for thread names (default: \"jobrunr-clj-worker\")"
  ([worker-count composite-classloader]
   (make-clojure-executor worker-count composite-classloader "jobrunr-clj-worker"))
  ([worker-count composite-classloader thread-name-prefix]
   (let [factory  (make-clojure-thread-factory composite-classloader thread-name-prefix)
         executor (ScheduledThreadPoolExecutor. worker-count factory)
         stopping (atom false)]
     ;; Configure executor like PlatformThreadPoolJobRunrExecutor
     (.setMaximumPoolSize executor (* worker-count 2))
     (.setKeepAliveTime executor 1 TimeUnit/MINUTES)

     (reify JobRunrExecutor
       (getWorkerCount [_]
         worker-count)

       (start [_]
         (reset! stopping false)
         (.prestartAllCoreThreads executor))

       (stop [_ await-timeout]
         (reset! stopping true)
         (.shutdown executor)
         (.awaitTermination executor (.toMillis await-timeout) TimeUnit/MILLISECONDS))

       (isStopping [_]
         @stopping)

       (execute [_ command]
         (.execute executor command))))))

;; ---------------------------------------------------------------------------
;; ClojureBackgroundJobServerWorkerPolicy
;; ---------------------------------------------------------------------------

(defn make-clojure-worker-policy
  "Creates a BackgroundJobServerWorkerPolicy that uses our custom classloader.

  This policy creates a [[JobRunrExecutor]] with worker threads that have
  their context classloader set to a composite classloader that can find
  Clojure's dynamically-generated classes.

  Parameters:
  - `worker-count`: Number of worker threads (defaults to available processors)
  - `source-classloader`: The classloader where Clojure classes are defined
    (typically from `(.getClassLoader ClojureJobRequest)`)

  Example:
  ```clojure
  (require '[clj-jobrunr.request :as req])

  (let [source-cl (.getClassLoader (req/request-class))
        policy (make-clojure-worker-policy 4 source-cl)]
    ;; Use with JobRunr configuration
    )
  ```"
  ([]
   (make-clojure-worker-policy (.availableProcessors (Runtime/getRuntime))))
  ([worker-count]
   (make-clojure-worker-policy worker-count (cl/get-clojure-classloader)))
  ([worker-count source-classloader]
   (let [composite-cl (cl/make-composite-classloader source-classloader)]
     (reify BackgroundJobServerWorkerPolicy
       (toJobRunrExecutor [_]
         (make-clojure-executor worker-count composite-cl))

       (toWorkDistributionStrategy [_ background-job-server]
         (BasicWorkDistributionStrategy. background-job-server worker-count))))))

;; ---------------------------------------------------------------------------
;; Utility functions
;; ---------------------------------------------------------------------------

(defn default-worker-count
  "Returns the default worker count (number of available processors)."
  []
  (.availableProcessors (Runtime/getRuntime)))
