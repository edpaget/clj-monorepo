(ns bashketball-editor-ui.components.cards.set-selector-test
  (:require
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.components.cards.set-selector :refer [set-selector]]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-ui.context.auth :refer [auth-context]]
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
                                                              :updatedAt "2024-01-01"}
                                                         #js {:slug "expansion-1"
                                                              :name "Expansion 1"
                                                              :createdAt "2024-02-01"
                                                              :updatedAt "2024-02-01"}]}}}})

(defn with-providers
  [component {:keys [initial-entries logged-in?] :or {logged-in? false}}]
  (let [auth-state {:loading? false
                    :logged-in? logged-in?
                    :user (when logged-in? {:id "test-user"})
                    :refetch (fn [])}]
    ($ rr/MemoryRouter {:initialEntries (clj->js initial-entries)}
       ($ (.-Provider auth-context) {:value auth-state}
          ($ MockedProvider {:mocks #js [mock-card-sets-response]}
             component)))))

(t/deftest set-selector-renders-test
  (t/testing "renders selector"
    (tlr/render (with-providers ($ set-selector {}) {:initial-entries ["/"]}))
    (t/is (some? (tlr/get-by-role "combobox")))))

(t/deftest set-selector-shows-current-value-test
  (t/testing "shows current set when provided"
    (tlr/render (with-providers
                  ($ set-selector {:current-set-slug "core-set"})
                  {:initial-entries ["/?set=core-set"]}))
    (t/is (some? (tlr/get-by-role "combobox")))))
