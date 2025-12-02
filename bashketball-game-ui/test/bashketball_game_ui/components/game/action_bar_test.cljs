(ns bashketball-game-ui.components.game.action-bar-test
  (:require
   [bashketball-game-ui.components.game.action-bar :refer [action-bar]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-game-state
  {:ball    {:status :possessed :holder-id "player-1"}
   :players {:home {:team {:players {"player-1" {:id        "player-1"
                                                  :position  [2 5]
                                                  :exhausted? false}}}}}
   :board   {:occupants {}}})

(t/deftest action-bar-shows-end-turn-when-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                  :my-team    :home
                                  :is-my-turn true
                                  :on-end-turn identity}))
  (t/is (some? (screen/get-by-role "button" {:name "End Turn"}))))

(t/deftest action-bar-disables-end-turn-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                  :my-team    :home
                                  :is-my-turn false
                                  :on-end-turn identity}))
  (let [btn (screen/get-by-role "button" {:name "End Turn"})]
    (t/is (.-disabled btn))))

(t/deftest action-bar-shows-waiting-message-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                  :my-team    :home
                                  :is-my-turn false}))
  (t/is (some? (screen/get-by-text "Waiting for opponent..."))))

(t/deftest action-bar-shows-select-player-message-when-none-selected-test
  (uix-tlr/render ($ action-bar {:game-state      sample-game-state
                                  :my-team         :home
                                  :is-my-turn      true
                                  :selected-player nil}))
  (t/is (some? (screen/get-by-text "Select a player to move"))))

(t/deftest action-bar-end-turn-calls-handler-test
  (t/async done
           (let [clicked (atom false)
                 _       (uix-tlr/render ($ action-bar {:game-state  sample-game-state
                                                        :my-team     :home
                                                        :is-my-turn  true
                                                        :on-end-turn #(reset! clicked true)}))
                 usr     (user/setup)
                 btn     (screen/get-by-role "button" {:name "End Turn"})]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is @clicked)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
