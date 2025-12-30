(ns graphql-client.transformer-test
  "Tests for Malli transformers."
  (:require
   [cljs.test :as t :include-macros true]
   [graphql-client.transformer :as transformer]))

(def TestEnum
  [:enum :status/ACTIVE :status/INACTIVE :status/PENDING])

(def TestSchema
  [:map
   [:first-name :string]
   [:last-name :string]
   [:status TestEnum]])

(t/deftest decode-converts-camel-keys
  (t/testing "converts camelCase string keys to kebab-case keywords"
    (let [data   {"firstName" "Alice" "lastName" "Smith" "status" "ACTIVE"}
          result (transformer/decode data TestSchema)]
      (t/is (= "Alice" (:first-name result)))
      (t/is (= "Smith" (:last-name result))))))

(t/deftest decode-converts-enum-strings
  (t/testing "converts enum strings to namespaced keywords"
    (let [data   {"firstName" "Alice" "lastName" "Smith" "status" "ACTIVE"}
          result (transformer/decode data TestSchema)]
      (t/is (= :status/ACTIVE (:status result))))))

(t/deftest decode-handles-nested-maps
  (t/testing "decodes nested map structures"
    (let [schema [:map
                  [:user [:map
                          [:user-name :string]
                          [:email :string]]]]
          data   {"user" {"userName" "bob" "email" "bob@example.com"}}
          result (transformer/decode data schema)]
      (t/is (= "bob" (get-in result [:user :user-name])))
      (t/is (= "bob@example.com" (get-in result [:user :email]))))))

(t/deftest decode-preserves-typename
  (t/testing "preserves __typename as :__typename keyword"
    (let [schema [:map
                  [:__typename :string]
                  [:id :string]]
          data   {"__typename" "User" "id" "123"}
          result (transformer/decode data schema)]
      (t/is (= "User" (:__typename result)))
      (t/is (= "123" (:id result))))))

(t/deftest decode-preserves-uppercase-keys
  (t/testing "preserves uppercase keys as uppercase keywords"
    (let [schema [:map
                  [:HOME :int]
                  [:AWAY :int]]
          data   {"HOME" 42 "AWAY" 38}
          result (transformer/decode data schema)]
      (t/is (= 42 (:HOME result)))
      (t/is (= 38 (:AWAY result))))))

(t/deftest enum-transformer-invalid-value-passthrough
  (t/testing "passes through invalid enum values unchanged"
    (let [data   {"firstName" "Alice" "lastName" "Smith" "status" "UNKNOWN"}
          result (transformer/decode data TestSchema)]
      ;; Invalid values that don't match valid enum options are passed through as-is
      (t/is (= "UNKNOWN" (:status result))))))
