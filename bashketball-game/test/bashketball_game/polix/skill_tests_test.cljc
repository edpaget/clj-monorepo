(ns bashketball-game.polix.skill-tests-test
  "Tests for skill test resolution with source-tracked advantage.

  Tests skill test mechanics including:
  - Difficulty calculation
  - Advantage/disadvantage combination
  - Distance-based advantage
  - Size-based advantage
  - Fate reveal selection"
  (:require
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.skill-tests :as skill-tests]
   [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; Difficulty Calculation Tests
;; =============================================================================

(deftest compute-difficulty-test
  (testing "difficulty is 8 minus skill value"
    (is (= 6 (skill-tests/compute-difficulty 2)))
    (is (= 3 (skill-tests/compute-difficulty 5)))
    (is (= 8 (skill-tests/compute-difficulty 0)))
    (is (= 0 (skill-tests/compute-difficulty 8)))))

;; =============================================================================
;; Advantage Value Conversion Tests
;; =============================================================================

(deftest advantage-value-test
  (testing "converts advantage keywords to numeric values"
    (is (= 2 (skill-tests/advantage-value :advantage/DOUBLE_ADVANTAGE)))
    (is (= 1 (skill-tests/advantage-value :advantage/ADVANTAGE)))
    (is (= 0 (skill-tests/advantage-value :advantage/NORMAL)))
    (is (= -1 (skill-tests/advantage-value :advantage/DISADVANTAGE)))
    (is (= -2 (skill-tests/advantage-value :advantage/DOUBLE_DISADVANTAGE)))))

(deftest value-to-advantage-test
  (testing "converts numeric values back to advantage keywords"
    (is (= :advantage/DOUBLE_ADVANTAGE (skill-tests/value->advantage 2)))
    (is (= :advantage/ADVANTAGE (skill-tests/value->advantage 1)))
    (is (= :advantage/NORMAL (skill-tests/value->advantage 0)))
    (is (= :advantage/DISADVANTAGE (skill-tests/value->advantage -1)))
    (is (= :advantage/DOUBLE_DISADVANTAGE (skill-tests/value->advantage -2))))

  (testing "clamps extreme values"
    (is (= :advantage/DOUBLE_ADVANTAGE (skill-tests/value->advantage 5)))
    (is (= :advantage/DOUBLE_DISADVANTAGE (skill-tests/value->advantage -5)))))

;; =============================================================================
;; Advantage Source Combination Tests
;; =============================================================================

(deftest combine-single-source
  (testing "single source determines net level"
    (let [result (skill-tests/combine-advantage-sources
                  [{:source :distance :advantage :advantage/ADVANTAGE}])]
      (is (= :advantage/ADVANTAGE (:net-level result)))
      (is (= 1 (count (:sources result)))))))

(deftest combine-canceling-sources
  (testing "advantage and disadvantage cancel out"
    (let [result (skill-tests/combine-advantage-sources
                  [{:source :distance :advantage :advantage/ADVANTAGE}
                   {:source :zoc :advantage :advantage/DISADVANTAGE}])]
      (is (= :advantage/NORMAL (:net-level result))))))

(deftest combine-stacking-sources
  (testing "multiple advantages stack up to double"
    (let [result (skill-tests/combine-advantage-sources
                  [{:source :distance :advantage :advantage/ADVANTAGE}
                   {:source :uncontested :advantage :advantage/ADVANTAGE}])]
      (is (= :advantage/DOUBLE_ADVANTAGE (:net-level result))))))

(deftest combine-stacking-disadvantages
  (testing "multiple disadvantages stack"
    (let [result (skill-tests/combine-advantage-sources
                  [{:source :zoc :advantage :advantage/DISADVANTAGE}
                   {:source :size :advantage :advantage/DISADVANTAGE}])]
      (is (= :advantage/DOUBLE_DISADVANTAGE (:net-level result))))))

(deftest combine-mixed-sources
  (testing "net advantage from multiple sources"
    (let [result (skill-tests/combine-advantage-sources
                  [{:source :distance :advantage :advantage/ADVANTAGE}
                   {:source :uncontested :advantage :advantage/ADVANTAGE}
                   {:source :size :advantage :advantage/DISADVANTAGE}])]
      (is (= :advantage/ADVANTAGE (:net-level result))))))

;; =============================================================================
;; Distance Advantage Tests
;; =============================================================================

(deftest distance-advantage-close
  (testing "close range (1-2 hexes) gives advantage"
    (is (= :advantage/ADVANTAGE (skill-tests/distance->advantage 1)))
    (is (= :advantage/ADVANTAGE (skill-tests/distance->advantage 2)))))

(deftest distance-advantage-medium
  (testing "medium range (3-4 hexes) is normal"
    (is (= :advantage/NORMAL (skill-tests/distance->advantage 3)))
    (is (= :advantage/NORMAL (skill-tests/distance->advantage 4)))))

(deftest distance-advantage-long
  (testing "long range (5+ hexes) gives disadvantage"
    (is (= :advantage/DISADVANTAGE (skill-tests/distance->advantage 5)))
    (is (= :advantage/DISADVANTAGE (skill-tests/distance->advantage 7)))))

(deftest distance-advantage-source-test
  (testing "creates proper distance advantage source"
    (let [source (skill-tests/distance-advantage-source 2)]
      (is (= :distance (:source source)))
      (is (= 2 (:distance source)))
      (is (= :advantage/ADVANTAGE (:advantage source))))))

;; =============================================================================
;; Size Advantage Tests
;; =============================================================================

(deftest size-advantage-larger-actor
  (testing "larger actor gets advantage"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])   ; LG orc
                    (f/with-player-at f/away-player-2 [2 4]))] ; SM goblin
      (is (= :advantage/ADVANTAGE (skill-tests/size->advantage state f/home-player-1 f/away-player-2))))))

(deftest size-advantage-smaller-actor
  (testing "smaller actor gets disadvantage"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-2 [2 3])   ; SM elf
                    (f/with-player-at f/away-player-1 [2 4]))] ; LG troll
      (is (= :advantage/DISADVANTAGE (skill-tests/size->advantage state f/home-player-2 f/away-player-1))))))

(deftest size-advantage-same-size
  (testing "same size is normal"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-3 [2 3])   ; MD dwarf
                    (f/with-player-at f/away-player-3 [2 4]))] ; MD human
      (is (= :advantage/NORMAL (skill-tests/size->advantage state f/home-player-3 f/away-player-3))))))

(deftest size-advantage-source-test
  (testing "creates proper size advantage source"
    (let [state  (-> (f/base-game-state)
                     (f/with-player-at f/home-player-1 [2 3])
                     (f/with-player-at f/away-player-2 [2 4]))
          source (skill-tests/size-advantage-source state f/home-player-1 f/away-player-2)]
      (is (= :size (:source source)))
      (is (= f/home-player-1 (:actor-id source)))
      (is (= f/away-player-2 (:target-id source)))
      (is (= :advantage/ADVANTAGE (:advantage source))))))

;; =============================================================================
;; Uncontested Advantage Tests
;; =============================================================================

(deftest uncontested-advantage-source-test
  (testing "creates uncontested advantage source"
    (let [source (skill-tests/uncontested-advantage-source)]
      (is (= :uncontested (:source source)))
      (is (= :advantage/ADVANTAGE (:advantage source))))))

;; =============================================================================
;; Fate Reveal Count Tests
;; =============================================================================

(deftest fate-reveal-count-test
  (testing "double advantage reveals 3 cards"
    (is (= 3 (skill-tests/fate-reveal-count :advantage/DOUBLE_ADVANTAGE))))

  (testing "advantage reveals 2 cards"
    (is (= 2 (skill-tests/fate-reveal-count :advantage/ADVANTAGE))))

  (testing "normal reveals 1 card"
    (is (= 1 (skill-tests/fate-reveal-count :advantage/NORMAL))))

  (testing "disadvantage reveals 2 cards"
    (is (= 2 (skill-tests/fate-reveal-count :advantage/DISADVANTAGE))))

  (testing "double disadvantage reveals 3 cards"
    (is (= 3 (skill-tests/fate-reveal-count :advantage/DOUBLE_DISADVANTAGE)))))

;; =============================================================================
;; Fate Selection Mode Tests
;; =============================================================================

(deftest fate-selection-mode-test
  (testing "double advantage picks best"
    (is (= :best (skill-tests/fate-selection-mode :advantage/DOUBLE_ADVANTAGE))))

  (testing "advantage picks best"
    (is (= :best (skill-tests/fate-selection-mode :advantage/ADVANTAGE))))

  (testing "normal uses single"
    (is (= :single (skill-tests/fate-selection-mode :advantage/NORMAL))))

  (testing "disadvantage picks worst"
    (is (= :worst (skill-tests/fate-selection-mode :advantage/DISADVANTAGE))))

  (testing "double disadvantage picks worst"
    (is (= :worst (skill-tests/fate-selection-mode :advantage/DOUBLE_DISADVANTAGE)))))

;; =============================================================================
;; Fate Selection Tests
;; =============================================================================

(deftest select-fate-best
  (testing "best mode selects highest value"
    (is (= 6 (skill-tests/select-fate [3 6 2] :best)))))

(deftest select-fate-worst
  (testing "worst mode selects lowest value"
    (is (= 2 (skill-tests/select-fate [3 6 2] :worst)))))

(deftest select-fate-single
  (testing "single mode uses first value"
    (is (= 4 (skill-tests/select-fate [4] :single)))))

;; =============================================================================
;; Success Tests
;; =============================================================================

(deftest success-test
  (testing "success when fate meets difficulty"
    (is (skill-tests/success? 5 5))
    (is (skill-tests/success? 6 5)))

  (testing "failure when fate below difficulty"
    (is (not (skill-tests/success? 4 5)))))

(deftest success-margin-test
  (testing "margin is fate minus difficulty"
    (is (= 0 (skill-tests/success-margin 5 5)))
    (is (= 2 (skill-tests/success-margin 7 5)))
    (is (= -1 (skill-tests/success-margin 4 5)))))

(deftest success-by-two-plus-test
  (testing "success by 2+ triggers bonus"
    (is (skill-tests/success-by-two-plus? 7 5))
    (is (skill-tests/success-by-two-plus? 8 5)))

  (testing "success by less than 2 doesn't trigger bonus"
    (is (not (skill-tests/success-by-two-plus? 6 5)))
    (is (not (skill-tests/success-by-two-plus? 5 5)))))

;; =============================================================================
;; Shooting Advantage Sources Integration Tests
;; =============================================================================

(deftest shooting-advantage-sources-uncontested
  (testing "uncontested shot gets distance and uncontested advantages"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 11])  ; Close to basket [2 13]
                      (f/with-player-at f/away-player-1 [2 3]))  ; Far away
          sources (skill-tests/shooting-advantage-sources state f/home-player-1 [2 13] :team/AWAY)]
      (is (some #(= :distance (:source %)) sources))
      (is (some #(= :uncontested (:source %)) sources)))))

(deftest shooting-advantage-sources-contested
  (testing "contested shot gets ZoC disadvantages instead of uncontested bonus"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 11])
                      (f/with-player-at f/away-player-1 [2 12]))
          sources (skill-tests/shooting-advantage-sources state f/home-player-1 [2 13] :team/AWAY)]
      (is (some #(= :distance (:source %)) sources))
      (is (not (some #(= :uncontested (:source %)) sources)))
      (is (some #(= :zoc (:source %)) sources)))))

;; =============================================================================
;; Defense Advantage Sources Tests
;; =============================================================================

(deftest defense-advantage-sources-test
  (testing "defense gets size-based advantage"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 8])   ; LG defender
                      (f/with-player-at f/away-player-2 [2 9]))  ; SM ball carrier
          sources (skill-tests/defense-advantage-sources state f/home-player-1 f/away-player-2)]
      (is (= 1 (count sources)))
      (is (= :size (:source (first sources))))
      (is (= :advantage/ADVANTAGE (:advantage (first sources)))))))

;; =============================================================================
;; Tipoff Advantage Sources Tests
;; =============================================================================

(deftest tipoff-advantage-sources-test
  (testing "tipoff compares sizes"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 6])   ; LG orc
                      (f/with-player-at f/away-player-2 [2 7]))  ; SM goblin
          sources (skill-tests/tipoff-advantage-sources state f/home-player-1 f/away-player-2)]
      (is (= 1 (count sources)))
      (is (= :size (:source (first sources))))
      (is (= :advantage/ADVANTAGE (:advantage (first sources)))))))
