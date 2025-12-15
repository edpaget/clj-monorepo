(ns bashketball-game-ui.components.game.hex-grid-test
  (:require
   [bashketball-game-ui.components.game.hex-grid :refer [hex-grid]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-home-players
  {"player-1" {:id        "player-1"
               :name      "Guard"
               :position  [2 2]
               :exhausted false}
   "player-2" {:id        "player-2"
               :name      "Forward"
               :position  [1 3]
               :exhausted true}})

(def sample-away-players
  {"player-3" {:id        "player-3"
               :name      "Center"
               :position  [2 11]
               :exhausted false}})

(def sample-ball-possessed
  {:__typename "BallPossessed"
   :holder-id  "player-1"})

(def sample-ball-loose
  {:__typename "BallLoose"
   :position   [3 5]})

(t/deftest hex-grid-renders-svg-test
  (let [{:keys [container]} (uix-tlr/render ($ hex-grid {:board        nil
                                                         :ball         nil
                                                         :home-players {}
                                                         :away-players {}}))]
    (t/is (some? (.querySelector container "svg")))))

(t/deftest hex-grid-renders-70-tiles-test
  (let [{:keys [container]} (uix-tlr/render ($ hex-grid {:board        nil
                                                         :ball         nil
                                                         :home-players {}
                                                         :away-players {}}))]
    (t/is (= 70 (.-length (.querySelectorAll container "polygon"))))))

(t/deftest hex-grid-renders-player-tokens-test
  (let [{:keys [container]} (uix-tlr/render ($ hex-grid {:board        nil
                                                         :ball         nil
                                                         :home-players sample-home-players
                                                         :away-players sample-away-players}))]
    ;; 3 players with positions = 3 player groups, each with a circle
    (t/is (>= (.-length (.querySelectorAll container "circle")) 3))))

(t/deftest hex-grid-renders-loose-ball-test
  (let [{:keys [container]} (uix-tlr/render ($ hex-grid {:board        nil
                                                         :ball         sample-ball-loose
                                                         :home-players {}
                                                         :away-players {}}))]
    ;; Loose ball has circles for shadow and ball
    (t/is (>= (.-length (.querySelectorAll container "circle")) 1))))

(t/deftest hex-grid-renders-possessed-ball-at-holder-position-test
  (let [{:keys [container]} (uix-tlr/render ($ hex-grid {:board        nil
                                                         :ball         sample-ball-possessed
                                                         :home-players sample-home-players
                                                         :away-players {}}))]
    ;; With holder in home-players, ball indicator renders at holder position
    ;; Player circles + ball indicator circles
    (t/is (>= (.-length (.querySelectorAll container "circle")) 3))))

(t/deftest hex-grid-does-not-render-possessed-ball-without-holder-test
  (let [{:keys [container]} (uix-tlr/render ($ hex-grid {:board        nil
                                                         :ball         sample-ball-possessed
                                                         :home-players {}
                                                         :away-players {}}))]
    ;; Without holder player, no ball indicator circles
    (t/is (= 0 (.-length (.querySelectorAll container "circle"))))))

(t/deftest hex-grid-highlights-valid-moves-test
  (let [{:keys [container]} (uix-tlr/render ($ hex-grid {:board        nil
                                                         :ball         nil
                                                         :home-players {}
                                                         :away-players {}
                                                         :valid-moves  [[2 5] [3 5]]}))
        polygons            (.querySelectorAll container "polygon")
        fills               (map #(.getAttribute % "fill") polygons)]
    (t/is (some #(= "#bbf7d0" %) fills))))

(t/deftest hex-grid-tile-click-calls-handler-test
  (t/async done
           (let [clicked             (atom nil)
                 handler             (fn [q r] (reset! clicked [q r]))
                 {:keys [container]} (uix-tlr/render
                                      ($ hex-grid {:board        nil
                                                   :ball         nil
                                                   :home-players {}
                                                   :away-players {}
                                                   :on-hex-click handler}))
                 usr                 (user/setup)
                 polygons            (.querySelectorAll container "polygon")
                 ;; Click first polygon
                 polygon             (aget polygons 0)]
             (-> (user/click usr polygon)
                 (.then (fn []
                          (t/is (some? @clicked))
                          (t/is (vector? @clicked))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest hex-grid-player-click-calls-handler-test
  (t/async done
           (let [clicked             (atom nil)
                 handler             (fn [id] (reset! clicked id))
                 {:keys [container]} (uix-tlr/render
                                      ($ hex-grid {:board           nil
                                                   :ball            nil
                                                   :home-players    sample-home-players
                                                   :away-players    {}
                                                   :on-player-click handler}))
                 usr                 (user/setup)
                 ;; Find player token circles (the main circle, not selection rings)
                 circles             (.querySelectorAll container "circle")
                 ;; Get a circle that's part of a player token (has blue or red fill)
                 player-circle       (first (filter #(contains? #{"#3b82f6" "#ef4444"}
                                                                (.getAttribute % "fill"))
                                                    (array-seq circles)))]
             (if player-circle
               (-> (user/click usr player-circle)
                   (.then (fn []
                            (t/is (some? @clicked))
                            (t/is (contains? #{"player-1" "player-2"} @clicked))
                            (done)))
                   (.catch (fn [e]
                             (t/is false (str e))
                             (done))))
               (do
                 (t/is false "No player circle found")
                 (done))))))

(t/deftest hex-grid-possessed-ball-click-calls-handler-test
  (t/async done
           (let [clicked             (atom false)
                 handler             (fn [] (reset! clicked true))
                 {:keys [container]} (uix-tlr/render
                                      ($ hex-grid {:board         nil
                                                   :ball          sample-ball-possessed
                                                   :home-players  sample-home-players
                                                   :away-players  {}
                                                   :on-ball-click handler}))
                 usr                 (user/setup)
                 circles             (.querySelectorAll container "circle")
                 ;; Find the ball circle (orange fill)
                 ball-circle         (first (filter #(= "#f97316" (.getAttribute % "fill"))
                                                    (array-seq circles)))]
             (if ball-circle
               (-> (user/click usr ball-circle)
                   (.then (fn []
                            (t/is @clicked)
                            (done)))
                   (.catch (fn [e]
                             (t/is false (str e))
                             (done))))
               (do
                 (t/is false "No ball circle found")
                 (done))))))
