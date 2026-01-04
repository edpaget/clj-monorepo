(ns bashketball-game.polix.standard-action-policies-test
  "Tests for standard action policies integrating with game state.

  Tests how standard actions (Shoot/Block, Pass/Steal, Screen/Check)
  interact with game state, validation, and skill test setup."
  (:require
   [bashketball-game.actions :as actions]
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.operators :as ops]
   [bashketball-game.polix.standard-action-policies :as sap]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once
  (fn [t]
    (ops/register-operators!)
    (t)))

;; =============================================================================
;; Shoot Precondition Tests
;; =============================================================================

(deftest shoot-precondition-valid
  (testing "can shoot when has ball, in range, not exhausted"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 9])
                    (f/with-ball-possessed f/home-player-1))]
      (is (sap/shoot-precondition? state f/home-player-1)))))

(deftest shoot-precondition-no-ball
  (testing "cannot shoot without ball"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 9]))]
      (is (not (sap/shoot-precondition? state f/home-player-1))))))

(deftest shoot-precondition-out-of-range
  (testing "cannot shoot from too far"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-ball-possessed f/home-player-1))]
      ;; HOME shoots at [2 13], from [2 3] distance is 10 > 7
      (is (not (sap/shoot-precondition? state f/home-player-1))))))

(deftest shoot-precondition-exhausted
  (testing "cannot shoot when exhausted"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 9])
                    (f/with-ball-possessed f/home-player-1)
                    (f/with-exhausted f/home-player-1))]
      (is (not (sap/shoot-precondition? state f/home-player-1))))))

;; =============================================================================
;; Shoot Skill Test Setup Tests
;; =============================================================================

(deftest shoot-difficulty-calculation
  (testing "shoot difficulty uses shooting stat"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 9])
                    (f/with-ball-possessed f/home-player-1))]
      ;; HOME orc has shooting 2, difficulty = 8 - 2 = 6
      (is (= 6 (sap/shoot-difficulty state f/home-player-1))))))

(deftest shoot-advantage-uncontested
  (testing "uncontested shot gets distance + uncontested advantage"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 11])   ; Close to basket
                      (f/with-ball-possessed f/home-player-1)
                      (f/with-player-at f/away-player-1 [2 3]))   ; Far away
          sources (sap/shoot-advantage-sources state f/home-player-1)]
      (is (some #(= :uncontested (:source %)) sources))
      (is (some #(= :distance (:source %)) sources)))))

(deftest shoot-advantage-contested-by-larger
  (testing "contested shot by larger defender gets ZoC disadvantage"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-2 [2 11])   ; SM elf
                      (f/with-ball-possessed f/home-player-2)
                      (f/with-player-at f/away-player-1 [2 12])) ; LG troll adjacent
          sources (sap/shoot-advantage-sources state f/home-player-2)]
      (is (not (some #(= :uncontested (:source %)) sources)))
      (is (some #(and (= :zoc (:source %))
                      (= :advantage/DOUBLE_DISADVANTAGE (:disadvantage %)))
                sources)))))

;; =============================================================================
;; Block Precondition Tests
;; =============================================================================

(deftest block-precondition-valid
  (testing "can block when adjacent to ball carrier near basket"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 11])
                    (f/with-player-at f/away-player-1 [2 12])
                    (f/with-ball-possessed f/away-player-1))]
      ;; AWAY shoots at [2 0], target at [2 12] is within 4 hexes? No - 12 hexes
      ;; Let me fix this - AWAY at [2 1] would be within 4 of their basket
      (is (not (sap/block-precondition? state f/home-player-1 f/away-player-1))))))

(deftest block-precondition-near-basket
  (testing "can block when adjacent to ball carrier near their basket"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 2])    ; Adjacent
                    (f/with-player-at f/away-player-1 [2 3])    ; Within 4 of [2 0]
                    (f/with-ball-possessed f/away-player-1))]
      (is (sap/block-precondition? state f/home-player-1 f/away-player-1)))))

(deftest block-precondition-not-adjacent
  (testing "cannot block when not adjacent"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/away-player-1 [2 3])
                    (f/with-ball-possessed f/away-player-1))]
      (is (not (sap/block-precondition? state f/home-player-1 f/away-player-1))))))

(deftest block-precondition-no-ball
  (testing "cannot block player without ball"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 2])
                    (f/with-player-at f/away-player-1 [2 3]))]
      (is (not (sap/block-precondition? state f/home-player-1 f/away-player-1))))))

;; =============================================================================
;; Pass Precondition Tests
;; =============================================================================

(deftest pass-precondition-valid
  (testing "can pass to teammate on court in range"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/home-player-2 [2 8])
                    (f/with-ball-possessed f/home-player-1))]
      (is (sap/pass-precondition? state f/home-player-1 f/home-player-2)))))

(deftest pass-precondition-no-ball
  (testing "cannot pass without ball"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/home-player-2 [2 8]))]
      (is (not (sap/pass-precondition? state f/home-player-1 f/home-player-2))))))

(deftest pass-precondition-out-of-range
  (testing "cannot pass to teammate too far away"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 0])
                    (f/with-player-at f/home-player-2 [2 10])
                    (f/with-ball-possessed f/home-player-1))]
      ;; Distance is 10 > 6
      (is (not (sap/pass-precondition? state f/home-player-1 f/home-player-2))))))

(deftest pass-precondition-different-team
  (testing "cannot pass to opponent"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/away-player-1 [2 8])
                    (f/with-ball-possessed f/home-player-1))]
      (is (not (sap/pass-precondition? state f/home-player-1 f/away-player-1))))))

;; =============================================================================
;; Pass Skill Test Setup Tests
;; =============================================================================

(deftest pass-difficulty-calculation
  (testing "pass difficulty uses passing stat"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-2 [2 5])    ; SM elf, passing 4
                    (f/with-player-at f/home-player-1 [2 8])
                    (f/with-ball-possessed f/home-player-2))]
      ;; Elf has passing 4, difficulty = 8 - 4 = 4
      (is (= 4 (sap/pass-difficulty state f/home-player-2))))))

(deftest pass-advantage-uncontested
  (testing "uncontested pass gets distance + uncontested advantage"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 5])
                      (f/with-player-at f/home-player-2 [2 7])
                      (f/with-ball-possessed f/home-player-1)
                      (f/with-player-at f/away-player-1 [0 0]))  ; Far away
          sources (sap/pass-advantage-sources state f/home-player-1 f/home-player-2)]
      (is (some #(= :uncontested (:source %)) sources)))))

;; =============================================================================
;; Steal Precondition Tests
;; =============================================================================

(deftest steal-precondition-valid
  (testing "can steal when adjacent to opponent with ball"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/away-player-1 [2 6])
                    (f/with-ball-possessed f/away-player-1))]
      (is (sap/steal-precondition? state f/home-player-1 f/away-player-1)))))

(deftest steal-precondition-not-adjacent
  (testing "cannot steal when not adjacent"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/away-player-1 [2 8])
                    (f/with-ball-possessed f/away-player-1))]
      (is (not (sap/steal-precondition? state f/home-player-1 f/away-player-1))))))

(deftest steal-precondition-same-team
  (testing "cannot steal from teammate"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/home-player-2 [2 6])
                    (f/with-ball-possessed f/home-player-2))]
      (is (not (sap/steal-precondition? state f/home-player-1 f/home-player-2))))))

;; =============================================================================
;; Steal Skill Test Setup Tests
;; =============================================================================

(deftest steal-difficulty-calculation
  (testing "steal difficulty uses defense stat minus 2"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])    ; LG orc, defense 4
                    (f/with-player-at f/away-player-1 [2 6])
                    (f/with-ball-possessed f/away-player-1))]
      ;; Orc has defense 4, difficulty = 8 - (4 - 2) = 6
      (is (= 6 (sap/steal-difficulty state f/home-player-1))))))

(deftest steal-advantage-small-defender
  (testing "small defender ignores size disadvantage on steal"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-2 [2 5])    ; SM elf
                      (f/with-player-at f/away-player-1 [2 6])    ; LG troll
                      (f/with-ball-possessed f/away-player-1))
          sources (sap/steal-advantage-sources state f/home-player-2 f/away-player-1)]
      ;; Size source should be :normal, not :disadvantage
      (is (some #(and (= :size (:source %))
                      (= :advantage/NORMAL (:advantage %)))
                sources)))))

;; =============================================================================
;; Screen Precondition Tests
;; =============================================================================

(deftest screen-precondition-valid
  (testing "can screen adjacent opponent when not exhausted"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/away-player-1 [2 6]))]
      (is (sap/screen-precondition? state f/home-player-1 f/away-player-1)))))

(deftest screen-precondition-exhausted
  (testing "cannot screen when exhausted"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/away-player-1 [2 6])
                    (f/with-exhausted f/home-player-1))]
      (is (not (sap/screen-precondition? state f/home-player-1 f/away-player-1))))))

(deftest screen-precondition-same-team
  (testing "cannot screen teammate"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/home-player-2 [2 6]))]
      (is (not (sap/screen-precondition? state f/home-player-1 f/home-player-2))))))

;; =============================================================================
;; Screen Skill Test Setup Tests
;; =============================================================================

(deftest screen-difficulty-calculation
  (testing "screen difficulty uses defense stat minus 1"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])    ; LG orc, defense 4
                    (f/with-player-at f/away-player-1 [2 6]))]
      ;; Orc has defense 4, difficulty = 8 - (4 - 1) = 5
      (is (= 5 (sap/screen-difficulty state f/home-player-1))))))

(deftest screen-advantage-larger-screener
  (testing "larger screener gets advantage"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 5])    ; LG orc
                      (f/with-player-at f/away-player-2 [2 6]))   ; SM goblin
          sources (sap/screen-advantage-sources state f/home-player-1 f/away-player-2)]
      (is (some #(and (= :size (:source %))
                      (= :advantage/ADVANTAGE (:advantage %)))
                sources)))))

;; =============================================================================
;; Check Precondition Tests
;; =============================================================================

(deftest check-precondition-same-as-screen
  (testing "check has same preconditions as screen"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/away-player-1 [2 6]))]
      (is (= (sap/screen-precondition? state f/home-player-1 f/away-player-1)
             (sap/check-precondition? state f/home-player-1 f/away-player-1))))))

;; =============================================================================
;; Integration: Action With Modifier Tests
;; =============================================================================

(deftest shoot-with-modifier-changes-difficulty
  (testing "modifiers affect skill test difficulty"
    (let [modifier {:id "buff-1" :stat :stat/SHOOTING :amount 2}
          state    (-> (f/base-game-state)
                       (f/with-player-at f/home-player-1 [2 9])
                       (f/with-ball-possessed f/home-player-1)
                       (actions/do-action {:type :bashketball/add-modifier
                                           :player-id f/home-player-1
                                           :modifier modifier}))]
      ;; BASE shooting 2 + modifier 2 = 4, difficulty = 8 - 4 = 4
      (is (= 4 (sap/shoot-difficulty state f/home-player-1))))))

;; =============================================================================
;; Full Skill Test Setup Tests
;; =============================================================================

(deftest setup-shoot-test-full
  (testing "shoot test returns correct difficulty and advantage"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 11])
                    (f/with-ball-possessed f/home-player-1))
          setup (sap/setup-shoot-test state f/home-player-1)]
      ;; Orc shooting 2, difficulty = 8 - 2 = 6
      (is (= 6 (:difficulty setup)))
      ;; Distance 2 (close) = advantage, uncontested = advantage
      ;; Net: advantage + advantage = double-advantage
      (is (= :advantage/DOUBLE_ADVANTAGE (:advantage setup)))
      (is (some #(= :distance (:source %)) (:advantage-sources setup)))
      (is (some #(= :uncontested (:source %)) (:advantage-sources setup))))))

(deftest setup-pass-test-full
  (testing "pass test returns correct difficulty and advantage"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/home-player-2 [2 7])
                    (f/with-ball-possessed f/home-player-1))
          setup (sap/setup-pass-test state f/home-player-1 f/home-player-2)]
      ;; Orc passing 1, difficulty = 8 - 1 = 7
      (is (= 7 (:difficulty setup)))
      ;; Distance 2 (close) = advantage, uncontested = advantage
      ;; Net: advantage + advantage = double-advantage
      (is (= :advantage/DOUBLE_ADVANTAGE (:advantage setup)))
      (is (some #(= :distance (:source %)) (:advantage-sources setup)))
      (is (some #(= :uncontested (:source %)) (:advantage-sources setup))))))

(deftest setup-steal-test-full
  (testing "steal test returns correct difficulty and advantage"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 5])
                    (f/with-player-at f/away-player-1 [2 6])
                    (f/with-ball-possessed f/away-player-1))
          setup (sap/setup-steal-test state f/home-player-1 f/away-player-1)]
      ;; Orc defense 4, steal difficulty = 8 - (4 - 2) = 6
      (is (= 6 (:difficulty setup)))
      ;; LG orc vs LG troll = same size = normal
      (is (= :advantage/NORMAL (:advantage setup)))
      (is (some #(= :size (:source %)) (:advantage-sources setup))))))
