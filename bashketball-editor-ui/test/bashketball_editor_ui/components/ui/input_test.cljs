(ns bashketball-editor-ui.components.ui.input-test
  (:require
   [bashketball-ui.components.input :refer [input]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest input-renders-with-placeholder-test
  (t/testing "renders input with placeholder"
    (tlr/render ($ input {:placeholder "Enter text..."}))
    (t/is (some? (tlr/get-by-placeholder-text "Enter text...")))))

(t/deftest input-type-into-field-test
  (t/async done
           (t/testing "user can type into input"
             (let [user (tlr/setup)]
               (tlr/render ($ input {:placeholder "Type here"}))
               (let [input-el (tlr/get-by-placeholder-text "Type here")]
                 (-> (tlr/type-text user input-el "hello world")
                     (.then (fn []
                              (t/is (= "hello world" (.-value input-el)))
                              (done)))
                     (.catch (fn [e]
                               (t/is false (str e))
                               (done)))))))))

(t/deftest input-disabled-test
  (t/testing "disabled input has disabled attribute"
    (tlr/render ($ input {:placeholder "Disabled" :disabled true}))
    (let [input-el (tlr/get-by-placeholder-text "Disabled")]
      (t/is (.-disabled input-el)))))

(t/deftest input-has-base-classes-test
  (t/testing "input has base styling classes"
    (tlr/render ($ input {:placeholder "Styled"}))
    (let [input-el (tlr/get-by-placeholder-text "Styled")]
      (t/is (.includes (.-className input-el) "rounded-md"))
      (t/is (.includes (.-className input-el) "border")))))
