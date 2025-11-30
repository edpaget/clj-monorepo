(ns bashketball-game-api.subscriptions.core-test
  (:require [bashketball-game-api.subscriptions.core :as subs]
            [clojure.core.async :as async]
            [clojure.test :refer [deftest is testing]]))

(deftest subscribe-and-publish-test
  (testing "subscriber receives published messages"
    (let [manager (subs/create-subscription-manager)
          topic   [:game (random-uuid)]
          channel (subs/subscribe! manager topic)]
      (subs/publish! manager topic {:type :test :data "hello"})
      (let [[msg _] (async/alts!! [channel (async/timeout 100)])]
        (is (= {:type :test :data "hello"} msg)))
      (subs/unsubscribe! manager topic channel))))

(deftest multiple-subscribers-test
  (testing "all subscribers receive the same message"
    (let [manager (subs/create-subscription-manager)
          topic   [:game (random-uuid)]
          ch1     (subs/subscribe! manager topic)
          ch2     (subs/subscribe! manager topic)
          ch3     (subs/subscribe! manager topic)]
      (subs/publish! manager topic {:type :broadcast})
      (is (= {:type :broadcast} (first (async/alts!! [ch1 (async/timeout 100)]))))
      (is (= {:type :broadcast} (first (async/alts!! [ch2 (async/timeout 100)]))))
      (is (= {:type :broadcast} (first (async/alts!! [ch3 (async/timeout 100)]))))
      (is (= 3 (subs/subscriber-count manager topic))))))

(deftest unsubscribe-test
  (testing "unsubscribed channel no longer receives messages"
    (let [manager (subs/create-subscription-manager)
          topic   [:lobby]
          ch1     (subs/subscribe! manager topic)
          ch2     (subs/subscribe! manager topic)]
      (is (= 2 (subs/subscriber-count manager topic)))
      (subs/unsubscribe! manager topic ch1)
      (is (= 1 (subs/subscriber-count manager topic)))
      (subs/publish! manager topic {:type :after-unsub})
      ;; ch1 should be closed
      (is (nil? (first (async/alts!! [ch1 (async/timeout 100)]))))
      ;; ch2 should receive message
      (is (= {:type :after-unsub} (first (async/alts!! [ch2 (async/timeout 100)])))))))

(deftest topic-isolation-test
  (testing "messages only go to subscribers of the specific topic"
    (let [manager (subs/create-subscription-manager)
          game1   [:game (random-uuid)]
          game2   [:game (random-uuid)]
          ch1     (subs/subscribe! manager game1)
          ch2     (subs/subscribe! manager game2)]
      (subs/publish! manager game1 {:game 1})
      (subs/publish! manager game2 {:game 2})
      (is (= {:game 1} (first (async/alts!! [ch1 (async/timeout 100)]))))
      (is (= {:game 2} (first (async/alts!! [ch2 (async/timeout 100)])))))))

(deftest empty-topic-cleanup-test
  (testing "topics with no subscribers are removed"
    (let [manager (subs/create-subscription-manager)
          topic   [:game (random-uuid)]
          ch      (subs/subscribe! manager topic)]
      (is (contains? (set (subs/topics manager)) topic))
      (subs/unsubscribe! manager topic ch)
      (is (not (contains? (set (subs/topics manager)) topic))))))

(deftest sliding-buffer-test
  (testing "slow consumers don't block publishers (sliding buffer)"
    (let [manager (subs/create-subscription-manager)
          topic   [:game (random-uuid)]
          ch      (subs/subscribe! manager topic)]
      ;; Publish more messages than buffer size (10)
      (dotimes [i 20]
        (subs/publish! manager topic {:n i}))
      ;; Should not block, channel has sliding buffer
      ;; Only last 10 messages should be in buffer
      (let [messages (loop [msgs []]
                       (let [[msg _] (async/alts!! [ch (async/timeout 50)])]
                         (if msg
                           (recur (conj msgs msg))
                           msgs)))]
        ;; Should have at most 10 messages (sliding buffer size)
        (is (<= (count messages) 10))))))

(deftest publish-no-subscribers-test
  (testing "publishing to topic with no subscribers is a no-op"
    (let [manager (subs/create-subscription-manager)
          topic   [:game (random-uuid)]]
      ;; Should not throw
      (is (nil? (subs/publish! manager topic {:type :orphan})))
      (is (= 0 (subs/subscriber-count manager topic))))))

(deftest topics-returns-active-topics-test
  (testing "topics returns only topics with active subscribers"
    (let [manager (subs/create-subscription-manager)
          topic1  [:game (random-uuid)]
          topic2  [:lobby]
          ch1     (subs/subscribe! manager topic1)
          ch2     (subs/subscribe! manager topic2)]
      (is (= #{topic1 topic2} (set (subs/topics manager))))
      (subs/unsubscribe! manager topic1 ch1)
      (is (= #{topic2} (set (subs/topics manager))))
      (subs/unsubscribe! manager topic2 ch2)
      (is (empty? (subs/topics manager))))))
