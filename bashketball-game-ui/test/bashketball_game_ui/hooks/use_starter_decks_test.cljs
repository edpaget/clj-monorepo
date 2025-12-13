(ns bashketball-game-ui.hooks.use-starter-decks-test
  "Tests for starter deck hooks."
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing/react" :refer [MockedProvider]]
   ["@testing-library/react" :as rtl]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.hooks.use-starter-decks :as use-starter-decks]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-starter-deck-1
  #js {:id "goblin-blitz"
       :name "Goblin Blitz"
       :description "Fast-paced goblin aggro deck"
       :cardCount 35})

(def sample-starter-deck-2
  #js {:id "elven-finesse"
       :name "Elven Finesse"
       :description "Precision passing and shooting"
       :cardCount 40})

(def sample-claimed-deck
  #js {:starterDeckId "goblin-blitz"
       :deckId "550e8400-e29b-41d4-a716-446655440000"
       :claimedAt "2024-01-15T10:30:00Z"})

(def mock-available-starter-decks-query
  #js {:request #js {:query queries/AVAILABLE_STARTER_DECKS_QUERY}
       :result #js {:data #js {:availableStarterDecks #js [sample-starter-deck-1
                                                           sample-starter-deck-2]}}})

(def mock-empty-available-query
  #js {:request #js {:query queries/AVAILABLE_STARTER_DECKS_QUERY}
       :result #js {:data #js {:availableStarterDecks #js []}}})

(def mock-claimed-starter-decks-query
  #js {:request #js {:query queries/CLAIMED_STARTER_DECKS_QUERY}
       :result #js {:data #js {:claimedStarterDecks #js [sample-claimed-deck]}}})

(def mock-claim-starter-deck-mutation
  #js {:request #js {:query mutations/CLAIM_STARTER_DECK_MUTATION
                     :variables #js {:starterDeckId "elven-finesse"}}
       :result #js {:data #js {:claimStarterDeck
                               #js {:starterDeckId "elven-finesse"
                                    :deckId "550e8400-e29b-41d4-a716-446655440001"
                                    :claimedAt "2024-01-16T14:00:00Z"
                                    :deck #js {:id "550e8400-e29b-41d4-a716-446655440001"
                                               :name "Elven Finesse"
                                               :cardSlugs #js ["elf-1" "elf-2"]
                                               :isValid true
                                               :validationErrors #js []}}}}})

(defn create-test-cache []
  (InMemoryCache. #js {:addTypename false}))

(defn with-mocked-provider [hook-fn mocks]
  (render/render-hook
   hook-fn
   {:wrapper (fn [props]
               ($ MockedProvider {:mocks (clj->js mocks)
                                  :cache (create-test-cache)
                                  :addTypename false}
                  (.-children props)))}))

(defn get-result [hook-result]
  (.-current (.-result hook-result)))

(defn wait-for [f]
  (rtl/waitFor f))

;; =============================================================================
;; use-available-starter-decks tests
;; =============================================================================

(t/deftest use-available-starter-decks-returns-loading-initially-test
  (let [hook-result (with-mocked-provider
                      #(use-starter-decks/use-available-starter-decks)
                      [mock-available-starter-decks-query])
        result      (get-result hook-result)]
    (t/is (:loading result) "Should be loading initially")))

(t/deftest use-available-starter-decks-returns-decks-test
  (t/async done
           (let [hook-result (with-mocked-provider
                               #(use-starter-decks/use-available-starter-decks)
                               [mock-available-starter-decks-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          decks  (:starter-decks result)]
                      (t/is (not (:loading result)) "Should not be loading")
                      (t/is (= 2 (count decks)) "Should return 2 starter decks")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-available-starter-decks-decodes-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider
                               #(use-starter-decks/use-available-starter-decks)
                               [mock-available-starter-decks-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (empty? (:starter-decks result)))
                        (throw (js/Error. "Still loading or no decks"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          deck   (first (:starter-decks result))]
                      (t/is (= "goblin-blitz" (:id deck)) "Should have correct id")
                      (t/is (= "Goblin Blitz" (:name deck)) "Should have correct name")
                      (t/is (= "Fast-paced goblin aggro deck" (:description deck)))
                      (t/is (= 35 (:card-count deck)) "Should have correct card count")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-available-starter-decks-returns-empty-when-all-claimed-test
  (t/async done
           (let [hook-result (with-mocked-provider
                               #(use-starter-decks/use-available-starter-decks)
                               [mock-empty-available-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (empty? (:starter-decks result)) "Should return empty")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

;; =============================================================================
;; use-claimed-starter-decks tests
;; =============================================================================

(t/deftest use-claimed-starter-decks-returns-loading-initially-test
  (let [hook-result (with-mocked-provider
                      #(use-starter-decks/use-claimed-starter-decks)
                      [mock-claimed-starter-decks-query])
        result      (get-result hook-result)]
    (t/is (:loading result) "Should be loading initially")))

(t/deftest use-claimed-starter-decks-returns-claims-test
  (t/async done
           (let [hook-result (with-mocked-provider
                               #(use-starter-decks/use-claimed-starter-decks)
                               [mock-claimed-starter-decks-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          claims (:claimed-decks result)]
                      (t/is (not (:loading result)) "Should not be loading")
                      (t/is (= 1 (count claims)) "Should return 1 claimed deck")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-claimed-starter-decks-decodes-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider
                               #(use-starter-decks/use-claimed-starter-decks)
                               [mock-claimed-starter-decks-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (empty? (:claimed-decks result)))
                        (throw (js/Error. "Still loading or no claims"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          claim  (first (:claimed-decks result))]
                      (t/is (= "goblin-blitz" (:starter-deck-id claim)))
                      (t/is (some? (:deck-id claim)) "Should have deck-id")
                      (t/is (some? (:claimed-at claim)) "Should have claimed-at")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

;; =============================================================================
;; use-claim-starter-deck tests
;; =============================================================================

(t/deftest use-claim-starter-deck-returns-tuple-test
  (let [hook-result (with-mocked-provider
                      #(use-starter-decks/use-claim-starter-deck)
                      [mock-claim-starter-deck-mutation])
        result      (get-result hook-result)]
    (t/is (vector? result) "Should return a vector")
    (t/is (= 2 (count result)) "Should have 2 elements")
    (t/is (fn? (first result)) "First element should be mutation function")))

(t/deftest use-claim-starter-deck-returns-loading-state-test
  (let [hook-result (with-mocked-provider
                      #(use-starter-decks/use-claim-starter-deck)
                      [mock-claim-starter-deck-mutation])
        [_ state]   (get-result hook-result)]
    (t/is (contains? state :loading) "State should have :loading")
    (t/is (contains? state :error) "State should have :error")
    (t/is (contains? state :data) "State should have :data")))
