(ns bashketball-game-api.graphql.subscriptions.game-test
  "HTTP integration tests for game subscriptions."
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

(deftest game-subscription-unauthenticated-test
  (testing "game subscription returns 401 without session"
    (let [game-id  (random-uuid)
          url      (str (server-url) "/subscriptions/game/" game-id)
          response (http/get url {:throw-exceptions false})]
      (is (= 401 (:status response))))))

(deftest game-subscription-invalid-id-test
  (testing "invalid game ID returns 400 error"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          url        (str (server-url) "/subscriptions/game/not-a-uuid")
          response   (http/get url
                               {:throw-exceptions false
                                :headers {"Cookie" (str "bashketball-game-session="
                                                        (tu/create-session-cookie session-id))}})]
      (is (= 400 (:status response))))))

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

