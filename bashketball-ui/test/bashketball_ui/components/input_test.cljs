(ns bashketball-ui.components.input-test
  (:require
   ["react" :refer [useState]]
   [bashketball-ui.components.input :refer [input]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$ defui]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest input-renders-test
  (uix-tlr/render ($ input {:placeholder "Enter text"}))
  (t/is (some? (screen/get-by-placeholder-text "Enter text"))))

(t/deftest input-default-type-text-test
  (uix-tlr/render ($ input {:placeholder "Text input"}))
  (let [inp (screen/get-by-placeholder-text "Text input")]
    (t/is (= "text" (.-type inp)))))

(t/deftest input-password-type-test
  (uix-tlr/render ($ input {:type "password" :placeholder "Password"}))
  (let [inp (screen/get-by-placeholder-text "Password")]
    (t/is (= "password" (.-type inp)))))

(t/deftest input-email-type-test
  (uix-tlr/render ($ input {:type "email" :placeholder "Email"}))
  (let [inp (screen/get-by-placeholder-text "Email")]
    (t/is (= "email" (.-type inp)))))

(t/deftest input-disabled-state-test
  (uix-tlr/render ($ input {:disabled true :placeholder "Disabled"}))
  (let [inp (screen/get-by-placeholder-text "Disabled")]
    (t/is (.-disabled inp))))

(t/deftest input-has-styling-test
  (uix-tlr/render ($ input {:placeholder "Styled"}))
  (let [inp (screen/get-by-placeholder-text "Styled")]
    (t/is (str/includes? (.-className inp) "rounded-md"))))

(t/deftest input-custom-class-test
  (uix-tlr/render ($ input {:class "my-class" :placeholder "Custom"}))
  (let [inp (screen/get-by-placeholder-text "Custom")]
    (t/is (str/includes? (.-className inp) "my-class"))))

(t/deftest input-id-attribute-test
  (uix-tlr/render ($ input {:id "my-input" :placeholder "With ID"}))
  (let [inp (screen/get-by-placeholder-text "With ID")]
    (t/is (= "my-input" (.-id inp)))))

(t/deftest input-name-attribute-test
  (uix-tlr/render ($ input {:name "my-name" :placeholder "With Name"}))
  (let [inp (screen/get-by-placeholder-text "With Name")]
    (t/is (= "my-name" (.-name inp)))))

(defui controlled-input [{:keys [initial-value]}]
  (let [[value set-value] (useState (or initial-value ""))]
    ($ input {:value value
              :on-change #(set-value (.. % -target -value))
              :placeholder "Controlled"})))

(t/deftest input-controlled-value-test
  (t/async done
    (let [_   (uix-tlr/render ($ controlled-input {}))
          usr (user/setup)
          inp (screen/get-by-placeholder-text "Controlled")]
      (-> (user/type-text usr inp "hello")
          (.then (fn []
                   (t/is (= "hello" (.-value inp)))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest input-default-value-test
  (uix-tlr/render ($ input {:default-value "initial" :placeholder "Default"}))
  (let [inp (screen/get-by-placeholder-text "Default")]
    (t/is (= "initial" (.-value inp)))))
