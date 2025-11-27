(ns bashketball-editor-ui.components.ui.select-test
  (:require
   ["@radix-ui/react-select" :as SelectPrimitive]
   [bashketball-editor-ui.components.ui.select :refer [select
                                                       select-item
                                                       select-trigger]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def test-options
  [{:value "apple" :label "Apple"}
   {:value "banana" :label "Banana"}
   {:value "cherry" :label "Cherry"}])

(t/deftest select-renders-combobox-test
  (t/testing "renders select with combobox role"
    (tlr/render ($ select {:placeholder "Select a fruit..."
                           :options test-options}))
    (t/is (some? (tlr/get-by-role "combobox")))))

(t/deftest select-shows-placeholder-test
  (t/testing "displays placeholder when no value selected"
    (tlr/render ($ select {:placeholder "Choose one..."
                           :options test-options}))
    (t/is (some? (tlr/get-by-text "Choose one...")))))

(t/deftest select-trigger-has-correct-classes-test
  (t/testing "trigger has expected styling classes"
    (tlr/render ($ select {:placeholder "Select..."
                           :options test-options}))
    (let [trigger (tlr/get-by-role "combobox")]
      (t/is (.includes (.-className trigger) "rounded-md"))
      (t/is (.includes (.-className trigger) "border")))))

(t/deftest select-disabled-test
  (t/testing "select can be disabled"
    (tlr/render ($ select {:placeholder "Select..."
                           :options test-options
                           :disabled true}))
    (let [trigger (tlr/get-by-role "combobox")]
      (t/is (.hasAttribute trigger "data-disabled")))))

(t/deftest select-has-id-test
  (t/testing "id prop is passed to trigger"
    (tlr/render ($ select {:placeholder "Select..."
                           :options test-options
                           :id "fruit-select"}))
    (t/is (some? (js/document.getElementById "fruit-select")))))

(t/deftest select-empty-options-test
  (t/testing "renders with empty options"
    (tlr/render ($ select {:placeholder "No options"
                           :options []}))
    (t/is (some? (tlr/get-by-role "combobox")))))

(t/deftest select-custom-class-test
  (t/testing "custom class is applied to trigger"
    (tlr/render ($ select {:placeholder "Select..."
                           :options test-options
                           :class "custom-class"}))
    (let [trigger (tlr/get-by-role "combobox")]
      (t/is (.includes (.-className trigger) "custom-class")))))

(t/deftest select-has-chevron-icon-test
  (t/testing "renders chevron down icon"
    (tlr/render ($ select {:placeholder "Select..."
                           :options test-options}))
    (let [svg (js/document.querySelector "svg.lucide-chevron-down")]
      (t/is (some? svg)))))

(t/deftest select-trigger-standalone-test
  (t/testing "select-trigger renders independently"
    (tlr/render
     ($ SelectPrimitive/Root
        ($ select-trigger {:placeholder "Pick one..."})))
    (t/is (some? (tlr/get-by-role "combobox")))
    (t/is (some? (tlr/get-by-text "Pick one...")))))

(t/deftest select-item-renders-test
  (t/testing "select-item renders with correct value"
    (tlr/render
     ($ SelectPrimitive/Root {:defaultValue "test"}
        ($ SelectPrimitive/Trigger
           ($ SelectPrimitive/Value))
        ($ SelectPrimitive/Content
           ($ select-item {:value "test"} "Test Item"))))
    (t/is (some? (tlr/get-by-text "Test Item")))))
