(ns bashketball-editor-ui.components.ui.textarea-test
  (:require
   [bashketball-editor-ui.components.ui.textarea :refer [textarea]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest textarea-renders-test
  (t/testing "renders textarea element"
    (tlr/render ($ textarea {:placeholder "Enter text..."}))
    (t/is (some? (tlr/get-by-role "textbox")))))

(t/deftest textarea-placeholder-test
  (t/testing "displays placeholder text"
    (tlr/render ($ textarea {:placeholder "Type here..."}))
    (t/is (some? (tlr/get-by-placeholder-text "Type here...")))))

(t/deftest textarea-value-test
  (t/testing "displays provided value"
    (tlr/render ($ textarea {:value "Hello world"
                             :on-change identity}))
    (let [ta (tlr/get-by-role "textbox")]
      (t/is (= "Hello world" (.-value ta))))))

(t/deftest textarea-disabled-test
  (t/testing "can be disabled"
    (tlr/render ($ textarea {:disabled true}))
    (let [ta (tlr/get-by-role "textbox")]
      (t/is (.-disabled ta)))))

(t/deftest textarea-rows-test
  (t/testing "sets rows attribute"
    (tlr/render ($ textarea {:rows 5}))
    (let [ta (tlr/get-by-role "textbox")]
      (t/is (= "5" (.getAttribute ta "rows"))))))

(t/deftest textarea-default-rows-test
  (t/testing "defaults to 3 rows"
    (tlr/render ($ textarea {}))
    (let [ta (tlr/get-by-role "textbox")]
      (t/is (= "3" (.getAttribute ta "rows"))))))

(t/deftest textarea-custom-class-test
  (t/testing "applies custom class"
    (tlr/render ($ textarea {:class "custom-textarea"}))
    (let [ta (tlr/get-by-role "textbox")]
      (t/is (.includes (.-className ta) "custom-textarea")))))

(t/deftest textarea-id-test
  (t/testing "sets id attribute"
    (tlr/render ($ textarea {:id "my-textarea"}))
    (t/is (some? (js/document.getElementById "my-textarea")))))

(t/deftest textarea-has-styling-test
  (t/testing "has expected styling classes"
    (tlr/render ($ textarea {}))
    (let [ta (tlr/get-by-role "textbox")]
      (t/is (.includes (.-className ta) "rounded-md"))
      (t/is (.includes (.-className ta) "border")))))
