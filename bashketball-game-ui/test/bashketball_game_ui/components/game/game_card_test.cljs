(ns bashketball-game-ui.components.game.game-card-test
  (:require
   [bashketball-game-ui.components.game.game-card :refer [game-card]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-user-id "550e8400-e29b-41d4-a716-446655440000")

(def sample-game-waiting
  {:id "game-1"
   :player-1-id sample-user-id
   :player-2-id nil
   :status :game-status/waiting
   :created-at "2024-01-15T10:00:00Z"
   :started-at nil})

(def sample-game-active
  {:id "game-2"
   :player-1-id sample-user-id
   :player-2-id "other-user-id"
   :status :game-status/active
   :created-at "2024-01-15T09:00:00Z"
   :started-at "2024-01-15T09:05:00Z"})

(def sample-game-completed
  {:id "game-3"
   :player-1-id sample-user-id
   :player-2-id "other-user-id"
   :status :game-status/completed
   :created-at "2024-01-14T10:00:00Z"
   :started-at "2024-01-14T10:05:00Z"})

(t/deftest game-card-renders-waiting-status-badge-test
  (uix-tlr/render ($ game-card {:game sample-game-waiting
                                :current-user-id sample-user-id}))
  (t/is (some? (screen/get-by-text "Waiting"))))

(t/deftest game-card-renders-active-status-badge-test
  (uix-tlr/render ($ game-card {:game sample-game-active
                                :current-user-id sample-user-id}))
  (t/is (some? (screen/get-by-text "In Progress"))))

(t/deftest game-card-renders-completed-status-badge-test
  (uix-tlr/render ($ game-card {:game sample-game-completed
                                :current-user-id sample-user-id}))
  (t/is (some? (screen/get-by-text "Completed"))))

(t/deftest game-card-shows-you-when-current-user-is-owner-test
  (uix-tlr/render ($ game-card {:game sample-game-waiting
                                :current-user-id sample-user-id}))
  (t/is (some? (screen/get-by-text "You"))))

(t/deftest game-card-shows-player-1-when-not-owner-test
  (uix-tlr/render ($ game-card {:game sample-game-waiting
                                :current-user-id "different-user"}))
  (t/is (some? (screen/get-by-text "Player 1"))))

(t/deftest game-card-shows-vs-player-2-when-has-opponent-test
  (uix-tlr/render ($ game-card {:game sample-game-active
                                :current-user-id sample-user-id}))
  (t/is (some? (screen/get-by-text "vs")))
  (t/is (some? (screen/get-by-text "Player 2"))))

(t/deftest game-card-shows-cancel-for-own-waiting-game-test
  (uix-tlr/render ($ game-card {:game sample-game-waiting
                                :current-user-id sample-user-id
                                :on-cancel identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Cancel"}))))

(t/deftest game-card-shows-join-for-other-waiting-game-test
  (uix-tlr/render ($ game-card {:game sample-game-waiting
                                :current-user-id "different-user"
                                :show-join? true
                                :on-join identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Join Game"}))))

(t/deftest game-card-shows-resume-for-active-game-test
  (uix-tlr/render ($ game-card {:game sample-game-active
                                :current-user-id sample-user-id
                                :on-resume identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Resume"}))))

(t/deftest game-card-shows-view-for-completed-game-test
  (uix-tlr/render ($ game-card {:game sample-game-completed
                                :current-user-id sample-user-id
                                :on-view identity}))
  (t/is (some? (screen/get-by-role "button" {:name "View"}))))

(t/deftest game-card-join-button-calls-handler-test
  (t/async done
           (let [clicked (atom nil)
                 game    (assoc sample-game-waiting :player-1-id "other-user")
                 _       (uix-tlr/render ($ game-card {:game game
                                                       :current-user-id sample-user-id
                                                       :show-join? true
                                                       :on-join #(reset! clicked %)}))
                 usr     (user/setup)
                 btn     (screen/get-by-role "button" {:name "Join Game"})]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is (= game @clicked))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
