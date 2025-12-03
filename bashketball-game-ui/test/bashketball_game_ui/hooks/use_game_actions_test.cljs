(ns bashketball-game-ui.hooks.use-game-actions-test
  "Tests for the use-game-actions hook."
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing/react" :refer [MockedProvider]]
   [bashketball-game-ui.graphql.mutations :as mutations]
   [bashketball-game-ui.hooks.use-game-actions :as use-game-actions]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def game-id "test-game-123")

(def mock-submit-action-success
  #js {:request #js {:query mutations/SUBMIT_ACTION_MUTATION
                     :variables #js {:gameId game-id
                                     :action #js {:type "bashketball/advance-turn"}}}
       :result #js {:data #js {:submitAction #js {:success true
                                                  :gameId game-id
                                                  :error nil}}}})

(def mock-submit-action-move
  #js {:request #js {:query mutations/SUBMIT_ACTION_MUTATION
                     :variables #js {:gameId game-id
                                     :action #js {:type "bashketball/move-player"
                                                  :playerId "player-1"
                                                  :position #js [2 3]}}}
       :result #js {:data #js {:submitAction #js {:success true
                                                  :gameId game-id
                                                  :error nil}}}})

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

;; =============================================================================
;; Hook interface tests
;; =============================================================================

(t/deftest use-game-actions-returns-expected-keys-test
  (let [hook-result (with-mocked-provider
                      #(use-game-actions/use-game-actions game-id)
                      [mock-submit-action-success])
        result      (get-result hook-result)]
    (t/is (fn? (:submit result)))
    (t/is (fn? (:move-player result)))
    (t/is (fn? (:pass-ball result)))
    (t/is (fn? (:shoot-ball result)))
    (t/is (fn? (:draw-cards result)))
    (t/is (fn? (:discard-cards result)))
    (t/is (fn? (:end-turn result)))
    (t/is (fn? (:set-phase result)))))

(t/deftest use-game-actions-returns-loading-state-test
  (let [hook-result (with-mocked-provider
                      #(use-game-actions/use-game-actions game-id)
                      [mock-submit-action-success])
        result      (get-result hook-result)]
    (t/is (contains? result :loading))
    (t/is (contains? result :error))))

(t/deftest use-game-actions-loading-initially-false-test
  (let [hook-result (with-mocked-provider
                      #(use-game-actions/use-game-actions game-id)
                      [mock-submit-action-success])
        result      (get-result hook-result)]
    (t/is (not (:loading result)))))

