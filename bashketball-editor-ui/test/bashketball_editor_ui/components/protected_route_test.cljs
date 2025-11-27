(ns bashketball-editor-ui.components.protected-route-test
  (:require
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.components.protected-route :refer [protected-route
                                                             require-auth]]
   [bashketball-editor-ui.context.auth :as auth]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$ defui]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

;; -----------------------------------------------------------------------------
;; require-auth tests
;; -----------------------------------------------------------------------------

(t/deftest require-auth-loading-test
  (t/testing "shows loading state when auth is loading"
    (with-redefs [auth/use-auth (fn [] {:loading? true :logged-in? false})]
      (tlr/render ($ require-auth ($ :div "Protected content")))
      (t/is (some? (tlr/get-by-text "Loading..."))))))

(t/deftest require-auth-logged-in-test
  (t/testing "shows children when logged in"
    (with-redefs [auth/use-auth (fn [] {:loading? false :logged-in? true})]
      (tlr/render ($ require-auth ($ :div "Protected content")))
      (t/is (some? (tlr/get-by-text "Protected content"))))))

(t/deftest require-auth-not-logged-in-test
  (t/testing "shows login prompt when not logged in"
    (with-redefs [auth/use-auth (fn [] {:loading? false :logged-in? false})]
      (tlr/render ($ require-auth ($ :div "Protected content")))
      (t/is (some? (tlr/get-by-text "Please log in to access this feature.")))
      (t/is (some? (tlr/get-by-role "button" #js {:name "Login with GitHub"}))))))

(t/deftest require-auth-hides-content-when-not-logged-in-test
  (t/testing "does not show protected content when not logged in"
    (with-redefs [auth/use-auth (fn [] {:loading? false :logged-in? false})]
      (tlr/render ($ require-auth ($ :div "Secret stuff")))
      (t/is (nil? (tlr/query-by-text "Secret stuff"))))))

(t/deftest require-auth-login-link-href-test
  (t/testing "login button links to GitHub OAuth"
    (with-redefs [auth/use-auth (fn [] {:loading? false :logged-in? false})]
      (tlr/render ($ require-auth ($ :div "Protected")))
      (let [link (tlr/get-by-role "link")]
        (t/is (.includes (.-href link) "/auth/github/login"))))))

;; -----------------------------------------------------------------------------
;; protected-route tests (requires Router context)
;; -----------------------------------------------------------------------------

(defui test-protected-content
  "Test component to render inside protected route."
  []
  ($ :div {:data-testid "protected-content"} "You are authenticated!"))

(defui test-home
  "Test home component for redirect target."
  []
  ($ :div {:data-testid "home"} "Home page"))

(defn render-with-router
  "Renders component within a MemoryRouter for testing."
  [initial-route]
  (tlr/render
   ($ rr/MemoryRouter {:initialEntries #js [initial-route]}
      ($ rr/Routes
         ($ rr/Route {:path "/" :element ($ test-home)})
         ($ rr/Route {:path "/protected" :element ($ protected-route)}
            ($ rr/Route {:index true :element ($ test-protected-content)}))))))

(t/deftest protected-route-loading-test
  (t/testing "shows loading state when auth is loading"
    (with-redefs [auth/use-auth (fn [] {:loading? true :logged-in? false})]
      (render-with-router "/protected")
      (t/is (some? (tlr/get-by-text "Loading..."))))))

(t/deftest protected-route-shows-content-when-logged-in-test
  (t/testing "shows protected content when logged in"
    (with-redefs [auth/use-auth (fn [] {:loading? false :logged-in? true})]
      (render-with-router "/protected")
      (t/is (some? (tlr/get-by-text "You are authenticated!"))))))

(t/deftest protected-route-redirects-when-not-logged-in-test
  (t/testing "redirects to home when not logged in"
    (with-redefs [auth/use-auth (fn [] {:loading? false :logged-in? false})]
      (render-with-router "/protected")
      (t/is (some? (tlr/get-by-text "Home page")))
      (t/is (nil? (tlr/query-by-text "You are authenticated!"))))))

(t/deftest protected-route-does-not-show-home-when-logged-in-test
  (t/testing "does not show home page when logged in"
    (with-redefs [auth/use-auth (fn [] {:loading? false :logged-in? true})]
      (render-with-router "/protected")
      (t/is (nil? (tlr/query-by-text "Home page"))))))
