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
   :players {:home {:team {:players {"player-1" {:id         "player-1"
                                                  :position   [2 5]
                                                  :exhausted? false}}}}}
   :board   {:occupants {}}})

;; ---------------------------------------------------------------------------
;; Setup Phase Tests

(t/deftest action-bar-shows-start-game-when-all-placed-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                  :my-team            :home
                                  :is-my-turn         true
                                  :phase              "SETUP"
                                  :setup-placed-count 3
                                  :on-start-game      identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Start Game"}))))

(t/deftest action-bar-shows-place-players-when-not-all-placed-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                  :my-team            :home
                                  :is-my-turn         true
                                  :phase              "SETUP"
                                  :setup-placed-count 1
                                  :on-start-game      identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Place Players (1/3)"}))))

(t/deftest action-bar-shows-select-player-message-in-setup-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                  :my-team            :home
                                  :is-my-turn         true
                                  :phase              "SETUP"
                                  :setup-placed-count 2
                                  :on-start-game      identity}))
  (t/is (some? (screen/get-by-text #"Select a player to place"))))

(t/deftest action-bar-disables-button-when-not-all-placed-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                  :my-team            :home
                                  :is-my-turn         true
                                  :phase              "SETUP"
                                  :setup-placed-count 2
                                  :on-start-game      identity}))
  (let [btn (screen/get-by-role "button" {:name "Place Players (2/3)"})]
    (t/is (.-disabled btn))))

(t/deftest action-bar-hides-end-turn-in-setup-phase-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                  :my-team            :home
                                  :is-my-turn         true
                                  :phase              "SETUP"
                                  :setup-placed-count 3
                                  :on-start-game      identity
                                  :on-end-turn        identity}))
  (t/is (nil? (screen/query-by-role "button" {:name "End Turn"}))))

(t/deftest action-bar-start-game-calls-handler-test
  (t/async done
    (let [clicked (atom false)
          _       (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                                  :my-team            :home
                                                  :is-my-turn         true
                                                  :phase              "SETUP"
                                                  :setup-placed-count 3
                                                  :on-start-game      #(reset! clicked true)}))
          usr     (user/setup)
          btn     (screen/get-by-role "button" {:name "Start Game"})]
      (-> (user/click usr btn)
          (.then (fn []
                   (t/is @clicked)
                   (done)))
          (.catch (fn [e]
                    (t/is false (str e))
                    (done)))))))

;; ---------------------------------------------------------------------------
;; Draw Phase Tests

(t/deftest action-bar-shows-draw-card-in-draw-phase-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                  :my-team    :home
                                  :is-my-turn true
                                  :phase      "DRAW"
                                  :on-draw    identity
                                  :on-end-turn identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Draw Card"}))))

(t/deftest action-bar-hides-draw-card-when-not-draw-phase-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                  :my-team    :home
                                  :is-my-turn true
                                  :phase      "ACTIONS"
                                  :on-draw    identity
                                  :on-end-turn identity}))
  (t/is (nil? (screen/query-by-role "button" {:name "Draw Card"}))))

(t/deftest action-bar-hides-draw-card-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                  :my-team    :home
                                  :is-my-turn false
                                  :phase      "DRAW"
                                  :on-draw    identity
                                  :on-end-turn identity}))
  (t/is (nil? (screen/query-by-role "button" {:name "Draw Card"}))))

;; ---------------------------------------------------------------------------
;; Actions Phase Tests

(t/deftest action-bar-shows-end-turn-in-actions-phase-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                  :my-team    :home
                                  :is-my-turn true
                                  :phase      "ACTIONS"
                                  :on-end-turn identity}))
  (t/is (some? (screen/get-by-role "button" {:name "End Turn"}))))

(t/deftest action-bar-disables-end-turn-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                  :my-team    :home
                                  :is-my-turn false
                                  :phase      "ACTIONS"
                                  :on-end-turn identity}))
  (let [btn (screen/get-by-role "button" {:name "End Turn"})]
    (t/is (.-disabled btn))))

(t/deftest action-bar-shows-waiting-message-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                  :my-team    :home
                                  :is-my-turn false
                                  :phase      "ACTIONS"}))
  (t/is (some? (screen/get-by-text "Waiting for opponent..."))))

(t/deftest action-bar-shows-select-player-message-when-none-selected-test
  (uix-tlr/render ($ action-bar {:game-state      sample-game-state
                                  :my-team         :home
                                  :is-my-turn      true
                                  :phase           "ACTIONS"
                                  :selected-player nil}))
  (t/is (some? (screen/get-by-text "Select a player to move"))))

(t/deftest action-bar-end-turn-calls-handler-test
  (t/async done
    (let [clicked (atom false)
          _       (uix-tlr/render ($ action-bar {:game-state  sample-game-state
                                                  :my-team     :home
                                                  :is-my-turn  true
                                                  :phase       "ACTIONS"
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
