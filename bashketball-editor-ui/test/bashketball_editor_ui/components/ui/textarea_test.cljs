(ns bashketball-editor-ui.components.ui.textarea-test
  (:require
   ["react" :refer [useState]]
   [bashketball-ui.components.textarea :refer [textarea]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$ defui]]))

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

(defui controlled-textarea [{:keys [initial-value]}]
  (let [[value set-value] (useState (or initial-value ""))]
    ($ textarea {:value value
                 :on-change #(set-value (.. % -target -value))
                 :placeholder "Enter text"})))

(t/deftest textarea-accepts-newlines-test
  (t/async done
           (let [_   (uix-tlr/render ($ controlled-textarea {}))
                 usr (user/setup)
                 ta  (screen/get-by-placeholder-text "Enter text")]
             (-> (user/type-text usr ta "line1{Enter}line2")
                 (.then (fn []
                          (t/is (= "line1\nline2" (.-value ta)))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest textarea-preserves-multiple-newlines-test
  (t/async done
           (let [_   (uix-tlr/render ($ controlled-textarea {}))
                 usr (user/setup)
                 ta  (screen/get-by-placeholder-text "Enter text")]
             (-> (user/type-text usr ta "a{Enter}{Enter}b")
                 (.then (fn []
                          (t/is (= "a\n\nb" (.-value ta)))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest textarea-trailing-newline-test
  (t/async done
           (let [_   (uix-tlr/render ($ controlled-textarea {}))
                 usr (user/setup)
                 ta  (screen/get-by-placeholder-text "Enter text")]
             (-> (user/type-text usr ta "text{Enter}")
                 (.then (fn []
                          (t/is (= "text\n" (.-value ta)))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
