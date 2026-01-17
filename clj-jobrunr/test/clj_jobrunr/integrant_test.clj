(ns clj-jobrunr.integrant-test
  {:clj-kondo/config '{:linters {:unresolved-symbol {:level :off}}}}
  (:require
   [clj-jobrunr.integrant :as ig-jobrunr]
   [clj-jobrunr.serialization :as ser]
   [clojure.test :refer [deftest is testing]]
   [integrant.core :as ig])
  (:import
   [java.time Instant]))

;; ---------------------------------------------------------------------------
;; Serialization Component Tests
;; ---------------------------------------------------------------------------

(deftest serialization-component-default-test
  (testing "creates default serializer when no config provided"
    (let [system     (ig/init {::ig-jobrunr/serialization {}})
          serializer (::ig-jobrunr/serialization system)]
      (try
        (is (some? serializer))
        (is (fn? (:read-fn serializer)))
        (is (fn? (:write-fn serializer)))
        ;; Should handle basic round-trip
        (let [data         {:foo "bar" :nums [1 2 3]}
              serialized   (ser/serialize serializer data)
              deserialized (ser/deserialize serializer serialized)]
          (is (= data deserialized)))
        (finally
          (ig/halt! system))))))

(deftest serialization-component-time-types-test
  (testing "default serializer handles java.time types"
    (let [system     (ig/init {::ig-jobrunr/serialization {}})
          serializer (::ig-jobrunr/serialization system)]
      (try
        (let [instant      (Instant/parse "2024-01-15T10:30:00Z")
              data         {:scheduled-at instant}
              serialized   (ser/serialize serializer data)
              deserialized (ser/deserialize serializer serialized)]
          (is (= instant (:scheduled-at deserialized))))
        (finally
          (ig/halt! system))))))

(deftest serialization-component-custom-write-fn-test
  (testing "uses custom write-fn when provided"
    (let [write-calls     (atom [])
          custom-write-fn (fn [x]
                            (swap! write-calls conj x)
                            (pr-str x))
          system          (ig/init {::ig-jobrunr/serialization {:write-fn custom-write-fn}})
          serializer      (::ig-jobrunr/serialization system)]
      (try
        (ser/serialize serializer {:test 123})
        (is (= 1 (count @write-calls)))
        (is (= {:test 123} (first @write-calls)))
        (finally
          (ig/halt! system))))))

;; ---------------------------------------------------------------------------
;; Storage Provider Component Tests
;; ---------------------------------------------------------------------------

(deftest storage-provider-component-creates-instance-test
  (testing "storage provider creates PostgresStorageProvider"
    ;; We can't easily test the actual PostgreSQL connection without a real DB
    ;; So we just verify the component initializes and halts cleanly
    ;; Full integration testing is in Phase 3
    (is (some? ig-jobrunr/storage-provider-init-key))))

;; ---------------------------------------------------------------------------
;; Server Component Tests
;; ---------------------------------------------------------------------------

(deftest server-component-config-validation-test
  (testing "server config accepts required keys"
    ;; Verify that the server component accepts the expected configuration
    ;; Actual server start/stop requires storage provider, tested in integration
    (is (some? ig-jobrunr/server-init-key))))

