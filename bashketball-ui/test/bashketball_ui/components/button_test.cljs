(ns bashketball-ui.components.button-test
  (:require
   [bashketball-ui.components.button :refer [button]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest button-renders-with-text-test
  (uix-tlr/render ($ button {} "Click me"))
  (t/is (some? (screen/get-by-role "button" {:name "Click me"}))))

(t/deftest button-renders-default-variant-test
  (uix-tlr/render ($ button {} "Default"))
  (let [btn (screen/get-by-role "button")]
    (t/is (str/includes? (.-className btn) "bg-gray-900"))))

(t/deftest button-renders-destructive-variant-test
  (uix-tlr/render ($ button {:variant :destructive} "Delete"))
  (let [btn (screen/get-by-role "button")]
    (t/is (str/includes? (.-className btn) "bg-red-500"))))

(t/deftest button-renders-outline-variant-test
  (uix-tlr/render ($ button {:variant :outline} "Outline"))
  (let [btn (screen/get-by-role "button")]
    (t/is (str/includes? (.-className btn) "border"))))

(t/deftest button-renders-sm-size-test
  (uix-tlr/render ($ button {:size :sm} "Small"))
  (let [btn (screen/get-by-role "button")]
    (t/is (str/includes? (.-className btn) "h-8"))))

(t/deftest button-renders-lg-size-test
  (uix-tlr/render ($ button {:size :lg} "Large"))
  (let [btn (screen/get-by-role "button")]
    (t/is (str/includes? (.-className btn) "h-10"))))

(t/deftest button-disabled-state-test
  (uix-tlr/render ($ button {:disabled true} "Disabled"))
  (let [btn (screen/get-by-role "button")]
    (t/is (.-disabled btn))))

(t/deftest button-click-handler-test
  (t/async done
    (let [clicked (atom false)
          _       (uix-tlr/render ($ button {:on-click #(reset! clicked true)} "Click"))
          usr     (user/setup)]
      (-> (user/click usr (screen/get-by-role "button"))
          (.then (fn []
                   (t/is @clicked)
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest button-type-submit-test
  (uix-tlr/render ($ button {:type "submit"} "Submit"))
  (let [btn (screen/get-by-role "button")]
    (t/is (= "submit" (.-type btn)))))

(t/deftest button-custom-class-test
  (uix-tlr/render ($ button {:class "my-custom-class"} "Custom"))
  (let [btn (screen/get-by-role "button")]
    (t/is (str/includes? (.-className btn) "my-custom-class"))))
