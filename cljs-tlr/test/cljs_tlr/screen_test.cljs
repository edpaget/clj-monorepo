(ns cljs-tlr.screen-test
  (:require
   ["react" :refer [createElement]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs-tlr.screen :as screen]
   [cljs.test :as t :include-macros true]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest get-by-role-finds-button-test
  (t/testing "get-by-role finds button element"
    (render/render (createElement "button" nil "Submit"))
    (t/is (some? (screen/get-by-role "button")))))

(t/deftest get-by-role-with-name-option-test
  (t/testing "get-by-role filters by accessible name"
    (render/render
     (createElement "div" nil
                    (createElement "button" nil "Save")
                    (createElement "button" nil "Cancel")))
    (let [save-btn (screen/get-by-role "button" {:name "Save"})]
      (t/is (some? save-btn))
      (t/is (= "Save" (.-textContent save-btn))))))

(t/deftest get-by-label-text-finds-input-test
  (t/testing "get-by-label-text finds form input by label"
    (render/render
     (createElement "div" nil
                    (createElement "label" #js {:htmlFor "email"} "Email")
                    (createElement "input" #js {:id "email" :type "email"})))
    (t/is (some? (screen/get-by-label-text "Email")))))

(t/deftest get-by-placeholder-text-test
  (t/testing "get-by-placeholder-text finds input"
    (render/render
     (createElement "input" #js {:placeholder "Enter your name"}))
    (t/is (some? (screen/get-by-placeholder-text "Enter your name")))))

(t/deftest get-by-display-value-test
  (t/testing "get-by-display-value finds input with value"
    (render/render
     (createElement "input" #js {:defaultValue "test@example.com"}))
    (t/is (some? (screen/get-by-display-value "test@example.com")))))

(t/deftest get-by-alt-text-finds-image-test
  (t/testing "get-by-alt-text finds image"
    (render/render
     (createElement "img" #js {:alt "Company logo" :src "/logo.png"}))
    (t/is (some? (screen/get-by-alt-text "Company logo")))))

(t/deftest get-by-title-test
  (t/testing "get-by-title finds element by title attribute"
    (render/render
     (createElement "span" #js {:title "Close"} "X"))
    (t/is (some? (screen/get-by-title "Close")))))

(t/deftest get-by-test-id-test
  (t/testing "get-by-test-id finds element by data-testid"
    (render/render
     (createElement "div" #js {:data-testid "custom-element"} "Content"))
    (t/is (some? (screen/get-by-test-id "custom-element")))))

(t/deftest get-all-by-role-returns-all-matches-test
  (t/testing "get-all-by-role returns array of all matches"
    (render/render
     (createElement "div" nil
                    (createElement "button" nil "One")
                    (createElement "button" nil "Two")
                    (createElement "button" nil "Three")))
    (let [buttons (screen/get-all-by-role "button")]
      (t/is (= 3 (.-length buttons))))))

(t/deftest query-all-by-role-returns-empty-array-test
  (t/testing "query-all-by-role returns empty array when none found"
    (render/render (createElement "div" nil "No buttons here"))
    (let [buttons (screen/query-all-by-role "button")]
      (t/is (= 0 (.-length buttons))))))
