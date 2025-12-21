(ns bashketball-ui.components.multiselect-typeahead-test
  "Tests for multiselect-typeahead component.

  Note: Radix Popover Portal does not work well in JSDom environment,
  so tests focus on trigger rendering, selected tags, and component
  structure rather than dropdown interactions."
  (:require
   ["react" :refer [useState]]
   [bashketball-ui.components.multiselect-typeahead :refer [multiselect-typeahead tag option-item]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [clojure.string :as str]
   [uix.core :refer [$ defui]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def fruit-options
  [{:value "apple" :label "Apple"}
   {:value "banana" :label "Banana"}
   {:value "cherry" :label "Cherry"}
   {:value "date" :label "Date"}])

(t/deftest multiselect-renders-trigger-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options}))
  (t/is (some? (screen/get-by-role "button"))))

(t/deftest multiselect-shows-placeholder-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options
                                            :placeholder "Choose fruits..."}))
  (t/is (some? (screen/get-by-text "Choose fruits..."))))

(t/deftest multiselect-shows-default-placeholder-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options}))
  (t/is (some? (screen/get-by-text "Select items..."))))

(t/deftest multiselect-shows-selected-values-as-tags-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options
                                            :value ["apple" "cherry"]}))
  (t/is (some? (screen/get-by-text "Apple")))
  (t/is (some? (screen/get-by-text "Cherry"))))

(t/deftest multiselect-hides-placeholder-when-values-selected-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options
                                            :value ["apple"]
                                            :placeholder "Choose..."}))
  (t/is (nil? (screen/query-by-text "Choose..."))))

(t/deftest multiselect-disabled-renders-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options
                                            :disabled true}))
  (t/is (some? (screen/get-by-role "button"))))

(t/deftest multiselect-with-id-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options
                                            :id "my-multiselect"}))
  (let [trigger (screen/get-by-role "button")]
    (t/is (= "my-multiselect" (.-id trigger)))))

(t/deftest multiselect-custom-class-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options
                                            :class "my-custom-class"}))
  (let [trigger (screen/get-by-role "button")]
    (t/is (str/includes? (.-className trigger) "my-custom-class"))))

(t/deftest multiselect-trigger-has-styling-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options}))
  (let [trigger (screen/get-by-role "button")]
    (t/is (str/includes? (.-className trigger) "rounded-md"))
    (t/is (str/includes? (.-className trigger) "border"))))

(t/deftest multiselect-empty-value-shows-placeholder-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options
                                            :value []
                                            :placeholder "Nothing selected"}))
  (t/is (some? (screen/get-by-text "Nothing selected"))))

(t/deftest multiselect-nil-value-shows-placeholder-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options
                                            :value nil
                                            :placeholder "Nothing selected"}))
  (t/is (some? (screen/get-by-text "Nothing selected"))))

(t/deftest tag-renders-with-label-test
  (uix-tlr/render ($ tag {:label "Test Tag" :on-remove identity}))
  (t/is (some? (screen/get-by-text "Test Tag"))))

(t/deftest tag-has-remove-element-test
  (uix-tlr/render ($ tag {:label "Removable" :on-remove identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Remove Removable"}))))

(t/deftest tag-remove-button-calls-on-remove-test
  (t/async done
           (let [removed (atom false)
                 _       (uix-tlr/render ($ tag {:label "Remove Me"
                                                 :on-remove #(reset! removed true)}))
                 usr     (user/setup)]
             (-> (user/click usr (screen/get-by-role "button" {:name "Remove Remove Me"}))
                 (.then (fn []
                          (t/is @removed)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest tag-has-styling-test
  (uix-tlr/render ($ tag {:label "Styled" :on-remove identity}))
  (let [text-el (screen/get-by-text "Styled")
        span-el (.closest text-el "span")]
    (t/is (some? span-el))
    (t/is (str/includes? (.-className span-el) "rounded-md"))
    (t/is (str/includes? (.-className span-el) "bg-blue-100"))))

(t/deftest option-item-renders-label-test
  (uix-tlr/render ($ option-item {:label "Option" :selected? false :on-select identity}))
  (t/is (some? (screen/get-by-text "Option"))))

(t/deftest option-item-shows-selected-styling-test
  (uix-tlr/render ($ option-item {:label "Selected" :selected? true :on-select identity}))
  (let [option-el (.-parentElement (screen/get-by-text "Selected"))]
    (t/is (str/includes? (.-className option-el) "bg-gray-50"))))

(t/deftest option-item-click-calls-on-select-test
  (t/async done
           (let [selected (atom false)
                 _        (uix-tlr/render ($ option-item {:label "Click Me"
                                                          :selected? false
                                                          :on-select #(reset! selected true)}))
                 usr      (user/setup)]
             (-> (user/click usr (.-parentElement (screen/get-by-text "Click Me")))
                 (.then (fn []
                          (t/is @selected)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(defui controlled-multiselect [{:keys [initial-value options]}]
  (let [[value set-value] (useState (or initial-value []))]
    ($ multiselect-typeahead {:value value
                              :options options
                              :on-change set-value})))

(t/deftest multiselect-tag-removal-updates-value-test
  (t/async done
           (let [_   (uix-tlr/render ($ controlled-multiselect {:initial-value ["apple" "banana"]
                                                                :options fruit-options}))
                 usr (user/setup)]
             (t/is (some? (screen/get-by-text "Apple")))
             (t/is (some? (screen/get-by-text "Banana")))
             (let [apple-tag     (screen/get-by-text "Apple")
                   remove-button (.. apple-tag -parentElement (querySelector "[role='button']"))]
               (-> (user/click usr remove-button)
                   (.then (fn []
                            (t/is (nil? (screen/query-by-text "Apple")))
                            (t/is (some? (screen/get-by-text "Banana")))
                            (done)))
                   (.catch (fn [e]
                             (t/is false (str e))
                             (done))))))))

(t/deftest multiselect-all-options-selected-test
  (uix-tlr/render ($ multiselect-typeahead {:options fruit-options
                                            :value ["apple" "banana" "cherry" "date"]}))
  (t/is (some? (screen/get-by-text "Apple")))
  (t/is (some? (screen/get-by-text "Banana")))
  (t/is (some? (screen/get-by-text "Cherry")))
  (t/is (some? (screen/get-by-text "Date"))))

(t/deftest multiselect-empty-options-test
  (uix-tlr/render ($ multiselect-typeahead {:options []
                                            :placeholder "No options"}))
  (t/is (some? (screen/get-by-text "No options"))))
