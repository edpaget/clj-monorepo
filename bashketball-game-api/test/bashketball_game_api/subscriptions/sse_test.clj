(ns bashketball-game-api.subscriptions.sse-test
  (:require [bashketball-game-api.subscriptions.core :as subs]
            [bashketball-game-api.subscriptions.sse :as sse]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

(deftest sse-response-headers-test
  (testing "SSE response has correct headers"
    (let [ch       (async/chan)
          response (sse/sse-response ch)]
      (is (= 200 (:status response)))
      (is (= "text/event-stream" (get-in response [:headers "Content-Type"])))
      (is (= "no-cache, no-store, must-revalidate"
             (get-in response [:headers "Cache-Control"])))
      (is (= "keep-alive" (get-in response [:headers "Connection"])))
      (async/close! ch))))

(deftest game-subscription-auth-required-test
  (testing "game subscription returns 401 without authentication"
    (let [manager  (subs/create-subscription-manager)
          handler  (sse/game-subscription-handler manager)
          request  {:path-params          {:game-id (str (random-uuid))}
                    :authn/authenticated? false}
          response (handler request)]
      (is (= 401 (:status response)))
      (is (str/includes? (:body response) "Authentication required")))))

(deftest game-subscription-invalid-id-test
  (testing "game subscription returns 400 for invalid game ID"
    (let [manager  (subs/create-subscription-manager)
          handler  (sse/game-subscription-handler manager)
          request  {:path-params          {:game-id "not-a-uuid"}
                    :authn/authenticated? true
                    :authn/user-id        (str (random-uuid))}
          response (handler request)]
      (is (= 400 (:status response)))
      (is (clojure.string/includes? (:body response) "Invalid game ID")))))

(deftest lobby-subscription-auth-required-test
  (testing "lobby subscription returns 401 without authentication"
    (let [manager  (subs/create-subscription-manager)
          handler  (sse/lobby-subscription-handler manager)
          request  {:authn/authenticated? false}
          response (handler request)]
      (is (= 401 (:status response))))))

(deftest authenticated-game-subscription-test
  (testing "authenticated request returns SSE stream and creates subscriber"
    (let [manager  (subs/create-subscription-manager)
          handler  (sse/game-subscription-handler manager)
          game-id  (random-uuid)
          user-id  (random-uuid)
          request  {:path-params          {:game-id (str game-id)}
                    :authn/authenticated? true
                    :authn/user-id        (str user-id)}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= "text/event-stream" (get-in response [:headers "Content-Type"])))
      (is (= 1 (subs/subscriber-count manager [:game game-id]))))))

(deftest authenticated-lobby-subscription-test
  (testing "authenticated lobby subscription returns SSE stream"
    (let [manager  (subs/create-subscription-manager)
          handler  (sse/lobby-subscription-handler manager)
          user-id  (random-uuid)
          request  {:authn/authenticated? true
                    :authn/user-id        (str user-id)}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= "text/event-stream" (get-in response [:headers "Content-Type"])))
      (is (= 1 (subs/subscriber-count manager [:lobby]))))))

(deftest wrap-subscription-routes-game-test
  (testing "subscription routes middleware routes game subscription"
    (let [manager  (subs/create-subscription-manager)
          fallback (fn [_] {:status 200 :body "fallback"})
          handler  (sse/wrap-subscription-routes fallback manager)
          game-id  (random-uuid)
          resp     (handler {:request-method       :get
                             :uri                  (str "/subscriptions/game/" game-id)
                             :authn/authenticated? true
                             :authn/user-id        (str (random-uuid))})]
      (is (= 200 (:status resp)))
      (is (= "text/event-stream" (get-in resp [:headers "Content-Type"]))))))

(deftest wrap-subscription-routes-lobby-test
  (testing "subscription routes middleware routes lobby subscription"
    (let [manager  (subs/create-subscription-manager)
          fallback (fn [_] {:status 200 :body "fallback"})
          handler  (sse/wrap-subscription-routes fallback manager)
          resp     (handler {:request-method       :get
                             :uri                  "/subscriptions/lobby"
                             :authn/authenticated? true
                             :authn/user-id        (str (random-uuid))})]
      (is (= 200 (:status resp)))
      (is (= "text/event-stream" (get-in resp [:headers "Content-Type"]))))))

(deftest wrap-subscription-routes-passthrough-test
  (testing "non-subscription routes pass through"
    (let [manager  (subs/create-subscription-manager)
          fallback (fn [_] {:status 200 :body "fallback"})
          handler  (sse/wrap-subscription-routes fallback manager)
          resp     (handler {:request-method :get
                             :uri            "/other"})]
      (is (= "fallback" (:body resp))))))

(deftest wrap-subscription-routes-options-test
  (testing "OPTIONS preflight for subscription routes"
    (let [manager  (subs/create-subscription-manager)
          fallback (fn [_] {:status 200 :body "fallback"})
          handler  (sse/wrap-subscription-routes fallback manager)
          resp     (handler {:request-method :options
                             :uri            "/subscriptions/game/123"})]
      (is (= 204 (:status resp)))
      (is (= "GET, OPTIONS" (get-in resp [:headers "Access-Control-Allow-Methods"]))))))
