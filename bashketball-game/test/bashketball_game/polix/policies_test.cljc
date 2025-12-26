(ns bashketball-game.polix.policies-test
  (:require
   [bashketball-game.polix.operators :as ops]
   [bashketball-game.polix.policies :as policies]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.core :as polix]))

(use-fixtures :once
  (fn [f]
    (ops/register-operators!)
    (f)))

;; =============================================================================
;; Policy Registry Tests
;; =============================================================================

(deftest policy-registry-test
  (testing "action-policies contains expected policies"
    (is (some? (policies/get-policy :bashketball/move-player)))
    (is (some? (policies/get-policy :bashketball/substitute)))
    (is (some? (policies/get-policy :bashketball/play-card)))
    (is (some? (policies/get-policy :bashketball/attach-ability))))

  (testing "unknown action returns nil"
    (is (nil? (policies/get-policy :bashketball/unknown-action)))))

;; =============================================================================
;; Move Player Policy Tests
;; =============================================================================

(defn- eval-policy
  "Evaluates a policy against a document using unify."
  [policy doc]
  (polix/unify policy doc))

(deftest move-player-policy-direct-test
  (testing "satisfied when all conditions met"
    (let [doc {:player-on-court true
               :player-exhausted false
               :valid-position true
               :can-move-to true}]
      (is (polix/satisfied?
           (eval-policy policies/move-player-policy doc)))))

  (testing "conflict when player exhausted"
    (let [doc {:player-on-court true
               :player-exhausted true
               :valid-position true
               :can-move-to true}]
      (is (polix/has-conflicts?
           (eval-policy policies/move-player-policy doc))))))

;; =============================================================================
;; Substitute Policy Tests
;; =============================================================================

(deftest substitute-policy-direct-test
  (testing "satisfied when valid substitution"
    (let [doc {:on-court-on-court true
               :off-court-on-court false
               :on-court-team :team/HOME
               :off-court-team :team/HOME}]
      (is (polix/satisfied?
           (eval-policy policies/substitute-policy doc)))))

  (testing "conflict when cross-team substitution"
    (let [doc {:on-court-on-court true
               :off-court-on-court false
               :on-court-team :team/HOME
               :off-court-team :team/AWAY}]
      (is (polix/has-conflicts?
           (eval-policy policies/substitute-policy doc))))))

;; =============================================================================
;; Exhaust Player Policy Tests
;; =============================================================================

(deftest exhaust-player-policy-direct-test
  (testing "satisfied when player not exhausted"
    (let [doc {:player-on-court true
               :player-exhausted false}]
      (is (polix/satisfied?
           (eval-policy policies/exhaust-player-policy doc)))))

  (testing "conflict when player already exhausted"
    (let [doc {:player-on-court true
               :player-exhausted true}]
      (is (polix/has-conflicts?
           (eval-policy policies/exhaust-player-policy doc))))))

;; =============================================================================
;; Refresh Player Policy Tests
;; =============================================================================

(deftest refresh-player-policy-direct-test
  (testing "satisfied when player is exhausted"
    (let [doc {:player-exhausted true}]
      (is (polix/satisfied?
           (eval-policy policies/refresh-player-policy doc)))))

  (testing "conflict when player not exhausted"
    (let [doc {:player-exhausted false}]
      (is (polix/has-conflicts?
           (eval-policy policies/refresh-player-policy doc))))))
