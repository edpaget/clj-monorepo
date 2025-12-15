(ns bashketball-game-ui.components.game.play-area-panel-test
  (:require
   [bashketball-game-ui.components.game.play-area-panel :refer [play-area-panel]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-play-area
  [{:instance-id "uuid-1" :card-slug "fast-break" :played-by :team/HOME}
   {:instance-id "uuid-2" :card-slug "slam-dunk" :played-by :team/AWAY}])

(def sample-catalog
  {"fast-break" {:name "Fast Break"}
   "slam-dunk"  {:name "Slam Dunk"}})

(t/deftest play-area-panel-renders-empty-state-test
  (uix-tlr/render ($ play-area-panel {:play-area []}))
  (t/is (some? (screen/get-by-text "No cards in play area"))))

(t/deftest play-area-panel-renders-cards-test
  (uix-tlr/render ($ play-area-panel {:play-area sample-play-area
                                      :catalog   sample-catalog}))
  (t/is (some? (screen/get-by-text "Fast Break")))
  (t/is (some? (screen/get-by-text "Slam Dunk"))))

(t/deftest play-area-panel-shows-card-count-test
  (uix-tlr/render ($ play-area-panel {:play-area sample-play-area
                                      :catalog   sample-catalog}))
  (t/is (some? (screen/get-by-text "Play Area (2)"))))

(t/deftest play-area-panel-resolve-button-calls-handler-test
  (t/async done
           (let [resolved (atom nil)
                 _        (uix-tlr/render ($ play-area-panel {:play-area  sample-play-area
                                                              :catalog    sample-catalog
                                                              :on-resolve #(reset! resolved %)}))
                 usr      (user/setup)
                 btns     (screen/get-all-by-text "Resolve")]
             (-> (user/click usr (first btns))
                 (.then (fn []
                          (t/is (= "uuid-1" @resolved))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest play-area-panel-card-click-calls-info-handler-test
  (t/async done
           (let [info-slug (atom nil)
                 _         (uix-tlr/render ($ play-area-panel {:play-area     sample-play-area
                                                               :catalog       sample-catalog
                                                               :on-info-click #(reset! info-slug %)}))
                 usr       (user/setup)
                 card-btn  (screen/get-by-text "Fast Break")]
             (-> (user/click usr card-btn)
                 (.then (fn []
                          (t/is (= "fast-break" @info-slug))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest play-area-panel-renders-token-button-test
  (uix-tlr/render ($ play-area-panel {:play-area sample-play-area
                                      :catalog   sample-catalog}))
  (t/is (= 2 (count (screen/get-all-by-text "Token")))))

(t/deftest play-area-panel-token-button-calls-handler-test
  (t/async done
           (let [called? (atom false)
                 _       (uix-tlr/render ($ play-area-panel {:play-area       sample-play-area
                                                             :catalog         sample-catalog
                                                             :on-create-token #(reset! called? true)}))
                 usr     (user/setup)
                 btns    (screen/get-all-by-text "Token")]
             (-> (user/click usr (first btns))
                 (.then (fn []
                          (t/is @called?)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(def ability-card-play-area
  [{:instance-id "ability-1" :card-slug "power-shot" :played-by :team/HOME}])

(def ability-card-catalog
  {"power-shot" {:name      "Power Shot"
                 :card-type :card-type/ABILITY_CARD}})

(t/deftest play-area-panel-ability-card-calls-attach-handler-test
  (t/async done
           (let [attach-called (atom nil)
                 resolve-called (atom nil)
                 _              (uix-tlr/render ($ play-area-panel {:play-area  ability-card-play-area
                                                                    :catalog    ability-card-catalog
                                                                    :on-attach  (fn [instance-id card-slug played-by]
                                                                                  (reset! attach-called {:instance-id instance-id
                                                                                                         :card-slug   card-slug
                                                                                                         :played-by   played-by}))
                                                                    :on-resolve #(reset! resolve-called %)}))
                 usr            (user/setup)
                 btn            (first (screen/get-all-by-text "Resolve"))]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is (some? @attach-called) "on-attach should be called for ability cards")
                          (t/is (= "ability-1" (:instance-id @attach-called)))
                          (t/is (= "power-shot" (:card-slug @attach-called)))
                          (t/is (= :team/HOME (:played-by @attach-called)))
                          (t/is (nil? @resolve-called) "on-resolve should NOT be called for ability cards")
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))
