(ns clj-jobrunr.java-bridge-test
  "Tests for the AOT-compiled Java bridge class."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require [clj-jobrunr.bridge :as bridge]
            [clj-jobrunr.java-bridge :as java-bridge]
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
;; Java Bridge Tests
;; ---------------------------------------------------------------------------

(deftest java-bridge-run-executes-job-test
  (testing "ClojureBridge.run dispatches to handler via execute!"
    (let [executed (atom nil)]
      (defjob bridge-test-job
        [payload]
        (reset! executed payload)
        :done)

      ;; Create EDN for the job
      (let [serializer (ser/default-serializer)
            edn        (bridge/job-edn serializer ::bridge-test-job {:data 42})]
        ;; Call the bridge's -run function (same as ClojureBridge.run)
        (binding [ser/*serializer* serializer]
          (let [result (java-bridge/-run edn)]
            (is (= :done result))
            (is (= {:data 42} @executed))))))))

(deftest java-bridge-run-with-default-serializer-test
  (testing "ClojureBridge.run uses default serializer when none bound"
    (let [executed (atom nil)]
      (defjob default-ser-job
        [payload]
        (reset! executed payload)
        :success)

      (let [serializer (ser/default-serializer)
            edn        (bridge/job-edn serializer ::default-ser-job {:value "test"})]
        ;; Call without binding *serializer* - should use default
        (binding [ser/*serializer* nil]
          (let [result (java-bridge/-run edn)]
            (is (= :success result))
            (is (= {:value "test"} @executed))))))))

(deftest java-bridge-run-propagates-exceptions-test
  (testing "ClojureBridge.run propagates exceptions from handlers"
    (defjob error-bridge-job
      [payload]
      (throw (ex-info "Bridge job failed" {:reason (:reason payload)})))

    (let [serializer (ser/default-serializer)
          edn        (bridge/job-edn serializer ::error-bridge-job {:reason :network})]
      (binding [ser/*serializer* serializer]
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo
             #"Bridge job failed"
             (java-bridge/-run edn)))))))

(deftest java-bridge-class-has-static-run-method-test
  (testing "ClojureBridge class exists and has static run method"
    ;; This test verifies the AOT-compiled class is loadable
    (let [bridge-class (Class/forName "clj_jobrunr.ClojureBridge")
          run-method   (.getMethod bridge-class "run" (into-array Class [String]))]
      (is (some? bridge-class))
      (is (some? run-method))
      (is (java.lang.reflect.Modifier/isStatic (.getModifiers run-method))))))
