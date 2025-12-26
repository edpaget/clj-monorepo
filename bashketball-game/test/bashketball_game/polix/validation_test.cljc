(ns bashketball-game.polix.validation-test
  (:require
   [bashketball-game.polix.fixtures :as fixtures]
   [bashketball-game.polix.operators :as ops]
   [bashketball-game.polix.validation :as validation]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.core :as polix]))

(use-fixtures :once
  (fn [f]
    (ops/register-operators!)
    (f)))

;; =============================================================================
;; Move Player Validation Tests
;; =============================================================================

(deftest move-player-valid-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3]))]
    (testing "valid move returns satisfied"
      (is (polix/satisfied?
           (validation/validate-action
            state
            {:type :bashketball/move-player
             :player-id fixtures/home-player-1
             :position [2 4]}))))))

(deftest move-player-exhausted-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3])
                  (fixtures/with-exhausted fixtures/home-player-1))]
    (testing "exhausted player returns conflict"
      (is (polix/has-conflicts?
           (validation/validate-action
            state
            {:type :bashketball/move-player
             :player-id fixtures/home-player-1
             :position [2 4]}))))))

(deftest move-player-off-court-test
  (let [state (fixtures/base-game-state)]
    (testing "player not on court returns conflict"
      (is (polix/has-conflicts?
           (validation/validate-action
            state
            {:type :bashketball/move-player
             :player-id fixtures/home-player-1
             :position [2 4]}))))))

(deftest move-player-invalid-position-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3]))]
    (testing "invalid target position returns conflict"
      (is (polix/has-conflicts?
           (validation/validate-action
            state
            {:type :bashketball/move-player
             :player-id fixtures/home-player-1
             :position [10 10]}))))))

;; =============================================================================
;; Substitute Validation Tests
;; =============================================================================

(deftest substitute-valid-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3]))]
    (testing "valid substitution returns satisfied"
      (is (polix/satisfied?
           (validation/validate-action
            state
            {:type :bashketball/substitute
             :on-court-id fixtures/home-player-1
             :off-court-id fixtures/home-player-2}))))))

(deftest substitute-on-court-not-on-court-test
  (let [state (fixtures/base-game-state)]
    (testing "on-court player not actually on court returns conflict"
      (is (polix/has-conflicts?
           (validation/validate-action
            state
            {:type :bashketball/substitute
             :on-court-id fixtures/home-player-1
             :off-court-id fixtures/home-player-2}))))))

(deftest substitute-off-court-on-court-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3])
                  (fixtures/with-player-at fixtures/home-player-2 [3 3]))]
    (testing "off-court player already on court returns conflict"
      (is (polix/has-conflicts?
           (validation/validate-action
            state
            {:type :bashketball/substitute
             :on-court-id fixtures/home-player-1
             :off-court-id fixtures/home-player-2}))))))

;; =============================================================================
;; Play Card Validation Tests
;; =============================================================================

(deftest play-card-valid-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-drawn-cards :team/HOME 3))
        hand  (get-in state [:players :team/HOME :deck :hand])
        card  (first hand)]
    (testing "playing card from hand returns satisfied"
      (is (polix/satisfied?
           (validation/validate-action
            state
            {:type :bashketball/play-card
             :player :team/HOME
             :instance-id (:instance-id card)}))))))

(deftest play-card-not-active-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-drawn-cards :team/AWAY 3))
        hand  (get-in state [:players :team/AWAY :deck :hand])
        card  (first hand)]
    (testing "non-active player playing card returns conflict"
      (is (polix/has-conflicts?
           (validation/validate-action
            state
            {:type :bashketball/play-card
             :player :team/AWAY
             :instance-id (:instance-id card)}))))))

(deftest play-card-not-in-hand-test
  (let [state (fixtures/base-game-state)]
    (testing "card not in hand returns conflict"
      (is (polix/has-conflicts?
           (validation/validate-action
            state
            {:type :bashketball/play-card
             :player :team/HOME
             :instance-id "nonexistent-card"}))))))

;; =============================================================================
;; Give Ball Validation Tests
;; =============================================================================

(deftest give-ball-valid-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3]))]
    (testing "giving ball to player on court returns satisfied"
      (is (polix/satisfied?
           (validation/validate-action
            state
            {:type :bashketball/set-ball-possessed
             :holder-id fixtures/home-player-1}))))))

(deftest give-ball-off-court-test
  (let [state (fixtures/base-game-state)]
    (testing "giving ball to off-court player returns conflict"
      (is (polix/has-conflicts?
           (validation/validate-action
            state
            {:type :bashketball/set-ball-possessed
             :holder-id fixtures/home-player-1}))))))

;; =============================================================================
;; Action Available Tests
;; =============================================================================

(deftest action-available-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3]))]
    (testing "valid action returns true"
      (is (validation/action-available?
           state
           {:type :bashketball/move-player
            :player-id fixtures/home-player-1
            :position [2 4]})))

    (testing "invalid action returns false"
      (is (not (validation/action-available?
                state
                {:type :bashketball/move-player
                 :player-id fixtures/home-player-2
                 :position [2 4]}))))))

;; =============================================================================
;; Action Requirements Tests
;; =============================================================================

(deftest action-requirements-available-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3]))]
    (testing "valid action returns :available status"
      (is (= :available
             (:status (validation/action-requirements
                       state
                       {:type :bashketball/move-player
                        :player-id fixtures/home-player-1
                        :position [2 4]})))))))

(deftest action-requirements-impossible-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3])
                  (fixtures/with-exhausted fixtures/home-player-1))]
    (testing "impossible action returns :impossible status with conflicts"
      (let [result (validation/action-requirements
                    state
                    {:type :bashketball/move-player
                     :player-id fixtures/home-player-1
                     :position [2 4]})]
        (is (= :impossible (:status result)))
        (is (some? (:conflicts result)))))))

;; =============================================================================
;; Available Actions Tests
;; =============================================================================

(deftest available-actions-test
  (let [state (fixtures/base-game-state)]
    (testing "returns set of action types"
      (let [actions (validation/available-actions state :team/HOME)]
        (is (set? actions))
        (is (contains? actions :bashketball/draw-cards))))))

;; =============================================================================
;; No Policy Actions
;; =============================================================================

(deftest no-policy-returns-satisfied-test
  (let [state (fixtures/base-game-state)]
    (testing "action without policy returns satisfied"
      (is (polix/satisfied?
           (validation/validate-action
            state
            {:type :bashketball/set-phase
             :phase :phase/UPKEEP}))))))
