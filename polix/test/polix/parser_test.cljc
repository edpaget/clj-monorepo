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

(deftest fn-accessor-test
  (testing "recognizes function accessors"
    (is (parser/fn-accessor? :fn/count))
    (is (parser/fn-accessor? :fn/sum))
    (is (parser/fn-accessor? :fn/min)))
  (testing "rejects non-fn accessors"
    (is (not (parser/fn-accessor? :doc/users)))
    (is (not (parser/fn-accessor? :u/role)))
    (is (not (parser/fn-accessor? :count)))))

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
  (testing "rejects non-symbol/keyword name"
    (let [result (parser/parse-binding [123 :doc/users] [0 0])]
      (is (r/error? result))
      (is (= :invalid-binding-name (:error (r/unwrap result))))))
  (testing "rejects invalid collection path"
    (let [result (parser/parse-binding [:u "users"] [0 0])]
      (is (r/error? result))
      (is (= :invalid-collection-path (:error (r/unwrap result)))))))

;;; ---------------------------------------------------------------------------
;;; Filtered Binding Tests
;;; ---------------------------------------------------------------------------

(deftest parse-binding-with-where-test
  (testing "parses binding with :where clause"
    (let [result (parser/parse-binding [:u :doc/users :where [:= :u/active true]] [0 0])]
      (is (r/ok? result))
      (let [binding (r/unwrap result)]
        (is (= :u (:name binding)))
        (is (= "doc" (:namespace binding)))
        (is (= [:users] (:path binding)))
        (is (some? (:where binding)))
        (is (= ::ast/function-call (:type (:where binding))))))))

(deftest parse-binding-where-complex-predicate-test
  (testing "parses binding with complex :where predicate"
    (let [result (parser/parse-binding
                  [:u :doc/users :where [:and [:= :u/active true] [:> :u/age 18]]]
                  [0 0])]
      (is (r/ok? result))
      (let [binding (r/unwrap result)]
        (is (= :and (:value (:where binding))))))))

(deftest parse-binding-where-error-cases-test
  (testing "rejects :where without predicate"
    (let [result (parser/parse-binding [:u :doc/users :where] [0 0])]
      (is (r/error? result))
      (is (= :invalid-where-clause (:error (r/unwrap result))))))
  (testing "rejects extra elements without :where"
    (let [result (parser/parse-binding [:u :doc/users :extra] [0 0])]
      (is (r/error? result))
      (is (= :invalid-binding (:error (r/unwrap result))))))
  (testing "rejects too many elements"
    (let [result (parser/parse-binding [:u :doc/users :where [:= :u/x 1] :extra] [0 0])]
      (is (r/error? result))
      (is (= :invalid-binding (:error (r/unwrap result)))))))

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
      (let [ast   (r/unwrap result)
            inner (first (:children ast))]
        (is (= ::ast/quantifier (:type ast)))
        (is (= :forall (:value ast)))
        (is (= ::ast/quantifier (:type inner)))
        (is (= :exists (:value inner)))
        (is (= "team" (:namespace (:binding (:metadata inner)))))))))

(deftest parse-binding-accessor-in-body-test
  (testing "binding accessor in body has correct metadata"
    (let [result   (parser/parse-policy [:forall [:u :doc/users] [:= :u/role "admin"]])
          ast      (r/unwrap result)
          body     (first (:children ast))
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

;;; ---------------------------------------------------------------------------
;;; Quantifier with Filter Tests
;;; ---------------------------------------------------------------------------

(deftest parse-forall-with-filter-test
  (testing "parses forall with :where clause"
    (let [result (parser/parse-policy
                  [:forall [:u :doc/users :where [:= :u/active true]]
                   [:= :u/role "admin"]])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/quantifier (:type ast)))
        (is (= :forall (:value ast)))
        (is (some? (get-in ast [:metadata :binding :where])))))))

(deftest parse-exists-with-filter-test
  (testing "parses exists with :where clause"
    (let [result (parser/parse-policy
                  [:exists [:t :doc/teams :where [:> :t/size 3]]
                   [:= :t/status "active"]])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/quantifier (:type ast)))
        (is (= :exists (:value ast)))
        (is (some? (get-in ast [:metadata :binding :where])))))))

(deftest parse-nested-quantifiers-with-filters-test
  (testing "parses nested quantifiers with filters"
    (let [result (parser/parse-policy
                  [:forall [:team :doc/teams :where [:= :team/active true]]
                   [:exists [:m :team/members :where [:> :m/level 5]]
                    [:= :m/role "lead"]]])]
      (is (r/ok? result))
      (let [ast   (r/unwrap result)
            inner (first (:children ast))]
        (is (some? (get-in ast [:metadata :binding :where])))
        (is (some? (get-in inner [:metadata :binding :where])))))))

;;; ---------------------------------------------------------------------------
;;; Value Function Parsing Tests
;;; ---------------------------------------------------------------------------

(deftest parse-fn-count-simple-test
  (testing "parses simple fn/count"
    (let [result (parser/parse-policy [:fn/count :doc/users])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/value-fn (:type ast)))
        (is (= :count (:value ast)))
        (is (= [:users] (get-in ast [:metadata :binding :path])))
        (is (= "doc" (get-in ast [:metadata :binding :namespace])))))))

(deftest parse-fn-count-nested-path-test
  (testing "parses fn/count with nested path"
    (let [result (parser/parse-policy [:fn/count :doc/org.members])]
      (is (r/ok? result))
      (is (= [:org :members] (get-in (r/unwrap result) [:metadata :binding :path]))))))

(deftest parse-fn-count-with-filter-test
  (testing "parses fn/count with filtered binding"
    (let [result (parser/parse-policy
                  [:fn/count [:u :doc/users :where [:= :u/active true]]])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/value-fn (:type ast)))
        (is (= :count (:value ast)))
        (is (= :u (get-in ast [:metadata :binding :name])))
        (is (some? (get-in ast [:metadata :binding :where])))))))

(deftest parse-fn-count-in-comparison-test
  (testing "fn/count in comparison expression"
    (let [result (parser/parse-policy [:>= [:fn/count :doc/users] 5])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/function-call (:type ast)))
        (is (= :>= (:value ast)))
        (is (= ::ast/value-fn (:type (first (:children ast)))))))))

(deftest parse-fn-count-error-cases-test
  (testing "rejects fn/count without argument"
    (let [result (parser/parse-policy [:fn/count])]
      (is (r/error? result))
      (is (= :invalid-value-fn (:error (r/unwrap result))))))
  (testing "rejects fn/count with multiple arguments"
    (let [result (parser/parse-policy [:fn/count :doc/users :doc/teams])]
      (is (r/error? result))
      (is (= :invalid-value-fn (:error (r/unwrap result))))))
  (testing "rejects fn/count with invalid argument"
    (let [result (parser/parse-policy [:fn/count "not-a-path"])]
      (is (r/error? result))
      (is (= :invalid-value-fn-arg (:error (r/unwrap result)))))))

;;; ---------------------------------------------------------------------------
;;; Self Accessor Tests
;;; ---------------------------------------------------------------------------

(deftest self-accessor-predicate-test
  (testing "identifies self accessors"
    (is (parser/self-accessor? :self/value))
    (is (parser/self-accessor? :self/computed.result))
    (is (not (parser/self-accessor? :doc/value)))
    (is (not (parser/self-accessor? :param/value)))
    (is (not (parser/self-accessor? :event/value)))))

(deftest classify-token-self-accessor-test
  (testing "parses self accessor as self-accessor node"
    (let [result (parser/classify-token :self/value [0 0])]
      (is (r/ok? result))
      (let [node (r/unwrap result)]
        (is (= ::ast/self-accessor (:type node)))
        (is (= [:value] (:value node))))))
  (testing "parses nested self accessor path"
    (let [result (parser/classify-token :self/computed.result [0 0])]
      (is (r/ok? result))
      (let [node (r/unwrap result)]
        (is (= ::ast/self-accessor (:type node)))
        (is (= [:computed :result] (:value node)))))))

;;; ---------------------------------------------------------------------------
;;; Param Accessor Tests
;;; ---------------------------------------------------------------------------

(deftest param-accessor-predicate-test
  (testing "identifies param accessors"
    (is (parser/param-accessor? :param/role))
    (is (parser/param-accessor? :param/min-level))
    (is (not (parser/param-accessor? :doc/role)))
    (is (not (parser/param-accessor? :self/value)))))

(deftest classify-token-param-accessor-test
  (testing "parses param accessor as param-accessor node"
    (let [result (parser/classify-token :param/role [0 0])]
      (is (r/ok? result))
      (let [node (r/unwrap result)]
        (is (= ::ast/param-accessor (:type node)))
        (is (= :role (:value node)))))))

;;; ---------------------------------------------------------------------------
;;; Event Accessor Tests
;;; ---------------------------------------------------------------------------

(deftest event-accessor-predicate-test
  (testing "identifies event accessors"
    (is (parser/event-accessor? :event/target-id))
    (is (parser/event-accessor? :event/amount))
    (is (not (parser/event-accessor? :doc/target)))
    (is (not (parser/event-accessor? :param/target)))))

(deftest classify-token-event-accessor-test
  (testing "parses event accessor as event-accessor node"
    (let [result (parser/classify-token :event/target-id [0 0])]
      (is (r/ok? result))
      (let [node (r/unwrap result)]
        (is (= ::ast/event-accessor (:type node)))
        (is (= [:target-id] (:value node))))))
  (testing "parses nested event accessor path"
    (let [result (parser/classify-token :event/damage.amount [0 0])]
      (is (r/ok? result))
      (let [node (r/unwrap result)]
        (is (= ::ast/event-accessor (:type node)))
        (is (= [:damage :amount] (:value node)))))))

;;; ---------------------------------------------------------------------------
;;; Policy Reference Tests
;;; ---------------------------------------------------------------------------

(deftest policy-reference-predicate-test
  (testing "identifies policy references"
    (is (parser/policy-reference? [:auth/admin]))
    (is (parser/policy-reference? [:auth/has-role {:role "editor"}]))
    (is (not (parser/policy-reference? [:= :doc/x 1])))
    (is (not (parser/policy-reference? [:forall [:u :doc/users] true])))
    (is (not (parser/policy-reference? [:fn/count :doc/users])))))

(deftest parse-policy-reference-simple-test
  (testing "parses simple policy reference"
    (let [result (parser/parse-policy [:auth/admin])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/policy-reference (:type ast)))
        (is (= {:namespace :auth :name :admin} (:value ast)))
        (is (nil? (:metadata ast)))))))

(deftest parse-policy-reference-with-params-test
  (testing "parses policy reference with params"
    (let [result (parser/parse-policy [:auth/has-role {:role "editor"}])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/policy-reference (:type ast)))
        (is (= {:namespace :auth :name :has-role} (:value ast)))
        (is (= {:params {:role "editor"}} (:metadata ast)))))))

(deftest parse-policy-reference-errors-test
  (testing "rejects policy reference with too many args"
    (let [result (parser/parse-policy [:auth/admin {} :extra])]
      (is (r/error? result))
      (is (= :invalid-policy-reference (:error (r/unwrap result))))))
  (testing "rejects policy reference with non-map params"
    (let [result (parser/parse-policy [:auth/admin "not-a-map"])]
      (is (r/error? result))
      (is (= :invalid-policy-params (:error (r/unwrap result)))))))

;;; ---------------------------------------------------------------------------
;;; Let Binding Tests
;;; ---------------------------------------------------------------------------

(deftest let-binding-predicate-test
  (testing "identifies let bindings"
    (is (parser/let-binding? [:let [] true]))
    (is (parser/let-binding? [:let ['x 1] 'x]))
    (is (not (parser/let-binding? [:= :doc/x 1])))
    (is (not (parser/let-binding? [:and true false])))))

(deftest parse-let-binding-simple-test
  (testing "parses simple let binding"
    (let [result (parser/parse-policy [:let [:x 5] [:= :self/x 5]])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/let-binding (:type ast)))
        (is (= 1 (count (get-in ast [:metadata :bindings]))))
        (let [binding (first (get-in ast [:metadata :bindings]))]
          (is (= :x (:name binding)))
          (is (= ::ast/literal (:type (:expr binding)))))
        (is (= 1 (count (:children ast))))
        (is (= ::ast/function-call (:type (first (:children ast)))))))))

(deftest parse-let-binding-multiple-test
  (testing "parses let with multiple bindings"
    (let [result (parser/parse-policy [:let [:x 1 :y 2] [:and [:= :self/x 1] [:= :self/y 2]]])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= 2 (count (get-in ast [:metadata :bindings]))))
        (is (= :x (:name (first (get-in ast [:metadata :bindings])))))
        (is (= :y (:name (second (get-in ast [:metadata :bindings])))))))))

(deftest parse-let-binding-with-doc-accessor-test
  (testing "parses let with doc accessor expression"
    (let [result (parser/parse-policy [:let [:val :doc/amount] [:> :self/val 10]])]
      (is (r/ok? result))
      (let [ast (r/unwrap result)]
        (is (= ::ast/doc-accessor (:type (:expr (first (get-in ast [:metadata :bindings]))))))))))

(deftest parse-let-binding-errors-test
  (testing "rejects let without body"
    (let [result (parser/parse-policy [:let [:x 1]])]
      (is (r/error? result))
      (is (= :invalid-let (:error (r/unwrap result))))))
  (testing "rejects let with too many args"
    (let [result (parser/parse-policy [:let [:x 1] true :extra])]
      (is (r/error? result))
      (is (= :invalid-let (:error (r/unwrap result))))))
  (testing "rejects let with non-vector bindings"
    (let [result (parser/parse-policy [:let {:x 1} true])]
      (is (r/error? result))
      (is (= :invalid-let-bindings (:error (r/unwrap result))))))
  (testing "rejects let with odd binding count"
    (let [result (parser/parse-policy [:let [:x] true])]
      (is (r/error? result))
      (is (= :invalid-let-bindings (:error (r/unwrap result))))))
  (testing "rejects let with invalid binding name"
    (let [result (parser/parse-policy [:let ["x" 1] true])]
      (is (r/error? result))
      (is (= :invalid-let-binding-name (:error (r/unwrap result)))))))

;;; ---------------------------------------------------------------------------
;;; Binding Accessor Updated Tests
;;; ---------------------------------------------------------------------------

(deftest binding-accessor-excludes-reserved-test
  (testing "binding-accessor? excludes reserved namespaces"
    (is (not (parser/binding-accessor? :self/value)))
    (is (not (parser/binding-accessor? :param/role)))
    (is (not (parser/binding-accessor? :event/target)))
    (is (not (parser/binding-accessor? :doc/value)))
    (is (not (parser/binding-accessor? :fn/count)))
    (is (parser/binding-accessor? :u/value))
    (is (parser/binding-accessor? :custom/field))))
