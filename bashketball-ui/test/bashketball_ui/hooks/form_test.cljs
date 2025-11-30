(ns bashketball-ui.hooks.form-test
  "Tests for form hook utilities including textarea-list-handler."
  (:require
   ["react" :refer [useState]]
   [bashketball-ui.components.textarea :refer [textarea]]
   [bashketball-ui.hooks.form :as form]
   [cljs-tlr.events :as events]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$ defui]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(defui textarea-list-component
  [{:keys [initial-value]}]
  (let [[value set-value] (useState (or initial-value []))
        update-fn         (fn [_field v] (set-value v))]
    ($ :div
       ($ textarea {:value (if (seq value) (str/join "\n" value) "")
                    :on-change (form/textarea-list-handler update-fn :abilities)
                    :placeholder "Enter abilities"
                    :data-testid "abilities-textarea"})
       ($ :div {:data-testid "value-display"} (pr-str value)))))

(t/deftest textarea-list-handler-single-line-test
  (t/async done
           (let [_   (uix-tlr/render ($ textarea-list-component {}))
                 usr (user/setup)
                 ta  (screen/get-by-placeholder-text "Enter abilities")]
             (-> (user/type-text usr ta "ability one")
                 (.then (fn []
                          (let [display (screen/get-by-test-id "value-display")]
                            (t/is (= "[\"ability one\"]" (.-textContent display))))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest textarea-list-handler-multiline-test
  (t/testing "splits textarea value on newlines"
    (uix-tlr/render ($ textarea-list-component {}))
    (let [ta      (screen/get-by-placeholder-text "Enter abilities")
          display (screen/get-by-test-id "value-display")]
      (events/change ta "line one\nline two")
      (t/is (= "[\"line one\" \"line two\"]" (.-textContent display))))))

(t/deftest textarea-list-handler-trailing-newline-test
  (t/testing "preserves trailing empty line"
    (uix-tlr/render ($ textarea-list-component {}))
    (let [ta      (screen/get-by-placeholder-text "Enter abilities")
          display (screen/get-by-test-id "value-display")]
      (events/change ta "line one\n")
      (t/is (= "[\"line one\" \"\"]" (.-textContent display))))))

(t/deftest textarea-list-handler-multiple-newlines-test
  (t/testing "preserves consecutive empty lines"
    (uix-tlr/render ($ textarea-list-component {}))
    (let [ta      (screen/get-by-placeholder-text "Enter abilities")
          display (screen/get-by-test-id "value-display")]
      (events/change ta "one\n\nthree")
      (t/is (= "[\"one\" \"\" \"three\"]" (.-textContent display))))))

(t/deftest textarea-list-roundtrip-preserves-newlines-test
  (t/testing "joining vector items with newlines produces correct textarea value"
    (uix-tlr/render ($ textarea-list-component {:initial-value ["line one" "line two"]}))
    (let [ta (screen/get-by-placeholder-text "Enter abilities")]
      (t/is (= "line one\nline two" (.-value ta))))))
