(ns oidc.ring-test
  "Tests for OIDC ring middleware."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [oidc.authorization :as auth]
   [oidc.core :as oidc]
   [oidc.discovery :as discovery]
   [oidc.ring :as oidc-ring]))

(def test-client
  (oidc/create-client
   {:issuer "https://accounts.example.com"
    :client-id "test-client-id"
    :client-secret "test-client-secret"
    :redirect-uri "http://localhost:3000/auth/callback"
    :scopes ["openid" "email" "profile"]}))

(def test-discovery-doc
  {:issuer "https://accounts.example.com"
   :authorization_endpoint "https://accounts.example.com/authorize"
   :token_endpoint "https://accounts.example.com/token"
   :jwks_uri "https://accounts.example.com/jwks"
   :end_session_endpoint "https://accounts.example.com/logout"
   :response_types_supported ["code"]
   :subject_types_supported ["public"]
   :id_token_signing_alg_values_supported ["RS256"]})

(deftest login-handler-test
  (testing "Login handler generates state/nonce and redirects"
    (with-redefs [discovery/fetch-discovery-document (constantly test-discovery-doc)]
      (let [handler (oidc-ring/login-handler test-client {})
            request {:request-method :get
                     :uri "/auth/login"
                     :session {}}
            response (handler request)]
        (is (= 302 (:status response)))
        (is (string? (get-in response [:headers "Location"])))
        (is (get-in response [:session ::oidc-ring/state]))
        (is (get-in response [:session ::oidc-ring/nonce]))
        ;; Verify redirect URL contains required params
        (let [location (get-in response [:headers "Location"])]
          (is (str/includes? location "response_type=code"))
          (is (str/includes? location "client_id=test-client-id"))
          (is (str/includes? location "redirect_uri="))
          (is (str/includes? location "scope=openid"))
          (is (str/includes? location "state="))
          (is (str/includes? location "nonce=")))))))

(deftest login-handler-with-options-test
  (testing "Login handler accepts additional options"
    (with-redefs [discovery/fetch-discovery-document (constantly test-discovery-doc)]
      (let [handler (oidc-ring/login-handler test-client
                                              {:prompt "consent"
                                               :max-age 3600
                                               :additional-params {"display" "popup"}})
            request {:request-method :get
                     :uri "/auth/login"
                     :session {}}
            response (handler request)]
        (is (= 302 (:status response)))
        (let [location (get-in response [:headers "Location"])]
          (is (str/includes? location "prompt=consent"))
          (is (str/includes? location "max_age=3600"))
          (is (str/includes? location "display=popup")))))))

(deftest callback-handler-error-cases-test
  (testing "Callback handler returns error when provider returns error"
    (let [handler (oidc-ring/callback-handler test-client {})
          request {:request-method :get
                   :uri "/auth/callback"
                   :params {"error" "access_denied"}
                   :session {::oidc-ring/state "test-state"}}
          response (handler request)]
      (is (= 401 (:status response)))))

  (testing "Callback handler returns error when code is missing"
    (let [handler (oidc-ring/callback-handler test-client {})
          request {:request-method :get
                   :uri "/auth/callback"
                   :params {"state" "test-state"}
                   :session {::oidc-ring/state "test-state"}}
          response (handler request)]
      (is (= 401 (:status response)))))

  (testing "Callback handler returns error on state mismatch"
    (let [handler (oidc-ring/callback-handler test-client {})
          request {:request-method :get
                   :uri "/auth/callback"
                   :params {"code" "test-code"
                            "state" "wrong-state"}
                   :session {::oidc-ring/state "test-state"}}
          response (handler request)]
      (is (= 401 (:status response))))))

(deftest callback-handler-success-test
  (testing "Callback handler exchanges code and stores tokens"
    (with-redefs [discovery/fetch-discovery-document (constantly test-discovery-doc)
                  auth/exchange-code (fn [_endpoint _code _client-id _secret _redirect _opts]
                                       {:access_token "test-access-token"
                                        :token_type "Bearer"
                                        :id_token "test-id-token"
                                        :expires_in 3600})]
      (let [success-called (atom nil)
            handler (oidc-ring/callback-handler
                     test-client
                     {:success-fn (fn [req tokens]
                                    (reset! success-called {:req req :tokens tokens})
                                    {:status 302
                                     :headers {"Location" "/dashboard"}})
                      :verify-id-token? false})
            request {:request-method :get
                     :uri "/auth/callback"
                     :params {"code" "test-code"
                              "state" "test-state"}
                     :session {::oidc-ring/state "test-state"
                               ::oidc-ring/nonce "test-nonce"}}
            response (handler request)]
        (is (= 302 (:status response)))
        (is (= "/dashboard" (get-in response [:headers "Location"])))
        (is @success-called)
        (is (= "test-access-token" (get-in @success-called [:tokens :access_token])))
        ;; Verify tokens stored in session
        (is (= "test-access-token" (get-in response [:session ::oidc-ring/tokens :access_token])))
        ;; Verify state and nonce cleared
        (is (nil? (get-in response [:session ::oidc-ring/state])))
        (is (nil? (get-in response [:session ::oidc-ring/nonce])))))))

(deftest logout-handler-test
  (testing "Logout handler clears session"
    (let [handler (oidc-ring/logout-handler test-client {})
          request {:request-method :post
                   :uri "/auth/logout"
                   :session {::oidc-ring/tokens {:access_token "test-token"}}}
          response (handler request)]
      (is (= 200 (:status response)))
      (is (nil? (:session response))))))

(deftest logout-handler-with-end-session-test
  (testing "Logout handler redirects to provider end_session_endpoint"
    (with-redefs [discovery/fetch-discovery-document (constantly test-discovery-doc)]
      (let [handler (oidc-ring/logout-handler
                     test-client
                     {:post-logout-redirect-uri "http://localhost:3000/"
                      :end-session-redirect? true})
            request {:request-method :post
                     :uri "/auth/logout"
                     :session {::oidc-ring/tokens {:id_token "test-id-token"}}}
            response (handler request)]
        (is (= 302 (:status response)))
        (is (nil? (:session response)))
        (let [location (get-in response [:headers "Location"])]
          (is (str/starts-with? location "https://accounts.example.com/logout"))
          (is (str/includes? location "post_logout_redirect_uri="))
          (is (str/includes? location "id_token_hint=")))))))

(deftest oidc-middleware-test
  (testing "Middleware routes to correct handlers"
    (with-redefs [discovery/fetch-discovery-document (constantly test-discovery-doc)]
      (let [base-handler (fn [_req] {:status 200 :body "base"})
            app (oidc-ring/oidc-middleware base-handler {:client test-client})]
        ;; Login route
        (let [response (app {:request-method :get
                             :uri "/auth/login"
                             :session {}})]
          (is (= 302 (:status response))))

        ;; Callback route
        (let [response (app {:request-method :get
                             :uri "/auth/callback"
                             :params {"error" "access_denied"}
                             :session {}})]
          (is (= 401 (:status response))))

        ;; Logout route
        (let [response (app {:request-method :post
                             :uri "/auth/logout"
                             :session {}})]
          (is (= 200 (:status response))))

        ;; Other routes pass through
        (let [response (app {:request-method :get
                             :uri "/other"
                             :session {}})]
          (is (= 200 (:status response)))
          (is (= "base" (:body response))))))))

(deftest wrap-oidc-tokens-test
  (testing "Middleware adds tokens to request"
    (let [handler (fn [req]
                    {:status 200
                     :body (get-in req [:oidc/tokens :access_token])})
          app (oidc-ring/wrap-oidc-tokens handler)
          request {:session {::oidc-ring/tokens {:access_token "test-token"}}}
          response (app request)]
      (is (= 200 (:status response)))
      (is (= "test-token" (:body response)))))

  (testing "Middleware handles missing tokens"
    (let [handler (fn [req]
                    {:status 200
                     :body (if (:oidc/tokens req) "has-tokens" "no-tokens")})
          app (oidc-ring/wrap-oidc-tokens handler)
          request {:session {}}
          response (app request)]
      (is (= 200 (:status response)))
      (is (= "no-tokens" (:body response))))))
