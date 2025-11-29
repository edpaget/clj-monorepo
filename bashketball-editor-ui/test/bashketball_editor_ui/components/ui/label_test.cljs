(ns bashketball-editor-ui.components.ui.label-test
  (:require
   [bashketball-ui.components.label :refer [label]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest label-renders-test
  (t/testing "renders label element"
    (tlr/render ($ label {} "Field Name"))
    (t/is (some? (tlr/get-by-text "Field Name")))))

(t/deftest label-for-attribute-test
  (t/testing "sets htmlFor attribute"
    (tlr/render ($ label {:for "my-input"} "My Label"))
    (let [lbl (tlr/get-by-text "My Label")]
      (t/is (= "my-input" (.getAttribute lbl "for"))))))

(t/deftest label-custom-class-test
  (t/testing "applies custom class"
    (tlr/render ($ label {:class "custom-label"} "Label"))
    (let [lbl (tlr/get-by-text "Label")]
      (t/is (.includes (.-className lbl) "custom-label")))))

(t/deftest label-has-styling-test
  (t/testing "has expected styling classes"
    (tlr/render ($ label {} "Styled Label"))
    (let [lbl (tlr/get-by-text "Styled Label")]
      (t/is (.includes (.-className lbl) "text-sm"))
      (t/is (.includes (.-className lbl) "font-medium")))))
