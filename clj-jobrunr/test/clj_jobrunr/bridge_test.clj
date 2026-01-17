(ns clj-jobrunr.bridge-test
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
   [clj-jobrunr.bridge :as bridge]
   [clj-jobrunr.job :refer [defjob]]
   [clj-jobrunr.serialization :as ser]
   [clj-jobrunr.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :each tu/reset-handlers)

;; ---------------------------------------------------------------------------
;; job-class-name tests
;; ---------------------------------------------------------------------------

(deftest job-class-name-derives-package-from-namespace-test
  (testing "derives Java package from keyword namespace"
    (is (= "my.ns.SendEmail"
           (bridge/job-class-name :my.ns/send-email)))))

(deftest job-class-name-hyphenated-name-test
  (testing "converts hyphens in name to PascalCase"
    (is (= "my.ns.ProcessUserOrder"
           (bridge/job-class-name :my.ns/process-user-order)))))

(deftest job-class-name-hyphenated-namespace-test
  (testing "converts hyphens in namespace to underscores"
    (is (= "admin_tasks.core.Cleanup"
           (bridge/job-class-name :admin-tasks.core/cleanup)))))

(deftest job-class-name-prevents-conflicts-test
  (testing "different namespaces produce different class names"
    (is (not= (bridge/job-class-name :user.jobs/send-email)
              (bridge/job-class-name :admin.jobs/send-email)))
    (is (= "user.jobs.SendEmail"
           (bridge/job-class-name :user.jobs/send-email)))
    (is (= "admin.jobs.SendEmail"
           (bridge/job-class-name :admin.jobs/send-email)))))

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

(deftest job-edn-with-time-types-test
  (testing "serializes java.time types as tagged literals by default"
    (let [serializer (ser/default-serializer)
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
