(ns basketball-editor-ui.schemas.card-test
  (:require
   [basketball-editor-ui.schemas.card :as card]
   [cljs.test :as t :include-macros true]
   [malli.core :as m]))

(t/deftest player-card-valid-test
  (t/testing "valid player card passes validation"
    (let [valid-card {:card-type :card-type-enum/PLAYER_CARD
                      :name "Star Player"
                      :sht 5
                      :pss 4
                      :def 3
                      :speed 6
                      :size :size-enum/MD
                      :deck-size 5
                      :abilities ["Slam Dunk" "Fast Break"]}]
      (t/is (m/validate card/PlayerCard valid-card)))))

(t/deftest player-card-requires-name-test
  (t/testing "player card requires name field"
    (let [invalid-card {:card-type :card-type-enum/PLAYER_CARD
                        :sht 5
                        :pss 4
                        :def 3
                        :speed 6
                        :size :size-enum/MD
                        :deck-size 5
                        :abilities []}]
      (t/is (not (m/validate card/PlayerCard invalid-card))))))

(t/deftest player-card-stat-range-test
  (t/testing "player card stats must be in valid range"
    (let [invalid-card {:card-type :card-type-enum/PLAYER_CARD
                        :name "Invalid Stats"
                        :sht 15
                        :pss 4
                        :def 3
                        :speed 6
                        :size :size-enum/MD
                        :deck-size 5
                        :abilities []}]
      (t/is (not (m/validate card/PlayerCard invalid-card))))))

(t/deftest play-card-valid-test
  (t/testing "valid play card passes validation"
    (let [valid-card {:card-type :card-type-enum/PLAY_CARD
                      :name "Quick Pass"
                      :fate 2
                      :play "Pass the ball to an open teammate."}]
      (t/is (m/validate card/PlayCard valid-card)))))

(t/deftest game-card-multi-dispatch-test
  (t/testing "GameCard dispatches to correct schema"
    (let [player {:card-type :card-type-enum/PLAYER_CARD
                  :name "Player"
                  :sht 1 :pss 1 :def 1 :speed 1
                  :size :size-enum/SM
                  :deck-size 5
                  :abilities []}
          play   {:card-type :card-type-enum/PLAY_CARD
                  :name "Play"
                  :fate 2
                  :play "Do something"}]
      (t/is (m/validate card/GameCard player))
      (t/is (m/validate card/GameCard play)))))

(t/deftest valid-fn-test
  (t/testing "valid? helper function works"
    (let [valid-card {:card-type :card-type-enum/COACHING_CARD
                      :name "Timeout"
                      :fate 1
                      :coaching "Call a timeout to regroup."}]
      (t/is (card/valid? valid-card)))))

(t/deftest explain-fn-test
  (t/testing "explain returns nil for valid card"
    (let [valid-card {:card-type :card-type-enum/TEAM_ASSET_CARD
                      :name "Home Court"
                      :fate 0
                      :asset-power "Gain advantage on home games."}]
      (t/is (nil? (card/explain valid-card))))))

(t/deftest explain-returns-errors-test
  (t/testing "explain returns errors for invalid card"
    (let [invalid-card {:card-type :card-type-enum/PLAYER_CARD
                        :name "Missing Stats"}]
      (t/is (some? (card/explain invalid-card))))))
