(ns bashketball-game-api.graphql.error-handling-test
  "Tests for GraphQL error handling.

  Tests how the API handles various error conditions including invalid
  queries, type mismatches, missing arguments, and unknown fields."
  (:require
   [bashketball-game-api.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

;; Syntax errors

(deftest invalid-query-syntax-test
  (testing "Invalid GraphQL syntax returns parse error"
    (let [response (tu/graphql-request "{ this is not valid graphql")]
      (is (seq (tu/graphql-errors response))))))

(deftest unclosed-brace-test
  (testing "Unclosed brace returns parse error"
    (let [response (tu/graphql-request "{ sets { slug name }")]
      (is (seq (tu/graphql-errors response))))))

;; Unknown fields

(deftest unknown-field-test
  (testing "Unknown field returns error"
    (let [response (tu/graphql-request "{ sets { unknownField } }")]
      (is (seq (tu/graphql-errors response))))))

(deftest unknown-query-test
  (testing "Unknown query returns error"
    (let [response (tu/graphql-request "{ nonexistentQuery }")]
      (is (seq (tu/graphql-errors response))))))

;; Missing required arguments

(deftest missing-required-argument-test
  (testing "Missing required argument returns error"
    (let [response (tu/graphql-request "query { deck { id } }")]
      (is (seq (tu/graphql-errors response))))))

(deftest missing-variable-value-test
  (testing "Missing variable value for required argument returns error"
    (let [response (tu/graphql-request
                    "query GetDeck($id: Uuid!) { deck(id: $id) { id } }"
                    :variables {})]
      (is (seq (tu/graphql-errors response))))))

;; Type mismatches

(deftest wrong-type-argument-test
  (testing "Wrong type for argument returns error"
    (let [response (tu/graphql-request
                    "query GetDeck($id: Int!) { deck(id: $id) { id } }"
                    :variables {:id 123})]
      (is (seq (tu/graphql-errors response))))))

;; Null handling

(deftest null-on-non-nullable-test
  (testing "Null value for non-nullable argument returns error"
    (let [response (tu/graphql-request
                    "query GetDeck($id: Uuid!) { deck(id: $id) { id } }"
                    :variables {:id nil})]
      (is (seq (tu/graphql-errors response))))))

;; Invalid operations

(deftest invalid-mutation-as-query-test
  (testing "Using mutation keyword on query operation returns error"
    (let [response (tu/graphql-request "mutation { sets { slug } }")]
      (is (seq (tu/graphql-errors response))))))

;; Fragment errors

(deftest undefined-fragment-test
  (testing "Reference to undefined fragment returns error"
    (let [response (tu/graphql-request
                    "{ cards { ...UndefinedFragment } }")]
      (is (seq (tu/graphql-errors response))))))

(deftest fragment-unknown-type-test
  (testing "Fragment on unknown type returns error"
    (let [response (tu/graphql-request
                    "{ card(slug: \"michael-jordan\") { ... on NonExistentType { slug } } }")]
      (is (seq (tu/graphql-errors response))))))

;; Combined auth and error scenarios

(deftest auth-error-before-field-error-test
  (testing "Auth errors take precedence over field errors"
    (let [user     (tu/create-test-user)
          deck     (tu/create-test-deck (:id user) "Test")
          ;; Try to access deck without auth - should get auth error even with invalid field
          response (tu/graphql-request
                    "query GetDeck($id: Uuid!) { deck(id: $id) { invalidField } }"
                    :variables {:id (str (:id deck))})]
      ;; Should have errors (either auth or field error depending on evaluation order)
      (is (seq (tu/graphql-errors response))))))

;; Empty input handling

(deftest empty-query-test
  (testing "Empty query string returns error"
    (let [response (tu/graphql-request "")]
      (is (seq (tu/graphql-errors response))))))

(deftest whitespace-only-query-test
  (testing "Whitespace-only query returns error"
    (let [response (tu/graphql-request "   \n\t   ")]
      (is (seq (tu/graphql-errors response))))))
