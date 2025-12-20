(ns polix-triggers.processing-test
  (:require [clojure.test :refer [deftest is testing]]
            [polix-triggers.core :as triggers]))

(deftest fire-event-basic-test
  (testing "fires matching after trigger"
    (let [reg (triggers/create-registry)
          reg' (triggers/register-trigger
                reg
                {:event-types #{:test/event}
                 :timing :polix-triggers.timing/after
                 :effect {:type :test-effect}}
                "source" "owner" "self")
          result (triggers/fire-event
                  {:state {:value 0} :registry reg'}
                  {:type :test/event})]
      (is (= 1 (count (:results result))))
      (is (true? (:fired? (first (:results result)))))
      (is (false? (:prevented? result)))))

  (testing "does not fire non-matching trigger"
    (let [reg (triggers/create-registry)
          reg' (triggers/register-trigger
                reg
                {:event-types #{:test/other}
                 :timing :polix-triggers.timing/after
                 :effect {:type :test-effect}}
                "source" "owner" "self")
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event})]
      (is (empty? (:results result))))))

(deftest fire-event-condition-test
  (testing "fires when condition satisfied"
    (let [reg (triggers/create-registry)
          reg' (triggers/register-trigger
                reg
                {:event-types #{:test/event}
                 :timing :polix-triggers.timing/after
                 :condition [:= :doc/target-id :doc/self]
                 :effect {:type :heal}}
                "source" "owner" "entity-1")
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event :target-id "entity-1"})]
      (is (true? (:fired? (first (:results result)))))))

  (testing "does not fire when condition not satisfied"
    (let [reg (triggers/create-registry)
          reg' (triggers/register-trigger
                reg
                {:event-types #{:test/event}
                 :timing :polix-triggers.timing/after
                 :condition [:= :doc/target-id :doc/self]
                 :effect {:type :heal}}
                "source" "owner" "entity-1")
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event :target-id "entity-2"})]
      (is (false? (:fired? (first (:results result))))))))

(deftest fire-event-once-test
  (testing "removes trigger after firing when once? is true"
    (let [reg (triggers/create-registry)
          reg' (triggers/register-trigger
                reg
                {:event-types #{:test/event}
                 :timing :polix-triggers.timing/after
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
    (let [reg (triggers/create-registry)
          reg' (-> reg
                   (triggers/register-trigger
                    {:event-types #{:test/event}
                     :timing :polix-triggers.timing/after
                     :priority 10
                     :effect {:type :last}}
                    "s" "o" nil)
                   (triggers/register-trigger
                    {:event-types #{:test/event}
                     :timing :polix-triggers.timing/after
                     :priority -10
                     :effect {:type :first}}
                    "s" "o" nil))
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event})
          effects (->> (:results result)
                       (filter :fired?)
                       (map #(get-in % [:effect-result :applied 0 :type])))]
      (is (= [:first :last] effects)))))

(deftest fire-event-timing-test
  (testing "processes before triggers before after triggers"
    (let [reg (triggers/create-registry)
          reg' (-> reg
                   (triggers/register-trigger
                    {:event-types #{:test/event}
                     :timing :polix-triggers.timing/after
                     :effect {:type :after-effect}}
                    "s" "o" nil)
                   (triggers/register-trigger
                    {:event-types #{:test/event}
                     :timing :polix-triggers.timing/before
                     :effect {:type :before-effect}}
                    "s" "o" nil))
          result (triggers/fire-event
                  {:state {} :registry reg'}
                  {:type :test/event})
          effects (->> (:results result)
                       (filter :fired?)
                       (map #(get-in % [:effect-result :applied 0 :type])))]
      (is (= [:before-effect :after-effect] effects)))))
