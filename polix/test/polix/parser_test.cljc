(ns polix.parser-test
  "Tests for the policy parser, including nested path support."
  (:require
   [clojure.test :refer [deftest is testing]]
   [polix.ast :as ast]
   [polix.parser :as parser]
   [polix.result :as r]))

;;; ---------------------------------------------------------------------------
;;; parse-doc-path Tests
;;; ---------------------------------------------------------------------------

(deftest parse-doc-path-single-segment-test
  (testing "single segment path"
    (let [result (parser/parse-doc-path "role")]
      (is (r/ok? result))
      (is (= [:role] (r/unwrap result))))))

(deftest parse-doc-path-multiple-segments-test
  (testing "two segment path"
    (let [result (parser/parse-doc-path "user.name")]
      (is (r/ok? result))
      (is (= [:user :name] (r/unwrap result)))))
  (testing "deep nesting"
    (let [result (parser/parse-doc-path "a.b.c.d")]
      (is (r/ok? result))
      (is (= [:a :b :c :d] (r/unwrap result))))))

(deftest parse-doc-path-error-cases-test
  (testing "empty path errors"
    (let [result (parser/parse-doc-path "")]
      (is (r/error? result))
      (is (= :invalid-path (:error (r/unwrap result))))))
  (testing "leading dot errors"
    (let [result (parser/parse-doc-path ".user")]
      (is (r/error? result))
      (is (= :invalid-path (:error (r/unwrap result))))))
  (testing "trailing dot errors"
    (let [result (parser/parse-doc-path "user.")]
      (is (r/error? result))
      (is (= :invalid-path (:error (r/unwrap result))))))
  (testing "empty segment errors"
    (let [result (parser/parse-doc-path "user..name")]
      (is (r/error? result))
      (is (= :invalid-path (:error (r/unwrap result)))))))

;;; ---------------------------------------------------------------------------
;;; classify-token Tests with Paths
;;; ---------------------------------------------------------------------------

(deftest classify-token-single-path-test
  (testing "single key becomes single-element path"
    (let [result (parser/classify-token :doc/role [0 0])]
      (is (r/ok? result))
      (let [node (r/unwrap result)]
        (is (= ::ast/doc-accessor (:type node)))
        (is (= [:role] (:value node)))))))

(deftest classify-token-nested-path-test
  (testing "dotted key becomes path vector"
    (let [result (parser/classify-token :doc/user.name [0 0])]
      (is (r/ok? result))
      (let [node (r/unwrap result)]
        (is (= ::ast/doc-accessor (:type node)))
        (is (= [:user :name] (:value node))))))
  (testing "deeply nested path"
    (let [result (parser/classify-token :doc/user.profile.email [0 0])]
      (is (r/ok? result))
      (let [node (r/unwrap result)]
        (is (= [:user :profile :email] (:value node)))))))

(deftest classify-token-invalid-path-test
  (testing "malformed path returns error"
    (let [result (parser/classify-token :doc/user..name [0 0])]
      (is (r/error? result))
      (is (= :invalid-path (:error (r/unwrap result)))))))

;;; ---------------------------------------------------------------------------
;;; parse-policy Tests with Paths
;;; ---------------------------------------------------------------------------

(deftest parse-policy-nested-path-test
  (testing "policy with nested path"
    (let [result (parser/parse-policy [:= :doc/user.name "Alice"])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/function-call (:type ast)))
        (is (= [:user :name] (-> ast :children first :value)))))))

(deftest parse-policy-multiple-nested-paths-test
  (testing "policy with multiple nested paths"
    (let [result (parser/parse-policy
                  [:and
                   [:= :doc/user.role "admin"]
                   [:= :doc/user.profile.active true]])]
      (is (r/ok? result)))))

;;; ---------------------------------------------------------------------------
;;; extract-doc-keys Tests with Paths
;;; ---------------------------------------------------------------------------

(deftest extract-doc-keys-single-path-test
  (testing "extracting single-element paths"
    (let [result (parser/parse-policy [:= :doc/role "admin"])
          ast    (r/unwrap result)]
      (is (= #{[:role]} (parser/extract-doc-keys ast))))))

(deftest extract-doc-keys-nested-paths-test
  (testing "extracting nested paths"
    (let [result (parser/parse-policy [:= :doc/user.name "Alice"])
          ast    (r/unwrap result)]
      (is (= #{[:user :name]} (parser/extract-doc-keys ast)))))
  (testing "extracting multiple nested paths"
    (let [result (parser/parse-policy
                  [:and
                   [:= :doc/user.role "admin"]
                   [:= :doc/user.profile.email "test@example.com"]])
          ast    (r/unwrap result)]
      (is (= #{[:user :role] [:user :profile :email]}
             (parser/extract-doc-keys ast))))))

;;; ---------------------------------------------------------------------------
;;; Quantifier Predicate Tests
;;; ---------------------------------------------------------------------------

(deftest binding-accessor-test
  (testing "recognizes binding accessors"
    (is (parser/binding-accessor? :u/role))
    (is (parser/binding-accessor? :team/members))
    (is (parser/binding-accessor? :member/profile.verified)))
  (testing "rejects doc and fn accessors"
    (is (not (parser/binding-accessor? :doc/users)))
    (is (not (parser/binding-accessor? :fn/count))))
  (testing "rejects non-namespaced keywords"
    (is (not (parser/binding-accessor? :role)))
    (is (not (parser/binding-accessor? "u/role")))))

(deftest quantifier-op-test
  (testing "recognizes quantifier operators"
    (is (parser/quantifier-op? :forall))
    (is (parser/quantifier-op? :exists)))
  (testing "rejects other operators"
    (is (not (parser/quantifier-op? :and)))
    (is (not (parser/quantifier-op? :=)))
    (is (not (parser/quantifier-op? :some)))))

;;; ---------------------------------------------------------------------------
;;; parse-binding Tests
;;; ---------------------------------------------------------------------------

(deftest parse-binding-simple-test
  (testing "parses simple binding with keyword name"
    (let [result (parser/parse-binding [:u :doc/users] [0 0])]
      (is (r/ok? result))
      (let [binding (r/unwrap result)]
        (is (= :u (:name binding)))
        (is (= "doc" (:namespace binding)))
        (is (= [:users] (:path binding)))))))

(deftest parse-binding-nested-path-test
  (testing "parses binding with nested collection path"
    (let [result (parser/parse-binding [:m :doc/org.members] [0 0])]
      (is (r/ok? result))
      (let [binding (r/unwrap result)]
        (is (= :m (:name binding)))
        (is (= [:org :members] (:path binding)))))))

(deftest parse-binding-from-outer-binding-test
  (testing "parses binding referencing outer quantifier"
    (let [result (parser/parse-binding [:m :team/members] [0 0])]
      (is (r/ok? result))
      (let [binding (r/unwrap result)]
        (is (= :m (:name binding)))
        (is (= "team" (:namespace binding)))
        (is (= [:members] (:path binding)))))))

(deftest parse-binding-error-cases-test
  (testing "rejects non-vector binding"
    (let [result (parser/parse-binding "not-a-vector" [0 0])]
      (is (r/error? result))
      (is (= :invalid-binding (:error (r/unwrap result))))))
  (testing "rejects single-element binding"
    (let [result (parser/parse-binding [:u] [0 0])]
      (is (r/error? result))
      (is (= :invalid-binding (:error (r/unwrap result))))))
  (testing "rejects three-element binding"
    (let [result (parser/parse-binding [:u :doc/users :extra] [0 0])]
      (is (r/error? result))
      (is (= :invalid-binding (:error (r/unwrap result))))))
  (testing "rejects non-symbol/keyword name"
    (let [result (parser/parse-binding [123 :doc/users] [0 0])]
      (is (r/error? result))
      (is (= :invalid-binding-name (:error (r/unwrap result))))))
  (testing "rejects invalid collection path"
    (let [result (parser/parse-binding [:u "users"] [0 0])]
      (is (r/error? result))
      (is (= :invalid-collection-path (:error (r/unwrap result)))))))

;;; ---------------------------------------------------------------------------
;;; Quantifier Parsing Tests
;;; ---------------------------------------------------------------------------

(deftest parse-forall-simple-test
  (testing "parses simple forall"
    (let [result (parser/parse-policy [:forall [:u :doc/users] [:= :u/role "admin"]])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/quantifier (:type ast)))
        (is (= :forall (:value ast)))
        (is (= {:name :u :namespace "doc" :path [:users]}
               (:binding (:metadata ast))))
        (is (= 1 (count (:children ast))))
        (is (= ::ast/function-call (:type (first (:children ast)))))))))

(deftest parse-exists-simple-test
  (testing "parses simple exists"
    (let [result (parser/parse-policy [:exists [:t :doc/teams] [:> :t/size 5]])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/quantifier (:type ast)))
        (is (= :exists (:value ast)))
        (is (= {:name :t :namespace "doc" :path [:teams]}
               (:binding (:metadata ast))))))))

(deftest parse-forall-nested-path-test
  (testing "parses forall with nested collection path"
    (let [result (parser/parse-policy [:forall [:m :doc/org.members] [:= :m/active true]])]
      (is (r/ok? result))
      (is (= [:org :members] (:path (:binding (:metadata (r/unwrap result)))))))))

(deftest parse-nested-quantifiers-test
  (testing "parses nested quantifiers"
    (let [result (parser/parse-policy
                  [:forall [:team :doc/teams]
                   [:exists [:member :team/members]
                    [:= :member/role "lead"]]])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)
            inner (first (:children ast))]
        (is (= ::ast/quantifier (:type ast)))
        (is (= :forall (:value ast)))
        (is (= ::ast/quantifier (:type inner)))
        (is (= :exists (:value inner)))
        (is (= "team" (:namespace (:binding (:metadata inner)))))))))

(deftest parse-binding-accessor-in-body-test
  (testing "binding accessor in body has correct metadata"
    (let [result (parser/parse-policy [:forall [:u :doc/users] [:= :u/role "admin"]])
          ast (r/unwrap result)
          body (first (:children ast))
          accessor (first (:children body))]
      (is (= ::ast/doc-accessor (:type accessor)))
      (is (= [:role] (:value accessor)))
      (is (= "u" (:binding-ns (:metadata accessor)))))))

(deftest parse-quantifier-error-cases-test
  (testing "rejects quantifier without body"
    (let [result (parser/parse-policy [:forall [:u :doc/users]])]
      (is (r/error? result))
      (is (= :invalid-quantifier (:error (r/unwrap result))))))
  (testing "rejects quantifier with too many arguments"
    (let [result (parser/parse-policy [:forall [:u :doc/users] [:= :u/x 1] :extra])]
      (is (r/error? result))
      (is (= :invalid-quantifier (:error (r/unwrap result))))))
  (testing "rejects quantifier with invalid binding"
    (let [result (parser/parse-policy [:forall "not-a-binding" [:= :u/x 1]])]
      (is (r/error? result))
      (is (= :invalid-binding (:error (r/unwrap result)))))))
