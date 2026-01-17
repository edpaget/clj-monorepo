(ns clj-jobrunr.request-test
  "Tests for ClojureJobRequest and ClojureJobRequestHandler."
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
   [clj-jobrunr.job :refer [defjob]]
   [clj-jobrunr.request :as req]
   [clj-jobrunr.serialization :as ser]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]])
  (:import
   [clj_jobrunr.request ClojureJobRequest ClojureJobRequestHandler]
   [org.jobrunr.jobs.lambdas JobRequest JobRequestHandler]
   [org.jobrunr.utils.mapper.gson GsonJsonMapper]))

;; Test job for execution tests
(def test-results (atom []))

(defn register-test-jobs!
  "Registers test job handlers. Called in fixture to ensure registration
  even after other test namespaces may have affected the multimethod."
  []
  (defjob test-request-job
    "A test job that records its execution."
    [{:keys [value]}]
    (swap! test-results conj value)
    {:processed value}))

(use-fixtures :each
  (fn [f]
    (reset! test-results [])
    (register-test-jobs!)
    (f)))

;; ---------------------------------------------------------------------------
;; Basic Type Tests
;; ---------------------------------------------------------------------------

(deftest clojure-job-request-implements-interface-test
  (testing "ClojureJobRequest implements JobRequest"
    (let [request (ClojureJobRequest. "{:test true}")]
      (is (instance? JobRequest request)))))

(deftest clojure-job-request-handler-implements-interface-test
  (testing "ClojureJobRequestHandler implements JobRequestHandler"
    (let [handler (ClojureJobRequestHandler.)]
      (is (instance? JobRequestHandler handler)))))

(deftest get-job-request-handler-returns-correct-class-test
  (testing "getJobRequestHandler returns ClojureJobRequestHandler class"
    (let [request       (ClojureJobRequest. "{:test true}")
          handler-class (.getJobRequestHandler request)]
      (is (= ClojureJobRequestHandler handler-class)))))

;; ---------------------------------------------------------------------------
;; Factory Function Tests
;; ---------------------------------------------------------------------------

(deftest make-job-request-test
  (testing "make-job-request creates request with serialized EDN"
    (let [request (req/make-job-request ::test-request-job {:value 42})]
      (is (instance? ClojureJobRequest request))
      (is (string? (.edn request)))
      (is (str/includes? (.edn request) ":job-type")))))

(deftest request-edn-extracts-edn-test
  (testing "request-edn extracts the EDN string"
    (let [edn     "{:job-type :test :payload {}}"
          request (ClojureJobRequest. edn)]
      (is (= edn (req/request-edn request))))))

;; ---------------------------------------------------------------------------
;; Handler Execution Tests
;; ---------------------------------------------------------------------------

(deftest handler-executes-job-test
  (testing "ClojureJobRequestHandler executes the job correctly"
    (let [serializer (ser/default-serializer)
          request    (req/make-job-request serializer
                                           ::test-request-job
                                           {:value "handler-test"})
          handler    (ClojureJobRequestHandler.)]
      ;; Bind the serializer for the handler
      (binding [ser/*serializer* serializer]
        (.run handler request))
      (is (= ["handler-test"] @test-results)))))

;; ---------------------------------------------------------------------------
;; Gson Serialization Tests
;; ---------------------------------------------------------------------------

(deftest gson-can-serialize-request-test
  (testing "Gson can serialize ClojureJobRequest to JSON"
    (let [request (ClojureJobRequest. "{:job-type :test :payload {:x 1}}")
          gson    (GsonJsonMapper.)
          json    (.serialize gson request)]
      (is (string? json))
      (is (str/includes? json "edn")))))

(deftest gson-can-deserialize-request-test
  (testing "Gson can deserialize JSON back to ClojureJobRequest"
    (let [original-edn "{:job-type :test :payload {:x 1}}"
          request      (ClojureJobRequest. original-edn)
          gson         (GsonJsonMapper.)
          json         (.serialize gson request)
          restored     (.deserialize gson json ClojureJobRequest)]
      (is (instance? ClojureJobRequest restored))
      (is (= original-edn (.edn restored))))))

(deftest gson-roundtrip-preserves-edn-test
  (testing "Gson serialization roundtrip preserves EDN content"
    (let [serializer   (ser/default-serializer)
          request      (req/make-job-request serializer ::test-request-job {:value 123})
          original-edn (.edn request)
          gson         (GsonJsonMapper.)
          json         (.serialize gson request)
          restored     (.deserialize gson json ClojureJobRequest)]
      (is (= original-edn (.edn restored))))))

;; ---------------------------------------------------------------------------
;; Class Loading Tests
;; ---------------------------------------------------------------------------

(deftest handler-class-returns-class-test
  (testing "handler-class returns the ClojureJobRequestHandler class"
    (is (= ClojureJobRequestHandler (req/handler-class)))))

(deftest request-class-returns-class-test
  (testing "request-class returns the ClojureJobRequest class"
    (is (= ClojureJobRequest (req/request-class)))))

(deftest handler-has-no-arg-constructor-test
  (testing "ClojureJobRequestHandler can be instantiated via reflection"
    (let [handler-class ClojureJobRequestHandler
          constructor   (.getDeclaredConstructor handler-class (into-array Class []))
          instance      (.newInstance constructor (object-array []))]
      (is (instance? ClojureJobRequestHandler instance)))))
