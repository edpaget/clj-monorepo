(ns cljs-tlr.render-test
  (:require
   ["react" :refer [createElement]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs-tlr.screen :as screen]
   [cljs.test :as t :include-macros true]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest render-basic-element-test
  (t/testing "renders a basic div element"
    (render/render (createElement "div" nil "Hello World"))
    (t/is (some? (screen/get-by-text "Hello World")))))

(t/deftest render-returns-query-utilities-test
  (t/testing "render returns object with query methods"
    (let [result (render/render (createElement "button" nil "Click me"))]
      (t/is (some? result))
      (t/is (fn? (.-getByText result)))
      (t/is (some? (.getByText result "Click me"))))))

(t/deftest query-returns-nil-when-not-found-test
  (t/testing "query-by-text returns nil when element not found"
    (render/render (createElement "div" nil "Hello"))
    (t/is (nil? (screen/query-by-text "Goodbye")))))

(t/deftest rerender-updates-component-test
  (t/testing "rerender updates the rendered component"
    (let [result (render/render (createElement "div" nil "Version 1"))]
      (t/is (some? (screen/get-by-text "Version 1")))
      (render/rerender result (createElement "div" nil "Version 2"))
      (t/is (nil? (screen/query-by-text "Version 1")))
      (t/is (some? (screen/get-by-text "Version 2"))))))

(t/deftest unmount-removes-component-test
  (t/testing "unmount removes the component from DOM"
    (let [result (render/render (createElement "div" nil "Will be gone"))]
      (t/is (some? (screen/get-by-text "Will be gone")))
      (render/unmount result)
      (t/is (nil? (screen/query-by-text "Will be gone"))))))
