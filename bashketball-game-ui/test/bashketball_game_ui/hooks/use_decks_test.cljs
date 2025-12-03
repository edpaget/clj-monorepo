(ns bashketball-game-ui.hooks.use-decks-test
  "Tests for deck-related hooks: use-my-decks, use-deck, and mutation hooks."
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing/react" :refer [MockedProvider]]
   ["@testing-library/react" :as rtl]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.hooks.use-decks :as use-decks]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-deck
  #js {:id "550e8400-e29b-41d4-a716-446655440000"
       :name "My Deck"
       :cardSlugs #js ["player-1" "action-1"]
       :isValid true
       :validationErrors #js []})

(def sample-player-card
  #js {:__typename "PlayerCard"
       :slug "player-1"
       :name "Star Player"
       :setSlug "core-set"
       :cardType "PLAYER_CARD"
       :sht 8 :pss 7 :def 6 :speed 5
       :size "MEDIUM"
       :abilities #js []
       :deckSize 1})

(def sample-deck-with-cards
  #js {:id "550e8400-e29b-41d4-a716-446655440000"
       :name "My Deck"
       :cardSlugs #js ["player-1" "action-1"]
       :cards #js [sample-player-card]
       :isValid false
       :validationErrors #js ["Need more cards"]})

(def sample-deck-2
  #js {:id "550e8400-e29b-41d4-a716-446655440001"
       :name "Second Deck"
       :cardSlugs #js []
       :isValid false
       :validationErrors #js ["Deck is empty"]})

(def mock-my-decks-query
  #js {:request #js {:query queries/MY_DECKS_QUERY}
       :result #js {:data #js {:myDecks #js [sample-deck sample-deck-2]}}})

(def mock-empty-decks-query
  #js {:request #js {:query queries/MY_DECKS_QUERY}
       :result #js {:data #js {:myDecks #js []}}})

(def mock-deck-query
  #js {:request #js {:query queries/DECK_QUERY
                     :variables #js {:id "550e8400-e29b-41d4-a716-446655440000"}}
       :result #js {:data #js {:deck sample-deck-with-cards}}})

(def mock-deck-not-found-query
  #js {:request #js {:query queries/DECK_QUERY
                     :variables #js {:id "not-found-id"}}
       :result #js {:data #js {:deck nil}}})

(def mock-create-deck-mutation
  #js {:request #js {:query mutations/CREATE_DECK_MUTATION
                     :variables #js {:name "New Deck"}}
       :result #js {:data #js {:createDeck #js {:id "new-deck-id"
                                                :name "New Deck"
                                                :cardSlugs #js []
                                                :isValid false
                                                :validationErrors #js ["Deck is empty"]}}}})

(def mock-update-deck-mutation
  #js {:request #js {:query mutations/UPDATE_DECK_MUTATION
                     :variables #js {:id "550e8400-e29b-41d4-a716-446655440000"
                                     :name "Updated Deck"
                                     :cardSlugs #js ["player-1" "player-2"]}}
       :result #js {:data #js {:updateDeck #js {:id "550e8400-e29b-41d4-a716-446655440000"
                                                :name "Updated Deck"
                                                :cardSlugs #js ["player-1" "player-2"]
                                                :isValid false
                                                :validationErrors #js []}}}})

(def mock-delete-deck-mutation
  #js {:request #js {:query mutations/DELETE_DECK_MUTATION
                     :variables #js {:id "550e8400-e29b-41d4-a716-446655440000"}}
       :result #js {:data #js {:deleteDeck true}}})

(def mock-validate-deck-mutation
  #js {:request #js {:query mutations/VALIDATE_DECK_MUTATION
                     :variables #js {:id "550e8400-e29b-41d4-a716-446655440000"}}
       :result #js {:data #js {:validateDeck #js {:id "550e8400-e29b-41d4-a716-446655440000"
                                                  :isValid true
                                                  :validationErrors #js []}}}})

(def game-card-possible-types
  "PossibleTypes configuration for the GameCard union type in deck cards."
  #js {:GameCard #js ["PlayerCard" "AbilityCard" "PlayCard"
                      "StandardActionCard" "SplitPlayCard"
                      "CoachingCard" "TeamAssetCard"]})

(defn create-test-cache
  "Creates an InMemoryCache configured for testing with union types."
  []
  (InMemoryCache. #js {:possibleTypes game-card-possible-types
                       :addTypename false}))

(defn with-mocked-provider
  "Wraps hook rendering with MockedProvider configured for union types."
  [hook-fn mocks]
  (render/render-hook
   hook-fn
   {:wrapper (fn [props]
               ($ MockedProvider {:mocks (clj->js mocks)
                                  :cache (create-test-cache)
                                  :addTypename false}
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
;; use-my-decks tests
;; =============================================================================

(t/deftest use-my-decks-returns-loading-initially-test
  (let [hook-result (with-mocked-provider #(use-decks/use-my-decks) [mock-my-decks-query])
        result      (get-result hook-result)]
    (t/is (:loading result) "Should be loading initially")))

(t/deftest use-my-decks-returns-decks-after-loading-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-decks/use-my-decks) [mock-my-decks-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          decks  (:decks result)]
                      (t/is (not (:loading result)) "Should not be loading")
                      (t/is (= 2 (count decks)) "Should return 2 decks")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-my-decks-decodes-first-deck-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-decks/use-my-decks) [mock-my-decks-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (empty? (:decks result)))
                        (throw (js/Error. "Still loading or no decks"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          deck   (first (:decks result))]
                      (t/is (some? (:id deck)) "Should have id")
                      (t/is (= "My Deck" (:name deck)) "Should have correct name")
                      (t/is (= ["player-1" "action-1"] (:card-slugs deck)) "Should have correct card-slugs")
                      (t/is (= true (:is-valid deck)) "Should have correct is-valid")
                      (t/is (= [] (:validation-errors deck)) "Should have empty validation-errors")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-my-decks-decodes-second-deck-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-decks/use-my-decks) [mock-my-decks-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (< (count (:decks result)) 2))
                        (throw (js/Error. "Still loading or not enough decks"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          deck   (second (:decks result))]
                      (t/is (= "Second Deck" (:name deck)) "Should have correct name")
                      (t/is (= [] (:card-slugs deck)) "Should have empty card-slugs")
                      (t/is (= false (:is-valid deck)) "Should be invalid")
                      (t/is (= ["Deck is empty"] (:validation-errors deck)) "Should have validation error")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-my-decks-returns-empty-when-no-decks-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-decks/use-my-decks) [mock-empty-decks-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (empty? (:decks result)) "Should return empty decks")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-my-decks-returns-refetch-function-test
  (let [hook-result (with-mocked-provider #(use-decks/use-my-decks) [mock-my-decks-query])
        result      (get-result hook-result)]
    (t/is (fn? (:refetch result)) "Should return refetch function")))

;; =============================================================================
;; use-deck tests
;; =============================================================================

(t/deftest use-deck-returns-loading-initially-test
  (let [hook-result (with-mocked-provider
                      #(use-decks/use-deck "550e8400-e29b-41d4-a716-446655440000")
                      [mock-deck-query])
        result      (get-result hook-result)]
    (t/is (:loading result) "Should be loading initially")))

(t/deftest use-deck-returns-deck-after-loading-test
  (t/async done
           (let [hook-result (with-mocked-provider
                               #(use-decks/use-deck "550e8400-e29b-41d4-a716-446655440000")
                               [mock-deck-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          deck   (:deck result)]
                      (t/is (not (:loading result)) "Should not be loading")
                      (t/is (some? deck) "Should return a deck")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-deck-decodes-deck-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider
                               #(use-decks/use-deck "550e8400-e29b-41d4-a716-446655440000")
                               [mock-deck-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (nil? (:deck result)))
                        (throw (js/Error. "Still loading or no deck"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          deck   (:deck result)]
                      (t/is (some? (:id deck)) "Should have id")
                      (t/is (= "My Deck" (:name deck)) "Should have correct name")
                      (t/is (= ["player-1" "action-1"] (:card-slugs deck)) "Should have correct card-slugs")
                      (t/is (= false (:is-valid deck)) "Should be invalid")
                      (t/is (= ["Need more cards"] (:validation-errors deck)) "Should have validation errors")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-deck-returns-nested-cards-with-typename-test
  (t/async done
           (let [hook-result (with-mocked-provider
                               #(use-decks/use-deck "550e8400-e29b-41d4-a716-446655440000")
                               [mock-deck-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (nil? (:deck result)))
                        (throw (js/Error. "Still loading or no deck"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          deck   (:deck result)
                          cards  (:cards deck)]
                      (t/is (vector? cards) "Cards should be a vector")
                      (t/is (= 1 (count cards)) "Should have 1 card")
                      (let [card (first cards)]
                        (t/is (map? card) "Card should be a map")
                        (t/is (= "PlayerCard" (:__typename card)) "Card should have PlayerCard typename"))
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-deck-skips-query-when-id-nil-test
  (let [hook-result (with-mocked-provider #(use-decks/use-deck nil) [])
        result      (get-result hook-result)]
    (t/is (nil? (:deck result)) "Should return nil when id is nil")
    (t/is (not (:loading result)) "Should not be loading when skipped")))

(t/deftest use-deck-returns-nil-for-not-found-test
  (t/async done
           (let [hook-result (with-mocked-provider
                               #(use-decks/use-deck "not-found-id")
                               [mock-deck-not-found-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)]
                      (t/is (nil? (:deck result)) "Should return nil for not found")
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

;; =============================================================================
;; Mutation hook tests
;; =============================================================================

(t/deftest use-create-deck-returns-tuple-test
  (let [hook-result (with-mocked-provider #(use-decks/use-create-deck) [mock-create-deck-mutation])
        result      (get-result hook-result)]
    (t/is (vector? result) "Should return a vector")
    (t/is (= 2 (count result)) "Should have 2 elements")
    (t/is (fn? (first result)) "First element should be mutation function")))

(t/deftest use-create-deck-returns-loading-state-test
  (let [hook-result (with-mocked-provider #(use-decks/use-create-deck) [mock-create-deck-mutation])
        [_ state]   (get-result hook-result)]
    (t/is (contains? state :loading) "State should have :loading")
    (t/is (contains? state :error) "State should have :error")
    (t/is (contains? state :data) "State should have :data")))

(t/deftest use-update-deck-returns-tuple-test
  (let [hook-result (with-mocked-provider #(use-decks/use-update-deck) [mock-update-deck-mutation])
        result      (get-result hook-result)]
    (t/is (vector? result) "Should return a vector")
    (t/is (= 2 (count result)) "Should have 2 elements")
    (t/is (fn? (first result)) "First element should be mutation function")))

(t/deftest use-update-deck-returns-loading-state-test
  (let [hook-result (with-mocked-provider #(use-decks/use-update-deck) [mock-update-deck-mutation])
        [_ state]   (get-result hook-result)]
    (t/is (contains? state :loading) "State should have :loading")
    (t/is (contains? state :error) "State should have :error")
    (t/is (contains? state :data) "State should have :data")))

(t/deftest use-delete-deck-returns-tuple-test
  (let [hook-result (with-mocked-provider #(use-decks/use-delete-deck) [mock-delete-deck-mutation])
        result      (get-result hook-result)]
    (t/is (vector? result) "Should return a vector")
    (t/is (= 2 (count result)) "Should have 2 elements")
    (t/is (fn? (first result)) "First element should be mutation function")))

(t/deftest use-delete-deck-returns-loading-state-test
  (let [hook-result (with-mocked-provider #(use-decks/use-delete-deck) [mock-delete-deck-mutation])
        [_ state]   (get-result hook-result)]
    (t/is (contains? state :loading) "State should have :loading")
    (t/is (contains? state :error) "State should have :error")))

(t/deftest use-validate-deck-returns-tuple-test
  (let [hook-result (with-mocked-provider #(use-decks/use-validate-deck) [mock-validate-deck-mutation])
        result      (get-result hook-result)]
    (t/is (vector? result) "Should return a vector")
    (t/is (= 2 (count result)) "Should have 2 elements")
    (t/is (fn? (first result)) "First element should be mutation function")))

(t/deftest use-validate-deck-returns-loading-state-test
  (let [hook-result (with-mocked-provider #(use-decks/use-validate-deck) [mock-validate-deck-mutation])
        [_ state]   (get-result hook-result)]
    (t/is (contains? state :loading) "State should have :loading")
    (t/is (contains? state :error) "State should have :error")
    (t/is (contains? state :data) "State should have :data")))
