(ns bashketball-game-ui.components.game.score-controls-test
  (:require
   [bashketball-game-ui.components.game.score-controls :refer [score-controls]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(t/deftest score-controls-renders-all-buttons-test
  (uix-tlr/render ($ score-controls {:on-add-score (fn [_ _])}))
  (t/is (some? (screen/get-by-text "HOME")))
  (t/is (some? (screen/get-by-text "AWAY")))
  (t/is (= 2 (count (screen/get-all-by-text "-1"))))
  (t/is (= 2 (count (screen/get-all-by-text "+1")))))

(t/deftest score-controls-home-plus-one-calls-handler-test
  (t/async done
           (let [calls   (atom [])
                 handler (fn [team points] (swap! calls conj {:team team :points points}))
                 _       (uix-tlr/render ($ score-controls {:on-add-score handler}))
                 usr     (user/setup)
                 btns    (screen/get-all-by-text "+1")
                 home-btn (first btns)]
             (-> (user/click usr home-btn)
                 (.then (fn []
                          (t/is (= [{:team :HOME :points 1}] @calls))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest score-controls-away-plus-one-calls-handler-test
  (t/async done
           (let [calls   (atom [])
                 handler (fn [team points] (swap! calls conj {:team team :points points}))
                 _       (uix-tlr/render ($ score-controls {:on-add-score handler}))
                 usr     (user/setup)
                 btns    (screen/get-all-by-text "+1")
                 away-btn (second btns)]
             (-> (user/click usr away-btn)
                 (.then (fn []
                          (t/is (= [{:team :AWAY :points 1}] @calls))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest score-controls-home-minus-one-calls-handler-test
  (t/async done
           (let [calls   (atom [])
                 handler (fn [team points] (swap! calls conj {:team team :points points}))
                 _       (uix-tlr/render ($ score-controls {:on-add-score handler}))
                 usr     (user/setup)
                 btns    (screen/get-all-by-text "-1")
                 home-btn (first btns)]
             (-> (user/click usr home-btn)
                 (.then (fn []
                          (t/is (= [{:team :HOME :points -1}] @calls))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest score-controls-disabled-prevents-clicks-test
  (uix-tlr/render ($ score-controls {:on-add-score (fn [_ _])
                                     :disabled     true}))
  (let [btns (screen/get-all-by-text "+1")]
    (t/is (every? #(.-disabled %) btns))))
