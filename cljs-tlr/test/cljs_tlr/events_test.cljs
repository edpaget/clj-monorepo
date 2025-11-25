(ns cljs-tlr.events-test
  (:require
   ["react" :refer [createElement]]
   [cljs-tlr.events :as events]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs-tlr.screen :as screen]
   [cljs.test :as t :include-macros true]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest click-fires-click-handler-test
  (t/testing "click fires onClick handler"
    (let [clicked (atom false)
          component (fn []
                      (createElement "button"
                                     #js {:onClick #(reset! clicked true)}
                                     "Click me"))]
      (render/render (createElement component))
      (events/click (screen/get-by-role "button"))
      (t/is @clicked))))

(t/deftest change-updates-input-value-test
  (t/testing "change fires onChange with new value"
    (let [value (atom "")
          component (fn []
                      (createElement "input"
                                     #js {:onChange #(reset! value (.. % -target -value))
                                          :placeholder "Type here"}))]
      (render/render (createElement component))
      (events/change (screen/get-by-placeholder-text "Type here") "new value")
      (t/is (= "new value" @value)))))

(t/deftest focus-and-blur-test
  (t/testing "focus and blur fire correctly"
    (let [focused (atom false)
          component (fn []
                      (createElement "input"
                                     #js {:onFocus #(reset! focused true)
                                          :onBlur #(reset! focused false)
                                          :placeholder "Focus me"}))]
      (render/render (createElement component))
      (let [input (screen/get-by-placeholder-text "Focus me")]
        (events/focus input)
        (t/is @focused)
        (events/blur input)
        (t/is (not @focused))))))

(t/deftest submit-fires-form-handler-test
  (t/testing "submit fires onSubmit handler"
    (let [submitted (atom false)
          component (fn []
                      (createElement "form"
                                     #js {:onSubmit (fn [e]
                                                      (.preventDefault e)
                                                      (reset! submitted true))}
                                     (createElement "button" #js {:type "submit"} "Submit")))]
      (render/render (createElement component))
      (events/submit (.. js/document (querySelector "form")))
      (t/is @submitted))))

(t/deftest key-down-fires-with-key-info-test
  (t/testing "key-down fires with key information"
    (let [key-pressed (atom nil)
          component (fn []
                      (createElement "input"
                                     #js {:onKeyDown #(reset! key-pressed (.-key %))
                                          :placeholder "Press a key"}))]
      (render/render (createElement component))
      (events/key-down (screen/get-by-placeholder-text "Press a key")
                       {:key "Enter"})
      (t/is (= "Enter" @key-pressed)))))
