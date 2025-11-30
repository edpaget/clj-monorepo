(ns bashketball-game-api.handler-test
  "Tests for HTTP handler and routes."
  (:require
   [bashketball-game-api.test-utils :refer [with-server server-port]]
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once with-server)

(defn base-url []
  (str "http://localhost:" (server-port)))

(deftest health-endpoint-test
  (testing "Health endpoint returns ok"
    (let [response (http/get (str (base-url) "/health")
                             {:as :json
                              :throw-exceptions false})]
      (is (= 200 (:status response)))
      (is (= "ok" (get-in response [:body :status]))))))

(deftest not-found-test
  (testing "Unknown routes return 404"
    (let [response (http/get (str (base-url) "/unknown")
                             {:throw-exceptions false})
          body     (json/parse-string (:body response) true)]
      (is (= 404 (:status response)))
      (is (= "Not found" (:error body))))))

(deftest google-auth-redirect-test
  (testing "Google auth endpoint redirects to Google"
    (let [response (http/get (str (base-url) "/auth/google/login")
                             {:throw-exceptions false
                              :redirect-strategy :none})]
      (is (= 302 (:status response)))
      (is (str/includes?
           (get-in response [:headers "Location"])
           "accounts.google.com")))))

(deftest cors-preflight-test
  (testing "CORS preflight returns 204"
    (let [response (http/request
                    {:method :options
                     :url (str (base-url) "/graphql")
                     :headers {"Origin" "http://localhost:3003"
                               "Access-Control-Request-Method" "POST"}
                     :throw-exceptions false})]
      (is (= 204 (:status response)))
      (is (= "http://localhost:3003"
             (get-in response [:headers "Access-Control-Allow-Origin"]))))))
