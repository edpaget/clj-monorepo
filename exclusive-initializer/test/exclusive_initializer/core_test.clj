(ns exclusive-initializer.core-test
  (:require
   [clojure.test :refer [deftest is testing] :as t]
   [exclusive-initializer.core :as core]))

(t/use-fixtures :each (fn [f] (core/reset-locks!) (f)))

(deftest wrap-run-once-test
  (testing "initialization happens only once"
    (let [run-count (atom 0)
          lock-name ::test-lock]
      (core/wrap [{:keys [lock unlock initialize! initialized?]} lock-name]
                 (lock)
                 (try
                   (when-not (initialized?)
                     (swap! run-count inc)
                     (initialize!))
                   (finally
                     (unlock))))
      (is (= 1 @run-count))

      (core/wrap [{:keys [lock unlock initialize! initialized?]} lock-name]
                 (lock)
                 (try
                   (when-not (initialized?)
                     (swap! run-count inc)
                     (initialize!))
                   (finally
                     (unlock))))
      (is (= 1 @run-count) "should not run again for the same lock"))))

(deftest wrap-not-shared-test
  (testing "different locks have different states"
    (let [run-count (atom 0)
          lock-name-1 ::test-lock-1
          lock-name-2 ::test-lock-2]

      (core/wrap [{:keys [lock unlock initialize! initialized?]} lock-name-1]
                 (lock)
                 (try (when-not (initialized?) (swap! run-count inc) (initialize!))
                      (finally (unlock))))
      (is (= 1 @run-count))

      (core/wrap [{:keys [lock unlock initialize! initialized?]} lock-name-2]
                 (lock)
                 (try (when-not (initialized?) (swap! run-count inc) (initialize!))
                      (finally (unlock))))
      (is (= 2 @run-count) "should run for a different lock"))))

(deftest wrap-concurrency-test
  (testing "initialization happens only once under concurrency"
    (let [run-count (atom 0)
          lock-name ::concurrent-lock
          num-threads 10
          promises (repeatedly num-threads promise)
          threads (mapv (fn [p]
                          (Thread.
                           (fn []
                             (core/wrap [{:keys [lock unlock initialize! initialized?]} lock-name]
                                        (lock)
                                        (try
                                          (when-not (initialized?)
                                            (swap! run-count inc)
                                            (initialize!))
                                          (finally
                                            (unlock)))
                                        (deliver p :done)))))
                        promises)]
      (doseq [t threads] (.start t))
      (doseq [p promises] (deref p 1000 :timeout))
      (is (= 1 @run-count)))))

(deftest wrap-deinitialize-test
  (testing "deinitialization allows re-initialization"
    (let [run-count (atom 0)
          lock-name ::deinit-test-lock]

      ;; First run, should initialize
      (core/wrap [{:keys [lock unlock initialize! initialized?]} lock-name]
        (lock)
        (try
          (when-not (initialized?)
            (swap! run-count inc)
            (initialize!))
          (finally (unlock))))
      (is (= 1 @run-count) "should run the first time")

      ;; Second run, should not re-initialize
      (core/wrap [{:keys [lock unlock initialize! initialized?]} lock-name]
        (lock)
        (try
          (when-not (initialized?)
            (swap! run-count inc)
            (initialize!))
          (finally (unlock))))
      (is (= 1 @run-count) "should not run the second time")

      ;; De-initialize
      (core/wrap [{:keys [lock unlock deinitialize!]} lock-name]
        (lock)
        (try
          (deinitialize!)
          (finally (unlock))))

      ;; Third run, should re-initialize
      (core/wrap [{:keys [lock unlock initialize! initialized?]} lock-name]
        (lock)
        (try
          (when-not (initialized?)
            (swap! run-count inc)
            (initialize!))
          (finally (unlock))))
      (is (= 2 @run-count) "should run again after deinitialization"))))
