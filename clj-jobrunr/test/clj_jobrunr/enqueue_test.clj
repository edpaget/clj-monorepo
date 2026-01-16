(ns clj-jobrunr.enqueue-test
  (:require [clj-jobrunr.enqueue :as enqueue]
            [clj-jobrunr.serialization :as ser]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import [java.time Instant Duration]))

;; ---------------------------------------------------------------------------
;; enqueue! tests
;; ---------------------------------------------------------------------------

(deftest enqueue-creates-job-request-test
  (testing "enqueue! creates a job request with correct structure"
    (let [serializer (ser/default-serializer)
          request    (enqueue/make-job-request serializer ::send-email {:to "user@example.com"})]
      (is (= ::send-email (:job-type request)))
      (is (= {:to "user@example.com"} (:payload request)))
      (is (string? (:edn request)))
      (is (= "clj_jobrunr.enqueue_test.SendEmail" (:class-name request))))))

(deftest enqueue-with-complex-payload-test
  (testing "enqueue! handles complex nested payloads"
    (let [serializer (ser/default-serializer)
          payload    {:user {:id 123 :roles [:admin :user]}
                      :items [{:id 1} {:id 2}]}
          request    (enqueue/make-job-request serializer ::process-order payload)]
      (is (= payload (:payload request)))
      ;; Verify EDN can be deserialized back
      (let [parsed (ser/deserialize serializer (:edn request))]
        (is (= payload (:payload parsed)))))))

;; ---------------------------------------------------------------------------
;; schedule! tests
;; ---------------------------------------------------------------------------

(deftest schedule-with-instant-test
  (testing "schedule! accepts Instant for scheduling"
    (let [serializer (ser/default-serializer)
          run-at     (Instant/parse "2024-06-15T10:30:00Z")
          request    (enqueue/make-scheduled-request serializer ::send-reminder {:user-id 1} run-at)]
      (is (= ::send-reminder (:job-type request)))
      (is (= run-at (:scheduled-at request)))
      (is (instance? Instant (:scheduled-at request))))))

(deftest schedule-with-duration-test
  (testing "schedule! accepts Duration, converts to Instant"
    (let [serializer (ser/default-serializer)
          duration   (Duration/ofHours 2)
          before     (Instant/now)
          request    (enqueue/make-scheduled-request serializer ::cleanup {} duration)
          after      (Instant/now)]
      (is (= ::cleanup (:job-type request)))
      (is (instance? Instant (:scheduled-at request)))
      ;; Scheduled time should be approximately 2 hours from now
      (let [scheduled    (:scheduled-at request)
            expected-min (.plus before duration)
            expected-max (.plus after duration)]
        (is (not (.isBefore scheduled expected-min)))
        (is (not (.isAfter scheduled expected-max)))))))

(deftest schedule-with-duration-zero-test
  (testing "schedule! with zero duration schedules for now"
    (let [serializer (ser/default-serializer)
          before     (Instant/now)
          request    (enqueue/make-scheduled-request serializer ::immediate {} (Duration/ofSeconds 0))
          after      (Instant/now)]
      (is (not (.isBefore (:scheduled-at request) before)))
      (is (not (.isAfter (:scheduled-at request) after))))))

;; ---------------------------------------------------------------------------
;; recurring! tests
;; ---------------------------------------------------------------------------

(deftest recurring-with-cron-test
  (testing "recurring! accepts cron expression and job-id"
    (let [serializer (ser/default-serializer)
          request    (enqueue/make-recurring-request serializer
                                                     "daily-report"
                                                     ::generate-report
                                                     "0 9 * * *"
                                                     {:report-type :daily})]
      (is (= "daily-report" (:job-id request)))
      (is (= ::generate-report (:job-type request)))
      (is (= "0 9 * * *" (:cron request)))
      (is (= {:report-type :daily} (:payload request))))))

(deftest recurring-with-complex-cron-test
  (testing "recurring! handles complex cron expressions"
    (let [serializer (ser/default-serializer)
          ;; Every weekday at 6:30 AM
          cron       "30 6 * * 1-5"
          request    (enqueue/make-recurring-request serializer "weekday-job" ::weekday-task cron {})]
      (is (= cron (:cron request))))))

;; ---------------------------------------------------------------------------
;; delete-recurring! tests
;; ---------------------------------------------------------------------------

(deftest delete-recurring-request-test
  (testing "delete-recurring creates request with job-id"
    (let [request (enqueue/make-delete-recurring-request "daily-report")]
      (is (= "daily-report" (:job-id request))))))

;; ---------------------------------------------------------------------------
;; Custom serializer tests
;; ---------------------------------------------------------------------------

(deftest enqueue-with-custom-serializer-test
  (testing "enqueue uses custom serializer for EDN"
    (ser/install-time-print-methods!)
    (let [readers    {'time/instant #(Instant/parse %)}
          serializer (ser/make-serializer {:readers readers})
          instant    (Instant/parse "2024-01-15T10:30:00Z")
          request    (enqueue/make-job-request serializer ::timed-job {:run-at instant})]
      ;; Verify the instant is in the EDN
      (is (str/includes? (:edn request) "#time/instant"))
      ;; Verify it deserializes correctly
      (let [parsed (ser/deserialize serializer (:edn request))]
        (is (= instant (get-in parsed [:payload :run-at])))))))
