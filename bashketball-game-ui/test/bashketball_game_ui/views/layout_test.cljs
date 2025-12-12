(ns bashketball-game-ui.views.layout-test
  (:require
   [bashketball-game-ui.views.layout :refer [user-menu login-button]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-user
  {:id "user-123"
   :name "John Doe"
   :email "john@example.com"
   :avatar-url "/api/avatars/user-123"})

(def sample-user-no-avatar
  {:id "user-456"
   :name "Jane Smith"
   :email "jane@example.com"
   :avatar-url nil})

(t/deftest user-menu-renders-avatar-test
  (uix-tlr/render ($ user-menu {:user sample-user :on-logout identity}))
  (let [img (screen/get-by-role "img")]
    (t/is (some? img))))

(t/deftest user-menu-avatar-has-correct-src-test
  (uix-tlr/render ($ user-menu {:user sample-user :on-logout identity}))
  (let [img (screen/get-by-role "img")]
    (t/is (clojure.string/ends-with? (.getAttribute img "src") "/api/avatars/user-123"))))

(t/deftest user-menu-avatar-has-alt-text-test
  (uix-tlr/render ($ user-menu {:user sample-user :on-logout identity}))
  (let [img (screen/get-by-role "img")]
    (t/is (= "John Doe" (.getAttribute img "alt")))))

(t/deftest user-menu-shows-fallback-when-no-avatar-test
  (uix-tlr/render ($ user-menu {:user sample-user-no-avatar :on-logout identity}))
  (t/is (some? (screen/get-by-text "JS"))))

(t/deftest user-menu-renders-user-name-test
  (uix-tlr/render ($ user-menu {:user sample-user :on-logout identity}))
  (t/is (some? (screen/get-by-text "John Doe"))))

(t/deftest user-menu-renders-logout-button-test
  (uix-tlr/render ($ user-menu {:user sample-user :on-logout identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Logout"}))))

(t/deftest user-menu-logout-calls-handler-test
  (t/async done
           (let [logged-out (atom false)
                 _          (uix-tlr/render ($ user-menu {:user sample-user
                                                          :on-logout #(reset! logged-out true)}))
                 usr        (user/setup)
                 btn        (screen/get-by-role "button" {:name "Logout"})]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is @logged-out)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest login-button-renders-test
  (uix-tlr/render ($ login-button))
  (t/is (some? (screen/get-by-text "Sign in with Google"))))
