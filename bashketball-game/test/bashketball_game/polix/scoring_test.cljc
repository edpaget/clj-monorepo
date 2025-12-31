(ns bashketball-game.polix.scoring-test
  "Tests for scoring zone and distance modifier policies.

  Tests scoring mechanics including:
  - Two-point vs three-point zones
  - Distance to basket calculations
  - Distance-based advantage modifiers
  - Shooting position summaries"
  (:require
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.scoring :as scoring]
   [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; Target Basket Tests
;; =============================================================================

(deftest target-basket-for-team-test
  (testing "HOME team shoots at right basket"
    (is (= [2 13] (scoring/target-basket-for-team :team/HOME))))

  (testing "AWAY team shoots at left basket"
    (is (= [2 0] (scoring/target-basket-for-team :team/AWAY)))))

;; =============================================================================
;; Scoring Zone Tests
;; =============================================================================

(deftest scoring-zone-two-point-left
  (testing "positions near left basket are two-point"
    (is (= :two-point (scoring/scoring-zone [2 0])))
    (is (= :two-point (scoring/scoring-zone [2 1])))
    (is (= :two-point (scoring/scoring-zone [2 2])))
    (is (= :two-point (scoring/scoring-zone [2 3])))))

(deftest scoring-zone-two-point-right
  (testing "positions near right basket are two-point"
    (is (= :two-point (scoring/scoring-zone [2 10])))
    (is (= :two-point (scoring/scoring-zone [2 11])))
    (is (= :two-point (scoring/scoring-zone [2 12])))
    (is (= :two-point (scoring/scoring-zone [2 13])))))

(deftest scoring-zone-three-point-middle
  (testing "middle positions are three-point"
    (is (= :three-point (scoring/scoring-zone [2 4])))
    (is (= :three-point (scoring/scoring-zone [2 5])))
    (is (= :three-point (scoring/scoring-zone [2 6])))
    (is (= :three-point (scoring/scoring-zone [2 7])))
    (is (= :three-point (scoring/scoring-zone [2 8])))
    (is (= :three-point (scoring/scoring-zone [2 9])))))

;; =============================================================================
;; Point Value Tests
;; =============================================================================

(deftest point-value-two-points
  (testing "two-point zone shots worth 2 points"
    (is (= 2 (scoring/point-value [2 2])))
    (is (= 2 (scoring/point-value [2 11])))))

(deftest point-value-three-points
  (testing "three-point zone shots worth 3 points"
    (is (= 3 (scoring/point-value [2 5])))
    (is (= 3 (scoring/point-value [2 7])))))

;; =============================================================================
;; Distance to Basket Tests
;; =============================================================================

(deftest distance-to-basket-test
  (testing "calculates hex distance to basket"
    (is (= 0 (scoring/distance-to-basket [2 13] [2 13])))
    (is (= 3 (scoring/distance-to-basket [2 10] [2 13])))
    (is (= 7 (scoring/distance-to-basket [2 6] [2 13])))))

;; =============================================================================
;; Shooting Distance Tests
;; =============================================================================

(deftest shooting-distance-home-team
  (testing "HOME team shooting distance to right basket"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 10]))]
      (is (= 3 (scoring/shooting-distance state f/home-player-1))))))

(deftest shooting-distance-away-team
  (testing "AWAY team shooting distance to left basket"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/away-player-1 [2 3]))]
      (is (= 3 (scoring/shooting-distance state f/away-player-1))))))

(deftest shooting-distance-off-court
  (testing "returns nil when player off court"
    (let [state (f/base-game-state)]
      (is (nil? (scoring/shooting-distance state f/home-player-1))))))

;; =============================================================================
;; Passing Distance Tests
;; =============================================================================

(deftest passing-distance-test
  (testing "calculates distance between two players"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-player-at f/home-player-2 [2 6]))]
      (is (= 3 (scoring/passing-distance state f/home-player-1 f/home-player-2))))))

(deftest passing-distance-off-court
  (testing "returns nil when either player off court"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3]))]
      (is (nil? (scoring/passing-distance state f/home-player-1 f/home-player-2))))))

;; =============================================================================
;; Distance Category Tests
;; =============================================================================

(deftest distance-category-close
  (testing "1-2 hexes is close"
    (is (= :close (scoring/distance-category 1)))
    (is (= :close (scoring/distance-category 2)))))

(deftest distance-category-medium
  (testing "3-4 hexes is medium"
    (is (= :medium (scoring/distance-category 3)))
    (is (= :medium (scoring/distance-category 4)))))

(deftest distance-category-long
  (testing "5+ hexes is long"
    (is (= :long (scoring/distance-category 5)))
    (is (= :long (scoring/distance-category 7)))))

;; =============================================================================
;; Distance Advantage Tests
;; =============================================================================

(deftest distance-advantage-close
  (testing "close distance gives advantage"
    (is (= :advantage (scoring/distance-advantage 2)))))

(deftest distance-advantage-medium
  (testing "medium distance is normal"
    (is (= :normal (scoring/distance-advantage 4)))))

(deftest distance-advantage-long
  (testing "long distance gives disadvantage"
    (is (= :disadvantage (scoring/distance-advantage 6)))))

;; =============================================================================
;; Court Position Tests
;; =============================================================================

(deftest in-paint-left
  (testing "columns 0-2 are left paint"
    (is (scoring/in-paint? [2 0]))
    (is (scoring/in-paint? [2 1]))
    (is (scoring/in-paint? [2 2]))
    (is (not (scoring/in-paint? [2 3])))))

(deftest in-paint-right
  (testing "columns 11-13 are right paint"
    (is (not (scoring/in-paint? [2 10])))
    (is (scoring/in-paint? [2 11]))
    (is (scoring/in-paint? [2 12]))
    (is (scoring/in-paint? [2 13]))))

(deftest on-three-point-line-test
  (testing "columns 3 and 10 are three-point line"
    (is (scoring/on-three-point-line? [2 3]))
    (is (scoring/on-three-point-line? [2 10]))
    (is (not (scoring/on-three-point-line? [2 4])))))

(deftest in-mid-range-test
  (testing "columns 4-9 are mid-range"
    (is (not (scoring/in-mid-range? [2 3])))
    (is (scoring/in-mid-range? [2 4]))
    (is (scoring/in-mid-range? [2 9]))
    (is (not (scoring/in-mid-range? [2 10])))))

;; =============================================================================
;; Court Side Tests
;; =============================================================================

(deftest court-side-left
  (testing "columns 0-6 are left side"
    (is (= :left (scoring/court-side [2 0])))
    (is (= :left (scoring/court-side [2 6])))))

(deftest court-side-right
  (testing "columns 7-13 are right side"
    (is (= :right (scoring/court-side [2 7])))
    (is (= :right (scoring/court-side [2 13])))))

;; =============================================================================
;; Own/Opponent Half Tests
;; =============================================================================

(deftest in-own-half-home
  (testing "HOME team's own half is left side"
    (is (scoring/in-own-half? [2 3] :team/HOME))
    (is (not (scoring/in-own-half? [2 10] :team/HOME)))))

(deftest in-own-half-away
  (testing "AWAY team's own half is right side"
    (is (scoring/in-own-half? [2 10] :team/AWAY))
    (is (not (scoring/in-own-half? [2 3] :team/AWAY)))))

(deftest in-opponent-half-test
  (testing "opponent half is opposite of own half"
    (is (scoring/in-opponent-half? [2 10] :team/HOME))
    (is (scoring/in-opponent-half? [2 3] :team/AWAY))))

;; =============================================================================
;; Shooting Position Summary Tests
;; =============================================================================

(deftest shooting-position-summary-test
  (testing "returns comprehensive position info"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 11]))
          summary (scoring/shooting-position-summary state f/home-player-1)]
      (is (= [2 11] (:position summary)))
      (is (= :two-point (:zone summary)))
      (is (= 2 (:point-value summary)))
      (is (= 2 (:distance summary)))
      (is (= :close (:distance-category summary)))
      (is (= :advantage (:distance-advantage summary)))
      (is (true? (:in-paint summary))))))

(deftest shooting-position-summary-three-point
  (testing "three-point shot summary"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 7]))
          summary (scoring/shooting-position-summary state f/home-player-1)]
      (is (= :three-point (:zone summary)))
      (is (= 3 (:point-value summary)))
      (is (= 6 (:distance summary)))
      (is (= :long (:distance-category summary)))
      (is (= :disadvantage (:distance-advantage summary)))
      (is (false? (:in-paint summary))))))

(deftest shooting-position-summary-off-court
  (testing "returns nil when player off court"
    (let [state (f/base-game-state)]
      (is (nil? (scoring/shooting-position-summary state f/home-player-1))))))
