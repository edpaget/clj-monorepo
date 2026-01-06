(ns bashketball-game.polix.policy-integration-test
  "Integration tests showing policies interacting with action application.

  These tests demonstrate end-to-end scenarios where:
  - Actions are validated against policies
  - ZoC affects movement and action availability
  - Skill tests use source-tracked advantages
  - Scoring zones affect point values"
  (:require
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.operators :as ops]
   [bashketball-game.polix.scoring :as scoring]
   [bashketball-game.polix.skill-tests :as skill]
   [bashketball-game.polix.standard-action-policies :as sap]
   [bashketball-game.polix.targeting :as targeting]
   [bashketball-game.polix.validation :as validation]
   [bashketball-game.polix.zoc :as zoc]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.core :as polix]
   [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [t]
    (ops/register-operators!)
    (t)))

;; =============================================================================
;; Scenario: Move Action with ZoC Effects
;; =============================================================================

(deftest move-blocked-by-exhaustion-policy
  (testing "exhausted player cannot move - policy prevents action"
    (let [state      (-> (f/base-game-state)
                         (f/with-player-at f/home-player-1 [2 3])
                         (f/with-exhausted f/home-player-1))
          action     {:type :bashketball/move-player
                      :player-id f/home-player-1
                      :position [2 4]}
          validation (validation/validate-action state action)]
      (is (polix/has-conflicts? validation))
      (is (not (validation/action-available? state action))))))

(deftest move-allowed-when-unexhausted
  (testing "unexhausted player can move - policy allows action"
    (let [state      (-> (f/base-game-state)
                         (f/with-player-at f/home-player-1 [2 3]))
          action     {:type :bashketball/move-player
                      :player-id f/home-player-1
                      :position [2 4]}
          validation (validation/validate-action state action)]
      (is (polix/satisfied? validation))
      (is (validation/action-available? state action)))))

(deftest move-through-zoc-costs-extra
  (testing "moving through opponent ZoC incurs movement cost"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-2 [2 5])    ; SM elf mover
                    (f/with-player-at f/away-player-1 [2 6]))]  ; LG troll defender
      ;; LG defender, SM mover = +2 movement cost
      (is (= 2 (zoc/zoc-movement-cost state f/home-player-2 f/away-player-1))))))

;; =============================================================================
;; Scenario: Shoot Action with Full Policy Chain
;; =============================================================================

(deftest shoot-action-full-policy-chain
  (testing "shoot action goes through targeting -> validation -> skill setup"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 10])
                    (f/with-ball-possessed f/home-player-1))]

      (testing "targeting shows shoot is available"
        (let [availability (targeting/categorize-shoot-availability state f/home-player-1)]
          (is (:available availability))))

      (testing "precondition check passes"
        (is (sap/shoot-precondition? state f/home-player-1)))

      (testing "skill test setup has correct difficulty"
        ;; Orc has shooting 2, difficulty = 8 - 2 = 6
        (is (= 6 (sap/shoot-difficulty state f/home-player-1))))

      (testing "scoring zone returns correct point value"
        ;; Position [2 10] is in two-point zone
        (is (= :two-point (scoring/scoring-zone [2 10])))
        (is (= 2 (scoring/point-value [2 10])))))))

(deftest shoot-contested-vs-uncontested
  (testing "contested shot gets ZoC disadvantage, uncontested gets bonus"
    (let [uncontested-state   (-> (f/base-game-state)
                                  (f/with-player-at f/home-player-1 [2 10])
                                  (f/with-ball-possessed f/home-player-1)
                                  (f/with-player-at f/away-player-1 [2 3]))
          contested-state     (-> (f/base-game-state)
                                  (f/with-player-at f/home-player-1 [2 10])
                                  (f/with-ball-possessed f/home-player-1)
                                  (f/with-player-at f/away-player-1 [2 11]))
          uncontested-sources (sap/shoot-advantage-sources uncontested-state f/home-player-1)
          contested-sources   (sap/shoot-advantage-sources contested-state f/home-player-1)]

      (is (some #(= :uncontested (:source %)) uncontested-sources))
      (is (not (some #(= :uncontested (:source %)) contested-sources)))
      (is (some #(= :zoc (:source %)) contested-sources)))))

;; =============================================================================
;; Scenario: Pass Action with ZoC Interception Risk
;; =============================================================================

(deftest pass-through-zoc-loses-advantage
  (testing "pass through defender ZoC loses uncontested bonus"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 3])
                      (f/with-player-at f/home-player-2 [2 9])
                      (f/with-ball-possessed f/home-player-1)
                      (f/with-player-at f/away-player-1 [2 6]))   ; In pass path
          sources (sap/pass-advantage-sources state f/home-player-1 f/home-player-2)]

      (is (zoc/pass-path-contested? state f/home-player-1 [2 9] :team/AWAY))
      (is (not (some #(= :uncontested (:source %)) sources))))))

(deftest pass-clear-path-gets-advantage
  (testing "pass with clear path gets uncontested bonus"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 3])
                      (f/with-player-at f/home-player-2 [2 5])    ; Short pass
                      (f/with-ball-possessed f/home-player-1)
                      (f/with-player-at f/away-player-1 [0 0]))   ; Far away
          sources (sap/pass-advantage-sources state f/home-player-1 f/home-player-2)]

      (is (not (zoc/pass-path-contested? state f/home-player-1 [2 5] :team/AWAY)))
      (is (some #(= :uncontested (:source %)) sources)))))

;; =============================================================================
;; Scenario: Block Action Requires Proximity to Basket
;; =============================================================================

(deftest block-requires-target-near-basket
  (testing "can only block when target is within 4 hexes of their basket"
    (let [near-basket-state     (-> (f/base-game-state)
                                    (f/with-player-at f/home-player-1 [2 2])
                                    (f/with-player-at f/away-player-1 [2 3])  ; 3 hexes from [2 0]
                                    (f/with-ball-possessed f/away-player-1))
          far-from-basket-state (-> (f/base-game-state)
                                    (f/with-player-at f/home-player-1 [2 6])
                                    (f/with-player-at f/away-player-1 [2 7])  ; 7 hexes from [2 0]
                                    (f/with-ball-possessed f/away-player-1))]

      (is (sap/block-precondition? near-basket-state f/home-player-1 f/away-player-1))
      (is (not (sap/block-precondition? far-from-basket-state f/home-player-1 f/away-player-1))))))

;; =============================================================================
;; Scenario: Size Affects Skill Test Advantage
;; =============================================================================

(deftest size-affects-defensive-actions
  (testing "larger defender gets advantage on screen/check"
    (let [state    (-> (f/base-game-state)
                       (f/with-player-at f/home-player-1 [2 5])    ; LG orc
                       (f/with-player-at f/away-player-2 [2 6]))   ; SM goblin
          sources  (sap/screen-advantage-sources state f/home-player-1 f/away-player-2)
          combined (skill/combine-advantage-sources sources)]
      (is (= :advantage/ADVANTAGE (:net-level combined)))))

  (testing "smaller defender gets disadvantage"
    (let [state    (-> (f/base-game-state)
                       (f/with-player-at f/home-player-2 [2 5])    ; SM elf
                       (f/with-player-at f/away-player-1 [2 6]))   ; LG troll
          sources  (sap/screen-advantage-sources state f/home-player-2 f/away-player-1)
          combined (skill/combine-advantage-sources sources)]
      (is (= :advantage/DISADVANTAGE (:net-level combined))))))

;; =============================================================================
;; Scenario: Modifiers Affect Skill Test Difficulty
;; =============================================================================

(deftest modifiers-affect-effective-stats
  (testing "stat modifiers change skill test difficulty"
    (let [base-state   (-> (f/base-game-state)
                           (f/with-player-at f/home-player-1 [2 10])
                           (f/with-ball-possessed f/home-player-1))
          buffed-state (:state (fx/apply-effect base-state
                                                {:type :bashketball/do-add-modifier
                                                 :player-id f/home-player-1
                                                 :id "buff-1"
                                                 :stat :stat/SHOOTING
                                                 :amount 2}
                                                {} {}))]
      ;; Base: shooting 2, difficulty 6
      ;; Buffed: shooting 4, difficulty 4
      (is (= 6 (sap/shoot-difficulty base-state f/home-player-1)))
      (is (= 4 (sap/shoot-difficulty buffed-state f/home-player-1))))))

(deftest modifier-removal-restores-difficulty
  (testing "removing modifier restores original difficulty"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 10])
                    (f/with-ball-possessed f/home-player-1)
                    (as-> s
                          (:state (fx/apply-effect s
                                                   {:type :bashketball/do-add-modifier
                                                    :player-id f/home-player-1
                                                    :id "buff-1"
                                                    :stat :stat/SHOOTING
                                                    :amount 2}
                                                   {} {})))
                    (as-> s
                          (:state (fx/apply-effect s
                                                   {:type :bashketball/do-remove-modifier
                                                    :player-id f/home-player-1
                                                    :modifier-id "buff-1"}
                                                   {} {}))))]
      (is (= 6 (sap/shoot-difficulty state f/home-player-1))))))

;; =============================================================================
;; Scenario: Advantage Sources Combine Correctly
;; =============================================================================

(deftest advantage-sources-combine-for-net-level
  (testing "multiple sources combine to net advantage level"
    (let [sources  [{:source :distance :advantage :advantage/ADVANTAGE}      ; +1
                    {:source :uncontested :advantage :advantage/ADVANTAGE}   ; +1
                    {:source :size :advantage :advantage/DISADVANTAGE}]      ; -1
          combined (skill/combine-advantage-sources sources)]
      ;; Net: +1 = advantage
      (is (= :advantage/ADVANTAGE (:net-level combined)))))

  (testing "capped at double-advantage/double-disadvantage"
    (let [sources  [{:source :a :advantage :advantage/ADVANTAGE}
                    {:source :b :advantage :advantage/ADVANTAGE}
                    {:source :c :advantage :advantage/ADVANTAGE}]
          combined (skill/combine-advantage-sources sources)]
      (is (= :advantage/DOUBLE_ADVANTAGE (:net-level combined))))))

;; =============================================================================
;; Scenario: Fate Selection Based on Advantage
;; =============================================================================

(deftest fate-selection-with-advantage
  (testing "advantage picks best of 2 fate values"
    (is (= 2 (skill/fate-reveal-count :advantage/ADVANTAGE)))
    (is (= :best (skill/fate-selection-mode :advantage/ADVANTAGE)))
    (is (= 6 (skill/select-fate [3 6] :best))))

  (testing "disadvantage picks worst of 2 fate values"
    (is (= 2 (skill/fate-reveal-count :advantage/DISADVANTAGE)))
    (is (= :worst (skill/fate-selection-mode :advantage/DISADVANTAGE)))
    (is (= 3 (skill/select-fate [3 6] :worst)))))

;; =============================================================================
;; Scenario: Scoring Zone Affects Point Value
;; =============================================================================

(deftest scoring-from-different-zones
  (testing "two-point zone shots worth 2 points"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 11]))  ; Near basket
          summary (scoring/shooting-position-summary state f/home-player-1)]
      (is (= :two-point (:zone summary)))
      (is (= 2 (:point-value summary)))))

  (testing "three-point zone shots worth 3 points"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 6]))   ; Mid-court
          summary (scoring/shooting-position-summary state f/home-player-1)]
      (is (= :three-point (:zone summary)))
      (is (= 3 (:point-value summary))))))

;; =============================================================================
;; Scenario: Add Score Action Updates Game State
;; =============================================================================

(deftest add-score-after-successful-shot
  (testing "scoring action updates team score"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 11])
                      (f/with-ball-possessed f/home-player-1))
          points  (scoring/point-value [2 11])
          updated (:state (fx/apply-effect state {:type :bashketball/do-add-score
                                                  :team :team/HOME
                                                  :points points}
                                           {} {}))]
      (is (= 2 (get-in (state/get-score updated) [:team/HOME]))))))

;; =============================================================================
;; Scenario: Small Defender Steal Advantage
;; =============================================================================

(deftest small-defender-ignores-size-disadvantage-on-steal
  (testing "small defenders are agile - ignore size disadvantage on steals"
    (let [state    (-> (f/base-game-state)
                       (f/with-player-at f/home-player-2 [2 5])    ; SM elf
                       (f/with-player-at f/away-player-1 [2 6])    ; LG troll
                       (f/with-ball-possessed f/away-player-1))
          sources  (sap/steal-advantage-sources state f/home-player-2 f/away-player-1)
          combined (skill/combine-advantage-sources sources)]
      ;; Small defender should have :normal instead of :disadvantage
      (is (= :advantage/NORMAL (:net-level combined))))))

;; =============================================================================
;; Scenario: Targeting API Provides UI Information
;; =============================================================================

(deftest targeting-provides-valid-targets
  (testing "targeting API returns valid move positions"
    (let [state        (-> (f/base-game-state)
                           (f/with-player-at f/home-player-1 [2 5]))
          move-targets (targeting/categorize-move-targets state f/home-player-1)
          positions    (:valid-positions move-targets)]
      (is (not (:blocked move-targets)))
      ;; Orc center has speed 2, should reach adjacent hexes
      (is (contains? positions [2 4]))   ; direct neighbor
      (is (contains? positions [2 6]))   ; direct neighbor
      (is (contains? positions [1 5]))   ; direct neighbor
      (is (contains? positions [3 5]))   ; direct neighbor
      ;; Should NOT contain current position
      (is (not (contains? positions [2 5]))))))

(deftest targeting-provides-pass-target-reasons
  (testing "targeting API explains invalid pass targets"
    (let [state        (-> (f/base-game-state)
                           (f/with-player-at f/home-player-1 [2 5])
                           (f/with-ball-possessed f/home-player-1))
          pass-targets (targeting/categorize-pass-targets state :team/HOME f/home-player-1)]
      ;; Ball holder should be invalid
      (is (= :invalid (get-in pass-targets [f/home-player-1 :status])))
      (is (= :is-ball-holder (get-in pass-targets [f/home-player-1 :reason])))
      ;; Off-court player should be invalid
      (is (= :invalid (get-in pass-targets [f/home-player-2 :status])))
      (is (= :off-court (get-in pass-targets [f/home-player-2 :reason]))))))

(deftest targeting-provides-block-target-status
  (testing "targeting API categorizes block targets"
    (let [state         (-> (f/base-game-state)
                            (f/with-player-at f/home-player-1 [2 2])
                            (f/with-player-at f/away-player-1 [2 3])
                            (f/with-ball-possessed f/away-player-1))
          block-targets (targeting/categorize-block-targets state f/home-player-1)]
      (is (= :valid (get-in block-targets [f/away-player-1 :status]))))))
