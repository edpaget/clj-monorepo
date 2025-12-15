(ns bashketball-game-ui.components.game.ball-indicator-test
  (:require
   [bashketball-game-ui.components.game.ball-indicator :refer [ball-indicator]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-ball-loose
  {:__typename "BallLoose"
   :position   [3 5]})

(def sample-ball-possessed
  {:__typename "BallPossessed"
   :holder-id  "player-1"})

(def sample-ball-in-air
  {:__typename  "BallInAir"
   :origin      [2 3]
   :target      {:position [4 7]}
   :action-type :action-type/PASS})

(def sample-player-positions
  {"player-1" [2 5]
   "player-2" [3 8]})

(t/deftest ball-indicator-renders-loose-ball-test
  (let [{:keys [container]} (uix-tlr/render
                             ($ ball-indicator {:ball     sample-ball-loose
                                                :selected false}))]
    (t/is (>= (.-length (.querySelectorAll container "circle")) 1))))

(t/deftest ball-indicator-renders-loose-ball-selection-ring-test
  (let [{:keys [container]} (uix-tlr/render
                             ($ ball-indicator {:ball     sample-ball-loose
                                                :selected true}))]
    (t/is (>= (.-length (.querySelectorAll container "circle")) 2))))

(t/deftest ball-indicator-renders-possessed-ball-when-holder-position-provided-test
  (let [{:keys [container]} (uix-tlr/render
                             ($ ball-indicator {:ball            sample-ball-possessed
                                                :holder-position [2 5]
                                                :selected        false}))]
    (t/is (>= (.-length (.querySelectorAll container "circle")) 1))))

(t/deftest ball-indicator-does-not-render-possessed-ball-without-holder-position-test
  (let [{:keys [container]} (uix-tlr/render
                             ($ ball-indicator {:ball     sample-ball-possessed
                                                :selected false}))]
    (t/is (= 0 (.-length (.querySelectorAll container "circle"))))))

(t/deftest ball-indicator-renders-possessed-ball-selection-ring-test
  (let [{:keys [container]} (uix-tlr/render
                             ($ ball-indicator {:ball            sample-ball-possessed
                                                :holder-position [2 5]
                                                :selected        true}))]
    (t/is (>= (.-length (.querySelectorAll container "circle")) 2))))

(t/deftest ball-indicator-renders-in-air-ball-test
  (let [{:keys [container]} (uix-tlr/render
                             ($ ball-indicator {:ball             sample-ball-in-air
                                                :player-positions sample-player-positions
                                                :selected         false}))]
    (t/is (>= (.-length (.querySelectorAll container "circle")) 1))))

(t/deftest ball-indicator-loose-ball-click-calls-handler-test
  (t/async done
           (let [clicked             (atom false)
                 {:keys [container]} (uix-tlr/render
                                      ($ ball-indicator {:ball     sample-ball-loose
                                                         :selected false
                                                         :on-click #(reset! clicked true)}))
                 usr                 (user/setup)
                 circles             (.querySelectorAll container "circle")
                 ball-circle         (aget circles 0)]
             (-> (user/click usr ball-circle)
                 (.then (fn []
                          (t/is @clicked)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest ball-indicator-possessed-ball-click-calls-handler-test
  (t/async done
           (let [clicked             (atom false)
                 {:keys [container]} (uix-tlr/render
                                      ($ ball-indicator {:ball            sample-ball-possessed
                                                         :holder-position [2 5]
                                                         :selected        false
                                                         :on-click        #(reset! clicked true)}))
                 usr                 (user/setup)
                 circles             (.querySelectorAll container "circle")
                 ball-circle         (aget circles 0)]
             (-> (user/click usr ball-circle)
                 (.then (fn []
                          (t/is @clicked)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
