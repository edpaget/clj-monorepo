(ns bashketball-game-api.graphql.subscriptions.lobby-test
  "HTTP integration tests for lobby subscriptions."
  (:require [bashketball-game-api.subscriptions.core :as subs]
            [bashketball-game-api.system :as system]
            [bashketball-game-api.test-utils :as tu]
            [clj-http.client :as http]
            [clojure.core.async :as async]
            [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(defn- server-url []
  (str "http://localhost:" (tu/server-port)))

(deftest lobby-subscription-unauthenticated-test
  (testing "lobby subscription returns 401 without session"
    (let [url      (str (server-url) "/subscriptions/lobby")
          response (http/get url {:throw-exceptions false})]
      (is (= 401 (:status response))))))

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

