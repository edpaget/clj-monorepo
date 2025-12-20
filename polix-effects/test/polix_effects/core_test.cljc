(ns polix-effects.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [polix-effects.core :as fx]))

(deftest noop-effect-test
  (testing "noop returns unchanged state"
    (let [state  {:count 0}
          result (fx/apply-effect state {:type :polix-effects/noop})]
      (is (= state (:state result)))
      (is (empty? (:applied result)))
      (is (empty? (:failed result)))
      (is (nil? (:pending result))))))

(deftest assoc-in-effect-test
  (testing "assoc-in sets value at path"
    (let [result (fx/apply-effect {} {:type :polix-effects/assoc-in
                                      :path [:user :name]
                                      :value "Alice"})]
      (is (= {:user {:name "Alice"}} (:state result)))
      (is (= 1 (count (:applied result))))
      (is (empty? (:failed result)))))

  (testing "assoc-in with nested path"
    (let [state  {:users {}}
          result (fx/apply-effect state {:type :polix-effects/assoc-in
                                         :path [:users "user-123" :email]
                                         :value "alice@example.com"})]
      (is (= {:users {"user-123" {:email "alice@example.com"}}}
             (:state result)))))

  (testing "assoc-in overwrites existing value"
    (let [state  {:x 1}
          result (fx/apply-effect state {:type :polix-effects/assoc-in
                                         :path [:x]
                                         :value 2})]
      (is (= {:x 2} (:state result))))))

(deftest sequence-effect-test
  (testing "empty sequence returns unchanged state"
    (let [state  {:x 1}
          result (fx/apply-effect state {:type :polix-effects/sequence
                                         :effects []})]
      (is (= state (:state result)))
      (is (empty? (:applied result)))))

  (testing "sequence applies effects in order"
    (let [result (fx/apply-effect {:x 1}
                                  {:type :polix-effects/sequence
                                   :effects [{:type :polix-effects/assoc-in :path [:y] :value 2}
                                             {:type :polix-effects/assoc-in :path [:z] :value 3}]})]
      (is (= {:x 1 :y 2 :z 3} (:state result)))
      (is (= 2 (count (:applied result))))))

  (testing "sequence threads state through effects"
    (let [result (fx/apply-effect {}
                                  {:type :polix-effects/sequence
                                   :effects [{:type :polix-effects/assoc-in :path [:a :x] :value 1}
                                             {:type :polix-effects/assoc-in :path [:a :y] :value 2}]})]
      (is (= {:a {:x 1 :y 2}} (:state result))))))

(deftest validation-test
  (testing "invalid effect returns failure"
    (let [result (fx/apply-effect {} {:type :polix-effects/assoc-in})]
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
                                   [{:type :polix-effects/assoc-in :path [:a] :value 1}
                                    {:type :polix-effects/assoc-in :path [:b] :value 2}])]
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
