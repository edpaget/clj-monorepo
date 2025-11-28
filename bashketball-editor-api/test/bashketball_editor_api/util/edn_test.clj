(ns bashketball-editor-api.util.edn-test
  (:require
   [bashketball-editor-api.util.edn :as edn-util]
   [clojure.test :refer [deftest is testing]])
  (:import
   [java.time Instant]))

(deftest instant-serialization-test
  (testing "Instant serializes to #inst format"
    (let [instant (Instant/parse "2024-01-15T10:30:00Z")
          output  (pr-str instant)]
      (is (= "#inst \"2024-01-15T10:30:00Z\"" output))))

  (testing "Instant round-trips through pr-str and read-edn"
    (let [instant  (Instant/parse "2024-06-20T14:45:30.123Z")
          output   (pr-str instant)
          restored (edn-util/read-edn output)]
      (is (instance? Instant restored))
      (is (= instant restored)))))

(deftest map-with-instant-test
  (testing "map with Instant values round-trips correctly"
    (let [now      (Instant/now)
          data     {:name "Test"
                    :created-at now
                    :updated-at now}
          output   (pr-str data)
          restored (edn-util/read-edn output)]
      (is (= (:name data) (:name restored)))
      (is (instance? Instant (:created-at restored)))
      (is (instance? Instant (:updated-at restored)))
      (is (= now (:created-at restored)))
      (is (= now (:updated-at restored))))))

(deftest nested-instant-test
  (testing "nested data with Instants round-trips correctly"
    (let [instant  (Instant/parse "2023-12-25T00:00:00Z")
          data     {:card {:name "Test Card"
                           :timestamps {:created-at instant
                                        :updated-at instant}}}
          output   (pr-str data)
          restored (edn-util/read-edn output)]
      (is (= instant (get-in restored [:card :timestamps :created-at])))
      (is (= instant (get-in restored [:card :timestamps :updated-at]))))))
