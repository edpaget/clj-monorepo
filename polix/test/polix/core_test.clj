(ns polix.core-test
  (:require
   [cats.core :as m]
   [cats.monad.either :as either]
   [clojure.test :refer [deftest is testing]]
   [polix.core :as core])
  (:import
   (polix.core Policy)))

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

(deftest doc-accessor-test
  (testing "identifying document accessors"
    (is (true? (core/doc-accessor? :doc/actor-role)))
    (is (true? (core/doc-accessor? :doc/actor-name)))
    (is (false? (core/doc-accessor? :actor-role)))
    (is (false? (core/doc-accessor? :uri/uri)))
    (is (false? (core/doc-accessor? "not-a-keyword")))))

(deftest uri-accessor-test
  (testing "identifying URI accessors"
    (is (true? (core/uri-accessor? :uri/uri)))
    (is (true? (core/uri-accessor? :uri/resource)))
    (is (false? (core/uri-accessor? :doc/actor-role)))
    (is (false? (core/uri-accessor? :other/thing)))
    (is (false? (core/uri-accessor? "not-a-keyword")))))

(deftest thunkable-test
  (testing "identifying forms that should be thunked"
    (is (true? (core/thunkable? #'core/map-document)))
    (is (true? (core/thunkable? '(+ 1 2))))
    (is (false? (core/thunkable? :doc/foo)))
    (is (false? (core/thunkable? "literal")))
    (is (false? (core/thunkable? 42)))))

(deftest classify-token-test
  (testing "classifying document accessors"
    (let [node (core/classify-token :doc/actor-role [0 1])]
      (is (= ::core/doc-accessor (:type node)))
      (is (= :actor-role (:value node)))
      (is (= [0 1] (:position node)))))

  (testing "classifying URI accessors"
    (let [node (core/classify-token :uri/uri [0 1])]
      (is (= ::core/uri (:type node)))
      (is (= :uri (:value node)))
      (is (= [0 1] (:position node)))))

  (testing "classifying literals"
    (let [node (core/classify-token "admin" [0 2])]
      (is (= ::core/literal (:type node)))
      (is (= "admin" (:value node)))
      (is (= [0 2] (:position node)))))

  (testing "classifying thunks"
    (let [node (core/classify-token #'core/map-document [1 0])]
      (is (= ::core/thunk (:type node)))
      (is (fn? (:value node)))
      (is (= [1 0] (:position node))))))

(deftest parse-policy-literal-test
  (testing "parsing simple literals"
    (let [result (core/parse-policy "admin")]
      (is (either/right? result))
      (is (= (core/ast-node ::core/literal "admin" [0 0])
             (m/extract result)))))

  (testing "parsing document accessors"
    (let [result (core/parse-policy :doc/actor-role)]
      (is (either/right? result))
      (is (= (core/ast-node ::core/doc-accessor :actor-role [0 0])
             (m/extract result))))))

(deftest parse-policy-function-call-test
  (testing "parsing simple function call"
    (let [result (core/parse-policy [:= :doc/actor-role "admin"])]
      (is (either/right? result))
      (is (= (core/ast-node ::core/function-call
                            :=
                            [0 0]
                            [(core/ast-node ::core/doc-accessor :actor-role [0 1])
                             (core/ast-node ::core/literal "admin" [0 2])])
             (m/extract result)))))

  (testing "parsing nested function calls"
    (let [result (core/parse-policy [:or [:= :doc/actor-role "admin"]
                                     [:= :doc/actor-role "user"]])]
      (is (either/right? result))
      (is (= (core/ast-node ::core/function-call
                            :or
                            [0 0]
                            [(core/ast-node ::core/function-call
                                            :=
                                            [0 1]
                                            [(core/ast-node ::core/doc-accessor :actor-role [0 2])
                                             (core/ast-node ::core/literal "admin" [0 3])])
                             (core/ast-node ::core/function-call
                                            :=
                                            [0 2]
                                            [(core/ast-node ::core/doc-accessor :actor-role [0 3])
                                             (core/ast-node ::core/literal "user" [0 4])])])
             (m/extract result)))))

  (testing "parsing function call with multiple arguments"
    (let [result (core/parse-policy [:match :uri/uri "prefix:" :doc/actor-name "/*"])]
      (is (either/right? result))
      (is (= (core/ast-node ::core/function-call
                            :match
                            [0 0]
                            [(core/ast-node ::core/uri :uri [0 1])
                             (core/ast-node ::core/literal "prefix:" [0 2])
                             (core/ast-node ::core/doc-accessor :actor-name [0 3])
                             (core/ast-node ::core/literal "/*" [0 4])])
             (m/extract result))))))

(deftest extract-doc-keys-test
  (testing "extracting document keys from simple policy"
    (let [result (core/parse-policy [:= :doc/actor-role "admin"])
          ast (m/extract result)]
      (is (= #{:actor-role} (core/extract-doc-keys ast)))))

  (testing "extracting document keys from nested policy"
    (let [result (core/parse-policy [:or [:= :doc/actor-role "admin"]
                                     [:= :doc/actor-name "bob"]])
          ast (m/extract result)]
      (is (= #{:actor-role :actor-name} (core/extract-doc-keys ast)))))

  (testing "extracting no keys from policy with only literals"
    (let [result (core/parse-policy [:match :uri/uri "prefix:"])
          ast (m/extract result)]
      (is (= #{} (core/extract-doc-keys ast))))))

(deftest parse-policy-error-test
  (testing "invalid function name - string"
    (let [result (core/parse-policy ["not-a-keyword" :doc/foo])]
      (is (either/left? result))
      (let [error (m/extract result)]
        (is (= :invalid-function-name (:error error)))
        (is (= "not-a-keyword" (:value error)))
        (is (= [0 0] (:position error))))))

  (testing "invalid function name - number"
    (let [result (core/parse-policy [42 :doc/foo])]
      (is (either/left? result))
      (let [error (m/extract result)]
        (is (= :invalid-function-name (:error error)))
        (is (= 42 (:value error))))))

  (testing "nested error propagates up"
    (let [result (core/parse-policy [:or ["invalid" :doc/foo] [:= :doc/bar "baz"]])]
      (is (either/left? result))
      (let [error (m/extract result)]
        (is (= :invalid-function-name (:error error)))
        (is (= "invalid" (:value error)))))))

(core/defpolicy TestPolicy
  "A test policy for testing"
  [:= :doc/actor-role "admin"])

(core/defpolicy TestPolicyWithoutDocstring
  [:or [:= :doc/role "admin"]
   [:= :doc/name "alice"]])

(deftest defpolicy-test
  (testing "policy with docstring"
    (is (instance? Policy TestPolicy))
    (is (= 'TestPolicy (:name TestPolicy)))
    (is (= "A test policy for testing" (:docstring TestPolicy)))
    (is (= #{:actor-role} (:schema TestPolicy)))
    (is (some? (:ast TestPolicy))))

  (testing "policy without docstring"
    (is (instance? Policy TestPolicyWithoutDocstring))
    (is (= 'TestPolicyWithoutDocstring (:name TestPolicyWithoutDocstring)))
    (is (nil? (:docstring TestPolicyWithoutDocstring)))
    (is (= #{:role :name} (:schema TestPolicyWithoutDocstring)))
    (is (some? (:ast TestPolicyWithoutDocstring)))))
