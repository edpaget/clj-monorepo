(ns polix.effects.resolution-test
  (:require [clojure.test :refer [deftest is]]
            [polix.effects.core :as fx]
            [polix.effects.resolution :as res]))

;;; ---------------------------------------------------------------------------
;;; resolve-ref Tests
;;; ---------------------------------------------------------------------------

(deftest resolve-ref-keyword-bindings-test
  (let [ctx {:bindings {:self "player-1"
                        :target "player-2"
                        :owner "team-a"}}]
    (is (= "player-1" (res/resolve-ref res/default-resolver :self ctx)))
    (is (= "player-2" (res/resolve-ref res/default-resolver :target ctx)))
    (is (= "team-a" (res/resolve-ref res/default-resolver :owner ctx)))
    (is (= :missing (res/resolve-ref res/default-resolver :missing ctx))
        "unbound keywords return themselves (for literal path segments)")))

(deftest resolve-ref-state-path-test
  (let [ctx {:state {:users {:current "user-123"
                             "user-123" {:name "Alice" :balance 100}}}}]
    (is (= "user-123" (res/resolve-ref res/default-resolver [:state :users :current] ctx)))
    (is (= "Alice" (res/resolve-ref res/default-resolver [:state :users "user-123" :name] ctx)))
    (is (= 100 (res/resolve-ref res/default-resolver [:state :users "user-123" :balance] ctx)))
    (is (nil? (res/resolve-ref res/default-resolver [:state :missing] ctx)))))

(deftest resolve-ref-ctx-path-test
  (let [ctx {:bindings {:self "player-1"}
             :source "trigger-123"}]
    (is (= {:self "player-1"} (res/resolve-ref res/default-resolver [:ctx :bindings] ctx)))
    (is (= "player-1" (res/resolve-ref res/default-resolver [:ctx :bindings :self] ctx)))
    (is (= "trigger-123" (res/resolve-ref res/default-resolver [:ctx :source] ctx)))))

(deftest resolve-ref-param-test
  (let [ctx {:params {:amount 50 :target-id "item-1"}}]
    (is (= 50 (res/resolve-ref res/default-resolver [:param :amount] ctx)))
    (is (= "item-1" (res/resolve-ref res/default-resolver [:param :target-id] ctx)))
    (is (nil? (res/resolve-ref res/default-resolver [:param :missing] ctx)))))

(deftest resolve-ref-literal-passthrough-test
  (is (= 42 (res/resolve-ref res/default-resolver 42 {})))
  (is (= "hello" (res/resolve-ref res/default-resolver "hello" {})))
  (is (= [1 2 3] (res/resolve-ref res/default-resolver [1 2 3] {})))
  (is (= {:a 1} (res/resolve-ref res/default-resolver {:a 1} {}))))

;;; ---------------------------------------------------------------------------
;;; resolve-fn Tests
;;; ---------------------------------------------------------------------------

(deftest resolve-fn-keyword-test
  (is (= inc (res/resolve-fn :inc)))
  (is (= dec (res/resolve-fn :dec)))
  (is (= conj (res/resolve-fn :conj)))
  (is (= merge (res/resolve-fn :merge)))
  (is (nil? (res/resolve-fn :unknown-fn))))

(deftest resolve-fn-symbol-test
  (is (= inc (res/resolve-fn 'inc)))
  (is (= dec (res/resolve-fn 'dec))))

(deftest resolve-fn-function-test
  (let [my-fn (fn [x] (+ x 10))]
    (is (= my-fn (res/resolve-fn my-fn)))))

;;; ---------------------------------------------------------------------------
;;; resolve-predicate Tests
;;; ---------------------------------------------------------------------------

(deftest resolve-predicate-keyword-test
  (let [pred (res/resolve-predicate :active)]
    (is (true? (pred {:active true})))
    (is (false? (pred {:active false})))
    (is (nil? (pred {:other true})))))

(deftest resolve-predicate-map-test
  (let [pred (res/resolve-predicate {:type :card :rarity :rare})]
    (is (true? (pred {:type :card :rarity :rare :name "Dragon"})))
    (is (false? (pred {:type :card :rarity :common})))
    (is (false? (pred {:type :unit :rarity :rare})))))

(deftest resolve-predicate-function-test
  (let [pred (res/resolve-predicate #(> (:value %) 10))]
    (is (true? (pred {:value 15})))
    (is (false? (pred {:value 5})))))

;;; ---------------------------------------------------------------------------
;;; resolve-path Tests
;;; ---------------------------------------------------------------------------

(deftest resolve-path-literal-test
  (let [ctx {:bindings {:key "dynamic-key"}}]
    (is (= [:users :current] (res/resolve-path res/default-resolver [:users :current] ctx)))))

(deftest resolve-path-with-reference-segments-test
  (let [ctx {:bindings {:user-key "user-123"}}]
    (is (= [:users "user-123" :balance]
           (res/resolve-path res/default-resolver [:users :user-key :balance] ctx)))))

(deftest resolve-path-reference-as-path-test
  (let [ctx {:state {:target-path [:items 0 :value]}}]
    (is (= [:items 0 :value]
           (res/resolve-path res/default-resolver [:state :target-path] ctx)))))

;;; ---------------------------------------------------------------------------
;;; Integration: :assoc-in with References
;;; ---------------------------------------------------------------------------

(deftest assoc-in-with-binding-reference-test
  (let [state  {:users {"player-1" {:score 0}}}
        ctx    {:bindings {:self "player-1"}}
        effect {:type :polix.effects/assoc-in
                :path [:users :self :score]
                :value 100}
        result (fx/apply-effect state effect ctx)]
    (is (= 100 (get-in (:state result) [:users "player-1" :score])))))

(deftest assoc-in-with-state-reference-value-test
  (let [state  {:users {:current-id "user-1"}
                :defaults {:starting-balance 500}}
        effect {:type :polix.effects/assoc-in
                :path [:users :current-id :balance]
                :value [:state :defaults :starting-balance]}
        ctx    {:bindings {:current-id "user-1"}}
        result (fx/apply-effect state effect ctx)]
    (is (= 500 (get-in (:state result) [:users "user-1" :balance])))))

;;; ---------------------------------------------------------------------------
;;; Integration: :update-in with Function Resolution
;;; ---------------------------------------------------------------------------

(deftest update-in-with-keyword-fn-test
  (let [state  {:counter 10}
        effect {:type :polix.effects/update-in :path [:counter] :f :inc}
        result (fx/apply-effect state effect)]
    (is (= 11 (:counter (:state result))))))

(deftest update-in-with-args-test
  (let [state  {:items []}
        effect {:type :polix.effects/update-in
                :path [:items]
                :f :conj
                :args [{:id 1 :name "Item 1"}]}
        result (fx/apply-effect state effect)]
    (is (= [{:id 1 :name "Item 1"}] (:items (:state result))))))

(deftest update-in-with-referenced-args-test
  (let [state  {:balance 100 :bonus 25}
        ctx    {:params {:amount 50}}
        effect {:type :polix.effects/update-in
                :path [:balance]
                :f :+
                :args [[:param :amount]]}
        result (fx/apply-effect state effect ctx)]
    (is (= 150 (:balance (:state result))))))

(deftest update-in-unknown-function-fails-test
  (let [state  {:value 10}
        effect {:type :polix.effects/update-in :path [:value] :f :nonexistent}
        result (fx/apply-effect state effect)]
    (is (seq (:failed result)))
    (is (= :unknown-function (:error (first (:failed result)))))))

;;; ---------------------------------------------------------------------------
;;; Integration: :dissoc-in
;;; ---------------------------------------------------------------------------

(deftest dissoc-in-nested-path-test
  (let [state  {:users {"user-1" {:name "Alice" :temp-data {:session "xyz"}}}}
        effect {:type :polix.effects/dissoc-in :path [:users "user-1" :temp-data]}
        result (fx/apply-effect state effect)]
    (is (= {:name "Alice"} (get-in (:state result) [:users "user-1"])))))

(deftest dissoc-in-top-level-test
  (let [state  {:keep-me true :remove-me true}
        effect {:type :polix.effects/dissoc-in :path [:remove-me]}
        result (fx/apply-effect state effect)]
    (is (= {:keep-me true} (:state result)))))

(deftest dissoc-in-with-reference-path-test
  (let [state  {:users {"player-1" {:score 100 :temp nil}}}
        ctx    {:bindings {:self "player-1"}}
        effect {:type :polix.effects/dissoc-in :path [:users :self :temp]}
        result (fx/apply-effect state effect ctx)]
    (is (= {:score 100} (get-in (:state result) [:users "player-1"])))))
