(ns clj-jobrunr.bridge-test
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require [clj-jobrunr.bridge :as bridge]
            [clj-jobrunr.job :refer [defjob handle-job]]
            [clj-jobrunr.serialization :as ser]
            [clojure.test :refer [deftest is testing use-fixtures]]))

;; Reset multimethod between tests
(defn reset-handlers [f]
  (doseq [k (keys (methods handle-job))]
    (when (not= k :default)
      (remove-method handle-job k)))
  (f))

(use-fixtures :each reset-handlers)

;; ---------------------------------------------------------------------------
;; job-class-name tests
;; ---------------------------------------------------------------------------

(deftest job-class-name-simple-test
  (testing "converts simple job keyword to Java class name"
    (is (= "clj_jobrunr.jobs.SendEmail"
           (bridge/job-class-name :my.ns/send-email)))))

(deftest job-class-name-hyphenated-test
  (testing "converts hyphens to camel case"
    (is (= "clj_jobrunr.jobs.ProcessUserOrder"
           (bridge/job-class-name :my.ns/process-user-order)))))

(deftest job-class-name-single-word-test
  (testing "handles single word job names"
    (is (= "clj_jobrunr.jobs.Cleanup"
           (bridge/job-class-name :my.ns/cleanup)))))

(deftest job-class-name-preserves-case-test
  (testing "capitalizes first letter of each segment"
    (is (= "clj_jobrunr.jobs.SendSmsNotification"
           (bridge/job-class-name :my.ns/send-sms-notification)))))

;; ---------------------------------------------------------------------------
;; job-edn tests
;; ---------------------------------------------------------------------------

(deftest job-edn-format-test
  (testing "job EDN includes job-type and payload"
    (let [serializer (ser/default-serializer)
          edn-str    (bridge/job-edn serializer :my.ns/send-email {:to "user@example.com"})
          parsed     (ser/deserialize serializer edn-str)]
      (is (= :my.ns/send-email (:job-type parsed)))
      (is (= {:to "user@example.com"} (:payload parsed))))))

(deftest job-edn-with-complex-payload-test
  (testing "handles complex nested payloads"
    (let [serializer (ser/default-serializer)
          payload    {:user {:id 123 :name "Alice"}
                      :items [{:sku "A1" :qty 2} {:sku "B2" :qty 1}]
                      :metadata {:source :web}}
          edn-str    (bridge/job-edn serializer :my.ns/process-order payload)
          parsed     (ser/deserialize serializer edn-str)]
      (is (= :my.ns/process-order (:job-type parsed)))
      (is (= payload (:payload parsed))))))

(deftest job-edn-with-custom-serializer-test
  (testing "uses provided serializer for writing"
    (ser/install-time-print-methods!)
    (let [readers    {'time/instant #(java.time.Instant/parse %)}
          serializer (ser/make-serializer {:readers readers})
          instant    (java.time.Instant/parse "2024-01-15T10:30:00Z")
          edn-str    (bridge/job-edn serializer :my.ns/schedule-task {:run-at instant})
          parsed     (ser/deserialize serializer edn-str)]
      (is (= instant (get-in parsed [:payload :run-at]))))))

;; ---------------------------------------------------------------------------
;; execute! tests
;; ---------------------------------------------------------------------------

(deftest execute-deserializes-and-dispatches-test
  (testing "execute! deserializes EDN and calls handle-job"
    (let [executed (atom nil)]
      ;; Define a test job
      (defjob test-execute-job
        [payload]
        (reset! executed payload)
        :job-completed)

      ;; Create job EDN
      (let [serializer (ser/default-serializer)
            edn-str    (bridge/job-edn serializer ::test-execute-job {:data 42})]
        ;; Execute it
        (bridge/execute! serializer edn-str)
        ;; Verify handler was called with payload
        (is (= {:data 42} @executed))))))

(deftest execute-returns-handler-result-test
  (testing "execute! returns the result from the handler"
    (defjob test-return-job
      [payload]
      {:processed true :input payload})

    (let [serializer (ser/default-serializer)
          edn-str    (bridge/job-edn serializer ::test-return-job {:x 1})
          result     (bridge/execute! serializer edn-str)]
      (is (= {:processed true :input {:x 1}} result)))))

(deftest execute-propagates-exceptions-test
  (testing "execute! propagates exceptions from handlers"
    (defjob test-error-job
      [payload]
      (throw (ex-info "Job failed" {:reason (:reason payload)})))

    (let [serializer (ser/default-serializer)
          edn-str    (bridge/job-edn serializer ::test-error-job {:reason :network-error})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Job failed"
           (bridge/execute! serializer edn-str))))))

(deftest execute-with-unknown-job-type-test
  (testing "execute! throws for unknown job types"
    (let [serializer (ser/default-serializer)
          edn-str    (bridge/job-edn serializer ::unknown-job {:data 1})]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"No handler registered"
           (bridge/execute! serializer edn-str))))))
