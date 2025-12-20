(ns polix-triggers.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [polix-triggers.schema :as schema]))

(deftest timing-schema-test
  (testing "valid timing values"
    (is (m/validate schema/Timing :polix-triggers.timing/before))
    (is (m/validate schema/Timing :polix-triggers.timing/instead))
    (is (m/validate schema/Timing :polix-triggers.timing/after))
    (is (m/validate schema/Timing :polix-triggers.timing/at)))

  (testing "invalid timing values"
    (is (not (m/validate schema/Timing :before)))
    (is (not (m/validate schema/Timing :invalid)))))

(deftest trigger-def-schema-test
  (testing "minimal trigger definition"
    (is (m/validate schema/TriggerDef
                    {:event-types #{:test/event}
                     :timing :polix-triggers.timing/after
                     :effect {:type :noop}})))

  (testing "full trigger definition"
    (is (m/validate schema/TriggerDef
                    {:event-types #{:test/event :test/other}
                     :timing :polix-triggers.timing/before
                     :condition [:= :doc/target-id :doc/self]
                     :effect {:type :heal :amount 1}
                     :once? true
                     :priority -10
                     :replacement? true}))))

(deftest trigger-schema-test
  (testing "instantiated trigger"
    (is (m/validate schema/Trigger
                    {:id "trigger-123"
                     :source "ability-456"
                     :owner "player-1"
                     :self "entity-1"
                     :event-types #{:test/event}
                     :timing :polix-triggers.timing/after
                     :effect {:type :noop}}))))

(deftest event-schema-test
  (testing "minimal event"
    (is (m/validate schema/Event {:type :test/event})))

  (testing "event with additional fields"
    (is (m/validate schema/Event
                    {:type :entity/damaged
                     :target-id "entity-1"
                     :amount 5
                     :source-id "entity-2"}))))

(deftest trigger-result-schema-test
  (testing "fired trigger result"
    (is (m/validate schema/TriggerResult
                    {:trigger-id "trigger-123"
                     :fired? true
                     :condition-result true
                     :effect-result {:state {} :applied [] :failed []}
                     :removed? false})))

  (testing "unfired trigger result"
    (is (m/validate schema/TriggerResult
                    {:trigger-id "trigger-123"
                     :fired? false
                     :condition-result false
                     :removed? false})))

  (testing "residual condition result"
    (is (m/validate schema/TriggerResult
                    {:trigger-id "trigger-123"
                     :fired? false
                     :condition-result {:residual {:level [[:> 5]]}}
                     :removed? false}))))
