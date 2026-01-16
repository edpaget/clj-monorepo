(ns clj-jobrunr.classloader
  "Custom classloader and ThreadFactory for JobRunr integration.

  This module enables JobRunr worker threads to load classes from Clojure's
  DynamicClassLoader, allowing `deftype` and `defrecord` classes to be used
  without AOT compilation.

  The approach:
  1. Capture Clojure's DynamicClassLoader at namespace load time
  2. Create a composite classloader that delegates to both Clojure's DL and system CL
  3. Provide a custom ThreadFactory that sets this classloader on worker threads
  4. Configure JobRunr to use our custom executor"
  (:import [java.util.concurrent ThreadFactory Executors]
           [clojure.lang DynamicClassLoader RT]))

;; ---------------------------------------------------------------------------
;; Composite ClassLoader
;; ---------------------------------------------------------------------------

(defn get-clojure-classloader
  "Returns Clojure's base classloader (the one that knows about dynamically
   compiled classes from deftype/defrecord)."
  []
  (RT/baseLoader))

(defn make-composite-classloader
  "Creates a classloader that tries Clojure's classloader first, then delegates
   to the parent. This allows JobRunr worker threads to find dynamically
   generated Clojure classes."
  ([]
   (make-composite-classloader (get-clojure-classloader)))
  ([clojure-classloader]
   (make-composite-classloader clojure-classloader
                               (ClassLoader/getSystemClassLoader)))
  ([clojure-classloader parent-classloader]
   (proxy [ClassLoader] [parent-classloader]
     (findClass [name]
       (try
         (.loadClass clojure-classloader name)
         (catch ClassNotFoundException _
           (throw (ClassNotFoundException. name))))))))

;; ---------------------------------------------------------------------------
;; Custom ThreadFactory
;; ---------------------------------------------------------------------------

(defn make-clojure-aware-thread-factory
  "Creates a ThreadFactory that sets the context classloader on new threads
   to include Clojure's DynamicClassLoader.

   This allows JobRunr worker threads to load classes created by deftype/defrecord."
  ([]
   (make-clojure-aware-thread-factory (make-composite-classloader)))
  ([composite-classloader]
   (make-clojure-aware-thread-factory composite-classloader "jobrunr-clojure-worker"))
  ([composite-classloader thread-name-prefix]
   (let [default-factory (Executors/defaultThreadFactory)
         counter         (atom 0)]
     (reify ThreadFactory
       (newThread [_ runnable]
         (let [thread (.newThread default-factory runnable)
               n      (swap! counter inc)]
           (.setName thread (str thread-name-prefix "-" n))
           (.setContextClassLoader thread composite-classloader)
           thread))))))

;; ---------------------------------------------------------------------------
;; Test utilities
;; ---------------------------------------------------------------------------

(defn verify-classloader-setup
  "Verifies that a thread with our custom classloader can load Clojure classes.
   Returns true if successful, throws if not."
  []
  (let [factory (make-clojure-aware-thread-factory)
        result  (promise)
        test-fn (fn []
                  (try
                    ;; Try to load a core Clojure class via Class.forName
                    ;; using the thread's context classloader
                    (let [cl  (.getContextClassLoader (Thread/currentThread))
                          cls (Class/forName "clojure.lang.IFn" true cl)]
                      (deliver result {:success true :class cls}))
                    (catch Exception e
                      (deliver result {:success false :error e}))))
        thread  (.newThread factory test-fn)]
    (.start thread)
    (.join thread 5000)
    (let [{:keys [success error]} @result]
      (if success
        true
        (throw (ex-info "Classloader verification failed" {:error error}))))))
