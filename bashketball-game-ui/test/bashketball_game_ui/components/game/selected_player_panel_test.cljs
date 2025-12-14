(ns bashketball-game-ui.components.game.selected-player-panel-test
  (:require
   [bashketball-game-ui.components.game.selected-player-panel :refer [selected-player-panel]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def base-player
  {:id         "player-1"
   :name       "Orc Center"
   :card-slug  "orc-center"
   :exhausted false
   :modifiers  []
   :attachments []
   :stats      {:speed    3
                :shooting 5
                :defense  4
                :dribbling 2
                :passing  3
                :size     :size/LG}})

(def sample-catalog
  {"power-shot"   {:name "Power Shot"}
   "quick-feet"   {:name "Quick Feet"}
   "token-ability" {:name "Token Ability"}})

(t/deftest renders-player-stats-test
  (uix-tlr/render ($ selected-player-panel {:player      base-player
                                            :token-label "H1"
                                            :team        :team/HOME}))
  (t/is (some? (screen/get-by-text "Orc Center")))
  (t/is (some? (screen/get-by-text "SPD")))
  (t/is (some? (screen/get-by-text "SHT"))))

(t/deftest no-attachments-section-when-empty-test
  (uix-tlr/render ($ selected-player-panel {:player      base-player
                                            :token-label "H1"
                                            :team        :team/HOME}))
  (t/is (nil? (screen/query-by-text "Attachments"))))

(t/deftest renders-attachments-section-test
  (let [player (assoc base-player :attachments
                      [{:instance-id "att-1" :card-slug "power-shot"}
                       {:instance-id "att-2" :card-slug "quick-feet"}])]
    (uix-tlr/render ($ selected-player-panel {:player      player
                                              :token-label "H1"
                                              :team        :team/HOME
                                              :catalog     sample-catalog}))
    (t/is (some? (screen/get-by-text "Attachments")))
    (t/is (some? (screen/get-by-text "Power Shot")))
    (t/is (some? (screen/get-by-text "Quick Feet")))))

(t/deftest attachment-click-calls-handler-test
  (t/async done
           (let [clicked-slug (atom nil)
                 player       (assoc base-player :attachments
                                     [{:instance-id "att-1" :card-slug "power-shot"}])]
             (uix-tlr/render ($ selected-player-panel {:player              player
                                                       :token-label         "H1"
                                                       :team                :team/HOME
                                                       :catalog             sample-catalog
                                                       :on-attachment-click #(reset! clicked-slug %)}))
             (let [usr (user/setup)
                   btn (screen/get-by-text "Power Shot")]
               (-> (user/click usr btn)
                   (.then (fn []
                            (t/is (= "power-shot" @clicked-slug))
                            (done)))
                   (.catch (fn [e]
                             (t/is false (str e))
                             (done))))))))

(t/deftest token-attachment-uses-inline-card-test
  (let [player (assoc base-player :attachments
                      [{:instance-id "att-1"
                        :token      true
                        :card        {:slug "token-ability" :name "Inline Token"}}])]
    (uix-tlr/render ($ selected-player-panel {:player      player
                                              :token-label "H1"
                                              :team        :team/HOME
                                              :catalog     sample-catalog}))
    (t/is (some? (screen/get-by-text "Attachments")))
    (t/is (some? (screen/get-by-text "Inline Token")))))
