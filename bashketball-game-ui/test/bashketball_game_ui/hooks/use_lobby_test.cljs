(ns bashketball-game-ui.hooks.use-lobby-test
  "Tests for lobby mutation hooks: use-create-game, use-join-game, etc."
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing" :refer [MockedProvider]]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-game-ui.hooks.use-lobby :as use-lobby]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def mock-create-game-mutation
  #js {:request #js {:query mutations/CREATE_GAME_MUTATION
                     :variables #js {:deckId "deck-1"}}
       :result #js {:data #js {:createGame #js {:id "new-game-id"
                                                 :player1Id "user-1"
                                                 :status "WAITING"
                                                 :createdAt "2024-01-15T10:00:00Z"}}}})

(def mock-join-game-mutation
  #js {:request #js {:query mutations/JOIN_GAME_MUTATION
                     :variables #js {:gameId "game-1" :deckId "deck-2"}}
       :result #js {:data #js {:joinGame #js {:id "game-1"
                                               :player1Id "user-1"
                                               :player2Id "user-2"
                                               :status "ACTIVE"
                                               :startedAt "2024-01-15T10:05:00Z"}}}})

(def mock-leave-game-mutation
  #js {:request #js {:query mutations/LEAVE_GAME_MUTATION
                     :variables #js {:gameId "game-1"}}
       :result #js {:data #js {:leaveGame true}}})

(def mock-forfeit-game-mutation
  #js {:request #js {:query mutations/FORFEIT_GAME_MUTATION
                     :variables #js {:gameId "game-1"}}
       :result #js {:data #js {:forfeitGame #js {:id "game-1"
                                                  :status "COMPLETED"
                                                  :winnerId "user-2"}}}})

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

;; =============================================================================
;; use-create-game tests
;; =============================================================================

(t/deftest use-create-game-returns-tuple-test
  (let [hook-result (with-mocked-provider #(use-lobby/use-create-game) [mock-create-game-mutation])
        result      (get-result hook-result)]
    (t/is (vector? result))
    (t/is (= 2 (count result)))
    (t/is (fn? (first result)))))

(t/deftest use-create-game-returns-loading-state-test
  (let [hook-result (with-mocked-provider #(use-lobby/use-create-game) [mock-create-game-mutation])
        [_ state]   (get-result hook-result)]
    (t/is (contains? state :loading))
    (t/is (contains? state :error))
    (t/is (contains? state :data))))

;; =============================================================================
;; use-join-game tests
;; =============================================================================

(t/deftest use-join-game-returns-tuple-test
  (let [hook-result (with-mocked-provider #(use-lobby/use-join-game) [mock-join-game-mutation])
        result      (get-result hook-result)]
    (t/is (vector? result))
    (t/is (= 2 (count result)))
    (t/is (fn? (first result)))))

(t/deftest use-join-game-returns-loading-state-test
  (let [hook-result (with-mocked-provider #(use-lobby/use-join-game) [mock-join-game-mutation])
        [_ state]   (get-result hook-result)]
    (t/is (contains? state :loading))
    (t/is (contains? state :error))
    (t/is (contains? state :data))))

;; =============================================================================
;; use-leave-game tests
;; =============================================================================

(t/deftest use-leave-game-returns-tuple-test
  (let [hook-result (with-mocked-provider #(use-lobby/use-leave-game) [mock-leave-game-mutation])
        result      (get-result hook-result)]
    (t/is (vector? result))
    (t/is (= 2 (count result)))
    (t/is (fn? (first result)))))

(t/deftest use-leave-game-returns-loading-state-test
  (let [hook-result (with-mocked-provider #(use-lobby/use-leave-game) [mock-leave-game-mutation])
        [_ state]   (get-result hook-result)]
    (t/is (contains? state :loading))
    (t/is (contains? state :error))))

;; =============================================================================
;; use-forfeit-game tests
;; =============================================================================

(t/deftest use-forfeit-game-returns-tuple-test
  (let [hook-result (with-mocked-provider #(use-lobby/use-forfeit-game) [mock-forfeit-game-mutation])
        result      (get-result hook-result)]
    (t/is (vector? result))
    (t/is (= 2 (count result)))
    (t/is (fn? (first result)))))

(t/deftest use-forfeit-game-returns-loading-state-test
  (let [hook-result (with-mocked-provider #(use-lobby/use-forfeit-game) [mock-forfeit-game-mutation])
        [_ state]   (get-result hook-result)]
    (t/is (contains? state :loading))
    (t/is (contains? state :error))
    (t/is (contains? state :data))))
