(ns bashketball-game.effect-catalog-test
  (:require [bashketball-game.effect-catalog :as catalog]
            [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; Sample Structured Cards (Test Fixtures)
;; =============================================================================

(def sample-player-card
  {:slug "clutch-shooter"
   :name "Clutch Shooter"
   :set-slug "base"
   :card-type :card-type/PLAYER_CARD
   :sht 8
   :pss 6
   :def 5
   :speed 7
   :size :size/MD
   :player-subtypes [:player-subtype/HUMAN]
   :abilities
   [{:ability/id "clutch"
     :ability/name "Clutch"
     :ability/description "When shooting in Q4, +2 to skill test"
     :ability/trigger {:trigger/event :bashketball/skill-test.before
                       :trigger/condition [:and
                                           [:= :event/test-type :shoot]
                                           [:= :doc/quarter 4]
                                           [:= :event/actor-id :self/id]]
                       :trigger/timing :before}
     :ability/effect {:effect/type :bashketball/add-modifier
                      :target :event/actor-id
                      :stat :stat/SHOOTING
                      :amount 2
                      :duration :this-test}}]})

(def sample-ability-card
  {:slug "quick-release"
   :name "Quick Release"
   :set-slug "base"
   :card-type :card-type/ABILITY_CARD
   :fate 3
   :abilities
   [{:ability/id "quick-release"
     :ability/name "Quick Release"
     :ability/description "After shooting, move 1 hex"
     :ability/trigger {:trigger/event :bashketball/shoot.after
                       :trigger/condition [:= :event/actor-id :self/id]
                       :trigger/timing :after}
     :ability/effect {:effect/type :bashketball/move-player
                      :player-id :self/id
                      :distance 1}}]})

(def sample-play-card
  {:slug "fast-break"
   :name "Fast Break"
   :set-slug "base"
   :card-type :card-type/PLAY_CARD
   :fate 4
   :play
   {:play/id "fast-break"
    :play/name "Fast Break"
    :play/description "Refresh a player and grant an extra action"
    :play/targets [:target/player-id]
    :play/effect {:effect/type :bashketball/sequence
                  :effects [{:effect/type :bashketball/refresh-player
                             :player-id :target/player-id}
                            {:effect/type :bashketball/grant-action
                             :player-id :target/player-id}]}}})

(def sample-standard-action
  {:slug "shoot-block"
   :name "Shoot / Block"
   :set-slug "base"
   :card-type :card-type/STANDARD_ACTION_CARD
   :fate 2
   :offense
   {:action/id "shoot"
    :action/name "Shoot"
    :action/description "Ball carrier within 7 hexes of basket attempts Shot"
    :action/requires [:bashketball/has-ball? :doc/state :actor/id]
    :action/targets [:actor/id]
    :action/effect {:effect/type :bashketball/initiate-skill-test
                    :test-type :shoot
                    :player-id :actor/id
                    :stat :stat/SHOOTING
                    :exhausts true}}
   :defense
   {:action/id "block"
    :action/name "Block"
    :action/description "Force opponent to shoot or exhaust"
    :action/requires [:and
                      [:bashketball/adjacent? :doc/state :actor/id :target/id]
                      [:bashketball/within-range? :doc/state :target/id :basket 4]]
    :action/targets [:actor/id :target/id]
    :action/effect {:effect/type :bashketball/force-choice
                    :target :target/id
                    :choices [:shoot :exhaust-skip]}}})

(def sample-split-play
  {:slug "pick-and-roll"
   :name "Pick and Roll"
   :set-slug "base"
   :card-type :card-type/SPLIT_PLAY_CARD
   :fate 5
   :offense
   {:action/id "pick-offense"
    :action/name "Pick and Roll"
    :action/effect {:effect/type :bashketball/sequence
                    :effects []}}
   :defense
   {:action/id "pick-defense"
    :action/name "Defensive Switch"
    :action/effect {:effect/type :bashketball/sequence
                    :effects []}}})

(def sample-coaching-card
  {:slug "quick-release-coaching"
   :name "Quick Release"
   :set-slug "base"
   :card-type :card-type/COACHING_CARD
   :fate 3
   :call
   {:call/id "quick-release-call"
    :call/name "Quick Release"
    :call/description "+2 SHT to target player until end of turn"
    :call/targets [:target/player-id]
    :call/effect {:effect/type :bashketball/add-modifier
                  :target :target/player-id
                  :stat :stat/SHOOTING
                  :amount 2
                  :duration :until-end-of-turn}}
   :signal
   {:signal/id "quick-release-signal"
    :signal/name "Quick Release Signal"
    :signal/description "+1 SHT to next Shot"
    :signal/effect {:effect/type :bashketball/add-modifier
                    :stat :stat/SHOOTING
                    :amount 1
                    :duration :next-skill-test}}})

(def sample-team-asset
  {:slug "home-court-advantage"
   :name "Home Court Advantage"
   :set-slug "base"
   :card-type :card-type/TEAM_ASSET_CARD
   :fate 4
   :asset-power
   {:asset/id "home-court"
    :asset/name "Home Court Advantage"
    :asset/description "Draw 1 card at the start of each of your turns"
    :asset/triggers
    [{:trigger {:trigger/event :bashketball/turn-started.at
                :trigger/condition [:= :doc/active-player :self/team]}
      :effect {:effect/type :bashketball/draw-cards
               :team :self/team
               :count 1}}]}})

(def sample-response-asset
  {:slug "defensive-timeout"
   :name "Defensive Timeout"
   :set-slug "base"
   :card-type :card-type/TEAM_ASSET_CARD
   :card-subtypes [:card-subtype/RESPONSE]
   :fate 2
   :asset-power
   {:asset/id "defensive-timeout"
    :asset/name "Defensive Timeout"
    :asset/response
    {:response/trigger {:trigger/event :bashketball/skill-test.before
                        :trigger/condition [:= :event/defender-team :self/team]}
     :response/prompt "Call Defensive Timeout?"
     :response/effect {:effect/type :bashketball/add-modifier
                       :target :event/defender-id
                       :stat :stat/DEFENSE
                       :amount 2}}}})

;; All sample cards
(def sample-cards
  [sample-player-card
   sample-ability-card
   sample-play-card
   sample-standard-action
   sample-split-play
   sample-coaching-card
   sample-team-asset
   sample-response-asset])

;; =============================================================================
;; Tests
;; =============================================================================

(deftest create-catalog-test
  (testing "creates catalog from map"
    (let [cat (catalog/create-catalog {"test" {:slug "test"}})]
      (is (some? cat))
      (is (= {:slug "test"} (catalog/get-card cat "test")))))

  (testing "creates catalog from sequence"
    (let [cat (catalog/create-catalog-from-seq sample-cards)]
      (is (some? cat))
      (is (= "clutch-shooter" (:slug (catalog/get-card cat "clutch-shooter")))))))

(deftest get-abilities-test
  (let [cat (catalog/create-catalog-from-seq sample-cards)]
    (testing "returns abilities for player card"
      (let [abilities (catalog/get-abilities cat "clutch-shooter")]
        (is (vector? abilities))
        (is (= 1 (count abilities)))
        (is (= "clutch" (:ability/id (first abilities))))))

    (testing "returns abilities for ability card"
      (let [abilities (catalog/get-abilities cat "quick-release")]
        (is (= 1 (count abilities)))
        (is (= "quick-release" (:ability/id (first abilities))))))

    (testing "returns nil for non-ability cards"
      (is (nil? (catalog/get-abilities cat "fast-break"))))))

(deftest get-play-test
  (let [cat (catalog/create-catalog-from-seq sample-cards)]
    (testing "returns play for play card"
      (let [play (catalog/get-play cat "fast-break")]
        (is (some? play))
        (is (= "fast-break" (:play/id play)))))

    (testing "returns nil for non-play cards"
      (is (nil? (catalog/get-play cat "clutch-shooter"))))))

(deftest get-offense-defense-test
  (let [cat (catalog/create-catalog-from-seq sample-cards)]
    (testing "returns offense for standard action"
      (let [offense (catalog/get-offense cat "shoot-block")]
        (is (some? offense))
        (is (= "shoot" (:action/id offense)))))

    (testing "returns defense for standard action"
      (let [defense (catalog/get-defense cat "shoot-block")]
        (is (some? defense))
        (is (= "block" (:action/id defense)))))

    (testing "returns offense for split play"
      (is (some? (catalog/get-offense cat "pick-and-roll"))))

    (testing "returns nil for non-action cards"
      (is (nil? (catalog/get-offense cat "fast-break"))))))

(deftest get-call-signal-test
  (let [cat (catalog/create-catalog-from-seq sample-cards)]
    (testing "returns call for coaching card"
      (let [call (catalog/get-call cat "quick-release-coaching")]
        (is (some? call))
        (is (= "quick-release-call" (:call/id call)))))

    (testing "returns signal for coaching card"
      (let [signal (catalog/get-signal cat "quick-release-coaching")]
        (is (some? signal))
        (is (= "quick-release-signal" (:signal/id signal)))))

    (testing "returns nil for non-coaching cards"
      (is (nil? (catalog/get-call cat "fast-break"))))))

(deftest get-asset-power-test
  (let [cat (catalog/create-catalog-from-seq sample-cards)]
    (testing "returns asset power for team asset"
      (let [power (catalog/get-asset-power cat "home-court-advantage")]
        (is (some? power))
        (is (= "home-court" (:asset/id power)))))

    (testing "returns asset power for response asset"
      (let [power (catalog/get-asset-power cat "defensive-timeout")]
        (is (some? power))
        (is (some? (:asset/response power)))))

    (testing "returns nil for non-asset cards"
      (is (nil? (catalog/get-asset-power cat "fast-break"))))))

(deftest inline-card-helpers-test
  (testing "get-abilities-from-card extracts abilities"
    (let [abilities (catalog/get-abilities-from-card sample-player-card)]
      (is (= 1 (count abilities)))))

  (testing "get-asset-power-from-card extracts asset power"
    (let [power (catalog/get-asset-power-from-card sample-team-asset)]
      (is (= "home-court" (:asset/id power))))))
