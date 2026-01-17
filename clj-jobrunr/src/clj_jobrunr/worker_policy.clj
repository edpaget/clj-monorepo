(ns clj-jobrunr.worker-policy
  "Custom BackgroundJobServerWorkerPolicy for Clojure integration.

  This module provides a worker policy that configures JobRunr worker threads
  to use a classloader capable of finding Clojure's dynamically-generated
  classes (deftype, defrecord).

  The key insight is that JobRunr uses `Class.forName()` with the thread's
  context classloader to load job request and handler classes. By default,
  worker threads use the system classloader which can't see Clojure's
  `DynamicClassLoader` classes. Our custom policy creates an executor with
  virtual threads that have the correct context classloader set.

  Usage with Integrant:
  ```clojure
  (let [policy (make-clojure-worker-policy worker-count source-classloader)]
    (-> (BackgroundJobServerConfiguration/usingStandardBackgroundJobServerConfiguration)
        (.andBackgroundJobServerWorkerPolicy policy)))
  ```"
  (:require [clj-jobrunr.classloader :as cl])
  (:import [java.util.concurrent Executors ThreadFactory TimeUnit]
           [org.jobrunr.server.configuration BackgroundJobServerWorkerPolicy]
           [org.jobrunr.server.strategy BasicWorkDistributionStrategy]
           [org.jobrunr.server.threadpool JobRunrExecutor]))

;; ---------------------------------------------------------------------------
;; Virtual Thread Factory
;; ---------------------------------------------------------------------------

(defn- make-virtual-thread-factory
  "Creates a ThreadFactory that produces virtual threads with the context
  classloader set to our composite classloader.

  Virtual threads (Java 21+) are lightweight and can be created in large
  numbers. Each virtual thread will have access to Clojure's DynamicClassLoader
  for loading deftype/defrecord classes."
  [composite-classloader thread-name-prefix]
  (let [counter      (atom 0)
        base-factory (-> (Thread/ofVirtual)
                         (.name thread-name-prefix 0)
                         (.factory))]
    (reify ThreadFactory
      (newThread [_ runnable]
        (let [n                (swap! counter inc)
              ;; Wrap runnable to set classloader before execution
              wrapped-runnable (fn []
                                 (.setContextClassLoader (Thread/currentThread)
                                                         composite-classloader)
                                 (.run runnable))
              thread           (.newThread base-factory wrapped-runnable)]
          (.setName thread (str thread-name-prefix "-" n))
          thread)))))

;; ---------------------------------------------------------------------------
;; ClojureJobRunrExecutor (Virtual Threads)
;; ---------------------------------------------------------------------------

(defn make-clojure-executor
  "Creates a JobRunrExecutor using virtual threads with our composite classloader.

  Uses Java 21+ virtual threads for lightweight, high-throughput job execution.
  Each virtual thread has its context classloader set to include Clojure's
  DynamicClassLoader, enabling Class.forName() to find deftype classes.

  Parameters:
  - `worker-count`: Logical worker count for work distribution strategy
  - `composite-classloader`: The classloader to set on worker threads
  - `thread-name-prefix`: Optional prefix for thread names (default: \"jobrunr-vthread\")"
  ([worker-count composite-classloader]
   (make-clojure-executor worker-count composite-classloader "jobrunr-vthread"))
  ([worker-count composite-classloader thread-name-prefix]
   (let [factory  (make-virtual-thread-factory composite-classloader thread-name-prefix)
         executor (Executors/newThreadPerTaskExecutor factory)
         stopping (atom false)]

     (reify JobRunrExecutor
       (getWorkerCount [_]
         worker-count)

       (start [_]
         (reset! stopping false))

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
  "Creates a BackgroundJobServerWorkerPolicy using virtual threads.

  This policy creates a [[JobRunrExecutor]] with virtual threads that have
  their context classloader set to a composite classloader that can find
  Clojure's dynamically-generated classes.

  Virtual threads (Java 21+) are lightweight and ideal for I/O-bound job
  processing. The `worker-count` parameter controls work distribution
  strategy, not the actual number of threads (virtual threads are created
  per-task).

  Parameters:
  - `worker-count`: Logical worker count for work distribution (defaults to available processors)
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
  "Returns the default worker count (number of available processors).

  For virtual threads, this is used for work distribution strategy rather
  than limiting actual thread count."
  []
  (.availableProcessors (Runtime/getRuntime)))
