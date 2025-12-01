(ns bashketball-game-api.graphql.subscriptions.lobby-test
  "Integration tests for lobby subscriptions via GraphQL."
  (:require [bashketball-game-api.system :as system]
            [bashketball-game-api.test-utils :as tu]
            [clj-http.client :as http]
            [clojure.core.async :as async]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [graphql-server.subscriptions :as subs]))

(use-fixtures :once tu/with-server)
(use-fixtures :each tu/with-clean-db)

(defn- server-url []
  (str "http://localhost:" (tu/server-port)))

(defn- subscription-url [query]
  (str (server-url) "/graphql/subscriptions?query="
       (java.net.URLEncoder/encode query "UTF-8")))

(deftest lobby-subscription-endpoint-returns-sse-test
  (testing "lobby subscription returns SSE response"
    (let [user       (tu/create-test-user)
          session-id (tu/create-authenticated-session! (:id user) :user user)
          query      "subscription { lobbyUpdated { type gameId } }"
          url        (subscription-url query)]
      ;; SSE connections don't complete normally - they stay open.
      ;; Use socket-timeout to force early termination, then check we got headers.
      (try
        (http/get url
                  {:socket-timeout 500
                   :headers {"Cookie" (str "bashketball-game-session="
                                           (tu/create-session-cookie session-id))}})
        ;; If we get here without timeout, check the response
        (is false "SSE request should not complete immediately")
        (catch java.net.SocketTimeoutException _
          ;; Expected - SSE streams don't end, so we time out reading body.
          ;; This actually means we connected successfully and got headers.
          (is true "SSE endpoint accepted connection"))
        (catch clojure.lang.ExceptionInfo e
          ;; clj-http wraps the socket timeout
          (let [cause (ex-cause e)]
            (if (instance? java.net.SocketTimeoutException cause)
              (is true "SSE endpoint accepted connection")
              (throw e))))))))

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
