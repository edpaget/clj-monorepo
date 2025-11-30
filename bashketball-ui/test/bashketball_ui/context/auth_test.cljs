(ns bashketball-ui.context.auth-test
  (:require
   [bashketball-ui.context.auth :refer [auth-provider use-auth create-logout-fn]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$ defui]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def mock-user {:id "123" :name "Test User" :email "test@example.com"})

(defn make-mock-hook
  [auth-state]
  (fn [] auth-state))

(defui auth-consumer []
  (let [{:keys [user loading? logged-in?]} (use-auth)]
    ($ :div
       (when loading?
         ($ :span {:data-testid "loading"} "Loading..."))
       (when logged-in?
         ($ :span {:data-testid "logged-in"} "Logged in"))
       (when user
         ($ :span {:data-testid "user-name"} (:name user))))))

(t/deftest auth-provider-passes-user-to-context-test
  (let [mock-hook (make-mock-hook {:user mock-user :loading? false :logged-in? true})]
    (uix-tlr/render
     ($ auth-provider {:use-user-hook mock-hook}
        ($ auth-consumer)))
    (t/is (some? (screen/get-by-test-id "user-name")))
    (t/is (= "Test User" (.-textContent (screen/get-by-test-id "user-name"))))))

(t/deftest auth-provider-passes-loading-state-test
  (let [mock-hook (make-mock-hook {:user nil :loading? true :logged-in? false})]
    (uix-tlr/render
     ($ auth-provider {:use-user-hook mock-hook}
        ($ auth-consumer)))
    (t/is (some? (screen/get-by-test-id "loading")))))

(t/deftest auth-provider-passes-logged-in-state-test
  (let [mock-hook (make-mock-hook {:user mock-user :loading? false :logged-in? true})]
    (uix-tlr/render
     ($ auth-provider {:use-user-hook mock-hook}
        ($ auth-consumer)))
    (t/is (some? (screen/get-by-test-id "logged-in")))))

(t/deftest auth-provider-logged-out-state-test
  (let [mock-hook (make-mock-hook {:user nil :loading? false :logged-in? false})]
    (uix-tlr/render
     ($ auth-provider {:use-user-hook mock-hook}
        ($ auth-consumer)))
    (t/is (nil? (screen/query-by-test-id "logged-in")))
    (t/is (nil? (screen/query-by-test-id "user-name")))))

(t/deftest use-auth-returns-nil-outside-provider-test
  (let [result (render/render-hook use-auth)]
    (t/is (nil? (.-current (.-result result))))))

(t/deftest use-auth-returns-auth-state-inside-provider-test
  (let [mock-hook (make-mock-hook {:user mock-user :loading? false :logged-in? true :refetch identity})
        wrapper   (fn [props]
                    ($ auth-provider {:use-user-hook mock-hook}
                       (.-children props)))
        result    (render/render-hook use-auth {:wrapper wrapper})]
    (t/is (= mock-user (:user (.-current (.-result result)))))
    (t/is (false? (:loading? (.-current (.-result result)))))
    (t/is (true? (:logged-in? (.-current (.-result result)))))))

(t/deftest create-logout-fn-returns-function-test
  (let [logout-fn (create-logout-fn {:logout-url "/api/logout"})]
    (t/is (fn? logout-fn))))

(t/deftest create-logout-fn-calls-fetch-test
  (t/async done
    (let [fetch-calls   (atom [])
          original-fetch js/fetch
          _             (set! js/fetch
                              (fn [url opts]
                                (swap! fetch-calls conj {:url url :opts opts})
                                (js/Promise.resolve #js {:ok true})))
          logout-fn     (create-logout-fn {:logout-url "/api/logout"})
          refetch-called (atom false)]
      (-> (logout-fn #(reset! refetch-called true))
          (.then (fn []
                   (t/is (= 1 (count @fetch-calls)))
                   (t/is (= "/api/logout" (:url (first @fetch-calls))))
                   (t/is (= "POST" (.-method (:opts (first @fetch-calls)))))
                   (t/is (= "include" (.-credentials (:opts (first @fetch-calls)))))
                   (t/is @refetch-called)
                   (set! js/fetch original-fetch)
                   (done)))
          (.catch (fn [e]
                    (set! js/fetch original-fetch)
                    (t/is false (str e))
                    (done)))))))

(defui refetch-consumer []
  (let [{:keys [refetch]} (use-auth)]
    ($ :button {:on-click refetch} "Refetch")))

(t/deftest auth-provider-passes-refetch-test
  (let [refetch-called (atom false)
        mock-hook      (make-mock-hook {:user nil
                                        :loading? false
                                        :logged-in? false
                                        :refetch #(reset! refetch-called true)})]
    (uix-tlr/render
     ($ auth-provider {:use-user-hook mock-hook}
        ($ refetch-consumer)))
    (t/is (some? (screen/get-by-role "button" {:name "Refetch"})))))
