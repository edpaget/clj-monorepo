(ns clj-jobrunr.worker-policy-test
  "Tests for custom BackgroundJobServerWorkerPolicy."
  (:require [clj-jobrunr.classloader :as cl]
            [clj-jobrunr.request :as req]
            [clj-jobrunr.worker-policy :as wp]
            [clojure.test :refer [deftest is testing]])
  (:import [java.time Duration]
           [java.util.concurrent CountDownLatch TimeUnit]
           [org.jobrunr.server.configuration BackgroundJobServerWorkerPolicy]
           [org.jobrunr.server.threadpool JobRunrExecutor]))

;; ---------------------------------------------------------------------------
;; Executor Tests
;; ---------------------------------------------------------------------------

(deftest make-clojure-executor-implements-interface-test
  (testing "make-clojure-executor returns a JobRunrExecutor"
    (let [composite-cl (cl/make-composite-classloader)
          executor     (wp/make-clojure-executor 2 composite-cl)]
      (is (instance? JobRunrExecutor executor)))))

(deftest executor-worker-count-test
  (testing "executor reports correct worker count"
    (let [composite-cl (cl/make-composite-classloader)
          executor     (wp/make-clojure-executor 4 composite-cl)]
      (is (= 4 (.getWorkerCount executor))))))

(deftest executor-start-stop-test
  (testing "executor can start and stop"
    (let [composite-cl (cl/make-composite-classloader)
          executor     (wp/make-clojure-executor 2 composite-cl)]
      (.start executor)
      (is (not (.isStopping executor)))
      (.stop executor (Duration/ofSeconds 5))
      (is (.isStopping executor)))))

(deftest executor-threads-have-correct-classloader-test
  (testing "executor threads have the composite classloader set"
    (let [source-cl            (.getClassLoader (req/request-class))
          composite-cl         (cl/make-composite-classloader source-cl)
          executor             (wp/make-clojure-executor 2 composite-cl)
          latch                (CountDownLatch. 1)
          captured-classloader (atom nil)]
      (.start executor)
      (.execute executor
                (fn []
                  (reset! captured-classloader
                          (.getContextClassLoader (Thread/currentThread)))
                  (.countDown latch)))
      (.await latch 5 TimeUnit/SECONDS)
      (.stop executor (Duration/ofSeconds 5))
      (is (= composite-cl @captured-classloader)))))

(deftest executor-can-load-clojure-classes-test
  (testing "executor threads can load ClojureJobRequest via Class.forName"
    (let [source-cl    (.getClassLoader (req/request-class))
          composite-cl (cl/make-composite-classloader source-cl)
          executor     (wp/make-clojure-executor 2 composite-cl)
          latch        (CountDownLatch. 1)
          loaded-class (atom nil)
          load-error   (atom nil)]
      (.start executor)
      (.execute executor
                (fn []
                  (try
                    (let [cl  (.getContextClassLoader (Thread/currentThread))
                          cls (Class/forName "clj_jobrunr.request.ClojureJobRequest"
                                             true cl)]
                      (reset! loaded-class cls))
                    (catch Exception e
                      (reset! load-error e)))
                  (.countDown latch)))
      (.await latch 5 TimeUnit/SECONDS)
      (.stop executor (Duration/ofSeconds 5))
      (is (nil? @load-error) (str "Failed to load class: " @load-error))
      (is (some? @loaded-class))
      (is (= "clj_jobrunr.request.ClojureJobRequest" (.getName @loaded-class))))))

;; ---------------------------------------------------------------------------
;; Worker Policy Tests
;; ---------------------------------------------------------------------------

(deftest make-clojure-worker-policy-implements-interface-test
  (testing "make-clojure-worker-policy returns a BackgroundJobServerWorkerPolicy"
    (let [policy (wp/make-clojure-worker-policy 2)]
      (is (instance? BackgroundJobServerWorkerPolicy policy)))))

(deftest worker-policy-creates-executor-test
  (testing "worker policy creates a working executor"
    (let [source-cl (.getClassLoader (req/request-class))
          policy    (wp/make-clojure-worker-policy 4 source-cl)
          executor  (.toJobRunrExecutor policy)]
      (is (instance? JobRunrExecutor executor))
      (is (= 4 (.getWorkerCount executor))))))

(deftest default-worker-count-test
  (testing "default-worker-count returns available processors"
    (is (= (.availableProcessors (Runtime/getRuntime))
           (wp/default-worker-count)))))

;; ---------------------------------------------------------------------------
;; Integration Test - Full ClassLoader Chain
;; ---------------------------------------------------------------------------

(deftest full-classloader-chain-test
  (testing "full chain: policy -> executor -> thread -> load class -> instantiate"
    (let [source-cl (.getClassLoader (req/request-class))
          policy    (wp/make-clojure-worker-policy 2 source-cl)
          executor  (.toJobRunrExecutor policy)
          latch     (CountDownLatch. 1)
          result    (atom nil)
          error     (atom nil)]
      (.start executor)
      (.execute executor
                (fn []
                  (try
                    (let [cl          (.getContextClassLoader (Thread/currentThread))
                          request-cls (Class/forName "clj_jobrunr.request.ClojureJobRequest"
                                                     true cl)
                          handler-cls (Class/forName "clj_jobrunr.request.ClojureJobRequestHandler"
                                                     true cl)
                          ;; Instantiate handler via reflection (like JobRunr does)
                          constructor (.getDeclaredConstructor handler-cls (into-array Class []))
                          handler     (.newInstance constructor (object-array []))]
                      (reset! result {:request-class request-cls
                                      :handler-class handler-cls
                                      :handler-instance handler}))
                    (catch Exception e
                      (reset! error e)))
                  (.countDown latch)))
      (.await latch 5 TimeUnit/SECONDS)
      (.stop executor (Duration/ofSeconds 5))
      (is (nil? @error) (str "Error in worker thread: " @error))
      (is (some? (:request-class @result)))
      (is (some? (:handler-class @result)))
      (is (some? (:handler-instance @result))))))
