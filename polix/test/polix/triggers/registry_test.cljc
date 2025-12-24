(ns polix.triggers.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [polix.triggers.registry :as registry]))

(deftest create-registry-test
  (testing "creates empty registry"
    (let [reg (registry/create-registry)]
      (is (= {} (:triggers reg)))
      (is (= {} (:index-by-event reg))))))

(deftest register-trigger-test
  (testing "registers a trigger with generated ID"
    (let [reg         (registry/create-registry)
          trigger-def {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :effect {:type :noop}}
          reg'        (registry/register-trigger reg trigger-def "source-1" "owner-1" "self-1")
          triggers    (registry/get-triggers reg')]
      (is (= 1 (count triggers)))
      (let [trigger (first triggers)]
        (is (string? (:id trigger)))
        (is (= "source-1" (:source trigger)))
        (is (= "owner-1" (:owner trigger)))
        (is (= "self-1" (:self trigger)))
        (is (= #{:test/event} (:event-types trigger)))
        (is (= :polix.triggers.timing/after (:timing trigger))))))

  (testing "defaults priority to 0"
    (let [reg         (registry/create-registry)
          trigger-def {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :effect {:type :noop}}
          reg'        (registry/register-trigger reg trigger-def "s" "o" nil)
          trigger     (first (registry/get-triggers reg'))]
      (is (= 0 (:priority trigger)))))

  (testing "defaults once? to false"
    (let [reg         (registry/create-registry)
          trigger-def {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :effect {:type :noop}}
          reg'        (registry/register-trigger reg trigger-def "s" "o" nil)
          trigger     (first (registry/get-triggers reg'))]
      (is (false? (:once? trigger))))))

(deftest unregister-trigger-test
  (testing "removes trigger by ID"
    (let [reg         (registry/create-registry)
          trigger-def {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :effect {:type :noop}}
          reg'        (registry/register-trigger reg trigger-def "s" "o" nil)
          trigger-id  (:id (first (registry/get-triggers reg')))
          reg''       (registry/unregister-trigger reg' trigger-id)]
      (is (empty? (registry/get-triggers reg'')))))

  (testing "does nothing for non-existent ID"
    (let [reg  (registry/create-registry)
          reg' (registry/unregister-trigger reg "non-existent")]
      (is (= reg reg')))))

(deftest unregister-triggers-by-source-test
  (testing "removes all triggers from a source"
    (let [reg         (registry/create-registry)
          trigger-def {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :effect {:type :noop}}
          reg'        (-> reg
                          (registry/register-trigger trigger-def "source-1" "o" nil)
                          (registry/register-trigger trigger-def "source-1" "o" nil)
                          (registry/register-trigger trigger-def "source-2" "o" nil))]
      (is (= 3 (count (registry/get-triggers reg'))))
      (let [reg'' (registry/unregister-triggers-by-source reg' "source-1")]
        (is (= 1 (count (registry/get-triggers reg''))))
        (is (= "source-2" (:source (first (registry/get-triggers reg'')))))))))

(deftest get-triggers-for-event-test
  (testing "returns triggers matching event type"
    (let [reg        (registry/create-registry)
          trigger-a  {:event-types #{:test/a}
                      :timing :polix.triggers.timing/after
                      :effect {:type :noop}}
          trigger-b  {:event-types #{:test/b}
                      :timing :polix.triggers.timing/after
                      :effect {:type :noop}}
          trigger-ab {:event-types #{:test/a :test/b}
                      :timing :polix.triggers.timing/after
                      :effect {:type :noop}}
          reg'       (-> reg
                         (registry/register-trigger trigger-a "s" "o" nil)
                         (registry/register-trigger trigger-b "s" "o" nil)
                         (registry/register-trigger trigger-ab "s" "o" nil))]
      (is (= 2 (count (registry/get-triggers-for-event reg' :test/a))))
      (is (= 2 (count (registry/get-triggers-for-event reg' :test/b))))
      (is (empty? (registry/get-triggers-for-event reg' :test/c)))))

  (testing "sorts by priority"
    (let [reg          (registry/create-registry)
          trigger-low  {:event-types #{:test/event}
                        :timing :polix.triggers.timing/after
                        :priority -10
                        :effect {:type :low}}
          trigger-high {:event-types #{:test/event}
                        :timing :polix.triggers.timing/after
                        :priority 10
                        :effect {:type :high}}
          trigger-mid  {:event-types #{:test/event}
                        :timing :polix.triggers.timing/after
                        :priority 0
                        :effect {:type :mid}}
          reg'         (-> reg
                           (registry/register-trigger trigger-high "s" "o" nil)
                           (registry/register-trigger trigger-low "s" "o" nil)
                           (registry/register-trigger trigger-mid "s" "o" nil))
          triggers     (registry/get-triggers-for-event reg' :test/event)]
      (is (= [:low :mid :high] (mapv #(get-in % [:effect :type]) triggers))))))

(deftest get-trigger-test
  (testing "returns trigger by ID"
    (let [reg         (registry/create-registry)
          trigger-def {:event-types #{:test/event}
                       :timing :polix.triggers.timing/after
                       :effect {:type :noop}}
          reg'        (registry/register-trigger reg trigger-def "s" "o" nil)
          trigger-id  (:id (first (registry/get-triggers reg')))]
      (is (some? (registry/get-trigger reg' trigger-id)))
      (is (nil? (registry/get-trigger reg' "non-existent"))))))
