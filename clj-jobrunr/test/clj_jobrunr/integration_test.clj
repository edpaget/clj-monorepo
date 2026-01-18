(ns clj-jobrunr.integration-test
  "Integration tests for clj-jobrunr.

  These tests require Docker for Testcontainers. They are marked with
  ^:integration metadata and can be excluded from regular test runs.

  To run integration tests:
    clojure -X:test :includes [:integration]

  To run only unit tests:
    clojure -X:test :excludes [:integration]"
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
   [clj-jobrunr.bridge :as bridge]
   [clj-jobrunr.core :as core]
   [clj-jobrunr.job :refer [defjob handle-job]]
   [clj-jobrunr.request :as req]
   [clj-jobrunr.serialization :as ser]
   [clj-jobrunr.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
   [java.time Duration Instant]))

;; ---------------------------------------------------------------------------
;; Test Jobs
;; ---------------------------------------------------------------------------

(defn register-test-jobs!
  "Registers test job handlers. Called in fixture to ensure registration
  even after other test namespaces may have cleared the multimethod."
  []
  (defjob test-simple-job
    "A simple test job that records its execution."
    [payload]
    (swap! tu/executions conj {:job-type ::test-simple-job
                               :payload payload
                               :at (Instant/now)})
    :success)

  (defjob test-failing-job
    "A job that fails a configurable number of times before succeeding."
    [{:keys [fail-count] :as payload}]
    (let [attempt-count (count (filter #(= ::test-failing-job (:job-type %)) @tu/executions))]
      (swap! tu/executions conj {:job-type ::test-failing-job
                                 :payload payload
                                 :attempt attempt-count
                                 :at (Instant/now)})
      (when (< attempt-count fail-count)
        (throw (ex-info "Intentional failure" {:attempt attempt-count})))
      :success)))

;; ---------------------------------------------------------------------------
;; Test Fixtures
;; ---------------------------------------------------------------------------

(defn register-jobs-fixture
  "Registers test jobs before each test."
  [f]
  (register-test-jobs!)
  (f))

;; Unit tests: just reset executions and register jobs
(use-fixtures :each
  (fn [f]
    (reset! tu/executions [])
    (register-test-jobs!)
    (f)))

;; ---------------------------------------------------------------------------
;; Unit Tests (can run without database)
;; ---------------------------------------------------------------------------

(deftest test-job-handler-works-test
  (testing "test job can be called via handle-job"
    (handle-job test-simple-job {:user-id 123})
    (is (= 1 (count @tu/executions)))
    (is (= {:user-id 123} (:payload (first @tu/executions))))))

(deftest test-job-multimethod-works-test
  (testing "test job dispatches through multimethod"
    (handle-job ::test-simple-job {:data "test"})
    (is (= 1 (count @tu/executions)))
    (is (= {:data "test"} (:payload (first @tu/executions))))))

(deftest test-job-request-creation-test
  (testing "job request is created correctly"
    (tu/with-test-serializer
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
      (is (= 1 (count @tu/executions)))
      (is (= {:from "bridge"} (:payload (first @tu/executions)))))))

;; ---------------------------------------------------------------------------
;; Integration Tests (require Docker for Testcontainers)
;; ---------------------------------------------------------------------------

(deftest ^:integration enqueue-executes-job-test
  (testing "enqueued job executes and records execution"
    ((tu/integration-fixture)
     (fn []
       (register-test-jobs!)
       (reset! tu/executions [])
       ;; Use the var instead of keyword - demonstrates new API
       (let [job-id (core/enqueue! test-simple-job {:value "test-enqueue"})]
         (is (uuid? job-id))
         (let [status (tu/wait-for-job tu/*storage-provider* job-id :timeout-ms 10000)]
           (is (= :succeeded status))
           (is (= 1 (count @tu/executions)))
           (is (= {:value "test-enqueue"} (:payload (first @tu/executions))))))))))

(deftest ^:integration schedule-executes-at-time-test
  (testing "scheduled job executes at the specified time"
    ((tu/integration-fixture)
     (fn []
       (register-test-jobs!)
       (reset! tu/executions [])
       (let [job-id (core/schedule! ::test-simple-job {:value "scheduled"}
                                    (Duration/ofSeconds 2))]
         ;; Should be scheduled, not executed yet
         (is (= :scheduled (tu/job-status tu/*storage-provider* job-id)))
         (is (empty? @tu/executions))
         ;; Wait for execution
         (let [status (tu/wait-for-job tu/*storage-provider* job-id :timeout-ms 10000)]
           (is (= :succeeded status))
           (is (= 1 (count @tu/executions)))))))))

(deftest ^:integration recurring-executes-on-schedule-test
  (testing "recurring job executes on cron schedule"
    ((tu/integration-fixture)
     (fn []
       (register-test-jobs!)
       (reset! tu/executions [])
       ;; Every 5 seconds (minimum supported by JobRunr 8.x)
       (core/recurring! "test-recurring" ::test-simple-job {:value "recurring"}
                        "*/5 * * * * *")
       ;; Wait for at least 2 executions (need 11+ seconds for 2 runs at 5s interval)
       (Thread/sleep 12000)
       (is (>= (count @tu/executions) 2))
       ;; Delete and verify no more executions
       (core/delete-recurring! "test-recurring")
       (let [count-before (count @tu/executions)]
         (Thread/sleep 3000)
         (is (= count-before (count @tu/executions))))))))

(deftest ^:integration failed-job-retries-test
  (testing "failed job is retried and eventually succeeds"
    ((tu/integration-fixture)
     (fn []
       (register-test-jobs!)
       (reset! tu/executions [])
       (let [job-id (core/enqueue! ::test-failing-job {:fail-count 2}
                                   {:retries 3})
             status (tu/wait-for-job tu/*storage-provider* job-id :timeout-ms 30000)]
         (is (= :succeeded status))
         ;; Should have 3 attempts: fail, fail, succeed
         (let [attempts (filter #(= ::test-failing-job (:job-type %)) @tu/executions)]
           (is (= 3 (count attempts)))
           (is (= [0 1 2] (map :attempt attempts)))))))))

(deftest ^:integration custom-serialization-roundtrip-test
  (testing "java.time types are serialized and deserialized correctly"
    ((tu/integration-fixture)
     (fn []
       (register-test-jobs!)
       (reset! tu/executions [])
       (let [test-instant (Instant/parse "2024-06-15T10:30:00Z")
             job-id       (core/enqueue! ::test-simple-job {:scheduled-at test-instant})
             status       (tu/wait-for-job tu/*storage-provider* job-id :timeout-ms 10000)]
         (is (= :succeeded status))
         (let [payload (:payload (first @tu/executions))]
           (is (instance? Instant (:scheduled-at payload)))
           (is (= test-instant (:scheduled-at payload)))))))))

(deftest ^:integration job-survives-restart-test
  (testing "pending job executes after server restart"
    ((tu/integration-fixture)
     (fn []
       (register-test-jobs!)
       (reset! tu/executions [])
       ;; Schedule job for 3 seconds from now
       (let [job-id (core/schedule! ::test-simple-job {:value "restart-test"}
                                    (Duration/ofSeconds 3))]
         (is (= :scheduled (tu/job-status tu/*storage-provider* job-id)))
         ;; Restart JobRunr server (keeps same database)
         (tu/restart-jobrunr!)
         (register-test-jobs!)
         (reset! tu/executions [])
         ;; Wait for job to execute on restarted server
         (let [status (tu/wait-for-job tu/*storage-provider* job-id :timeout-ms 10000)]
           (is (= :succeeded status))
           (is (= 1 (count @tu/executions)))))))))
