(ns exclusive-initializer.core-test
  (:require
   [clojure.test :refer [deftest is testing] :as t]
   [exclusive-initializer.core :as core]))

(t/use-fixtures :each (fn [f] (core/reset-locks!) (f)))

(deftest initialize-run-once-test
  (testing "initialization happens only once"
    (let [run-count (atom 0)
          lock-name ::test-lock]
      (core/initialize! lock-name (swap! run-count inc))
      (is (= 1 @run-count))

      (core/initialize! lock-name (swap! run-count inc))
      (is (= 1 @run-count) "should not run again for the same lock"))))

(deftest initialize-not-shared-test
  (testing "different locks have different states"
    (let [run-count   (atom 0)
          lock-name-1 ::test-lock-1
          lock-name-2 ::test-lock-2]

      (core/initialize! lock-name-1 (swap! run-count inc))
      (is (= 1 @run-count))

      (core/initialize! lock-name-2 (swap! run-count inc))
      (is (= 2 @run-count) "should run for a different lock"))))

(deftest initialize-concurrency-test
  (testing "initialization happens only once under concurrency"
    (let [run-count   (atom 0)
          lock-name   ::concurrent-lock
          num-threads 10
          promises    (repeatedly num-threads promise)
          threads     (mapv (fn [p]
                              (Thread.
                               (fn []
                                 (core/initialize! lock-name
                                                   (Thread/sleep 10)
                                                   (swap! run-count inc))
                                 (deliver p :done))))
                            promises)]
      (doseq [t threads] (.start t))
      (doseq [p promises] (deref p 1000 :timeout))
      (is (= 1 @run-count)))))

(deftest deinitialize-test
  (testing "deinitialization allows re-initialization"
    (let [run-count (atom 0)
          lock-name ::deinit-test-lock]

      ;; First run, should initialize
      (core/initialize! lock-name (swap! run-count inc))
      (is (= 1 @run-count) "should run the first time")

      ;; Second run, should not re-initialize
      (core/initialize! lock-name (swap! run-count inc))
      (is (= 1 @run-count) "should not run the second time")

      ;; De-initialize
      (core/deinitialize! lock-name)

      ;; Third run, should re-initialize
      (core/initialize! lock-name (swap! run-count inc))
      (is (= 2 @run-count) "should run again after deinitialization"))))
