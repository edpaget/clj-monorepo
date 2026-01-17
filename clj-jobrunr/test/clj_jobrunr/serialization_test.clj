(ns clj-jobrunr.serialization-test
  (:require
   [clj-jobrunr.serialization :as ser]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]])
  (:import
   [java.time Instant Duration LocalDate]
   [java.util UUID Date]))

(deftest default-serialization-test
  (testing "default serializer round-trips basic data"
    (let [s    (ser/default-serializer)
          data {:name "test" :count 42 :active? true}]
      (is (= data (ser/deserialize s (ser/serialize s data)))))))

(deftest serialize-simple-map-test
  (testing "round-trips a basic map with various types"
    (let [s    (ser/default-serializer)
          data {:string "hello"
                :number 123
                :float 3.14
                :keyword :foo
                :vector [1 2 3]
                :nested {:a {:b :c}}}]
      (is (= data (ser/deserialize s (ser/serialize s data)))))))

(deftest serialize-with-standard-readers-test
  (testing "handles #inst tagged literal"
    (let [s      (ser/default-serializer)
          date   (Date.)
          data   {:created-at date}
          result (ser/deserialize s (ser/serialize s data))]
      (is (= date (:created-at result)))))

  (testing "handles #uuid tagged literal"
    (let [s      (ser/default-serializer)
          id     (UUID/randomUUID)
          data   {:id id}
          result (ser/deserialize s (ser/serialize s data))]
      (is (= id (:id result))))))

(deftest serialize-with-custom-readers-test
  (testing "default readers include time/instant"
    (let [s       (ser/default-serializer)
          instant (Instant/parse "2024-01-15T10:30:00Z")
          edn-str "{:scheduled-at #time/instant \"2024-01-15T10:30:00Z\"}"
          result  (ser/deserialize s edn-str)]
      (is (= instant (:scheduled-at result)))))

  (testing "custom readers merge with default readers"
    (let [readers {'custom/tag (fn [v] {:custom v})}
          s       (ser/make-serializer {:readers readers})
          id      (UUID/randomUUID)
          edn-str (str "{:id #uuid \"" id "\" :custom #custom/tag \"value\"}")
          result  (ser/deserialize s edn-str)]
      (is (= id (:id result)))
      (is (= {:custom "value"} (:custom result))))))

(deftest serialize-with-custom-write-fn-test
  (testing "custom write function is used"
    (let [write-calls  (atom [])
          custom-write (fn [data]
                         (swap! write-calls conj data)
                         (pr-str data))
          s            (ser/make-serializer {:write-fn custom-write})
          data         {:foo :bar}]
      (ser/serialize s data)
      (is (= [data] @write-calls))))

  (testing "custom read function is used"
    (let [read-calls  (atom [])
          custom-read (fn [s]
                        (swap! read-calls conj s)
                        (edn/read-string s))
          s           (ser/make-serializer {:read-fn custom-read})
          edn-str     "{:foo :bar}"]
      (ser/deserialize s edn-str)
      (is (= [edn-str] @read-calls)))))

(deftest make-serializer-options-test
  (testing "readers option creates read-fn with those readers"
    (let [readers {'custom/tag (fn [v] {:custom v})}
          s       (ser/make-serializer {:readers readers})
          result  (ser/deserialize s "#custom/tag \"value\"")]
      (is (= {:custom "value"} result))))

  (testing "read-fn takes precedence over readers"
    (let [custom-read (fn [_] :custom-read-called)
          s           (ser/make-serializer {:readers {'ignored/tag identity}
                                            :read-fn custom-read})]
      (is (= :custom-read-called (ser/deserialize s "anything"))))))

(deftest default-time-serialization-test
  (testing "Instant serializes as tagged literal by default"
    (let [s          (ser/default-serializer)
          instant    (Instant/parse "2024-01-15T10:30:00Z")
          serialized (ser/serialize s {:at instant})]
      (is (str/includes? serialized "#time/instant"))))

  (testing "Duration serializes as tagged literal by default"
    (let [s          (ser/default-serializer)
          duration   (Duration/ofHours 2)
          serialized (ser/serialize s {:duration duration})]
      (is (str/includes? serialized "#time/duration"))))

  (testing "LocalDate serializes as tagged literal by default"
    (let [s          (ser/default-serializer)
          date       (LocalDate/of 2024 1 15)
          serialized (ser/serialize s {:date date})]
      (is (str/includes? serialized "#time/local-date")))))

(deftest time-round-trip-test
  (testing "java.time types round-trip with default serializer"
    (let [s          (ser/default-serializer)
          instant    (Instant/parse "2024-01-15T10:30:00Z")
          duration   (Duration/ofHours 2)
          local-date (LocalDate/of 2024 1 15)
          data       {:instant instant :duration duration :date local-date}
          result     (ser/deserialize s (ser/serialize s data))]
      (is (= instant (:instant result)))
      (is (= duration (:duration result)))
      (is (= local-date (:date result))))))

(deftest exclude-defaults-test
  (testing "exclude-readers removes default readers"
    (let [s (ser/make-serializer {:exclude-readers ['time/instant]})]
      (is (thrown? Exception
                   (ser/deserialize s "#time/instant \"2024-01-15T10:30:00Z\"")))))

  (testing "exclude-writers removes default writers"
    (let [s          (ser/make-serializer {:exclude-writers [Instant]})
          instant    (Instant/parse "2024-01-15T10:30:00Z")
          serialized (ser/serialize s {:at instant})]
      (is (not (str/includes? serialized "#time/instant"))))))
