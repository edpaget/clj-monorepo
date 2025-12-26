(ns bashketball-game.polix.targeting-test
  "Tests for the targeting API.

  Tests target categorization for move and pass actions."
  (:require
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.targeting :as targeting]
   [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; Move Target Tests
;; =============================================================================

(deftest categorize-move-targets-player-not-found
  (testing "returns blocked when player not found"
    (let [state (f/base-game-state)
          result (targeting/categorize-move-targets state "nonexistent")]
      (is (:blocked result))
      (is (= :player-not-found (:reason result))))))

(deftest categorize-move-targets-player-off-court
  (testing "returns blocked when player is off court"
    (let [state (f/base-game-state)
          result (targeting/categorize-move-targets state f/home-player-1)]
      (is (:blocked result))
      (is (= :player-off-court (:reason result))))))

(deftest categorize-move-targets-player-exhausted
  (testing "returns blocked when player is exhausted"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-exhausted f/home-player-1))
          result (targeting/categorize-move-targets state f/home-player-1)]
      (is (:blocked result))
      (is (= :player-exhausted (:reason result))))))

(deftest categorize-move-targets-valid-positions
  (testing "returns valid positions when player can move"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3]))
          result (targeting/categorize-move-targets state f/home-player-1)]
      (is (not (:blocked result)))
      (is (set? (:valid-positions result)))
      (is (pos? (count (:valid-positions result)))))))

;; =============================================================================
;; Pass Target Tests
;; =============================================================================

(deftest categorize-pass-targets-ball-holder-invalid
  (testing "ball holder cannot be a pass target"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-player-at f/home-player-2 [2 4])
                    (f/with-ball-possessed f/home-player-1))
          result (targeting/categorize-pass-targets state :team/HOME f/home-player-1)]
      (is (= :invalid (get-in result [f/home-player-1 :status])))
      (is (= :is-ball-holder (get-in result [f/home-player-1 :reason]))))))

(deftest categorize-pass-targets-off-court-invalid
  (testing "off-court players cannot be pass targets"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-ball-possessed f/home-player-1))
          result (targeting/categorize-pass-targets state :team/HOME f/home-player-1)]
      (is (= :invalid (get-in result [f/home-player-2 :status])))
      (is (= :off-court (get-in result [f/home-player-2 :reason]))))))

(deftest categorize-pass-targets-on-court-valid
  (testing "on-court teammates are valid pass targets"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-player-at f/home-player-2 [2 4])
                    (f/with-ball-possessed f/home-player-1))
          result (targeting/categorize-pass-targets state :team/HOME f/home-player-1)]
      (is (= :valid (get-in result [f/home-player-2 :status]))))))

;; =============================================================================
;; Convenience Function Tests
;; =============================================================================

(deftest get-valid-pass-targets-test
  (testing "returns set of valid pass target IDs"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-player-at f/home-player-2 [2 4])
                    (f/with-ball-possessed f/home-player-1))
          result (targeting/get-valid-pass-targets state :team/HOME f/home-player-1)]
      (is (set? result))
      (is (contains? result f/home-player-2))
      (is (not (contains? result f/home-player-1))))))

(deftest get-invalid-pass-targets-test
  (testing "returns map of invalid pass targets with reasons"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-ball-possessed f/home-player-1))
          result (targeting/get-invalid-pass-targets state :team/HOME f/home-player-1)]
      (is (map? result))
      (is (contains? result f/home-player-1))
      (is (= :is-ball-holder (get-in result [f/home-player-1 :reason]))))))

;; =============================================================================
;; Action Status Tests
;; =============================================================================

(deftest get-action-status-available
  (testing "returns available when action can be performed"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3]))
          result (targeting/get-action-status
                  state
                  {:type :bashketball/move-player
                   :player-id f/home-player-1
                   :position [2 4]})]
      (is (:available result)))))

(deftest get-action-status-unavailable
  (testing "returns unavailable with explanation when action cannot be performed"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-exhausted f/home-player-1))
          result (targeting/get-action-status
                  state
                  {:type :bashketball/move-player
                   :player-id f/home-player-1
                   :position [2 4]})]
      (is (not (:available result)))
      (is (vector? (:explanation result))))))
