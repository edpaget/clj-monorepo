(ns polix.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.core :as core]))

(deftest map-document-get-test
  (testing "getting values from document"
    (let [doc (core/map-document {:foo "bar" :baz 42})]
      (is (= "bar" (core/doc-get doc :foo)))
      (is (= 42 (core/doc-get doc :baz)))
      (is (nil? (core/doc-get doc :missing))))))

(deftest map-document-keys-test
  (testing "retrieving all document keys"
    (let [doc (core/map-document {:foo "bar" :baz 42})]
      (is (= #{:foo :baz} (set (core/doc-keys doc)))))))

(deftest map-document-project-test
  (testing "projecting document to subset of keys"
    (let [doc (core/map-document {:foo "bar" :baz 42 :qux "hello"})
          projected (core/doc-project doc [:foo :baz])]
      (is (= "bar" (core/doc-get projected :foo)))
      (is (= 42 (core/doc-get projected :baz)))
      (is (nil? (core/doc-get projected :qux)))
      (is (= #{:foo :baz} (set (core/doc-keys projected)))))))

(deftest map-document-merge-test
  (testing "merging two documents with left-to-right precedence"
    (let [doc1 (core/map-document {:foo "bar" :baz 42})
          doc2 (core/map-document {:baz 99 :qux "hello"})
          merged (core/doc-merge doc1 doc2)]
      (is (= "bar" (core/doc-get merged :foo)))
      (is (= 99 (core/doc-get merged :baz)))
      (is (= "hello" (core/doc-get merged :qux)))
      (is (= #{:foo :baz :qux} (set (core/doc-keys merged)))))))

(deftest map-document-empty-test
  (testing "creating and using empty document"
    (let [doc (core/map-document {})]
      (is (empty? (core/doc-keys doc)))
      (is (nil? (core/doc-get doc :anything))))))
