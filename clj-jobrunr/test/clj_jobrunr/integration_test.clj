(ns clj-jobrunr.integration-test
  "Integration tests for clj-jobrunr.

  These tests require a running PostgreSQL database. They are marked with
  ^:integration metadata and can be excluded from regular test runs.

  To run integration tests:
    clojure -X:test :includes [:integration]

  To run only unit tests:
    clojure -X:test :excludes [:integration]"
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require [clj-jobrunr.bridge :as bridge]
            [clj-jobrunr.job :refer [defjob handle-job]]
            [clj-jobrunr.request :as req]
            [clj-jobrunr.serialization :as ser]
            [clj-jobrunr.test-utils :as test-utils]
            [clojure.test :refer [deftest is testing use-fixtures]])
  (:import [java.time Instant]))

;; ---------------------------------------------------------------------------
;; Test Jobs
;; ---------------------------------------------------------------------------

(def executions
  "Atom to track job executions for verification."
  (atom []))

(defn register-test-jobs!
  "Registers test job handlers. Called in fixture to ensure registration
  even after other test namespaces may have cleared the multimethod."
  []
  ;; Define test jobs - defjob inside a function still registers them
  (defjob test-simple-job
    "A simple test job that records its execution."
    [payload]
    (swap! executions conj {:job-type ::test-simple-job
                            :payload payload
                            :at (Instant/now)})
    :success)

  (defjob test-failing-job
    "A job that fails a configurable number of times before succeeding."
    [{:keys [fail-count] :as payload}]
    (let [attempt-count (count (filter #(= ::test-failing-job (:job-type %)) @executions))]
      (swap! executions conj {:job-type ::test-failing-job
                              :payload payload
                              :attempt attempt-count
                              :at (Instant/now)})
      (when (< attempt-count fail-count)
        (throw (ex-info "Intentional failure" {:attempt attempt-count})))
      :success)))

(defn reset-executions [f]
  (reset! executions [])
  (register-test-jobs!)
  (f))

;; ---------------------------------------------------------------------------
;; Integration Tests (require PostgreSQL)
;; ---------------------------------------------------------------------------

;; Note: The following tests are placeholders for integration tests that require
;; a PostgreSQL database. They are commented out to avoid failures in CI.
;; Uncomment and implement when running with a database.

(comment
  (deftest ^:integration enqueue-executes-job-test
    (testing "enqueued job executes and records execution"
      ;; This test requires JobRunr to be running with a PostgreSQL database.
      ;; The test fixture starts JobRunr, enqueues a job, waits for completion,
      ;; and verifies the execution was recorded.
      ))

  (deftest ^:integration schedule-executes-at-time-test
    (testing "scheduled job executes at the specified time"
      ;; Schedule job for 2 seconds in future
      ;; Verify not executed immediately
      ;; Wait and verify executed after delay
      ))

  (deftest ^:integration recurring-executes-on-schedule-test
    (testing "recurring job executes on cron schedule"
      ;; Schedule recurring job with short interval
      ;; Wait for multiple executions
      ;; Delete recurring job
      ;; Verify no more executions
      ))

  (deftest ^:integration failed-job-retries-test
    (testing "failed job is retried and eventually succeeds"
      ;; Define job that fails first N times
      ;; Enqueue and wait for success
      ;; Verify retry attempts happened
      ))

  (deftest ^:integration custom-serialization-roundtrip-test
    (testing "custom serialization works end-to-end"
      ;; Configure custom reader for #time/instant
      ;; Enqueue job with Instant in payload
      ;; Verify handler receives correct Instant value
      ))

  (deftest ^:integration job-survives-restart-test
    (testing "pending job executes after server restart"
      ;; Enqueue job
      ;; Stop JobRunr server
      ;; Start JobRunr server
      ;; Verify job executes
      )))

;; ---------------------------------------------------------------------------
;; Unit Tests (can run without database)
;; ---------------------------------------------------------------------------

(use-fixtures :each reset-executions)

(deftest test-job-function-works-test
  (testing "test job function can be called directly"
    (test-simple-job {:user-id 123})
    (is (= 1 (count @executions)))
    (is (= {:user-id 123} (:payload (first @executions))))))

(deftest test-job-multimethod-works-test
  (testing "test job dispatches through multimethod"
    (handle-job ::test-simple-job {:data "test"})
    (is (= 1 (count @executions)))
    (is (= {:data "test"} (:payload (first @executions))))))

(deftest test-job-request-creation-test
  (testing "job request is created correctly"
    (test-utils/with-test-serializer
      (let [serializer (ser/default-serializer)
            request    (req/make-job-request serializer ::test-simple-job {:x 1})
            edn-str    (req/request-edn request)
            parsed     (ser/deserialize serializer edn-str)]
        (is (= ::test-simple-job (:job-type parsed)))
        (is (= {:x 1} (:payload parsed)))
        (is (string? edn-str))))))

(deftest test-bridge-execute-test
  (testing "bridge execute! dispatches to handler"
    (let [serializer (ser/default-serializer)
          edn-str    (bridge/job-edn serializer ::test-simple-job {:from "bridge"})]
      (bridge/execute! serializer edn-str)
      (is (= 1 (count @executions)))
      (is (= {:from "bridge"} (:payload (first @executions)))))))
