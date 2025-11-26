(ns bashketball-editor-ui.components.ui.button-test
  (:require
   [bashketball-editor-ui.components.ui.button :refer [button]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest button-renders-with-text-test
  (t/testing "renders button with provided text"
    (tlr/render ($ button {} "Click me"))
    (t/is (some? (tlr/get-by-role "button" {:name "Click me"})))))

(t/deftest button-click-calls-handler-test
  (t/async done
           (t/testing "click triggers on-click handler"
             (let [clicked (atom false)
                   user    (tlr/setup)]
               (tlr/render ($ button {:on-click #(reset! clicked true)} "Click"))
               (-> (tlr/click user (tlr/get-by-role "button"))
                   (.then (fn []
                            (t/is @clicked)
                            (done)))
                   (.catch (fn [e]
                             (t/is false (str e))
                             (done))))))))

(t/deftest button-disabled-prevents-click-test
  (t/async done
           (t/testing "disabled button does not trigger handler"
             (let [clicked (atom false)
                   user    (tlr/setup)]
               (tlr/render ($ button {:on-click #(reset! clicked true) :disabled true} "Disabled"))
               (-> (tlr/click user (tlr/get-by-role "button"))
                   (.then (fn []
                            (t/is (not @clicked))
                            (done)))
                   (.catch (fn [_e]
                             (t/is (not @clicked))
                             (done))))))))

(t/deftest button-outline-variant-test
  (t/testing "outline variant has border class"
    (tlr/render ($ button {:variant :outline} "Outline"))
    (let [btn (tlr/get-by-role "button")]
      (t/is (.includes (.-className btn) "border")))))

(t/deftest button-default-variant-test
  (t/testing "default variant has bg-gray-900 class"
    (tlr/render ($ button {:variant :default} "Default"))
    (let [btn (tlr/get-by-role "button")]
      (t/is (.includes (.-className btn) "bg-gray-900")))))
