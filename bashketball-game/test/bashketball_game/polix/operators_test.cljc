(ns bashketball-game.polix.operators-test
  (:require
   [bashketball-game.polix.fixtures :as fixtures]
   [bashketball-game.polix.operators :as ops]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.operators :as polix-ops]))

(use-fixtures :once
  (fn [f]
    (ops/register-operators!)
    (f)))

(defn eval-op
  "Helper to call operator eval function with correct signature."
  [op & args]
  (polix-ops/eval op (first args) (second args)))

(deftest hex-distance-operator-test
  (let [op (polix-ops/get-operator :hex-distance)]
    (testing "returns 0 for same position"
      (is (= 0 (eval-op op [0 0] [0 0]))))

    (testing "returns 1 for adjacent positions"
      (is (= 1 (eval-op op [0 0] [1 0])))
      (is (= 1 (eval-op op [0 0] [0 1]))))

    (testing "returns correct distance for distant positions"
      (is (= 3 (eval-op op [0 0] [2 2]))))))

(deftest distance-to-basket-operator-test
  (let [op (polix-ops/get-operator :distance-to-basket)]
    (testing "home team shoots at away basket [2 13]"
      (is (= 6 (eval-op op [2 7] :team/HOME))))

    (testing "away team shoots at home basket [2 0]"
      (is (= 7 (eval-op op [2 7] :team/AWAY))))

    (testing "at own basket is far from target"
      (is (= 13 (eval-op op [2 0] :team/HOME))))))

(deftest valid-position-operator-test
  (let [op (polix-ops/get-operator :valid-position?)]
    (testing "valid positions"
      (is (true? (eval-op op [0 0] nil)))
      (is (true? (eval-op op [4 13] nil)))
      (is (true? (eval-op op [2 7] nil))))

    (testing "invalid positions"
      (is (false? (eval-op op [-1 0] nil)))
      (is (false? (eval-op op [5 0] nil)))
      (is (false? (eval-op op [0 14] nil))))))

(deftest player-exhausted-operator-test
  (let [op    (polix-ops/get-operator :player-exhausted?)
        state (-> (fixtures/base-game-state)
                  (fixtures/with-exhausted fixtures/home-player-1))]
    (testing "returns true for exhausted player"
      (is (true? (eval-op op state fixtures/home-player-1))))

    (testing "returns false for non-exhausted player"
      (is (false? (eval-op op state fixtures/home-player-2))))))

(deftest player-on-court-operator-test
  (let [op    (polix-ops/get-operator :player-on-court?)
        state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3]))]
    (testing "returns true for player on court"
      (is (true? (eval-op op state fixtures/home-player-1))))

    (testing "returns false for player off court"
      (is (false? (eval-op op state fixtures/home-player-2))))))

(deftest has-ball-operator-test
  (let [op    (polix-ops/get-operator :has-ball?)
        state (-> (fixtures/base-game-state)
                  (fixtures/with-ball-possessed fixtures/home-player-1))]
    (testing "returns true for player with ball"
      (is (true? (eval-op op state fixtures/home-player-1))))

    (testing "returns false for player without ball"
      (is (false? (eval-op op state fixtures/home-player-2))))))

(deftest player-team-operator-test
  (let [op    (polix-ops/get-operator :player-team)
        state (fixtures/base-game-state)]
    (testing "returns team for home player"
      (is (= :team/HOME (eval-op op state fixtures/home-player-1))))

    (testing "returns team for away player"
      (is (= :team/AWAY (eval-op op state fixtures/away-player-1))))))

(deftest count-on-court-operator-test
  (let [op    (polix-ops/get-operator :count-on-court)
        state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3])
                  (fixtures/with-player-at fixtures/home-player-2 [3 3]))]
    (testing "counts home players on court"
      (is (= 2 (eval-op op state :team/HOME))))

    (testing "counts away players on court"
      (is (= 0 (eval-op op state :team/AWAY))))))

(deftest path-clear-operator-test
  (testing "path is clear with no obstacles"
    (let [state (fixtures/base-game-state)]
      (is (true? (ops/path-clear? state [2 3] [2 7])))))

  (testing "path is blocked by player"
    (let [state (-> (fixtures/base-game-state)
                    (fixtures/with-player-at fixtures/home-player-1 [2 5]))]
      (is (false? (ops/path-clear? state [2 3] [2 7]))))))

(deftest can-move-to-operator-test
  (testing "returns true for reachable position"
    (let [state (-> (fixtures/base-game-state)
                    (fixtures/with-player-at fixtures/home-player-1 [2 3]))]
      (is (true? (ops/can-move-to? state fixtures/home-player-1 [2 4])))))

  (testing "returns false for position beyond movement range"
    (let [state (-> (fixtures/base-game-state)
                    (fixtures/with-player-at fixtures/home-player-1 [2 3]))]
      (is (false? (ops/can-move-to? state fixtures/home-player-1 [2 10]))))))

(deftest convenience-functions-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3])
                  (fixtures/with-exhausted fixtures/home-player-1)
                  (fixtures/with-ball-possessed fixtures/home-player-1))]
    (testing "hex-distance"
      (is (= 3 (ops/hex-distance [0 0] [2 2]))))

    (testing "player-exhausted?"
      (is (true? (ops/player-exhausted? state fixtures/home-player-1)))
      (is (false? (ops/player-exhausted? state fixtures/home-player-2))))

    (testing "player-on-court?"
      (is (true? (ops/player-on-court? state fixtures/home-player-1)))
      (is (false? (ops/player-on-court? state fixtures/home-player-2))))

    (testing "has-ball?"
      (is (true? (ops/has-ball? state fixtures/home-player-1)))
      (is (false? (ops/has-ball? state fixtures/home-player-2))))))
