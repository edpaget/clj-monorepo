(ns bashketball-game-api.subscriptions.system-test
  "System integration tests for subscription infrastructure."
  (:require [bashketball-game-api.system :as system]
            [bashketball-game-api.test-utils :as tu]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [graphql-server.subscriptions :as subs]))

(use-fixtures :once tu/with-system)

(deftest subscription-manager-initialized-test
  (testing "subscription manager is available in system"
    (let [sub-mgr (::system/subscription-manager tu/*system*)]
      (is (some? sub-mgr))
      (is (satisfies? subs/ISubscriptionManager sub-mgr)))))

(deftest subscription-manager-functional-test
  (testing "subscription manager can subscribe and publish"
    (let [sub-mgr (::system/subscription-manager tu/*system*)
          topic   [:test (random-uuid)]
          ch      (subs/subscribe! sub-mgr topic)]
      (is (= 1 (subs/subscriber-count sub-mgr topic)))
      (subs/unsubscribe! sub-mgr topic ch)
      (is (= 0 (subs/subscriber-count sub-mgr topic))))))
