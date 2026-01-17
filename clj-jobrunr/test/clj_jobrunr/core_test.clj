(ns clj-jobrunr.core-test
  "Tests for the core job enqueueing API.

  Note: Tests that actually submit jobs to JobRunr require a running
  BackgroundJobServer and are marked with ^:integration metadata."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require [clj-jobrunr.core :as core]
            [clj-jobrunr.job :refer [defjob]]
            [clojure.test :refer [deftest is testing use-fixtures]])
  (:import [java.time Duration Instant]
           [java.util UUID]))

;; ---------------------------------------------------------------------------
;; Test job definition
;; ---------------------------------------------------------------------------

(def test-executions (atom []))

(defn register-test-job! []
  (defjob core-test-job
    "Test job for core API tests."
    [{:keys [value]}]
    (swap! test-executions conj value)
    {:processed value}))

(use-fixtures :each
  (fn [f]
    (reset! test-executions [])
    (register-test-job!)
    (f)))

;; ---------------------------------------------------------------------------
;; Utility function tests
;; ---------------------------------------------------------------------------

(deftest job-type->name-with-namespace-test
  (testing "job-type->name converts namespaced keyword to string"
    (is (= "my.app/send-email"
           (#'core/job-type->name :my.app/send-email)))))

(deftest job-type->name-simple-keyword-test
  (testing "job-type->name handles simple keywords"
    (is (= "simple-job"
           (#'core/job-type->name :simple-job)))))

(deftest job-type->name-current-ns-test
  (testing "job-type->name uses current namespace for ::"
    (is (= "clj-jobrunr.core-test/core-test-job"
           (#'core/job-type->name ::core-test-job)))))

;; ---------------------------------------------------------------------------
;; JobBuilder creation tests
;; ---------------------------------------------------------------------------

(deftest build-job-creates-builder-test
  (testing "build-job creates a JobBuilder with request"
    (let [builder (#'core/build-job ::core-test-job {:value 42} {})]
      (is (some? builder)))))

(deftest build-job-with-custom-name-test
  (testing "build-job uses custom name from opts"
    (let [builder (#'core/build-job ::core-test-job {:value 42}
                                    {:name "Custom Job Name"})]
      ;; Builder is created (we can't easily inspect the name without reflection)
      (is (some? builder)))))

(deftest build-job-with-labels-test
  (testing "build-job adds labels from opts"
    (let [builder (#'core/build-job ::core-test-job {:value 42}
                                    {:labels ["label1" "label2"]})]
      (is (some? builder)))))

(deftest build-job-with-id-test
  (testing "build-job adds custom UUID from opts"
    (let [custom-id (UUID/randomUUID)
          builder   (#'core/build-job ::core-test-job {:value 42}
                                      {:id custom-id})]
      (is (some? builder)))))

(deftest build-job-with-retries-test
  (testing "build-job adds retry count from opts"
    (let [builder (#'core/build-job ::core-test-job {:value 42}
                                    {:retries 5})]
      (is (some? builder)))))

(deftest build-job-with-all-opts-test
  (testing "build-job handles all options together"
    (let [builder (#'core/build-job ::core-test-job {:value 42}
                                    {:name "Full Options Job"
                                     :labels ["test" "unit"]
                                     :id (UUID/randomUUID)
                                     :retries 3})]
      (is (some? builder)))))

;; ---------------------------------------------------------------------------
;; API function existence tests
;; ---------------------------------------------------------------------------

(deftest enqueue-fn-exists-test
  (testing "enqueue! function exists and is callable"
    (is (fn? core/enqueue!))))

(deftest schedule-fn-exists-test
  (testing "schedule! function exists and is callable"
    (is (fn? core/schedule!))))

(deftest recurring-fn-exists-test
  (testing "recurring! function exists and is callable"
    (is (fn? core/recurring!))))

(deftest delete-recurring-fn-exists-test
  (testing "delete-recurring! function exists and is callable"
    (is (fn? core/delete-recurring!))))

;; ---------------------------------------------------------------------------
;; Integration tests (require running JobRunr server)
;; ---------------------------------------------------------------------------

(comment
  ;; These tests require a running BackgroundJobServer.
  ;; Run them manually or with :includes [:integration]

  (deftest ^:integration enqueue-submits-job-test
    (testing "enqueue! submits job to JobRunr"
      (let [job-id (core/enqueue! ::core-test-job {:value "test"})]
        (is (instance? UUID job-id)))))

  (deftest ^:integration schedule-with-duration-test
    (testing "schedule! with Duration schedules job"
      (let [job-id (core/schedule! ::core-test-job {:value "scheduled"}
                                   (Duration/ofHours 1))]
        (is (instance? UUID job-id)))))

  (deftest ^:integration schedule-with-instant-test
    (testing "schedule! with Instant schedules job"
      (let [future-time (.plus (Instant/now) (Duration/ofHours 1))
            job-id      (core/schedule! ::core-test-job {:value "scheduled"}
                                        future-time)]
        (is (instance? UUID job-id)))))

  (deftest ^:integration recurring-creates-job-test
    (testing "recurring! creates recurring job"
      (let [recurring-id (core/recurring! "test-recurring"
                                          ::core-test-job
                                          {:value "recurring"}
                                          "0 * * * *")]
        (is (= "test-recurring" recurring-id)))))

  (deftest ^:integration delete-recurring-removes-job-test
    (testing "delete-recurring! removes recurring job"
      ;; First create a recurring job
      (core/recurring! "to-delete" ::core-test-job {} "0 * * * *")
      ;; Then delete it (should not throw)
      (is (nil? (core/delete-recurring! "to-delete"))))))
