(ns repository.cursor-test
  (:require [clojure.test :refer [deftest is testing]]
            [repository.cursor :as cursor]))

(deftest encode-decode-roundtrip-test
  (testing "encodes and decodes basic cursor data"
    (let [data    {:id 123 :name "test"}
          encoded (cursor/encode data)
          decoded (cursor/decode encoded)]
      (is (string? encoded))
      (is (= data decoded)))))

(deftest encode-decode-with-dates-test
  (testing "encodes and decodes cursor with inst values"
    (let [now     #inst "2024-01-15T10:30:00.000Z"
          data    {:created-at now :id "abc"}
          encoded (cursor/encode data)
          decoded (cursor/decode encoded)]
      (is (= data decoded)))))

(deftest encode-nil-returns-nil-test
  (testing "encode returns nil for nil or empty input"
    (is (nil? (cursor/encode nil)))
    (is (nil? (cursor/encode {})))))

(deftest decode-nil-returns-nil-test
  (testing "decode returns nil for nil or empty input"
    (is (nil? (cursor/decode nil)))
    (is (nil? (cursor/decode "")))))

(deftest decode-invalid-returns-nil-test
  (testing "decode returns nil for invalid cursor"
    (is (nil? (cursor/decode "not-valid-base64!!!")))))

(deftest from-entity-test
  (testing "extracts order-by fields from entity"
    (let [entity   {:id "abc" :name "Alice" :created-at #inst "2024-01-15"}
          order-by [[:created-at :desc] [:id :asc]]
          result   (cursor/from-entity entity order-by)]
      (is (= {:created-at #inst "2024-01-15" :id "abc"} result)))))

(deftest from-entity-nil-test
  (testing "returns nil for nil entity or empty order-by"
    (is (nil? (cursor/from-entity nil [[:id :asc]])))
    (is (nil? (cursor/from-entity {:id 1} nil)))
    (is (nil? (cursor/from-entity {:id 1} [])))))

(deftest page-info-with-more-pages-test
  (testing "page-info indicates more pages when data equals limit"
    (let [data     [{:id 1} {:id 2} {:id 3}]
          order-by [[:id :asc]]
          info     (cursor/page-info data order-by 3)]
      (is (:has-next-page info))
      (is (some? (:end-cursor info))))))

(deftest page-info-no-more-pages-test
  (testing "page-info indicates no more pages when data less than limit"
    (let [data     [{:id 1} {:id 2}]
          order-by [[:id :asc]]
          info     (cursor/page-info data order-by 3)]
      (is (not (:has-next-page info)))
      (is (nil? (:end-cursor info))))))
