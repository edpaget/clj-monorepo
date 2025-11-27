(ns bashketball-editor-ui.views.card-editor-test
  (:require
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.views.card-editor :refer [card-editor-view]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def mock-card-sets-response
  #js {:request #js {:query q/CARD_SETS_QUERY}
       :result #js {:data #js {:cardSets #js {:data #js [#js {:slug "core-set"
                                                              :name "Core Set"
                                                              :createdAt "2024-01-01"
                                                              :updatedAt "2024-01-01"}]}}}})

(defn with-providers
  "Wrap component with required providers for testing."
  ([component]
   (with-providers component "/cards/new"))
  ([component initial-path]
   ($ rr/MemoryRouter {:initialEntries #js [initial-path]}
      ($ MockedProvider {:mocks #js [mock-card-sets-response]}
         ($ rr/Routes
            ($ rr/Route {:path "cards/new" :element component})
            ($ rr/Route {:path "cards/:slug/edit" :element component}))))))

;; -----------------------------------------------------------------------------
;; Create new card tests
;; -----------------------------------------------------------------------------

(t/deftest card-editor-renders-title-test
  (t/testing "shows Create New Card title for new cards"
    (tlr/render (with-providers ($ card-editor-view)))
    (t/is (some? (tlr/get-by-text "Create New Card")))))

(t/deftest card-editor-has-back-button-test
  (t/testing "shows back button"
    (tlr/render (with-providers ($ card-editor-view)))
    (t/is (some? (tlr/get-by-role "button" {:name "Back"})))))

(t/deftest card-editor-shows-set-selector-test
  (t/async done
           (t/testing "shows set selector for new cards"
             (tlr/render (with-providers ($ card-editor-view)))
             (-> (tlr/wait-for #(tlr/get-by-text "Select Set"))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-text "Select Set")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest card-editor-has-set-selector-combobox-test
  (t/async done
           (t/testing "has set selector combobox"
             (tlr/render (with-providers ($ card-editor-view)))
             (-> (tlr/wait-for (fn [] (tlr/get-by-role "combobox")))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-role "combobox")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
