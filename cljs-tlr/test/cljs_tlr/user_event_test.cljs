(ns cljs-tlr.user-event-test
  (:require
   ["react" :refer [createElement useState]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest click-button-test
  (t/async done
    (let [clicked (atom false)
          component (fn []
                      (createElement "button"
                                     #js {:onClick #(reset! clicked true)}
                                     "Click me"))
          _ (render/render (createElement component))
          user-instance (user/setup)]
      (-> (user/click user-instance (screen/get-by-role "button"))
          (.then (fn []
                   (t/is @clicked)
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest type-into-input-test
  (t/async done
    (let [component (fn []
                      (let [[value set-value] (useState "")]
                        (createElement "input"
                                       #js {:value value
                                            :onChange #(set-value (.. % -target -value))
                                            :placeholder "Type here"})))
          _ (render/render (createElement component))
          user-instance (user/setup)
          input (screen/get-by-placeholder-text "Type here")]
      (-> (user/type-text user-instance input "hello world")
          (.then (fn []
                   (t/is (= "hello world" (.-value input)))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest clear-input-test
  (t/async done
    (let [component (fn []
                      (let [[value set-value] (useState "initial")]
                        (createElement "input"
                                       #js {:value value
                                            :onChange #(set-value (.. % -target -value))
                                            :placeholder "Clear me"})))
          _ (render/render (createElement component))
          user-instance (user/setup)
          input (screen/get-by-placeholder-text "Clear me")]
      (-> (user/clear user-instance input)
          (.then (fn []
                   (t/is (= "" (.-value input)))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest select-option-test
  (t/async done
    (let [component (fn []
                      (let [[value set-value] (useState "")]
                        (createElement "select"
                                       #js {:value value
                                            :onChange #(set-value (.. % -target -value))
                                            :aria-label "Choose option"}
                                       (createElement "option" #js {:value ""} "Select...")
                                       (createElement "option" #js {:value "a"} "Option A")
                                       (createElement "option" #js {:value "b"} "Option B"))))
          _ (render/render (createElement component))
          user-instance (user/setup)
          select (screen/get-by-role "combobox")]
      (-> (user/select-options user-instance select "a")
          (.then (fn []
                   (t/is (= "a" (.-value select)))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

(t/deftest keyboard-special-keys-test
  (t/async done
    (let [key-pressed (atom nil)
          component (fn []
                      (createElement "input"
                                     #js {:onKeyDown #(reset! key-pressed (.-key %))
                                          :placeholder "Press Enter"}))
          _ (render/render (createElement component))
          user-instance (user/setup)
          input (screen/get-by-placeholder-text "Press Enter")]
      (-> (user/click user-instance input)
          (.then #(user/keyboard user-instance "{Enter}"))
          (.then (fn []
                   (t/is (= "Enter" @key-pressed))
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))
