(ns bashketball-editor-api.handler-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [bashketball-editor-api.handler :as handler]))

(deftest health-handler-test
  (testing "returns 200 with ok status"
    (let [response (handler/health-handler {})]
      (is (= 200 (:status response)))
      (is (= "application/json" (get-in response [:headers "Content-Type"])))
      (is (= {:status "ok"} (:body response))))))

(deftest not-found-handler-test
  (testing "returns 404 with error message"
    (let [response (handler/not-found-handler {})]
      (is (= 404 (:status response)))
      (is (= "application/json" (get-in response [:headers "Content-Type"])))
      (is (= {:error "Not found"} (:body response))))))

(deftest routes-test
  (testing "GET /health returns health response"
    (let [mock-authenticator {}
          routes-handler (handler/routes mock-authenticator)
          response (routes-handler {:request-method :get :uri "/health"})]
      (is (= 200 (:status response)))
      (is (= {:status "ok"} (:body response)))))

  (testing "unknown route returns 404"
    (let [mock-authenticator {}
          routes-handler (handler/routes mock-authenticator)
          response (routes-handler {:request-method :get :uri "/unknown"})]
      (is (= 404 (:status response))))))
