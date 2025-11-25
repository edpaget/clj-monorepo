(ns basketball-editor-ui.views.home-test
  (:require
   [basketball-editor-ui.views.home :refer [home-view]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest home-view-renders-header-test
  (t/testing "renders application header"
    (tlr/render ($ home-view))
    (t/is (some? (tlr/get-by-text "Bashketball Card Editor")))))

(t/deftest home-view-renders-search-test
  (t/testing "renders search input"
    (tlr/render ($ home-view))
    (t/is (some? (tlr/get-by-placeholder-text "Search cards...")))))

(t/deftest home-view-renders-buttons-test
  (t/testing "renders Search and New Card buttons"
    (tlr/render ($ home-view))
    (t/is (some? (tlr/get-by-role "button" {:name "Search"})))
    (t/is (some? (tlr/get-by-role "button" {:name "New Card"})))))

(t/deftest home-view-search-input-works-test
  (t/async done
    (t/testing "user can type in search input"
      (let [user (tlr/setup)]
        (tlr/render ($ home-view))
        (let [search-input (tlr/get-by-placeholder-text "Search cards...")]
          (-> (tlr/type-text user search-input "fire")
              (.then (fn []
                       (t/is (= "fire" (.-value search-input)))
                       (done)))
              (.catch (fn [e]
                        (t/is false (str e))
                        (done)))))))))
