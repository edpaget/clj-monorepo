(ns bashketball-game-ui.hooks.use-games-test
  "Tests for game query hooks: use-my-games, use-available-games, use-game."
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["@testing-library/react" :as rtl]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.hooks.use-games :as use-games]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-game-waiting
  #js {:id "game-1"
       :player1Id "user-1"
       :player2Id nil
       :status "WAITING"
       :createdAt "2024-01-15T10:00:00Z"
       :startedAt nil})

(def sample-game-active
  #js {:id "game-2"
       :player1Id "user-1"
       :player2Id "user-2"
       :status "ACTIVE"
       :createdAt "2024-01-15T09:00:00Z"
       :startedAt "2024-01-15T09:05:00Z"})

(def sample-game-with-state
  #js {:id "game-1"
       :player1Id "user-1"
       :player2Id "user-2"
       :status "ACTIVE"
       :gameState #js {:phase "actions" :turnNumber 1}
       :winnerId nil
       :createdAt "2024-01-15T10:00:00Z"
       :startedAt "2024-01-15T10:05:00Z"})

(def sample-page-info
  #js {:totalCount 2
       :hasNextPage false
       :hasPreviousPage false})

(def mock-my-games-query
  #js {:request #js {:query queries/MY_GAMES_QUERY
                     :variables #js {}}
       :result #js {:data #js {:myGames #js {:data #js [sample-game-waiting sample-game-active]
                                             :pageInfo sample-page-info}}}})

(def mock-my-games-active-query
  #js {:request #js {:query queries/MY_GAMES_QUERY
                     :variables #js {:status "ACTIVE"}}
       :result #js {:data #js {:myGames #js {:data #js [sample-game-active]
                                             :pageInfo #js {:totalCount 1
                                                            :hasNextPage false
                                                            :hasPreviousPage false}}}}})

(def mock-available-games-query
  #js {:request #js {:query queries/AVAILABLE_GAMES_QUERY}
       :result #js {:data #js {:availableGames #js [sample-game-waiting]}}})

(def mock-game-query
  #js {:request #js {:query queries/GAME_QUERY
                     :variables #js {:id "game-1"}}
       :result #js {:data #js {:game sample-game-with-state}}})

(defn create-test-cache []
  (InMemoryCache.))

(defn with-mocked-provider [hook-fn mocks]
  (render/render-hook
   hook-fn
   {:wrapper (fn [props]
               ($ MockedProvider {:mocks (clj->js mocks)
                                  :cache (create-test-cache)}
                  (.-children props)))}))

(defn get-result [hook-result]
  (.-current (.-result hook-result)))

(defn wait-for [f]
  (rtl/waitFor f))

;; =============================================================================
;; use-my-games tests
;; =============================================================================

(t/deftest use-my-games-returns-loading-initially-test
  (let [hook-result (with-mocked-provider #(use-games/use-my-games) [mock-my-games-query])
        result      (get-result hook-result)]
    (t/is (:loading result))))

(t/deftest use-my-games-returns-games-after-loading-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-games/use-my-games) [mock-my-games-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          games  (:games result)]
                      (t/is (not (:loading result)))
                      (t/is (= 2 (count games)))
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-my-games-filters-by-status-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-games/use-my-games "ACTIVE") [mock-my-games-active-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          games  (:games result)]
                      (t/is (= 1 (count games)))
                      (t/is (= :game-status/ACTIVE (:status (first games))))
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-my-games-decodes-game-fields-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-games/use-my-games) [mock-my-games-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (or (:loading result) (empty? (:games result)))
                        (throw (js/Error. "Still loading or no games"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          game   (first (:games result))]
                      (t/is (some? (:id game)))
                      (t/is (some? (:player-1-id game)))
                      (t/is (= :game-status/WAITING (:status game)))
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-my-games-returns-page-info-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-games/use-my-games) [mock-my-games-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result    (get-result hook-result)
                          page-info (:page-info result)]
                      (t/is (some? page-info))
                      (t/is (= 2 (:total-count page-info)))
                      (t/is (= false (:has-next-page page-info)))
                      (t/is (= false (:has-previous-page page-info)))
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

;; =============================================================================
;; use-available-games tests
;; =============================================================================

(t/deftest use-available-games-returns-loading-initially-test
  (let [hook-result (with-mocked-provider #(use-games/use-available-games) [mock-available-games-query])
        result      (get-result hook-result)]
    (t/is (:loading result))))

(t/deftest use-available-games-returns-waiting-games-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-games/use-available-games) [mock-available-games-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          games  (:games result)]
                      (t/is (= 1 (count games)))
                      (t/is (= :game-status/WAITING (:status (first games))))
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

;; =============================================================================
;; use-game tests
;; =============================================================================

(t/deftest use-game-returns-loading-initially-test
  (let [hook-result (with-mocked-provider #(use-games/use-game "game-1") [mock-game-query])
        result      (get-result hook-result)]
    (t/is (:loading result))))

(t/deftest use-game-returns-single-game-test
  (t/async done
           (let [hook-result (with-mocked-provider #(use-games/use-game "game-1") [mock-game-query])]
             (-> (wait-for
                  (fn []
                    (let [result (get-result hook-result)]
                      (when (:loading result)
                        (throw (js/Error. "Still loading"))))))
                 (.then
                  (fn []
                    (let [result (get-result hook-result)
                          game   (:game result)]
                      (t/is (some? game))
                      (t/is (= "game-1" (:id game)))
                      (t/is (= :game-status/ACTIVE (:status game)))
                      (done))))
                 (.catch
                  (fn [e]
                    (t/is false (str "Test failed: " e))
                    (done)))))))

(t/deftest use-game-skips-when-id-nil-test
  (let [hook-result (with-mocked-provider #(use-games/use-game nil) [])
        result      (get-result hook-result)]
    (t/is (nil? (:game result)))
    (t/is (not (:loading result)))))
