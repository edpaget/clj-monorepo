(ns bashketball-game-ui.hooks.use-game-actions-test
  "Tests for the use-game-actions hook."
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing" :refer [MockedProvider]]
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

;; =============================================================================
;; Action construction tests (unit tests for clj->action-input)
;; =============================================================================

(t/deftest clj->action-input-converts-move-player-test
  (let [action {:type "bashketball/move-player"
                :player-id "player-1"
                :position [2 3]}
        result (#'use-game-actions/clj->action-input action)]
    (t/is (= "bashketball/move-player" (:type result)))
    (t/is (= "player-1" (:playerId result)))
    (t/is (= [2 3] (:position result)))))

(t/deftest clj->action-input-converts-draw-cards-test
  (let [action {:type "bashketball/draw-cards"
                :player "home"
                :count 3}
        result (#'use-game-actions/clj->action-input action)]
    (t/is (= "bashketball/draw-cards" (:type result)))
    (t/is (= "home" (:player result)))
    (t/is (= 3 (:count result)))))

(t/deftest clj->action-input-converts-ball-action-test
  (let [action {:type "bashketball/set-ball-in-air"
                :origin [1 2]
                :target [3 4]
                :action-type "shot"}
        result (#'use-game-actions/clj->action-input action)]
    (t/is (= "bashketball/set-ball-in-air" (:type result)))
    (t/is (= [1 2] (:origin result)))
    (t/is (= [3 4] (:target result)))
    (t/is (= "shot" (:actionType result)))))

(t/deftest clj->action-input-converts-discard-cards-test
  (let [action {:type "bashketball/discard-cards"
                :player "away"
                :card-slugs ["card-1" "card-2"]}
        result (#'use-game-actions/clj->action-input action)]
    (t/is (= "bashketball/discard-cards" (:type result)))
    (t/is (= "away" (:player result)))
    (t/is (= ["card-1" "card-2"] (:cardSlugs result)))))
