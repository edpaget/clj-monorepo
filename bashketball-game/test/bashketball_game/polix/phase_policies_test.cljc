(ns bashketball-game.polix.phase-policies-test
  (:require
   [bashketball-game.polix.phase-policies :as pp]
   [bashketball-game.polix.phase-triggers :as pt]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing]]))

(deftest valid-transitions-test
  (testing "SETUP can only transition to TIP_OFF"
    (is (pp/valid-transition? :phase/SETUP :phase/TIP_OFF))
    (is (not (pp/valid-transition? :phase/SETUP :phase/UPKEEP)))
    (is (not (pp/valid-transition? :phase/SETUP :phase/ACTIONS))))

  (testing "TIP_OFF can only transition to UPKEEP"
    (is (pp/valid-transition? :phase/TIP_OFF :phase/UPKEEP))
    (is (not (pp/valid-transition? :phase/TIP_OFF :phase/ACTIONS))))

  (testing "UPKEEP can only transition to ACTIONS"
    (is (pp/valid-transition? :phase/UPKEEP :phase/ACTIONS))
    (is (not (pp/valid-transition? :phase/UPKEEP :phase/END_OF_TURN))))

  (testing "ACTIONS can only transition to END_OF_TURN"
    (is (pp/valid-transition? :phase/ACTIONS :phase/END_OF_TURN))
    (is (not (pp/valid-transition? :phase/ACTIONS :phase/UPKEEP))))

  (testing "END_OF_TURN can transition to UPKEEP or GAME_OVER"
    (is (pp/valid-transition? :phase/END_OF_TURN :phase/UPKEEP))
    (is (pp/valid-transition? :phase/END_OF_TURN :phase/GAME_OVER))
    (is (not (pp/valid-transition? :phase/END_OF_TURN :phase/ACTIONS)))))

(deftest constants-test
  (testing "hand limit is 8"
    (is (= 8 pp/hand-limit)))

  (testing "12 turns per quarter"
    (is (= 12 pp/turns-per-quarter)))

  (testing "4 quarters per game"
    (is (= 4 pp/quarters-per-game)))

  (testing "action costs are defined"
    (is (= 0 (:move pp/action-costs)))
    (is (= 1 (:play-card pp/action-costs)))
    (is (= 3 (:standard-action pp/action-costs)))))

(deftest should-advance-quarter-test
  (testing "returns false when turn <= 12"
    (is (not (pt/should-advance-quarter? {:turn-number 1})))
    (is (not (pt/should-advance-quarter? {:turn-number 12}))))

  (testing "returns true when turn > 12"
    (is (pt/should-advance-quarter? {:turn-number 13}))))

(deftest should-end-game-test
  (testing "returns false in earlier quarters"
    (is (not (pt/should-end-game? {:turn-number 13 :quarter 1})))
    (is (not (pt/should-end-game? {:turn-number 13 :quarter 3}))))

  (testing "returns true after quarter 4 turn 12"
    (is (pt/should-end-game? {:turn-number 13 :quarter 4}))))

(def test-config
  {:home {:deck ["card-1" "card-2" "card-3"]
          :players [{:card-slug "player-1" :name "P1" :stats {:size :size/MD :speed 3 :shooting 2 :passing 2 :defense 2}}]}
   :away {:deck ["card-4" "card-5" "card-6"]
          :players [{:card-slug "player-2" :name "P2" :stats {:size :size/MD :speed 3 :shooting 2 :passing 2 :defense 2}}]}})

(deftest check-hand-limit-test
  (testing "returns nil when under hand limit"
    (let [game-state (state/create-game test-config)]
      (is (nil? (pt/check-hand-limit game-state :team/HOME)))))

  (testing "returns effect when over hand limit"
    (let [game-state (-> (state/create-game test-config)
                         (assoc-in [:players :team/HOME :deck :hand]
                                   (mapv (fn [i] {:instance-id (str i) :card-slug (str "card-" i)})
                                         (range 10))))
          effect     (pt/check-hand-limit game-state :team/HOME)]
      (is (some? effect))
      (is (= :bashketball/offer-choice (:effect/type effect)))
      (is (= :discard-to-hand-limit (:choice-type effect)))
      (is (= 2 (get-in effect [:context :discard-count]))))))

(deftest create-game-includes-quarter-test
  (testing "new game starts at quarter 1"
    (let [game-state (state/create-game test-config)]
      (is (= 1 (:quarter game-state))))))
