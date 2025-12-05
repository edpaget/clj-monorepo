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
  {:ball    {:__typename "BallPossessed" :holder-id "player-1"}
   :players {:home {:team {:players {"player-1" {:id         "player-1"
                                                 :position   [2 5]
                                                 :exhausted? false}}}}}
   :board   {:occupants {}}})

;; ---------------------------------------------------------------------------
;; Setup Phase Tests

(t/deftest action-bar-shows-start-game-when-both-teams-ready-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                 :my-team            :home
                                 :is-my-turn         true
                                 :phase              "SETUP"
                                 :setup-placed-count 3
                                 :my-setup-complete  true
                                 :both-teams-ready   true
                                 :on-start-game      identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Start Game"}))))

(t/deftest action-bar-shows-done-when-my-setup-complete-but-not-both-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                 :my-team            :home
                                 :is-my-turn         true
                                 :phase              "SETUP"
                                 :setup-placed-count 3
                                 :my-setup-complete  true
                                 :both-teams-ready   false
                                 :on-setup-done      identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Done"}))))

(t/deftest action-bar-shows-place-players-when-not-all-placed-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                 :my-team            :home
                                 :is-my-turn         true
                                 :phase              "SETUP"
                                 :setup-placed-count 1
                                 :my-setup-complete  false
                                 :both-teams-ready   false}))
  (t/is (some? (screen/get-by-role "button" {:name "Place Players (1/3)"}))))

(t/deftest action-bar-shows-select-player-message-in-setup-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                 :my-team            :home
                                 :is-my-turn         true
                                 :phase              "SETUP"
                                 :setup-placed-count 2
                                 :my-setup-complete  false
                                 :both-teams-ready   false}))
  (t/is (some? (screen/get-by-text #"Select a player to place"))))

(t/deftest action-bar-disables-button-when-not-all-placed-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                 :my-team            :home
                                 :is-my-turn         true
                                 :phase              "SETUP"
                                 :setup-placed-count 2
                                 :my-setup-complete  false
                                 :both-teams-ready   false}))
  (let [btn (screen/get-by-role "button" {:name "Place Players (2/3)"})]
    (t/is (.-disabled btn))))

(t/deftest action-bar-shows-waiting-in-setup-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                 :my-team            :home
                                 :is-my-turn         false
                                 :phase              "SETUP"
                                 :setup-placed-count 0
                                 :my-setup-complete  false
                                 :both-teams-ready   false}))
  (t/is (some? (screen/get-by-text "Waiting for opponent to place players..."))))

(t/deftest action-bar-hides-end-turn-in-setup-phase-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                 :my-team            :home
                                 :is-my-turn         true
                                 :phase              "SETUP"
                                 :setup-placed-count 3
                                 :my-setup-complete  true
                                 :both-teams-ready   true
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
                                                        :my-setup-complete  true
                                                        :both-teams-ready   true
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

(t/deftest action-bar-setup-done-calls-handler-test
  (t/async done
           (let [clicked (atom false)
                 _       (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                                        :my-team            :home
                                                        :is-my-turn         true
                                                        :phase              "SETUP"
                                                        :setup-placed-count 3
                                                        :my-setup-complete  true
                                                        :both-teams-ready   false
                                                        :on-setup-done      #(reset! clicked true)}))
                 usr     (user/setup)
                 btn     (screen/get-by-role "button" {:name "Done"})]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is @clicked)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

;; ---------------------------------------------------------------------------
;; Draw Phase Tests

(t/deftest action-bar-shows-draw-card-in-upkeep-phase-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                 :my-team    :home
                                 :is-my-turn true
                                 :phase      "UPKEEP"
                                 :on-draw    identity
                                 :on-end-turn identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Draw Card"}))))

(t/deftest action-bar-shows-draw-card-in-actions-phase-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                 :my-team    :home
                                 :is-my-turn true
                                 :phase      "ACTIONS"
                                 :on-draw    identity
                                 :on-end-turn identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Draw Card"}))))

(t/deftest action-bar-hides-draw-card-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state sample-game-state
                                 :my-team    :home
                                 :is-my-turn false
                                 :phase      "UPKEEP"
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

;; ---------------------------------------------------------------------------
;; Next Phase Tests

(t/deftest action-bar-shows-next-phase-in-upkeep-test
  (uix-tlr/render ($ action-bar {:game-state    sample-game-state
                                 :my-team       :home
                                 :is-my-turn    true
                                 :phase         "UPKEEP"
                                 :on-next-phase identity
                                 :on-end-turn   identity}))
  (t/is (some? (screen/get-by-role "button" {:name #"Next Phase"}))))

(t/deftest action-bar-shows-correct-next-phase-label-test
  (uix-tlr/render ($ action-bar {:game-state    sample-game-state
                                 :my-team       :home
                                 :is-my-turn    true
                                 :phase         "UPKEEP"
                                 :on-next-phase identity
                                 :on-end-turn   identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Next Phase (Actions)"}))))

(t/deftest action-bar-hides-next-phase-in-setup-test
  (uix-tlr/render ($ action-bar {:game-state         sample-game-state
                                 :my-team            :home
                                 :is-my-turn         true
                                 :phase              "SETUP"
                                 :setup-placed-count 3
                                 :my-setup-complete  true
                                 :both-teams-ready   true
                                 :on-next-phase      identity
                                 :on-start-game      identity}))
  (t/is (nil? (screen/query-by-role "button" {:name #"Next Phase"}))))

(t/deftest action-bar-hides-next-phase-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state    sample-game-state
                                 :my-team       :home
                                 :is-my-turn    false
                                 :phase         "ACTIONS"
                                 :on-next-phase identity
                                 :on-end-turn   identity}))
  (t/is (nil? (screen/query-by-role "button" {:name #"Next Phase"}))))

(t/deftest action-bar-next-phase-calls-handler-test
  (t/async done
           (let [clicked (atom false)
                 _       (uix-tlr/render ($ action-bar {:game-state    sample-game-state
                                                        :my-team       :home
                                                        :is-my-turn    true
                                                        :phase         "UPKEEP"
                                                        :on-next-phase #(reset! clicked true)
                                                        :on-end-turn   identity}))
                 usr     (user/setup)
                 btn     (screen/get-by-role "button" {:name "Next Phase (Actions)"})]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is @clicked)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

;; ---------------------------------------------------------------------------
;; Shuffle Button Tests

(t/deftest action-bar-shows-shuffle-when-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state        sample-game-state
                                 :my-team           :home
                                 :is-my-turn        true
                                 :phase             "ACTIONS"
                                 :on-shuffle        identity
                                 :on-return-discard identity
                                 :on-end-turn       identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Shuffle"}))))

(t/deftest action-bar-hides-shuffle-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state        sample-game-state
                                 :my-team           :home
                                 :is-my-turn        false
                                 :phase             "ACTIONS"
                                 :on-shuffle        identity
                                 :on-return-discard identity
                                 :on-end-turn       identity}))
  (t/is (nil? (screen/query-by-role "button" {:name "Shuffle"}))))

;; ---------------------------------------------------------------------------
;; Return Discard Button Tests

(t/deftest action-bar-shows-return-discard-when-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state        sample-game-state
                                 :my-team           :home
                                 :is-my-turn        true
                                 :phase             "ACTIONS"
                                 :on-shuffle        identity
                                 :on-return-discard identity
                                 :on-end-turn       identity}))
  (t/is (some? (screen/get-by-role "button" {:name "Return Discard"}))))

(t/deftest action-bar-hides-return-discard-when-not-my-turn-test
  (uix-tlr/render ($ action-bar {:game-state        sample-game-state
                                 :my-team           :home
                                 :is-my-turn        false
                                 :phase             "ACTIONS"
                                 :on-shuffle        identity
                                 :on-return-discard identity
                                 :on-end-turn       identity}))
  (t/is (nil? (screen/query-by-role "button" {:name "Return Discard"}))))
