(ns graphql-client.core-test
  "Tests for the core JS to Clojure conversion utilities."
  (:require
   [cljs.test :as t :include-macros true]
   [graphql-client.core :as core]))

(t/deftest js->clj-preserve-keys-converts-objects
  (t/testing "converts JS object to map preserving string keys"
    (let [js-obj #js {:firstName "Alice" :lastName "Smith"}
          result (core/js->clj-preserve-keys js-obj)]
      (t/is (map? result))
      (t/is (= "Alice" (get result "firstName")))
      (t/is (= "Smith" (get result "lastName"))))))

(t/deftest js->clj-preserve-keys-converts-arrays
  (t/testing "converts JS arrays to vectors"
    (let [js-arr #js [1 2 3]
          result (core/js->clj-preserve-keys js-arr)]
      (t/is (vector? result))
      (t/is (= [1 2 3] result)))))

(t/deftest js->clj-preserve-keys-handles-nested
  (t/testing "recursively converts nested structures"
    (let [js-obj #js {:user #js {:name "Bob" :items #js [#js {:id 1} #js {:id 2}]}}
          result (core/js->clj-preserve-keys js-obj)]
      (t/is (= "Bob" (get-in result ["user" "name"])))
      (t/is (= 1 (get-in result ["user" "items" 0 "id"]))))))

(t/deftest convert-key-kebabs-regular-strings
  (t/testing "converts camelCase string to kebab-case keyword"
    (t/is (= :first-name (core/convert-key "firstName")))
    (t/is (= :user-id (core/convert-key "userId")))))

(t/deftest convert-key-preserves-uppercase
  (t/testing "preserves uppercase string keys as uppercase keywords"
    (t/is (= :HOME (core/convert-key "HOME")))
    (t/is (= :AWAY (core/convert-key "AWAY")))))

(t/deftest convert-key-preserves-keywords
  (t/testing "passes through existing keywords unchanged"
    (t/is (= :existing (core/convert-key :existing)))
    (t/is (= :some-key (core/convert-key :some-key)))))

(t/deftest convert-remaining-string-keys-transforms-map
  (t/testing "converts string keys in a map to kebab-case keywords"
    (let [data   {"firstName" "Alice" "lastName" "Smith"}
          result (core/convert-remaining-string-keys data)]
      (t/is (= "Alice" (:first-name result)))
      (t/is (= "Smith" (:last-name result))))))

(t/deftest convert-remaining-string-keys-handles-nested
  (t/testing "recursively converts nested structures"
    (let [data   {"user" {"firstName" "Bob"}}
          result (core/convert-remaining-string-keys data)]
      (t/is (= "Bob" (get-in result [:user :first-name]))))))

(t/deftest convert-remaining-string-keys-preserves-vectors
  (t/testing "preserves vectors while converting nested maps"
    (let [data   {"items" [{"itemName" "A"} {"itemName" "B"}]}
          result (core/convert-remaining-string-keys data)]
      (t/is (vector? (:items result)))
      (t/is (= "A" (get-in result [:items 0 :item-name]))))))

(t/deftest get-typename-finds-string-key
  (t/testing "finds __typename as string key"
    (let [data {"__typename" "User" "id" "123"}]
      (t/is (= "User" (core/get-typename data))))))

(t/deftest get-typename-finds-keyword-key
  (t/testing "finds __typename as keyword"
    (let [data {:__typename "User" :id "123"}]
      (t/is (= "User" (core/get-typename data))))))

(t/deftest get-typename-returns-nil-when-absent
  (t/testing "returns nil when __typename is not present"
    (let [data {:id "123" :name "Test"}]
      (t/is (nil? (core/get-typename data))))))
