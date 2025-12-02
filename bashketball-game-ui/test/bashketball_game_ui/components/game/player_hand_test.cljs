(ns bashketball-game-ui.components.game.player-hand-test
  (:require
   [bashketball-game-ui.components.game.player-hand :refer [player-hand]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-hand ["fast-break" "slam-dunk" "zone-defense"])

(t/deftest player-hand-renders-cards-test
  (uix-tlr/render ($ player-hand {:hand sample-hand}))
  (t/is (some? (screen/get-by-text "fast break")))
  (t/is (some? (screen/get-by-text "slam dunk")))
  (t/is (some? (screen/get-by-text "zone defense"))))

(t/deftest player-hand-shows-empty-message-when-no-cards-test
  (uix-tlr/render ($ player-hand {:hand []}))
  (t/is (some? (screen/get-by-text "No cards in hand"))))

(t/deftest player-hand-card-click-calls-handler-test
  (t/async done
           (let [clicked (atom nil)
                 _       (uix-tlr/render ($ player-hand {:hand          sample-hand
                                                         :on-card-click #(reset! clicked %)}))
                 usr     (user/setup)
                 btn     (screen/get-by-text "fast break")]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is (= "fast-break" @clicked))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest player-hand-disabled-prevents-clicks-test
  (uix-tlr/render ($ player-hand {:hand     sample-hand
                                   :disabled true}))
  (let [btn (screen/get-by-text "fast break")]
    (t/is (.-disabled btn))))
