(ns bashketball-game-ui.components.game.game-list-test
  (:require
   [bashketball-game-ui.components.game.game-list :refer [game-list]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-user-id "550e8400-e29b-41d4-a716-446655440000")

(def sample-games
  [{:id "game-1"
    :player-1-id sample-user-id
    :player-2-id nil
    :status :game-status/WAITING
    :created-at "2024-01-15T10:00:00Z"}
   {:id "game-2"
    :player-1-id "other-user"
    :player-2-id sample-user-id
    :status :game-status/ACTIVE
    :created-at "2024-01-15T09:00:00Z"}])

(t/deftest game-list-renders-loading-skeleton-test
  (uix-tlr/render ($ game-list {:loading true}))
  ;; Loading state should not show "No games found" since we're loading
  (t/is (nil? (screen/query-by-text "No games found."))))

(t/deftest game-list-renders-empty-message-test
  (uix-tlr/render ($ game-list {:games []}))
  (t/is (some? (screen/get-by-text "No games found."))))

(t/deftest game-list-renders-custom-empty-message-test
  (uix-tlr/render ($ game-list {:games []
                                :empty-message "Create a game to get started!"}))
  (t/is (some? (screen/get-by-text "Create a game to get started!"))))

(t/deftest game-list-renders-all-games-test
  (uix-tlr/render ($ game-list {:games sample-games
                                :current-user-id sample-user-id}))
  ;; Should show status badges for both games
  (t/is (some? (screen/get-by-text "Waiting")))
  (t/is (some? (screen/get-by-text "In Progress"))))

(t/deftest game-list-renders-correct-number-of-status-badges-test
  (uix-tlr/render ($ game-list {:games sample-games
                                :current-user-id sample-user-id}))
  ;; Should have status badges for both games
  (t/is (= 2 (+ (count (screen/query-all-by-text "Waiting"))
                (count (screen/query-all-by-text "In Progress"))))))
