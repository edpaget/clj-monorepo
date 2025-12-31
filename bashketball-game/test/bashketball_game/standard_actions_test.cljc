(ns bashketball-game.standard-actions-test
  "Tests for standard action card definitions and execution."
  (:require
   [bashketball-game.effect-catalog :as catalog]
   [bashketball-game.polix.fixtures :as fixtures]
   [bashketball-game.polix.targeting :as targeting]
   [bashketball-game.standard-actions :as actions]
   [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; Card Definition Tests
;; =============================================================================

(deftest standard-action-cards-structure-test
  (testing "all cards have required base fields"
    (doseq [card actions/standard-action-cards]
      (is (string? (:slug card)))
      (is (string? (:name card)))
      (is (= "standard" (:set-slug card)))
      (is (= :card-type/STANDARD_ACTION_CARD (:card-type card)))
      (is (integer? (:fate card)))))

  (testing "all cards have structured offense mode"
    (doseq [card actions/standard-action-cards]
      (let [offense (:offense card)]
        (is (map? offense) (str "Missing offense for " (:slug card)))
        (is (string? (:action/id offense)))
        (is (some? (:action/effect offense))))))

  (testing "all cards have structured defense mode"
    (doseq [card actions/standard-action-cards]
      (let [defense (:defense card)]
        (is (map? defense) (str "Missing defense for " (:slug card)))
        (is (string? (:action/id defense)))
        (is (some? (:action/effect defense)))))))

(deftest shoot-block-card-test
  (let [card (actions/get-standard-action "shoot-block")]
    (testing "shoot offense mode"
      (let [offense (:offense card)]
        (is (= "shoot" (:action/id offense)))
        (is (= "Shoot" (:action/name offense)))
        (is (vector? (:action/requires offense)))
        (is (= [:actor/id] (:action/targets offense)))
        (is (= :polix.effects/sequence (get-in offense [:action/effect :effect/type])))))

    (testing "block defense mode"
      (let [defense (:defense card)]
        (is (= "block" (:action/id defense)))
        (is (= "Block" (:action/name defense)))
        (is (vector? (:action/requires defense)))
        (is (= [:actor/id :target/id] (:action/targets defense)))
        (is (= :bashketball/force-choice (get-in defense [:action/effect :effect/type])))))))

(deftest pass-steal-card-test
  (let [card (actions/get-standard-action "pass-steal")]
    (testing "pass offense mode"
      (let [offense (:offense card)]
        (is (= "pass" (:action/id offense)))
        (is (= [:actor/id :target/id] (:action/targets offense)))
        (is (= :bashketball/initiate-skill-test (get-in offense [:action/effect :effect/type])))))

    (testing "steal defense mode"
      (let [defense (:defense card)]
        (is (= "steal" (:action/id defense)))
        (is (= [:actor/id :target/id] (:action/targets defense)))
        (is (= :bashketball/initiate-skill-test (get-in defense [:action/effect :effect/type])))))))

(deftest screen-check-card-test
  (let [card (actions/get-standard-action "screen-check")]
    (testing "screen offense mode"
      (let [offense (:offense card)]
        (is (= "screen" (:action/id offense)))
        (is (= [:actor/id :target/id] (:action/targets offense)))
        (is (= :polix.effects/sequence (get-in offense [:action/effect :effect/type])))))

    (testing "check defense mode"
      (let [defense (:defense card)]
        (is (= "check" (:action/id defense)))
        (is (= [:actor/id :target/id] (:action/targets defense)))
        (is (= :bashketball/initiate-skill-test (get-in defense [:action/effect :effect/type])))))))

;; =============================================================================
;; Catalog Integration Tests
;; =============================================================================

(deftest standard-action-catalog-test
  (let [cat (catalog/create-catalog-from-seq actions/standard-action-cards)]
    (testing "catalog loads all standard actions"
      (is (some? (catalog/get-card cat "shoot-block")))
      (is (some? (catalog/get-card cat "pass-steal")))
      (is (some? (catalog/get-card cat "screen-check"))))

    (testing "get-offense returns structured mode"
      (let [offense (catalog/get-offense cat "shoot-block")]
        (is (= "shoot" (:action/id offense)))))

    (testing "get-defense returns structured mode"
      (let [defense (catalog/get-defense cat "shoot-block")]
        (is (= "block" (:action/id defense)))))))

;; =============================================================================
;; Targeting Tests
;; =============================================================================

(deftest categorize-shoot-availability-test
  (testing "shoot requires ball possession"
    (let [state (-> (fixtures/base-game-state)
                    (fixtures/with-player-at fixtures/home-player-1 [2 7]))]
      (is (= :not-ball-carrier
             (:reason (targeting/categorize-shoot-availability state fixtures/home-player-1))))))

  (testing "shoot requires being within 7 hexes of basket"
    (let [state (-> (fixtures/base-game-state)
                    (fixtures/with-player-at fixtures/home-player-1 [2 3])
                    (fixtures/with-ball-possessed fixtures/home-player-1))]
      ;; HOME shoots at [2 13], from [2 3] distance is 10 - too far
      (is (= :out-of-range
             (:reason (targeting/categorize-shoot-availability state fixtures/home-player-1))))))

  (testing "shoot is available when requirements met"
    (let [state (-> (fixtures/base-game-state)
                    (fixtures/with-player-at fixtures/home-player-1 [2 8])
                    (fixtures/with-ball-possessed fixtures/home-player-1))]
      ;; HOME shoots at [2 13], from [2 8] distance is 5 - valid
      (is (true? (:available (targeting/categorize-shoot-availability state fixtures/home-player-1)))))))

(deftest categorize-block-targets-test
  (testing "block requires adjacency to ball carrier"
    (let [state   (-> (fixtures/base-game-state)
                      (fixtures/with-player-at fixtures/home-player-1 [2 3])
                      (fixtures/with-player-at fixtures/away-player-1 [2 10])
                      (fixtures/with-ball-possessed fixtures/away-player-1))
          targets (targeting/categorize-block-targets state fixtures/home-player-1)]
      (is (= :not-adjacent (:reason (get targets fixtures/away-player-1))))))

  (testing "block requires target to have ball"
    (let [state   (-> (fixtures/base-game-state)
                      (fixtures/with-player-at fixtures/home-player-1 [2 3])
                      (fixtures/with-player-at fixtures/away-player-1 [2 4]))
          targets (targeting/categorize-block-targets state fixtures/home-player-1)]
      (is (= :not-ball-carrier (:reason (get targets fixtures/away-player-1)))))))

(deftest categorize-screen-targets-test
  (testing "screen requires adjacency"
    (let [state   (-> (fixtures/base-game-state)
                      (fixtures/with-player-at fixtures/home-player-1 [2 3])
                      (fixtures/with-player-at fixtures/away-player-1 [2 10]))
          targets (targeting/categorize-screen-targets state fixtures/home-player-1)]
      (is (= :not-adjacent (:reason (get targets fixtures/away-player-1))))))

  (testing "screen is valid when adjacent"
    (let [state   (-> (fixtures/base-game-state)
                      (fixtures/with-player-at fixtures/home-player-1 [2 3])
                      (fixtures/with-player-at fixtures/away-player-1 [2 4]))
          targets (targeting/categorize-screen-targets state fixtures/home-player-1)]
      (is (= :valid (:status (get targets fixtures/away-player-1))))))

  (testing "screen blocked when actor exhausted"
    (let [state   (-> (fixtures/base-game-state)
                      (fixtures/with-player-at fixtures/home-player-1 [2 3])
                      (fixtures/with-player-at fixtures/away-player-1 [2 4])
                      (fixtures/with-exhausted fixtures/home-player-1))
          targets (targeting/categorize-screen-targets state fixtures/home-player-1)]
      (is (= :actor-exhausted (:reason (get targets fixtures/away-player-1)))))))

(deftest get-valid-standard-action-targets-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 8])
                  (fixtures/with-player-at fixtures/away-player-1 [2 9])
                  (fixtures/with-ball-possessed fixtures/home-player-1))]
    (testing "shoot returns :self when available"
      (is (contains? (targeting/get-valid-standard-action-targets state fixtures/home-player-1 :shoot)
                     :self)))

    (testing "screen returns adjacent opponents"
      (let [targets (targeting/get-valid-standard-action-targets state fixtures/home-player-1 :screen)]
        (is (contains? targets fixtures/away-player-1))))))
