(ns clj-jobrunr.classloader-test
  "Tests for custom classloader and ThreadFactory."
  (:require [clj-jobrunr.classloader :as cl]
            [clojure.test :refer [deftest is testing]]))

;; Define a test type dynamically (no AOT)
(deftype TestJobRequest [edn])

(deftest get-clojure-classloader-test
  (testing "can get Clojure's base classloader"
    (let [loader (cl/get-clojure-classloader)]
      (is (some? loader))
      (is (instance? ClassLoader loader)))))

(deftest make-composite-classloader-test
  (testing "composite classloader can load Clojure core classes"
    (let [composite (cl/make-composite-classloader)
          cls       (Class/forName "clojure.lang.IFn" true composite)]
      (is (= "clojure.lang.IFn" (.getName cls))))))

(deftest make-clojure-aware-thread-factory-test
  (testing "factory creates threads with correct classloader"
    (let [composite (cl/make-composite-classloader)
          factory   (cl/make-clojure-aware-thread-factory composite)
          result    (promise)
          thread    (.newThread factory
                                (fn []
                                  (deliver result
                                           (.getContextClassLoader
                                            (Thread/currentThread)))))]
      (.start thread)
      (.join thread 1000)
      (is (= composite @result)))))

(deftest thread-can-load-clojure-class-test
  (testing "worker thread can load Clojure classes via Class.forName"
    (is (true? (cl/verify-classloader-setup)))))

(deftest thread-can-load-deftype-class-test
  (testing "worker thread can load dynamically-defined deftype class"
    ;; Use the ACTUAL classloader of the TestJobRequest class
    ;; This ensures we use the right DynamicClassLoader chain
    (let [class-name      (.getName TestJobRequest)
          source-loader   (.getClassLoader TestJobRequest)
          composite       (cl/make-composite-classloader source-loader)
          result          (promise)
          test-fn         (fn []
                            (try
                              (let [cl  (.getContextClassLoader (Thread/currentThread))
                                    cls (Class/forName class-name true cl)]
                                (deliver result {:success true
                                                 :class-name (.getName cls)}))
                              (catch Exception e
                                (deliver result {:success false
                                                 :error (.getMessage e)}))))
          thread          (Thread. test-fn)]
      (.setContextClassLoader thread composite)
      (.start thread)
      (.join thread 5000)
      (let [{:keys [success class-name error]} @result]
        (is success (str "Failed to load class: " error))
        (when success
          (is (= (.getName TestJobRequest) class-name)))))))
