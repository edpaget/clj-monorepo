(ns bashketball-game-ui.hooks.use-cards-test
  "Tests for the use-cards, use-sets, and use-card hooks."
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing/react" :refer [MockedProvider]]
   ["@testing-library/react" :as rtl]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.hooks.use-cards :as use-cards]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-player-card
  #js {:__typename "PlayerCard"
       :slug "player-1"
       :name "Star Player"
       :setSlug "core-set"
       :cardType "PLAYER_CARD"
       :sht 8
       :pss 7
       :def 6
       :speed 5
       :size "MEDIUM"
       :abilities #js [#js {:name "Quick Hands"
                            :description "Improves ball handling"}]
       :deckSize 1})

(def sample-action-card
  #js {:__typename "StandardActionCard"
       :slug "action-1"
       :name "Fast Break"
       :setSlug "core-set"
       :cardType "STANDARD_ACTION_CARD"
       :fate 3
       :offense #js {:name "Score bonus"
                     :description "Add +2 to your shot roll"}
       :defense nil})

(def sample-card-set
  #js {:slug "core-set"
       :name "Core Set"
       :description "The base set of cards"})

(defn mock-cards-query
  "Creates a mock for the CARDS_QUERY with optional set filter."
  ([]
   (mock-cards-query nil))
  ([set-slug]
   #js {:request #js {:query queries/CARDS_QUERY
                      :variables (if set-slug #js {:setSlug set-slug} #js {})}
        :result #js {:data #js {:cards #js [sample-player-card sample-action-card]}}}))

(def mock-empty-cards-query
  #js {:request #js {:query queries/CARDS_QUERY
                     :variables #js {}}
       :result #js {:data #js {:cards #js []}}})

(def mock-sets-query
  #js {:request #js {:query queries/SETS_QUERY}
       :result #js {:data #js {:sets #js [sample-card-set]}}})

(def mock-empty-sets-query
  #js {:request #js {:query queries/SETS_QUERY}
       :result #js {:data #js {:sets #js []}}})

(def mock-card-query
  #js {:request #js {:query queries/CARD_QUERY
                     :variables #js {:slug "player-1"}}
       :result #js {:data #js {:card sample-player-card}}})

(def mock-card-not-found-query
  #js {:request #js {:query queries/CARD_QUERY
                     :variables #js {:slug "not-found"}}
       :result #js {:data #js {:card nil}}})

(def game-card-possible-types
  "PossibleTypes configuration for the GameCard union type."
  #js {:GameCard #js ["PlayerCard" "AbilityCard" "PlayCard"
                      "StandardActionCard" "SplitPlayCard"
                      "CoachingCard" "TeamAssetCard"]})

(defn create-test-cache
  "Creates an InMemoryCache configured for testing with union types."
  []
  (InMemoryCache. #js {:possibleTypes game-card-possible-types}))

(defn with-mocked-provider
  "Wraps hook rendering with MockedProvider configured for union types."
  [hook-fn mocks]
  (render/render-hook
   hook-fn
   {:wrapper (fn [props]
               ($ MockedProvider {:mocks (clj->js mocks)
                                  :cache (create-test-cache)}
                  (.-children props)))}))

(defn get-result
  "Gets the current hook result from render-hook output."
  [hook-result]
  (.-current (.-result hook-result)))

(defn wait-for
  "Wrapper around RTL waitFor that returns a promise."
  [f]
  (rtl/waitFor f))

;; =============================================================================
;; use-cards tests
;; =============================================================================

(t/deftest use-cards-returns-loading-state-initially-test
  (let [hook-result (with-mocked-provider #(use-cards/use-cards) [(mock-cards-query)])
        result      (get-result hook-result)]
    (t/is (:loading result) "Should be loading initially")))

(t/deftest use-cards-returns-cards-after-loading-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-cards) [(mock-cards-query)])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          cards  (:cards result)]
                      (t/is (not (:loading result)) "Should not be loading")
                      (t/is (= 2 (count cards)) "Should return 2 cards")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-returns-card-objects-with-typename-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-cards) [(mock-cards-query)])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (empty? (:cards result)))
                        (throw (js/Error. "Still loading or no cards"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          player (first (:cards result))]
                      (t/is (map? player) "Card should be a map")
                      (t/is (= "PlayerCard" (:__typename player)) "First card should be PlayerCard type")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-decodes-player-card-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-cards) [(mock-cards-query)])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (empty? (:cards result)))
                        (throw (js/Error. "Still loading or no cards"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          player (first (:cards result))]
                      (t/is (= "player-1" (:slug player)) "Should have correct slug")
                      (t/is (= "Star Player" (:name player)) "Should have correct name")
                      (t/is (= "core-set" (:set-slug player)) "Should have correct set-slug")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-decodes-player-card-stats-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-cards) [(mock-cards-query)])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (empty? (:cards result)))
                        (throw (js/Error. "Still loading or no cards"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          player (first (:cards result))]
                      (t/is (= 8 (:sht player)) "Should have correct sht stat")
                      (t/is (= 7 (:pss player)) "Should have correct pss stat")
                      (t/is (= 6 (:def player)) "Should have correct def stat")
                      (t/is (= 5 (:speed player)) "Should have correct speed stat")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-decodes-action-card-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-cards) [(mock-cards-query)])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (< (count (:cards result)) 2))
                        (throw (js/Error. "Still loading or not enough cards"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          action (second (:cards result))]
                      (t/is (= "action-1" (:slug action)) "Should have correct slug")
                      (t/is (= "Fast Break" (:name action)) "Should have correct name")
                      (t/is (= 3 (:fate action)) "Should have correct fate value")
                      (t/is (= "Score bonus" (get-in action [:offense :name])) "Should have correct offense name")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-returns-multiple-card-types-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-cards) [(mock-cards-query)])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (< (count (:cards result)) 2))
                        (throw (js/Error. "Still loading or not enough cards"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          action (second (:cards result))]
                      (t/is (map? action) "Second card should be a map")
                      (t/is (= "StandardActionCard" (:__typename action)) "Second card should be StandardActionCard type")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-returns-empty-when-no-cards-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-cards) [mock-empty-cards-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (empty? (:cards result)) "Should return empty cards")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-passes-set-slug-filter-test
  (t/async done
           (let [mock        #js {:request #js {:query queries/CARDS_QUERY
                                                :variables #js {:setSlug "core-set"}}
                                  :result #js {:data #js {:cards #js [sample-player-card]}}}
                 hook-result (with-mocked-provider #(use-cards/use-cards "core-set") [mock])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (= 1 (count (:cards result))) "Should return filtered cards")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-passes-card-type-filter-test
  (t/async done
           (let [mock        #js {:request #js {:query queries/CARDS_QUERY
                                                :variables #js {:cardType "PLAYER_CARD"}}
                                  :result #js {:data #js {:cards #js [sample-player-card]}}}
                 hook-result (with-mocked-provider
                               #(use-cards/use-cards {:card-type "PLAYER_CARD"})
                               [mock])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (= 1 (count (:cards result))) "Should return filtered cards")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-passes-both-filters-test
  (t/async done
           (let [mock        #js {:request #js {:query queries/CARDS_QUERY
                                                :variables #js {:setSlug "core-set"
                                                                :cardType "PLAYER_CARD"}}
                                  :result #js {:data #js {:cards #js [sample-player-card]}}}
                 hook-result (with-mocked-provider
                               #(use-cards/use-cards {:set-slug "core-set"
                                                      :card-type "PLAYER_CARD"})
                               [mock])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (= 1 (count (:cards result))) "Should return filtered cards")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-cards-returns-refetch-function-test
  (let [hook-result (with-mocked-provider #(use-cards/use-cards) [(mock-cards-query)])
        result      (get-result hook-result)]
    (t/is (fn? (:refetch result)) "Should return refetch function")))

;; =============================================================================
;; use-sets tests
;; =============================================================================

(t/deftest use-sets-returns-loading-initially-test
  (let [hook-result (with-mocked-provider #(use-cards/use-sets) [mock-sets-query])
        result      (get-result hook-result)]
    (t/is (:loading result) "Should be loading initially")))

(t/deftest use-sets-returns-sets-after-loading-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-sets) [mock-sets-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          sets   (:sets result)]
                      (t/is (= 1 (count sets)) "Should return 1 set")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-sets-decodes-set-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-sets) [mock-sets-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (empty? (:sets result)))
                        (throw (js/Error. "Still loading or no sets"))))))
                 (.then
                  (fn []
                    (let [result   (get-result hook-result)
                          card-set (first (:sets result))]
                      (t/is (= "core-set" (:slug card-set)) "Should have correct slug")
                      (t/is (= "Core Set" (:name card-set)) "Should have correct name")
                      (t/is (= "The base set of cards" (:description card-set)) "Should have correct description")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-sets-returns-empty-sets-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-sets) [mock-empty-sets-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (empty? (:sets result)) "Should return empty sets")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

;; =============================================================================
;; use-card tests
;; =============================================================================

(t/deftest use-card-returns-loading-initially-test
  (let [hook-result (with-mocked-provider #(use-cards/use-card "player-1") [mock-card-query])
        result      (get-result hook-result)]
    (t/is (:loading result) "Should be loading initially")))

(t/deftest use-card-returns-card-after-loading-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-card "player-1") [mock-card-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          card   (:card result)]
                      (t/is (some? card) "Should return a card")
                      (t/is (not (:loading result)) "Should not be loading")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-card-returns-card-with-typename-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-card "player-1") [mock-card-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (nil? (:card result)))
                        (throw (js/Error. "Still loading or no card"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          card   (:card result)]
                      (t/is (map? card) "Card should be a map")
                      (t/is (= "PlayerCard" (:__typename card)) "Card should have PlayerCard typename")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-card-decodes-card-basic-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-card "player-1") [mock-card-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (nil? (:card result)))
                        (throw (js/Error. "Still loading or no card"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          card   (:card result)]
                      (t/is (= "player-1" (:slug card)) "Should have correct slug")
                      (t/is (= "Star Player" (:name card)) "Should have correct name")
                      (t/is (= "core-set" (:set-slug card)) "Should have correct set-slug")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-card-decodes-player-stats-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-card "player-1") [mock-card-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (nil? (:card result)))
                        (throw (js/Error. "Still loading or no card"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          card   (:card result)]
                      (t/is (= 8 (:sht card)) "Should have correct sht stat")
                      (t/is (= 7 (:pss card)) "Should have correct pss stat")
                      (t/is (= 6 (:def card)) "Should have correct def stat")
                      (t/is (= 5 (:speed card)) "Should have correct speed stat")
                      (t/is (= 1 (:deck-size card)) "Should have correct deck-size")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-card-returns-nil-for-not-found-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-cards/use-card "not-found") [mock-card-not-found-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (nil? (:card result)) "Should return nil for not found")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-card-skips-query-when-slug-nil-test
  (let [hook-result (with-mocked-provider #(use-cards/use-card nil) [])
        result      (get-result hook-result)]
    (t/is (nil? (:card result)) "Should return nil when slug is nil")
    (t/is (not (:loading result)) "Should not be loading when skipped")))
