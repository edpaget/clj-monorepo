(ns bashketball-game-api.graphql.subscriptions.lobby-test
  "Integration tests for lobby subscriptions via GraphQL."
  (:require [bashketball-game-api.system :as system]
            [bashketball-game-api.test-utils :as tu]
            [clojure.core.async :as async]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [graphql-server.subscriptions :as subs]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(deftest lobby-subscription-returns-sse-headers-test
  (testing "lobby subscription returns SSE headers immediately"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          query      "subscription { lobbyUpdated { type gameId } }"
          response   (tu/sse-request query session-id)]
      (try
        (is (= 200 (:status response)))
        (is (= "text/event-stream" (get-in response [:headers "content-type"])))
        (finally
          (.close (:reader response)))))))

(deftest lobby-subscription-sends-connected-event-test
  (testing "lobby subscription sends initial connected event from ring layer"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          query      "subscription { lobbyUpdated { type userId } }"
          response   (tu/sse-request query session-id)
          reader     (:reader response)]
      (try
        ;; First event is from the ring layer
        (let [ring-event (tu/read-sse-event reader)]
          (is (= "connected" (:event ring-event)))
          (is (= "active" (get-in ring-event [:data :subscription]))))
        ;; Second event is from the streamer with user-specific data
        (let [streamer-event (tu/read-sse-event reader)]
          (is (= "message" (:event streamer-event)))
          (is (= "CONNECTED" (get-in streamer-event [:data :lobbyUpdated :type])))
          (is (= (str (:id user)) (get-in streamer-event [:data :lobbyUpdated :userId]))))
        (finally
          (.close reader))))))

(deftest lobby-subscription-receives-published-events-test
  (testing "lobby subscription receives events published to lobby topic"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          query      "subscription { lobbyUpdated { type gameId } }"
          response   (tu/sse-request query session-id)
          reader     (:reader response)
          sub-mgr    (::system/subscription-manager tu/*system*)]
      (try
        ;; Skip the ring layer's connected event
        (tu/read-sse-event reader)
        ;; Skip the streamer's connected event
        (tu/read-sse-event reader)
        ;; Publish an event to the lobby topic
        (subs/publish! sub-mgr [:lobby]
                       {:type :game-created
                        :data {:game-id "new-game-456"}})
        ;; Read the published event
        (let [event (tu/read-sse-event reader)]
          (is (= "message" (:event event)))
          (is (= "GAME_CREATED" (get-in event [:data :lobbyUpdated :type])))
          (is (= "new-game-456" (get-in event [:data :lobbyUpdated :gameId]))))
        (finally
          (.close reader))))))

(deftest lobby-subscription-publishes-to-channel-test
  (testing "published messages go to lobby topic subscribers"
    (let [sub-mgr (::system/subscription-manager tu/*system*)
          ch      (subs/subscribe! sub-mgr [:lobby])]
      (subs/publish! sub-mgr [:lobby]
                     {:type :game-created
                      :data {:game-id "test-123"}})
      (let [[msg _] (async/alts!! [ch (async/timeout 1000)])]
        (is (= :game-created (:type msg)))
        (is (= "test-123" (get-in msg [:data :game-id]))))
      (subs/unsubscribe! sub-mgr [:lobby] ch))))
