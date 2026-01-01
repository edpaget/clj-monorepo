(ns bashketball-game.polix.functions-test
  "Tests for game-specific function registry."
  (:require
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.fixtures :as fixtures]
   [bashketball-game.polix.functions :as functions]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once
  (fn [f]
    (polix/initialize!)
    (f)))

;;; ---------------------------------------------------------------------------
;;; Registry Tests
;;; ---------------------------------------------------------------------------

(deftest registered-fns-returns-all-keys-test
  (let [fns (functions/registered-fns)]
    (testing "includes movement cost functions"
      (is (contains? fns :bashketball-fn/step-cost))
      (is (contains? fns :bashketball-fn/base-cost))
      (is (contains? fns :bashketball-fn/zoc-cost))
      (is (contains? fns :bashketball-fn/zoc-defender-ids)))
    (testing "includes arithmetic helpers"
      (is (contains? fns :bashketball-fn/add))
      (is (contains? fns :bashketball-fn/max)))))

(deftest get-fn-returns-function-test
  (testing "returns registered function"
    (is (fn? (functions/get-fn :bashketball-fn/step-cost))))
  (testing "returns nil for unknown key"
    (is (nil? (functions/get-fn :bashketball-fn/unknown)))))

;;; ---------------------------------------------------------------------------
;;; Movement Cost Function Tests
;;; ---------------------------------------------------------------------------

(deftest step-cost-no-zoc-test
  (let [game (-> (fixtures/base-game-state)
                 (fixtures/with-player-at fixtures/home-player-1 [2 3]))
        f (functions/get-fn :bashketball-fn/step-cost)
        cost (f game {} fixtures/home-player-1 [2 4])]
    (testing "returns base cost with no defenders"
      (is (= 1 cost)))))

(deftest step-cost-with-zoc-test
  (let [game (-> (fixtures/base-game-state)
                 (fixtures/with-player-at fixtures/home-player-2 [2 3])
                 (fixtures/with-player-at fixtures/away-player-1 [2 4]))
        f (functions/get-fn :bashketball-fn/step-cost)
        cost (f game {} fixtures/home-player-2 [2 4])]
    (testing "includes ZoC cost from larger defender"
      (is (= 3 cost)))))

(deftest base-cost-test
  (let [f (functions/get-fn :bashketball-fn/base-cost)
        cost (f {} {})]
    (testing "always returns 1"
      (is (= 1 cost)))))

(deftest zoc-cost-test
  (let [game (-> (fixtures/base-game-state)
                 (fixtures/with-player-at fixtures/home-player-2 [2 3])
                 (fixtures/with-player-at fixtures/away-player-1 [2 4]))
        f (functions/get-fn :bashketball-fn/zoc-cost)
        cost (f game {} fixtures/home-player-2 [2 4])]
    (testing "returns ZoC penalty from larger defender"
      (is (= 2 cost)))))

(deftest zoc-defender-ids-test
  (let [game (-> (fixtures/base-game-state)
                 (fixtures/with-player-at fixtures/home-player-2 [2 3])
                 (fixtures/with-player-at fixtures/away-player-1 [2 4]))
        f (functions/get-fn :bashketball-fn/zoc-defender-ids)
        defenders (f game {} fixtures/home-player-2 [2 4])]
    (testing "returns defender IDs in ZoC"
      (is (= [fixtures/away-player-1] defenders)))))

;;; ---------------------------------------------------------------------------
;;; Arithmetic Helper Tests
;;; ---------------------------------------------------------------------------

(deftest add-test
  (let [f (functions/get-fn :bashketball-fn/add)]
    (testing "adds numbers"
      (is (= 6 (f {} {} 1 2 3))))
    (testing "handles single number"
      (is (= 5 (f {} {} 5))))
    (testing "handles empty args"
      (is (= 0 (f {} {}))))))

(deftest max-test
  (let [f (functions/get-fn :bashketball-fn/max)]
    (testing "returns max of numbers"
      (is (= 5 (f {} {} 1 5 3))))
    (testing "handles single number"
      (is (= 7 (f {} {} 7))))
    (testing "handles empty args"
      (is (= 0 (f {} {}))))))
