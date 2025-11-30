(ns bashketball-ui.components.select-test
  (:require
   [bashketball-ui.components.select :refer [select]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def fruit-options
  [{:value "apple" :label "Apple"}
   {:value "banana" :label "Banana"}
   {:value "cherry" :label "Cherry"}])

(t/deftest select-renders-trigger-test
  (uix-tlr/render ($ select {:placeholder "Select fruit..." :options fruit-options}))
  (t/is (some? (screen/get-by-role "combobox"))))

(t/deftest select-shows-placeholder-test
  (uix-tlr/render ($ select {:placeholder "Select fruit..." :options fruit-options}))
  (t/is (some? (screen/get-by-text "Select fruit..."))))

(t/deftest select-trigger-has-styling-test
  (uix-tlr/render ($ select {:placeholder "Select..." :options fruit-options}))
  (let [trigger (screen/get-by-role "combobox")]
    (t/is (str/includes? (.-className trigger) "rounded-md"))))

(t/deftest select-disabled-state-test
  (uix-tlr/render ($ select {:placeholder "Select..." :options fruit-options :disabled true}))
  (let [trigger (screen/get-by-role "combobox")]
    (t/is (.hasAttribute trigger "data-disabled"))))

(t/deftest select-with-id-test
  (uix-tlr/render ($ select {:placeholder "Select..." :options fruit-options :id "my-select"}))
  (let [trigger (screen/get-by-role "combobox")]
    (t/is (= "my-select" (.-id trigger)))))

(t/deftest select-custom-class-test
  (uix-tlr/render ($ select {:placeholder "Select..." :options fruit-options :class "my-custom-class"}))
  (let [trigger (screen/get-by-role "combobox")]
    (t/is (str/includes? (.-className trigger) "my-custom-class"))))

(t/deftest select-shows-default-value-test
  (uix-tlr/render ($ select {:placeholder "Select fruit..."
                             :options fruit-options
                             :default-value "cherry"}))
  (t/is (some? (screen/get-by-text "Cherry"))))

(t/deftest select-shows-controlled-value-test
  (uix-tlr/render ($ select {:placeholder "Select fruit..."
                             :options fruit-options
                             :value "apple"}))
  (t/is (some? (screen/get-by-text "Apple"))))

(t/deftest select-aria-expanded-false-initially-test
  (uix-tlr/render ($ select {:placeholder "Select..." :options fruit-options}))
  (let [trigger (screen/get-by-role "combobox")]
    (t/is (= "false" (.getAttribute trigger "aria-expanded")))))

(t/deftest select-has-aria-autocomplete-test
  (uix-tlr/render ($ select {:placeholder "Select..." :options fruit-options}))
  (let [trigger (screen/get-by-role "combobox")]
    (t/is (.hasAttribute trigger "aria-autocomplete"))))
