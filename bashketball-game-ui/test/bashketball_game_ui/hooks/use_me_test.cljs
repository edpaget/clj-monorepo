(ns bashketball-game-ui.hooks.use-me-test
  "Tests for the use-me hook."
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing/react" :refer [MockedProvider]]
   ["@testing-library/react" :as rtl]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.hooks.use-me :as use-me]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-user
  #js {:id "550e8400-e29b-41d4-a716-446655440000"
       :email "user@example.com"
       :name "Test User"
       :avatarUrl "https://example.com/avatar.jpg"})

(def mock-me-query-logged-in
  #js {:request #js {:query queries/ME_QUERY}
       :result #js {:data #js {:me sample-user}}})

(def mock-me-query-logged-out
  #js {:request #js {:query queries/ME_QUERY}
       :result #js {:data #js {:me nil}}})

(def mock-me-query-error
  #js {:request #js {:query queries/ME_QUERY}
       :error (js/Error. "Authentication failed")})

(defn create-test-cache
  "Creates an InMemoryCache configured for testing."
  []
  (InMemoryCache. #js {:addTypename false}))

(defn with-mocked-provider
  "Wraps hook rendering with MockedProvider."
  [hook-fn mocks]
  (render/render-hook
   hook-fn
   {:wrapper (fn [props]
               ($ MockedProvider {:mocks (clj->js mocks)
                                  :cache (create-test-cache)
                                  :addTypename false}
                  (.-children props)))}))

(defn get-result
  "Gets the current hook result from render-hook output."
  [hook-result]
  (.-current (.-result hook-result)))

(defn wait-for
  "Wrapper around RTL waitFor that returns a promise."
  [f]
  (rtl/waitFor f))

;; =============================================================================
;; use-me tests
;; =============================================================================

(t/deftest use-me-returns-loading-initially-test
  (let [hook-result (with-mocked-provider #(use-me/use-me) [mock-me-query-logged-in])
        result      (get-result hook-result)]
    (t/is (:loading? result) "Should be loading initially")))

(t/deftest use-me-returns-user-when-logged-in-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-me/use-me) [mock-me-query-logged-in])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading? result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          user   (:user result)]
                      (t/is (not (:loading? result)) "Should not be loading")
                      (t/is (some? user) "Should return a user")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-me-returns-logged-in-true-when-user-exists-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-me/use-me) [mock-me-query-logged-in])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading? result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (:logged-in? result) "Should be logged in")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-me-decodes-user-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-me/use-me) [mock-me-query-logged-in])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading? result) (nil? (:user result)))
                        (throw (js/Error. "Still loading or no user"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          user   (:user result)]
                      (t/is (some? (:id user)) "Should have id")
                      (t/is (= "user@example.com" (:email user)) "Should have correct email")
                      (t/is (= "Test User" (:name user)) "Should have correct name")
                      (t/is (= "https://example.com/avatar.jpg" (:avatar-url user)) "Should have correct avatar-url")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-me-returns-nil-user-when-logged-out-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-me/use-me) [mock-me-query-logged-out])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading? result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (nil? (:user result)) "Should return nil user")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-me-returns-logged-in-false-when-no-user-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-me/use-me) [mock-me-query-logged-out])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading? result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (not (:logged-in? result)) "Should not be logged in")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-me-returns-refetch-function-test
  (let [hook-result (with-mocked-provider #(use-me/use-me) [mock-me-query-logged-in])
        result      (get-result hook-result)]
    (t/is (fn? (:refetch result)) "Should return refetch function")))

(t/deftest use-me-returns-error-on-failure-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-me/use-me) [mock-me-query-error])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading? result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (some? (:error result)) "Should have error")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-me-returns-all-expected-keys-test
  (let [hook-result (with-mocked-provider #(use-me/use-me) [mock-me-query-logged-in])
        result      (get-result hook-result)]
    (t/is (contains? result :user) "Should have :user key")
    (t/is (contains? result :loading?) "Should have :loading? key")
    (t/is (contains? result :error) "Should have :error key")
    (t/is (contains? result :logged-in?) "Should have :logged-in? key")
    (t/is (contains? result :refetch) "Should have :refetch key")))
