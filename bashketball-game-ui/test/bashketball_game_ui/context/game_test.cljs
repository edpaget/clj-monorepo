(ns bashketball-game-ui.context.game-test
  "Tests for the game context provider."
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing/react" :refer [MockedProvider]]
   ["@testing-library/react" :as rtl]
   [bashketball-game-ui.context.game :as game-context]
   [bashketball-game-ui.graphql.queries :as queries]
   [bashketball-game-ui.graphql.subscriptions :as subscriptions]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$ defui]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def game-id "game-123")
(def user-id "user-456")

(def sample-game
  #js {:__typename "Game"
       :id game-id
       :player1Id user-id
       :player2Id "user-789"
       :status "ACTIVE"
       :gameState #js {:__typename "GameState"
                       :gameId game-id
                       :phase "ACTIONS"
                       :activePlayer "HOME"
                       :turnNumber 1
                       :score #js {:__typename "Score" :home 0 :away 0}
                       :board #js {:__typename "Board" :width 5 :height 14 :tiles #js {} :occupants #js {}}
                       :ball #js {:__typename "BallPossessed" :holderId "player-1"}
                       :players #js {:__typename "Players"
                                     :home #js {:__typename "PlayerState"
                                                :id "HOME"
                                                :actionsRemaining 2
                                                :deck #js {:__typename "Deck" :drawPile #js [] :hand #js [] :discard #js [] :removed #js []}
                                                :team #js {:__typename "Team" :starters #js [] :bench #js [] :players #js {}}
                                                :assets #js []}
                                     :away #js {:__typename "PlayerState"
                                                :id "AWAY"
                                                :actionsRemaining 0
                                                :deck #js {:__typename "Deck" :drawPile #js [] :hand #js [] :discard #js [] :removed #js []}
                                                :team #js {:__typename "Team" :starters #js [] :bench #js [] :players #js {}}
                                                :assets #js []}}
                       :stack #js []
                       :events #js []
                       :metadata #js {}}
       :winnerId nil
       :createdAt "2024-01-15T10:00:00Z"
       :startedAt "2024-01-15T10:05:00Z"})

(defn make-game-query-mock
  "Creates a fresh mock for the game query."
  []
  #js {:request #js {:query queries/GAME_QUERY
                     :variables #js {:id game-id}}
       :result #js {:data #js {:game sample-game}}
       :maxUsageCount js/Infinity})

(defn make-game-subscription-mock
  "Creates a fresh mock for the game subscription."
  []
  #js {:request #js {:query subscriptions/GAME_UPDATED_SUBSCRIPTION
                     :variables #js {:gameId game-id}}
       :result #js {:data #js {:gameUpdated #js {:type "state-changed"
                                                 :game sample-game
                                                 :event #js {:type "test"
                                                             :playerId user-id
                                                             :timestamp "2024-01-15T10:06:00Z"}}}}})

(defn create-test-cache []
  (InMemoryCache. #js {:addTypename false}))

;; Test component that uses the context
(defui test-consumer []
  (let [ctx (game-context/use-game-context)]
    ($ :div {:data-testid "context-consumer"}
       ($ :span {:data-testid "loading"} (str (:loading ctx)))
       ($ :span {:data-testid "my-team"} (str (:my-team ctx)))
       ($ :span {:data-testid "is-my-turn"} (str (:is-my-turn ctx)))
       (when (:game ctx)
         ($ :span {:data-testid "game-id"} (:id (:game ctx))))
       (when (:actions ctx)
         ($ :span {:data-testid "has-actions"} "true")))))

(defn render-with-context [game-id user-id mocks]
  (render/render
   ($ MockedProvider {:mocks (clj->js mocks)
                      :cache (create-test-cache)}
      ($ game-context/game-provider {:game-id game-id :user-id user-id}
         ($ test-consumer)))))

(defn wait-for [f]
  (rtl/waitFor f))

;; =============================================================================
;; Context provider tests
;; =============================================================================

(t/deftest game-provider-renders-children-test
  (render-with-context game-id user-id [(make-game-query-mock) (make-game-subscription-mock)])
  (t/is (some? (rtl/screen.getByTestId "context-consumer"))))

(t/deftest game-provider-shows-loading-initially-test
  (render-with-context game-id user-id [(make-game-query-mock) (make-game-subscription-mock)])
  (let [loading-el (rtl/screen.getByTestId "loading")]
    (t/is (= "true" (.-textContent loading-el)))))

(t/deftest game-provider-loads-game-test
  (t/async done
           (render-with-context game-id user-id [(make-game-query-mock) (make-game-subscription-mock)])
           (-> (wait-for
                (fn []
                  (let [el (rtl/screen.queryByTestId "game-id")]
                    (when (or (nil? el) (not= game-id (.-textContent el)))
                      (throw (js/Error. "Game not loaded"))))))
               (.then
                (fn []
                  (let [game-el (rtl/screen.getByTestId "game-id")]
                    (t/is (= game-id (.-textContent game-el)))
                    (done))))
               (.catch
                (fn [e]
                  (t/is false (str "Test failed: " e))
                  (done))))))

(t/deftest game-provider-determines-my-team-test
  (t/async done
           (render-with-context game-id user-id [(make-game-query-mock) (make-game-subscription-mock)])
           (-> (wait-for
                (fn []
                  (let [el (rtl/screen.queryByTestId "my-team")]
                    ;; Team enum uses namespaced :team/HOME/:team/AWAY per schema
                    (when (or (nil? el) (not= ":team/HOME" (.-textContent el)))
                      (throw (js/Error. "Team not determined"))))))
               (.then
                (fn []
                  (let [team-el (rtl/screen.getByTestId "my-team")]
                    (t/is (= ":team/HOME" (.-textContent team-el)))
                    (done))))
               (.catch
                (fn [e]
                  (t/is false (str "Test failed: " e))
                  (done))))))

;; This test passes because has-actions is always rendered (actions hook doesn't depend on game data)
(t/deftest game-provider-provides-actions-test
  (render-with-context game-id user-id [(make-game-query-mock) (make-game-subscription-mock)])
  (let [actions-el (rtl/screen.getByTestId "has-actions")]
    (t/is (= "true" (.-textContent actions-el)))))

;; =============================================================================
;; Helper function tests
;; =============================================================================

(t/deftest determine-my-team-returns-home-for-player1-test
  (let [game {:player-1-id "user-1" :player-2-id "user-2"}]
    (t/is (= :team/HOME (#'game-context/determine-my-team game "user-1")))))

(t/deftest determine-my-team-returns-away-for-player2-test
  (let [game {:player-1-id "user-1" :player-2-id "user-2"}]
    (t/is (= :team/AWAY (#'game-context/determine-my-team game "user-2")))))

(t/deftest determine-my-team-returns-nil-for-spectator-test
  (let [game {:player-1-id "user-1" :player-2-id "user-2"}]
    (t/is (nil? (#'game-context/determine-my-team game "user-3")))))

(t/deftest is-my-turn-returns-true-when-active-test
  (let [game-state {:active-player :team/HOME}]
    (t/is (#'game-context/is-my-turn? game-state :team/HOME))))

(t/deftest is-my-turn-returns-false-when-not-active-test
  (let [game-state {:active-player :team/AWAY}]
    (t/is (not (#'game-context/is-my-turn? game-state :team/HOME)))))

(t/deftest is-my-turn-handles-keyword-active-player-test
  (let [game-state {:active-player :team/HOME}]
    (t/is (#'game-context/is-my-turn? game-state :team/HOME))))
