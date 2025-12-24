(ns polix.effects.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [polix.effects.core :as fx]))

(deftest noop-effect-test
  (testing "noop returns unchanged state"
    (let [state  {:count 0}
          result (fx/apply-effect state {:type :polix.effects/noop})]
      (is (= state (:state result)))
      (is (empty? (:applied result)))
      (is (empty? (:failed result)))
      (is (nil? (:pending result))))))

(deftest assoc-in-effect-test
  (testing "assoc-in sets value at path"
    (let [result (fx/apply-effect {} {:type :polix.effects/assoc-in
                                      :path [:user :name]
                                      :value "Alice"})]
      (is (= {:user {:name "Alice"}} (:state result)))
      (is (= 1 (count (:applied result))))
      (is (empty? (:failed result)))))

  (testing "assoc-in with nested path"
    (let [state  {:users {}}
          result (fx/apply-effect state {:type :polix.effects/assoc-in
                                         :path [:users "user-123" :email]
                                         :value "alice@example.com"})]
      (is (= {:users {"user-123" {:email "alice@example.com"}}}
             (:state result)))))

  (testing "assoc-in overwrites existing value"
    (let [state  {:x 1}
          result (fx/apply-effect state {:type :polix.effects/assoc-in
                                         :path [:x]
                                         :value 2})]
      (is (= {:x 2} (:state result))))))

(deftest sequence-effect-test
  (testing "empty sequence returns unchanged state"
    (let [state  {:x 1}
          result (fx/apply-effect state {:type :polix.effects/sequence
                                         :effects []})]
      (is (= state (:state result)))
      (is (empty? (:applied result)))))

  (testing "sequence applies effects in order"
    (let [result (fx/apply-effect {:x 1}
                                  {:type :polix.effects/sequence
                                   :effects [{:type :polix.effects/assoc-in :path [:y] :value 2}
                                             {:type :polix.effects/assoc-in :path [:z] :value 3}]})]
      (is (= {:x 1 :y 2 :z 3} (:state result)))
      (is (= 2 (count (:applied result))))))

  (testing "sequence threads state through effects"
    (let [result (fx/apply-effect {}
                                  {:type :polix.effects/sequence
                                   :effects [{:type :polix.effects/assoc-in :path [:a :x] :value 1}
                                             {:type :polix.effects/assoc-in :path [:a :y] :value 2}]})]
      (is (= {:a {:x 1 :y 2}} (:state result))))))

(deftest validation-test
  (testing "invalid effect returns failure"
    (let [result (fx/apply-effect {} {:type :polix.effects/assoc-in})]
      (is (= {} (:state result)))
      (is (empty? (:applied result)))
      (is (= 1 (count (:failed result))))
      (is (= :invalid-effect (-> result :failed first :error)))))

  (testing "unknown effect type fails schema validation"
    (let [result (fx/apply-effect {} {:type :unknown/effect})]
      (is (= {} (:state result)))
      (is (= 1 (count (:failed result))))
      (is (= :invalid-effect (-> result :failed first :error)))))

  (testing "unknown effect type with validation disabled returns unknown-effect-type error"
    (let [result (fx/apply-effect {} {:type :unknown/effect} {} {:validate? false})]
      (is (= {} (:state result)))
      (is (= 1 (count (:failed result))))
      (is (= :unknown-effect-type (-> result :failed first :error)))))

  (testing "validation can be disabled"
    (let [result (fx/apply-effect {} {:type :unknown/effect} {} {:validate? false})]
      (is (= :unknown-effect-type (-> result :failed first :error))))))

(deftest apply-effects-test
  (testing "apply-effects convenience function"
    (let [result (fx/apply-effects {}
                                   [{:type :polix.effects/assoc-in :path [:a] :value 1}
                                    {:type :polix.effects/assoc-in :path [:b] :value 2}])]
      (is (= {:a 1 :b 2} (:state result)))
      (is (= 2 (count (:applied result)))))))

(deftest register-effect-test
  (testing "custom effects can be registered"
    (fx/register-effect! :test/increment
                         (fn [state {:keys [path amount]} _ctx _opts]
                           (let [new-state (update-in state path (fnil + 0) amount)]
                             (fx/success new-state [{:type :test/increment :path path :amount amount}]))))
    (let [result (fx/apply-effect {:count 5}
                                  {:type :test/increment :path [:count] :amount 3}
                                  {}
                                  {:validate? false})]
      (is (= {:count 8} (:state result)))
      (is (= 1 (count (:applied result)))))))

;;; ---------------------------------------------------------------------------
;;; Phase 3: Collection Effects
;;; ---------------------------------------------------------------------------

(deftest conj-in-effect-test
  (testing "conj-in adds to vector"
    (let [state  {:items [1 2]}
          result (fx/apply-effect state {:type :polix.effects/conj-in
                                         :path [:items]
                                         :value 3})]
      (is (= {:items [1 2 3]} (:state result)))))

  (testing "conj-in adds to set"
    (let [state  {:tags #{:a :b}}
          result (fx/apply-effect state {:type :polix.effects/conj-in
                                         :path [:tags]
                                         :value :c})]
      (is (= {:tags #{:a :b :c}} (:state result)))))

  (testing "conj-in with reference value"
    (let [state  {:items [] :new-item {:id 1}}
          result (fx/apply-effect state {:type :polix.effects/conj-in
                                         :path [:items]
                                         :value [:state :new-item]})]
      (is (= {:items [{:id 1}] :new-item {:id 1}} (:state result))))))

(deftest remove-in-effect-test
  (testing "remove-in by keyword predicate"
    (let [state  {:items [{:id 1 :active true} {:id 2 :active false}]}
          result (fx/apply-effect state {:type :polix.effects/remove-in
                                         :path [:items]
                                         :predicate :active})]
      (is (= [{:id 2 :active false}] (get-in (:state result) [:items])))))

  (testing "remove-in by map predicate"
    (let [state  {:items [{:type :a :val 1} {:type :b :val 2} {:type :a :val 3}]}
          result (fx/apply-effect state {:type :polix.effects/remove-in
                                         :path [:items]
                                         :predicate {:type :a}})]
      (is (= [{:type :b :val 2}] (get-in (:state result) [:items])))))

  (testing "remove-in by function predicate"
    (let [state  {:nums [1 2 3 4 5]}
          result (fx/apply-effect state {:type :polix.effects/remove-in
                                         :path [:nums]
                                         :predicate even?})]
      (is (= [1 3 5] (get-in (:state result) [:nums]))))))

(deftest move-effect-test
  (testing "move single item"
    (let [state  {:source [{:id 1} {:id 2}] :target []}
          result (fx/apply-effect state {:type :polix.effects/move
                                         :from-path [:source]
                                         :to-path [:target]
                                         :predicate {:id 1}})]
      (is (= [{:id 2}] (get-in (:state result) [:source])))
      (is (= [{:id 1}] (get-in (:state result) [:target])))))

  (testing "move multiple items"
    (let [state  {:active [{:type :a} {:type :b} {:type :a}] :archived []}
          result (fx/apply-effect state {:type :polix.effects/move
                                         :from-path [:active]
                                         :to-path [:archived]
                                         :predicate {:type :a}})]
      (is (= [{:type :b}] (get-in (:state result) [:active])))
      (is (= [{:type :a} {:type :a}] (get-in (:state result) [:archived])))))

  (testing "move with resolved paths"
    (let [state  {:lists {:inbox [{:id 1}] :done []}}
          ctx    {:bindings {:from :inbox :to :done}}
          result (fx/apply-effect state {:type :polix.effects/move
                                         :from-path [:lists :from]
                                         :to-path [:lists :to]
                                         :predicate {:id 1}}
                                  ctx)]
      (is (= [] (get-in (:state result) [:lists :inbox])))
      (is (= [{:id 1}] (get-in (:state result) [:lists :done]))))))

;;; ---------------------------------------------------------------------------
;;; Phase 3: Merge-in Effect
;;; ---------------------------------------------------------------------------

(deftest merge-in-effect-test
  (testing "merge-in merges map at path"
    (let [state  {:user {:name "Alice"}}
          result (fx/apply-effect state {:type :polix.effects/merge-in
                                         :path [:user]
                                         :value {:email "alice@example.com"}})]
      (is (= {:user {:name "Alice" :email "alice@example.com"}} (:state result)))))

  (testing "merge-in overwrites existing keys"
    (let [state  {:config {:debug true :level 1}}
          result (fx/apply-effect state {:type :polix.effects/merge-in
                                         :path [:config]
                                         :value {:level 2 :new-key "x"}})]
      (is (= {:config {:debug true :level 2 :new-key "x"}} (:state result)))))

  (testing "merge-in with reference value"
    (let [state  {:user {:name "Bob"} :updates {:role :admin}}
          result (fx/apply-effect state {:type :polix.effects/merge-in
                                         :path [:user]
                                         :value [:state :updates]})]
      (is (= {:name "Bob" :role :admin} (get-in (:state result) [:user]))))))

;;; ---------------------------------------------------------------------------
;;; Phase 3: Composite Effects
;;; ---------------------------------------------------------------------------

(deftest transaction-effect-test
  (testing "transaction applies all effects on success"
    (let [result (fx/apply-effect {:a 1}
                                  {:type :polix.effects/transaction
                                   :effects [{:type :polix.effects/assoc-in :path [:b] :value 2}
                                             {:type :polix.effects/assoc-in :path [:c] :value 3}]})]
      (is (= {:a 1 :b 2 :c 3} (:state result)))
      (is (= 2 (count (:applied result))))
      (is (empty? (:failed result)))))

  (testing "transaction rolls back on failure"
    (let [state  {:x 1}
          result (fx/apply-effect state
                                  {:type :polix.effects/transaction
                                   :effects [{:type :polix.effects/assoc-in :path [:y] :value 2}
                                             {:type :polix.effects/update-in :path [:z] :f :nonexistent}]})]
      (is (= {:x 1} (:state result)) "state should be unchanged")
      (is (empty? (:applied result)) "no effects should be marked as applied")
      (is (= 1 (count (:failed result))))))

  (testing "transaction stops at first failure"
    (let [result (fx/apply-effect {:count 0}
                                  {:type :polix.effects/transaction
                                   :effects [{:type :polix.effects/update-in :path [:count] :f :inc}
                                             {:type :polix.effects/update-in :path [:x] :f :bad-fn}
                                             {:type :polix.effects/update-in :path [:count] :f :inc}]})]
      (is (= {:count 0} (:state result)) "rolled back to original")
      (is (= :unknown-function (-> result :failed first :error))))))

(deftest let-effect-test
  (testing "let binds single value"
    (let [result (fx/apply-effect {:users {"u1" {:name "Alice"}}}
                                  {:type :polix.effects/let
                                   :bindings [:user-id "u1"]
                                   :effect {:type :polix.effects/assoc-in
                                            :path [:users :user-id :active]
                                            :value true}})]
      (is (= {:name "Alice" :active true}
             (get-in (:state result) [:users "u1"])))))

  (testing "let binds multiple values"
    (let [result (fx/apply-effect {}
                                  {:type :polix.effects/let
                                   :bindings [:a 1 :b 2]
                                   :effect {:type :polix.effects/sequence
                                            :effects [{:type :polix.effects/assoc-in :path [:x] :value :a}
                                                      {:type :polix.effects/assoc-in :path [:y] :value :b}]}})]
      (is (= {:x 1 :y 2} (:state result)))))

  (testing "let bindings can reference earlier bindings"
    (let [state  {:base 10}
          result (fx/apply-effect state
                                  {:type :polix.effects/let
                                   :bindings [:x [:state :base]
                                              :y :x]
                                   :effect {:type :polix.effects/assoc-in
                                            :path [:result]
                                            :value :y}})]
      (is (= 10 (get-in (:state result) [:result])))))

  (testing "nested let effects"
    (let [result (fx/apply-effect {}
                                  {:type :polix.effects/let
                                   :bindings [:outer "outer-val"]
                                   :effect {:type :polix.effects/let
                                            :bindings [:inner "inner-val"]
                                            :effect {:type :polix.effects/sequence
                                                     :effects [{:type :polix.effects/assoc-in :path [:a] :value :outer}
                                                               {:type :polix.effects/assoc-in :path [:b] :value :inner}]}}})]
      (is (= {:a "outer-val" :b "inner-val"} (:state result))))))

;;; ---------------------------------------------------------------------------
;;; Conditional Effects
;;; ---------------------------------------------------------------------------

(deftest conditional-then-branch-test
  (testing "applies then branch when condition is true"
    (let [result (fx/apply-effect
                  {:role "admin" :access false}
                  {:type :polix.effects/conditional
                   :condition [:= :doc/role "admin"]
                   :then {:type :polix.effects/assoc-in
                          :path [:access]
                          :value true}})]
      (is (= {:role "admin" :access true} (:state result)))
      (is (= 1 (count (:applied result)))))))

(deftest conditional-else-branch-test
  (testing "applies else branch when condition is false"
    (let [result (fx/apply-effect
                  {:role "guest" :access false}
                  {:type :polix.effects/conditional
                   :condition [:= :doc/role "admin"]
                   :else {:type :polix.effects/assoc-in
                          :path [:denied]
                          :value true}})]
      (is (= {:role "guest" :access false :denied true} (:state result)))
      (is (= 1 (count (:applied result)))))))

(deftest conditional-residual-test
  (testing "treats residual as false (missing key)"
    (let [result (fx/apply-effect
                  {:name "test"}
                  {:type :polix.effects/conditional
                   :condition [:= :doc/role "admin"]
                   :then {:type :polix.effects/assoc-in :path [:granted] :value true}
                   :else {:type :polix.effects/assoc-in :path [:pending] :value true}})]
      (is (= {:name "test" :pending true} (:state result))))))

(deftest conditional-missing-branch-test
  (testing "noop when then branch is missing and condition true"
    (let [result (fx/apply-effect
                  {:role "admin"}
                  {:type :polix.effects/conditional
                   :condition [:= :doc/role "admin"]})]
      (is (= {:role "admin"} (:state result)))
      (is (= 1 (count (:applied result))))))

  (testing "noop when else branch is missing and condition false"
    (let [result (fx/apply-effect
                  {:role "guest"}
                  {:type :polix.effects/conditional
                   :condition [:= :doc/role "admin"]})]
      (is (= {:role "guest"} (:state result)))
      (is (= 1 (count (:applied result)))))))
