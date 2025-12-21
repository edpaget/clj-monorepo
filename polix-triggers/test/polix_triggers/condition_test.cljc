(ns polix-triggers.condition-test
  (:require [clojure.test :refer [deftest is testing]]
            [polix-triggers.condition :as condition]))

(deftest compile-condition-test
  (testing "compiles simple equality condition"
    (let [check (condition/compile-condition [:= :doc/role "admin"])]
      (is (true? (check {:role "admin"})))
      (is (false? (check {:role "user"})))))

  (testing "compiles comparison condition"
    (let [check (condition/compile-condition [:> :doc/level 5])]
      (is (true? (check {:level 10})))
      (is (false? (check {:level 3})))))

  (testing "compiles compound condition"
    (let [check (condition/compile-condition
                 [:and
                  [:= :doc/role "admin"]
                  [:> :doc/level 5]])]
      (is (true? (check {:role "admin" :level 10})))
      (is (false? (check {:role "admin" :level 3})))
      (is (false? (check {:role "user" :level 10}))))))

(deftest build-trigger-document-test
  (testing "builds document from trigger and event"
    (let [trigger {:self "entity-1"
                   :owner "player-1"
                   :source "ability-1"}
          event   {:type :entity/damaged
                   :target-id "entity-1"
                   :amount 5}
          doc     (condition/build-trigger-document trigger event)]
      (is (= "entity-1" (:self doc)))
      (is (= "player-1" (:owner doc)))
      (is (= "ability-1" (:source doc)))
      (is (= :entity/damaged (:event-type doc)))
      (is (= "entity-1" (:target-id doc)))
      (is (= 5 (:amount doc)))))

  (testing "handles nil bindings"
    (let [trigger {:self nil :owner nil :source "src"}
          event   {:type :test/event}
          doc     (condition/build-trigger-document trigger event)]
      (is (nil? (:self doc)))
      (is (nil? (:owner doc)))
      (is (= "src" (:source doc))))))

(deftest evaluate-condition-test
  (testing "evaluates compiled condition"
    (let [trigger {:self "entity-1"
                   :owner "player-1"
                   :source "ability-1"
                   :condition-fn (condition/compile-condition
                                  [:= :doc/target-id :doc/self])}
          event   {:type :entity/damaged :target-id "entity-1"}]
      (is (true? (condition/evaluate-condition trigger event)))))

  (testing "evaluates raw condition"
    (let [trigger {:self "entity-1"
                   :owner "player-1"
                   :source "ability-1"
                   :condition [:= :doc/target-id :doc/self]}
          event   {:type :entity/damaged :target-id "entity-1"}]
      (is (true? (condition/evaluate-condition trigger event)))))

  (testing "returns true when no condition"
    (let [trigger {:self "entity-1" :owner "player-1" :source "ability-1"}
          event   {:type :entity/damaged}]
      (is (true? (condition/evaluate-condition trigger event)))))

  (testing "returns false when condition not satisfied"
    (let [trigger {:self "entity-1"
                   :owner "player-1"
                   :source "ability-1"
                   :condition [:= :doc/target-id :doc/self]}
          event   {:type :entity/damaged :target-id "entity-2"}]
      (is (false? (condition/evaluate-condition trigger event)))))

  (testing "returns residual for incomplete evaluation"
    (let [trigger {:self "entity-1"
                   :owner "player-1"
                   :source "ability-1"
                   :condition [:and
                               [:= :doc/target-id :doc/self]
                               [:> :doc/amount 10]]}
          event   {:type :entity/damaged :target-id "entity-1"}]
      (let [result (condition/evaluate-condition trigger event)]
        (is (map? result))
        (is (contains? result :residual))))))
