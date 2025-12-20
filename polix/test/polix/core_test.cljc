(ns polix.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.ast :as ast]
   [polix.core :as core])
  #?(:clj (:import (polix.policy Policy))))

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
    #?(:clj (is (true? (core/thunkable? #'core/evaluate))))
    (is (true? (core/thunkable? '(+ 1 2))))
    (is (false? (core/thunkable? :doc/foo)))
    (is (false? (core/thunkable? "literal")))
    (is (false? (core/thunkable? 42)))))

(deftest classify-token-test
  (testing "classifying document accessors"
    (let [node (core/classify-token :doc/actor-role [0 1])]
      (is (= ::ast/doc-accessor (:type node)))
      (is (= :actor-role (:value node)))
      (is (= [0 1] (:position node)))))

  (testing "classifying URI accessors"
    (let [node (core/classify-token :uri/uri [0 1])]
      (is (= ::ast/uri (:type node)))
      (is (= :uri (:value node)))
      (is (= [0 1] (:position node)))))

  (testing "classifying literals"
    (let [node (core/classify-token "admin" [0 2])]
      (is (= ::ast/literal (:type node)))
      (is (= "admin" (:value node)))
      (is (= [0 2] (:position node)))))

  #?(:clj
     (testing "classifying thunks"
       (let [node (core/classify-token #'core/evaluate [1 0])]
         (is (= ::ast/thunk (:type node)))
         (is (fn? (:value node)))
         (is (= [1 0] (:position node)))))))

(deftest parse-policy-literal-test
  (testing "parsing simple literals"
    (let [result (core/parse-policy "admin")]
      (is (core/ok? result))
      (is (= (core/ast-node ::ast/literal "admin" [0 0])
             (core/unwrap result)))))

  (testing "parsing document accessors"
    (let [result (core/parse-policy :doc/actor-role)]
      (is (core/ok? result))
      (is (= (core/ast-node ::ast/doc-accessor :actor-role [0 0])
             (core/unwrap result))))))

(deftest parse-policy-function-call-test
  (testing "parsing simple function call"
    (let [result (core/parse-policy [:= :doc/actor-role "admin"])]
      (is (core/ok? result))
      (is (= (core/ast-node ::ast/function-call
                            :=
                            [0 0]
                            [(core/ast-node ::ast/doc-accessor :actor-role [0 1])
                             (core/ast-node ::ast/literal "admin" [0 2])])
             (core/unwrap result)))))

  (testing "parsing nested function calls"
    (let [result (core/parse-policy [:or [:= :doc/actor-role "admin"]
                                     [:= :doc/actor-role "user"]])]
      (is (core/ok? result))
      (is (= (core/ast-node ::ast/function-call
                            :or
                            [0 0]
                            [(core/ast-node ::ast/function-call
                                            :=
                                            [0 1]
                                            [(core/ast-node ::ast/doc-accessor :actor-role [0 2])
                                             (core/ast-node ::ast/literal "admin" [0 3])])
                             (core/ast-node ::ast/function-call
                                            :=
                                            [0 2]
                                            [(core/ast-node ::ast/doc-accessor :actor-role [0 3])
                                             (core/ast-node ::ast/literal "user" [0 4])])])
             (core/unwrap result)))))

  (testing "parsing function call with multiple arguments"
    (let [result (core/parse-policy [:match :uri/uri "prefix:" :doc/actor-name "/*"])]
      (is (core/ok? result))
      (is (= (core/ast-node ::ast/function-call
                            :match
                            [0 0]
                            [(core/ast-node ::ast/uri :uri [0 1])
                             (core/ast-node ::ast/literal "prefix:" [0 2])
                             (core/ast-node ::ast/doc-accessor :actor-name [0 3])
                             (core/ast-node ::ast/literal "/*" [0 4])])
             (core/unwrap result))))))

(deftest extract-doc-keys-test
  (testing "extracting document keys from simple policy"
    (let [result (core/parse-policy [:= :doc/actor-role "admin"])
          ast    (core/unwrap result)]
      (is (= #{:actor-role} (core/extract-doc-keys ast)))))

  (testing "extracting document keys from nested policy"
    (let [result (core/parse-policy [:or [:= :doc/actor-role "admin"]
                                     [:= :doc/actor-name "bob"]])
          ast    (core/unwrap result)]
      (is (= #{:actor-role :actor-name} (core/extract-doc-keys ast)))))

  (testing "extracting no keys from policy with only literals"
    (let [result (core/parse-policy [:match :uri/uri "prefix:"])
          ast    (core/unwrap result)]
      (is (= #{} (core/extract-doc-keys ast))))))

(deftest parse-policy-error-test
  (testing "invalid function name - string"
    (let [result (core/parse-policy ["not-a-keyword" :doc/foo])]
      (is (core/error? result))
      (let [error (core/unwrap result)]
        (is (= :invalid-function-name (:error error)))
        (is (= "not-a-keyword" (:value error)))
        (is (= [0 0] (:position error))))))

  (testing "invalid function name - number"
    (let [result (core/parse-policy [42 :doc/foo])]
      (is (core/error? result))
      (let [error (core/unwrap result)]
        (is (= :invalid-function-name (:error error)))
        (is (= 42 (:value error))))))

  (testing "nested error propagates up"
    (let [result (core/parse-policy [:or ["invalid" :doc/foo] [:= :doc/bar "baz"]])]
      (is (core/error? result))
      (let [error (core/unwrap result)]
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
    #?(:clj (is (instance? Policy TestPolicy))
       :cljs (is (and (map? TestPolicy) (:name TestPolicy) (:ast TestPolicy))))
    (is (= 'TestPolicy (:name TestPolicy)))
    (is (= "A test policy for testing" (:docstring TestPolicy)))
    (is (= #{:actor-role} (:schema TestPolicy)))
    (is (some? (:ast TestPolicy))))

  (testing "policy without docstring"
    #?(:clj (is (instance? Policy TestPolicyWithoutDocstring))
       :cljs (is (and (map? TestPolicyWithoutDocstring) (:name TestPolicyWithoutDocstring) (:ast TestPolicyWithoutDocstring))))
    (is (= 'TestPolicyWithoutDocstring (:name TestPolicyWithoutDocstring)))
    (is (nil? (:docstring TestPolicyWithoutDocstring)))
    (is (= #{:role :name} (:schema TestPolicyWithoutDocstring)))
    (is (some? (:ast TestPolicyWithoutDocstring)))))

(deftest evaluate-with-nil-values-test
  (testing "evaluating doc-accessor with nil value vs missing key"
    (let [policy-ast         (core/unwrap (core/parse-policy :doc/status))
          result-with-nil    (core/evaluate policy-ast {:status nil})
          result-without-key (core/evaluate policy-ast {})]
      (is (core/ok? result-with-nil))
      (is (nil? (core/unwrap result-with-nil)))
      (is (core/error? result-without-key))
      (is (= :missing-document-key (:error (core/unwrap result-without-key))))))

  (testing "evaluating doc-accessor with false value"
    (let [policy-ast (core/unwrap (core/parse-policy :doc/active))
          result     (core/evaluate policy-ast {:active false})]
      (is (core/ok? result))
      (is (false? (core/unwrap result))))))
