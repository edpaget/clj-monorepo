(ns bashketball-game-ui.components.game.player-roster-item-test
  (:require
   [bashketball-game-ui.components.game.player-roster-item :refer [player-roster-item]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-player
  {:id        "home-orc-center-0"
   :name      "Orc Center"
   :card-slug "orc-center"
   :stats     {:shooting 8 :defense 6 :speed 4}
   :exhausted? false})

(t/deftest player-roster-item-renders-token-label-test
  (uix-tlr/render ($ player-roster-item {:player     sample-player
                                          :player-num 1
                                          :team       :HOME
                                          :on-field?  true}))
  (t/is (some? (screen/get-by-text "O1"))))

(t/deftest player-roster-item-renders-player-name-test
  (uix-tlr/render ($ player-roster-item {:player     sample-player
                                          :player-num 1
                                          :team       :HOME
                                          :on-field?  true}))
  (t/is (some? (screen/get-by-text "Orc Center"))))

(t/deftest player-roster-item-renders-stats-test
  (uix-tlr/render ($ player-roster-item {:player     sample-player
                                          :player-num 1
                                          :team       :HOME
                                          :on-field?  true}))
  (t/is (some? (screen/get-by-text "S:8")))
  (t/is (some? (screen/get-by-text "D:6")))
  (t/is (some? (screen/get-by-text "P:4"))))

(t/deftest player-roster-item-info-button-calls-handler-test
  (t/async done
           (let [clicked (atom nil)
                 _       (uix-tlr/render ($ player-roster-item
                                            {:player        sample-player
                                             :player-num    1
                                             :team          :HOME
                                             :on-field?     true
                                             :on-info-click #(reset! clicked %)}))
                 usr     (user/setup)
                 btn     (screen/get-by-text "i")]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is (= "orc-center" @clicked))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest player-roster-item-different-team-token-test
  (uix-tlr/render ($ player-roster-item {:player     (assoc sample-player :name "Elf Guard")
                                          :player-num 2
                                          :team       :AWAY
                                          :on-field?  false}))
  (t/is (some? (screen/get-by-text "E2"))))
