(ns polix.triggers.processing-test
  (:require [clojure.test :refer [deftest is testing]]
            [polix.triggers.core :as triggers]))

(defn- make-effect
  "Helper to create a simple effect that sets a value at a path."
  [path value]
  {:type :polix.effects/assoc-in
   :path path
   :value value})

(deftest fire-event-basic-test
  (testing "fires matching after trigger"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/event}
                   :timing :polix.triggers.timing/after
                   :effect {:type :test-effect}}
                  "source" "owner" "self")
          result (triggers/fire-event
                  {:state {:value 0} :registry reg'}
                  {:type :test/event})]
      (is (= 1 (count (:results result))))
      (is (true? (:fired? (first (:results result)))))
      (is (false? (:prevented? result)))))

  (testing "does not fire non-matching trigger"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/other}
                   :timing :polix.triggers.timing/after
                   :effect {:type :test-effect}}
                  "source" "owner" "self")
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event})]
      (is (empty? (:results result))))))

(deftest fire-event-condition-test
  (testing "fires when condition satisfied"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/event}
                   :timing :polix.triggers.timing/after
                   ;; Condition uses :doc/ prefix to access event and trigger fields
                   ;; Trigger bindings (:self, :owner, :source) are merged at top level
                   :condition [:= :doc/target-id :doc/self]
                   :effect {:type :heal}}
                  "source" "owner" "entity-1")
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event :target-id "entity-1"})]
      (is (true? (:fired? (first (:results result)))))
      (is (= :satisfied (:condition-result (first (:results result)))))))

  (testing "does not fire when condition not satisfied"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/event}
                   :timing :polix.triggers.timing/after
                   :condition [:= :doc/target-id :doc/self]
                   :effect {:type :heal}}
                  "source" "owner" "entity-1")
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event :target-id "entity-2"})]
      (is (false? (:fired? (first (:results result)))))
      (is (= :conflict (:condition-result (first (:results result)))))))

  (testing "does not fire when condition has residual (conservative)"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/event}
                   :timing :polix.triggers.timing/after
                   ;; Condition requires :amount field which may not be present
                   :condition [:and
                               [:= :doc/target-id :doc/self]
                               [:> :doc/amount 10]]
                   :effect {:type :heal}}
                  "source" "owner" "entity-1")
          ;; Event missing :amount field
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event :target-id "entity-1"})]
      (is (false? (:fired? (first (:results result)))))
      (is (= :open (:condition-result (first (:results result))))))))

(deftest fire-event-once-test
  (testing "removes trigger after firing when once? is true"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/event}
                   :timing :polix.triggers.timing/after
                   :once? true
                   :effect {:type :one-shot}}
                  "source" "owner" nil)
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event})]
      (is (true? (:removed? (first (:results result)))))
      (is (empty? (triggers/get-triggers (:registry result)))))))

(deftest fire-event-priority-test
  (testing "processes triggers in priority order"
    (let [reg    (triggers/create-registry)
          reg'   (-> reg
                     (triggers/register-trigger
                      {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :priority 10
                       :effect (make-effect [:order] [:second])}
                      "s" "o" nil)
                     (triggers/register-trigger
                      {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :priority -10
                       :effect (make-effect [:order] [:first])}
                      "s" "o" nil))
          result (triggers/fire-event
                  {:state {:order []} :registry reg'}
                  {:type :test/event})
          fired  (->> (:results result)
                      (filter :fired?))]
      (is (= 2 (count fired)))
      (is (= [:second] (get-in result [:state :order]))))))

(deftest fire-event-state-mutation-test
  (testing "effect actually modifies state"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/event}
                   :timing :polix.triggers.timing/after
                   :effect (make-effect [:count] 42)}
                  "source" "owner" "self")
          result (triggers/fire-event
                  {:state {:count 0} :registry reg'}
                  {:type :test/event})]
      (is (= 42 (get-in result [:state :count])))))

  (testing "multiple effects compose correctly"
    (let [reg    (triggers/create-registry)
          reg'   (-> reg
                     (triggers/register-trigger
                      {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :priority 0
                       :effect (make-effect [:a] 1)}
                      "s1" "o" nil)
                     (triggers/register-trigger
                      {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :priority 1
                       :effect (make-effect [:b] 2)}
                      "s2" "o" nil))
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event})]
      (is (= 1 (get-in result [:state :a])))
      (is (= 2 (get-in result [:state :b]))))))

(deftest fire-event-timing-test
  (testing "processes before triggers before after triggers"
    (let [reg    (triggers/create-registry)
          reg'   (-> reg
                     (triggers/register-trigger
                      {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :effect (make-effect [:log] [:after])}
                      "s" "o" nil)
                     (triggers/register-trigger
                      {:event-types #{:test/event}
                       :timing :polix.triggers.timing/before
                       :effect (make-effect [:log] [:before])}
                      "s" "o" nil))
          result (triggers/fire-event
                  {:state {:log []} :registry reg'}
                  {:type :test/event})
          fired  (->> (:results result)
                      (filter :fired?))]
      (is (= 2 (count fired)))
      (is (= [:after] (get-in result [:state :log]))))))

(deftest fire-event-no-condition-test
  (testing "trigger without condition always fires"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/event}
                   :timing :polix.triggers.timing/after
                   :effect {:type :always-fire}}
                  "source" "owner" "self")
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event})]
      (is (true? (:fired? (first (:results result)))))
      (is (= :satisfied (:condition-result (first (:results result))))))))

(deftest fire-event-complex-condition-test
  (testing "fires with compound AND condition when all satisfied"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/event}
                   :timing :polix.triggers.timing/after
                   ;; Test AND condition with two numeric comparisons
                   :condition [:and
                               [:> :doc/amount 5]
                               [:< :doc/amount 100]]
                   :effect {:type :conditional}}
                  "source" "owner" nil)
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event :amount 10})]
      (is (true? (:fired? (first (:results result)))))))

  (testing "fires with OR condition when any branch satisfied"
    (let [reg    (triggers/create-registry)
          reg'   (triggers/register-trigger
                  reg
                  {:event-types #{:test/event}
                   :timing :polix.triggers.timing/after
                   :condition [:or
                               [:= :doc/target-id "a"]
                               [:= :doc/target-id "b"]]
                   :effect {:type :conditional}}
                  "source" "owner" nil)
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event :target-id "b"})]
      (is (true? (:fired? (first (:results result))))))))
