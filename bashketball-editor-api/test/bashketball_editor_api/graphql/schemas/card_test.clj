(ns bashketball-editor-api.graphql.schemas.card-test
  (:require
   [bashketball-editor-api.graphql.schemas.card :as schemas]
   [clojure.test :refer [deftest is testing]]
   [malli.core :as m]))

(deftest card-type-enum-test
  (testing "validates valid card types"
    (is (m/validate schemas/CardType :card-type/PLAYER_CARD))
    (is (m/validate schemas/CardType :card-type/ABILITY_CARD))
    (is (m/validate schemas/CardType :card-type/PLAY_CARD)))

  (testing "rejects invalid card types"
    (is (not (m/validate schemas/CardType :card-type/INVALID)))
    (is (not (m/validate schemas/CardType "PLAYER_CARD")))))

(deftest player-size-enum-test
  (testing "validates valid sizes"
    (is (m/validate schemas/PlayerSize :size/SM))
    (is (m/validate schemas/PlayerSize :size/MD))
    (is (m/validate schemas/PlayerSize :size/LG)))

  (testing "rejects invalid sizes"
    (is (not (m/validate schemas/PlayerSize :size/XL)))
    (is (not (m/validate schemas/PlayerSize "SM")))))

(deftest player-card-schema-test
  (testing "validates complete player card"
    (let [card {:slug "jordan"
                :name "Jordan"
                :set-id (random-uuid)
                :card-type :card-type/PLAYER_CARD
                :deck-size 5
                :sht 5
                :pss 3
                :def 4
                :speed 4
                :size :size/MD
                :abilities ["Clutch" "Fadeaway"]}]
      (is (m/validate schemas/PlayerCard card))))

  (testing "validates player card with optional fields"
    (let [card {:slug "rookie"
                :name "Rookie"
                :set-id (random-uuid)
                :card-type :card-type/PLAYER_CARD
                :deck-size 3
                :sht 1
                :pss 1
                :def 1
                :speed 1
                :size :size/SM
                :abilities []
                :image-prompt "A young basketball player"}]
      (is (m/validate schemas/PlayerCard card)))))

(deftest ability-card-schema-test
  (testing "validates ability card"
    (let [card {:slug "double-jump"
                :name "Double Jump"
                :set-id (random-uuid)
                :card-type :card-type/ABILITY_CARD
                :abilities ["Jump twice in one turn"]}]
      (is (m/validate schemas/AbilityCard card)))))

(deftest play-card-schema-test
  (testing "validates play card"
    (let [card {:slug "fast-break"
                :name "Fast Break"
                :set-id (random-uuid)
                :card-type :card-type/PLAY_CARD
                :fate 2
                :play "Score on a fast break"}]
      (is (m/validate schemas/PlayCard card)))))

(deftest split-play-card-schema-test
  (testing "validates split play card"
    (let [card {:slug "versatile-play"
                :name "Versatile Play"
                :set-id (random-uuid)
                :card-type :card-type/SPLIT_PLAY_CARD
                :fate 3
                :offense "Drive to the basket"
                :defense "Set a pick"}]
      (is (m/validate schemas/SplitPlayCard card)))))

(deftest coaching-card-schema-test
  (testing "validates coaching card"
    (let [card {:slug "time-out"
                :name "Time Out"
                :set-id (random-uuid)
                :card-type :card-type/COACHING_CARD
                :fate 1
                :coaching "Reset your team's positions"}]
      (is (m/validate schemas/CoachingCard card)))))

(deftest standard-action-card-schema-test
  (testing "validates standard action card"
    (let [card {:slug "basic-move"
                :name "Basic Move"
                :set-id (random-uuid)
                :card-type :card-type/STANDARD_ACTION_CARD
                :fate 0
                :offense "Move one space"
                :defense "Block"}]
      (is (m/validate schemas/StandardActionCard card)))))

(deftest team-asset-card-schema-test
  (testing "validates team asset card"
    (let [card {:slug "home-court"
                :name "Home Court"
                :set-id (random-uuid)
                :card-type :card-type/TEAM_ASSET_CARD
                :fate 5
                :asset-power "+1 to all shots at home"}]
      (is (m/validate schemas/TeamAssetCard card)))))

(deftest game-card-multi-schema-test
  (testing "validates different card types via multi"
    (let [player {:slug "jordan"
                  :name "Jordan"
                  :set-id (random-uuid)
                  :card-type :card-type/PLAYER_CARD
                  :deck-size 5
                  :sht 5 :pss 3 :def 4 :speed 4
                  :size :size/MD
                  :abilities []}
          play {:slug "fast-break"
                :name "Fast Break"
                :set-id (random-uuid)
                :card-type :card-type/PLAY_CARD
                :fate 2
                :play "Score quickly"}]
      (is (m/validate schemas/GameCard player))
      (is (m/validate schemas/GameCard play)))))

(deftest card-set-schema-test
  (testing "validates card set with string id"
    (let [card-set {:id "abc-123"
                    :name "Base Set"
                    :description "The base game cards"}]
      (is (m/validate schemas/CardSet card-set))))

  (testing "validates card set without description"
    (let [card-set {:id "def-456"
                    :name "Expansion 1"}]
      (is (m/validate schemas/CardSet card-set)))))

(deftest input-schema-test
  (testing "validates player card input"
    (let [input {:slug "test-player"
                 :name "Test Player"}]
      (is (m/validate schemas/PlayerCardInput input))))

  (testing "validates card set input"
    (let [input {:name "New Set"}]
      (is (m/validate schemas/CardSetInput input)))))
