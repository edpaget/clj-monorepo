(ns bashketball-editor-ui.views.home-test
  (:require
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.graphql.queries :as q]
   [bashketball-editor-ui.views.home :refer [home-view]]
   [bashketball-ui.context.auth :refer [auth-context]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def mock-empty-cards-response
  #js {:request #js {:query q/CARDS_QUERY
                     :variables #js {}}
       :result #js {:data #js {:cards #js {:data #js []}}}})

(def mock-cards-with-player-type-filter
  #js {:request #js {:query q/CARDS_QUERY
                     :variables #js {:cardType "PLAYER_CARD"}}
       :result #js {:data #js {:cards #js {:data #js [#js {:__typename "PlayerCard"
                                                           :slug "jordan"
                                                           :setSlug "core-set"
                                                           :name "Jordan"
                                                           :updatedAt "2024-01-01"}]}}}})

(def mock-cards-with-set-filter
  #js {:request #js {:query q/CARDS_QUERY
                     :variables #js {:setSlug "core-set"}}
       :result #js {:data #js {:cards #js {:data #js [#js {:__typename "PlayerCard"
                                                           :slug "jordan"
                                                           :setSlug "core-set"
                                                           :name "Jordan"
                                                           :updatedAt "2024-01-01"}
                                                      #js {:__typename "PlayCard"
                                                           :slug "fast-break"
                                                           :setSlug "core-set"
                                                           :name "Fast Break"
                                                           :updatedAt "2024-01-01"}]}}}})

(def mock-cards-with-both-filters
  #js {:request #js {:query q/CARDS_QUERY
                     :variables #js {:setSlug "core-set" :cardType "PLAYER_CARD"}}
       :result #js {:data #js {:cards #js {:data #js [#js {:__typename "PlayerCard"
                                                           :slug "jordan"
                                                           :setSlug "core-set"
                                                           :name "Jordan"
                                                           :updatedAt "2024-01-01"}]}}}})

(def mock-card-sets-response
  #js {:request #js {:query q/CARD_SETS_QUERY}
       :result #js {:data #js {:cardSets #js {:data #js [#js {:slug "core-set"
                                                              :name "Core Set"
                                                              :createdAt "2024-01-01"
                                                              :updatedAt "2024-01-01"}]}}}})

(defn with-providers
  "Wrap component with required providers for testing."
  ([component]
   (with-providers component {}))
  ([component {:keys [logged-in? initial-entries mocks]
               :or {logged-in? false
                    initial-entries ["/"]
                    mocks [mock-empty-cards-response mock-card-sets-response]}}]
   (let [auth-state {:loading? false
                     :logged-in? logged-in?
                     :user (when logged-in? {:id "test-user"})
                     :refetch (fn [])}]
     ($ rr/MemoryRouter {:initialEntries (clj->js initial-entries)}
        ($ (.-Provider auth-context) {:value auth-state}
           ($ MockedProvider {:mocks (clj->js mocks)}
              component))))))

(t/deftest home-view-renders-search-test
  (t/testing "renders search input"
    (tlr/render (with-providers ($ home-view)))
    (t/is (some? (tlr/get-by-placeholder-text "Search cards...")))))

(t/deftest home-view-renders-new-card-button-test
  (t/testing "renders New Card button when logged in"
    (tlr/render (with-providers ($ home-view) {:logged-in? true}))
    (let [new-card-buttons (tlr/get-all-by-role "button" {:name "New Card"})]
      (t/is (>= (count new-card-buttons) 1)))))

(t/deftest home-view-hides-new-card-button-when-not-logged-in-test
  (t/testing "hides New Card button when not logged in"
    (tlr/render (with-providers ($ home-view) {:logged-in? false}))
    (t/is (nil? (tlr/query-by-role "button" {:name "New Card"})))))

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

(t/deftest home-view-card-type-filter-test
  (t/async done
           (t/testing "card type filter sends correct query variables"
             (tlr/render (with-providers
                           ($ home-view)
                           {:initial-entries ["/?type=PLAYER_CARD"]
                            :mocks [mock-cards-with-player-type-filter mock-card-sets-response]}))
             (-> (tlr/wait-for (fn [] (tlr/get-by-text "Jordan")))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-text "Jordan")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest home-view-set-filter-test
  (t/async done
           (t/testing "set filter sends correct query variables"
             (tlr/render (with-providers
                           ($ home-view)
                           {:initial-entries ["/?set=core-set"]
                            :mocks [mock-cards-with-set-filter mock-card-sets-response]}))
             (-> (tlr/wait-for (fn [] (tlr/get-by-text "Jordan")))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-text "Jordan")))
                          (t/is (some? (tlr/get-by-text "Fast Break")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest home-view-both-filters-test
  (t/async done
           (t/testing "both filters send correct query variables"
             (tlr/render (with-providers
                           ($ home-view)
                           {:initial-entries ["/?set=core-set&type=PLAYER_CARD"]
                            :mocks [mock-cards-with-both-filters mock-card-sets-response]}))
             (-> (tlr/wait-for (fn [] (tlr/get-by-text "Jordan")))
                 (.then (fn []
                          (t/is (some? (tlr/get-by-text "Jordan")))
                          (t/is (nil? (tlr/query-by-text "Fast Break")))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
