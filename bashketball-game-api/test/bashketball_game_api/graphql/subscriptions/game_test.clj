(ns bashketball-game-api.graphql.subscriptions.game-test
  "Integration tests for game subscriptions via GraphQL."
  (:require [bashketball-game-api.system :as system]
            [bashketball-game-api.test-utils :as tu]
            [clojure.core.async :as async]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [graphql-server.subscriptions :as subs]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(deftest game-subscription-returns-sse-headers-test
  (testing "game subscription returns SSE headers immediately"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          game-id    (random-uuid)
          query      (str "subscription { gameUpdated(gameId: \"" game-id "\") { type gameId } }")
          response   (tu/sse-request query session-id)]
      (try
        (is (= 200 (:status response)))
        (is (= "text/event-stream" (get-in response [:headers "content-type"])))
        (finally
          (.close (:reader response)))))))

(deftest game-subscription-sends-connected-event-test
  (testing "game subscription sends initial connected event from ring layer"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          game-id    (random-uuid)
          query      (str "subscription { gameUpdated(gameId: \"" game-id "\") { type gameId userId } }")
          response   (tu/sse-request query session-id)
          reader     (:reader response)]
      (try
        ;; First event is from the ring layer
        (let [ring-event (tu/read-sse-event reader)]
          (is (= "connected" (:event ring-event)))
          (is (= "active" (get-in ring-event [:data :subscription]))))
        ;; Second event is from the streamer with game-specific data
        (let [streamer-event (tu/read-sse-event reader)]
          (is (= "message" (:event streamer-event)))
          (is (= "CONNECTED" (get-in streamer-event [:data :gameUpdated :type])))
          (is (= (str game-id) (get-in streamer-event [:data :gameUpdated :gameId])))
          (is (= (str (:id user)) (get-in streamer-event [:data :gameUpdated :userId]))))
        (finally
          (.close reader))))))

(deftest game-subscription-receives-published-events-test
  (testing "game subscription receives events published to game topic"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          game-id    (random-uuid)
          query      (str "subscription { gameUpdated(gameId: \"" game-id "\") { type gameId } }")
          response   (tu/sse-request query session-id)
          reader     (:reader response)
          sub-mgr    (::system/subscription-manager tu/*system*)]
      (try
        ;; Skip the ring layer's connected event
        (tu/read-sse-event reader)
        ;; Skip the streamer's connected event
        (tu/read-sse-event reader)
        ;; Publish an event to the game topic
        (subs/publish! sub-mgr [:game game-id]
                       {:type :state-changed
                        :data {:game-id (str game-id)}})
        ;; Read the published event
        (let [event (tu/read-sse-event reader)]
          (is (= "message" (:event event)))
          (is (= "STATE_CHANGED" (get-in event [:data :gameUpdated :type])))
          (is (= (str game-id) (get-in event [:data :gameUpdated :gameId]))))
        (finally
          (.close reader))))))

(deftest game-subscription-publishes-to-channel-test
  (testing "published messages go to game topic subscribers"
    (let [sub-mgr (::system/subscription-manager tu/*system*)
          game-id (random-uuid)
          ch      (subs/subscribe! sub-mgr [:game game-id])]
      (subs/publish! sub-mgr [:game game-id]
                     {:type :state-changed
                      :data {:phase "actions"}})
      (let [[msg _] (async/alts!! [ch (async/timeout 1000)])]
        (is (= :state-changed (:type msg)))
        (is (= "actions" (get-in msg [:data :phase]))))
      (subs/unsubscribe! sub-mgr [:game game-id] ch))))
