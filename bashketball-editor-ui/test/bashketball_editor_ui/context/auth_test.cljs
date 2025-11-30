(ns bashketball-editor-ui.context.auth-test
  (:require
   [bashketball-ui.context.auth :as auth]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$ defui]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(defn mock-use-user
  "Mock user hook that returns static auth state."
  []
  {:user {:id "123" :name "Test User"}
   :loading? false
   :logged-in? true
   :refetch (fn [])})

(defui auth-consumer
  "Component that consumes auth context and displays user name."
  []
  (let [{:keys [user logged-in?]} (auth/use-auth)]
    ($ :div
       (when logged-in?
         ($ :span {:data-testid "user-name"} (:name user))))))

(t/deftest auth-provider-test
  (t/testing "auth provider passes auth state to children via context"
    (tlr/render
     ($ auth/auth-provider {:use-user-hook mock-use-user}
        ($ auth-consumer)))
    (t/is (some? (tlr/get-by-text "Test User")))))

(t/deftest auth-provider-loading-state-test
  (t/testing "auth provider handles loading state"
    (tlr/render
     ($ auth/auth-provider {:use-user-hook (fn [] {:loading? true :logged-in? false})}
        ($ auth-consumer)))
    (t/is (nil? (tlr/query-by-text "Test User")))))
