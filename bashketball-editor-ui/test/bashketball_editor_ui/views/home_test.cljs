(ns bashketball-editor-ui.views.home-test
  (:require
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.views.home :refer [home-view]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def mock-empty-cards-response
  #js {:request #js {:query q/CARDS_QUERY
                     :variables #js {:setSlug nil}}
       :result #js {:data #js {:cards #js {:data #js []}}}})

(def mock-card-sets-response
  #js {:request #js {:query q/CARD_SETS_QUERY}
       :result #js {:data #js {:cardSets #js {:data #js [#js {:slug "core-set"
                                                               :name "Core Set"
                                                               :createdAt "2024-01-01"
                                                               :updatedAt "2024-01-01"}]}}}})

(defn with-providers
  "Wrap component with required providers for testing."
  [component]
  ($ rr/MemoryRouter
     ($ MockedProvider {:mocks #js [mock-empty-cards-response
                                    mock-card-sets-response]}
        component)))

(t/deftest home-view-renders-search-test
  (t/testing "renders search input"
    (tlr/render (with-providers ($ home-view)))
    (t/is (some? (tlr/get-by-placeholder-text "Search cards...")))))

(t/deftest home-view-renders-buttons-test
  (t/testing "renders Search and New Card buttons"
    (tlr/render (with-providers ($ home-view)))
    (t/is (some? (tlr/get-by-role "button" {:name "Search"})))
    (let [new-card-buttons (tlr/get-all-by-role "button" {:name "New Card"})]
      (t/is (>= (count new-card-buttons) 1)))))

(t/deftest home-view-search-input-works-test
  (t/async done
           (t/testing "user can type in search input"
             (let [user (tlr/setup)]
               (tlr/render (with-providers ($ home-view)))
               (let [search-input (tlr/get-by-placeholder-text "Search cards...")]
                 (-> (tlr/type-text user search-input "fire")
                     (.then (fn []
                              (t/is (= "fire" (.-value search-input)))
                              (done)))
                     (.catch (fn [e]
                               (t/is false (str e))
                               (done)))))))))
