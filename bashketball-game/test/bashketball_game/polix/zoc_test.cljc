(ns bashketball-game.polix.zoc-test
  "Tests for Zone of Control policies.

  Tests ZoC mechanics including:
  - ZoC exertion by unexhausted players
  - Movement costs through opponent ZoC
  - Shooting disadvantage when in ZoC
  - Pass path interception"
  (:require
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.zoc :as zoc]
   [clojure.test :refer [deftest is testing]]))

;; =============================================================================
;; ZoC Exertion Tests
;; =============================================================================

(deftest exerts-zoc-on-court-unexhausted
  (testing "player on court and not exhausted exerts ZoC"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3]))]
      (is (zoc/exerts-zoc? state f/home-player-1)))))

(deftest exerts-zoc-exhausted-false
  (testing "exhausted player does not exert ZoC"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-exhausted f/home-player-1))]
      (is (not (zoc/exerts-zoc? state f/home-player-1))))))

(deftest exerts-zoc-off-court-false
  (testing "player off court does not exert ZoC"
    (let [state (f/base-game-state)]
      (is (not (zoc/exerts-zoc? state f/home-player-1))))))

;; =============================================================================
;; Position in ZoC Tests
;; =============================================================================

(deftest in-zoc-adjacent-position
  (testing "position adjacent to defender is in ZoC"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3]))]
      (is (zoc/in-zoc? state [2 4] f/home-player-1))
      (is (zoc/in-zoc? state [3 3] f/home-player-1)))))

(deftest in-zoc-same-position
  (testing "same position as defender is in ZoC"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3]))]
      (is (zoc/in-zoc? state [2 3] f/home-player-1)))))

(deftest in-zoc-far-position
  (testing "position far from defender is not in ZoC"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3]))]
      (is (not (zoc/in-zoc? state [2 6] f/home-player-1))))))

(deftest in-zoc-exhausted-defender
  (testing "exhausted defender does not project ZoC"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-exhausted f/home-player-1))]
      (is (not (zoc/in-zoc? state [2 4] f/home-player-1))))))

;; =============================================================================
;; ZoC Defenders Tests
;; =============================================================================

(deftest zoc-defenders-multiple
  (testing "returns all unexhausted defenders in range"
    (let [state     (-> (f/base-game-state)
                        (f/with-player-at f/away-player-1 [2 4])
                        (f/with-player-at f/away-player-2 [3 4]))
          defenders (zoc/zoc-defenders state [2 5] :team/AWAY)]
      (is (= 2 (count defenders)))
      (is (contains? defenders f/away-player-1))
      (is (contains? defenders f/away-player-2)))))

(deftest zoc-defenders-excludes-exhausted
  (testing "excludes exhausted defenders"
    (let [state     (-> (f/base-game-state)
                        (f/with-player-at f/away-player-1 [2 4])
                        (f/with-player-at f/away-player-2 [3 4])
                        (f/with-exhausted f/away-player-1))
          defenders (zoc/zoc-defenders state [2 5] :team/AWAY)]
      (is (= 1 (count defenders)))
      (is (contains? defenders f/away-player-2)))))

(deftest zoc-defenders-empty-when-none-in-range
  (testing "returns empty set when no defenders in range"
    (let [state     (-> (f/base-game-state)
                        (f/with-player-at f/away-player-1 [2 10]))
          defenders (zoc/zoc-defenders state [2 3] :team/AWAY)]
      (is (empty? defenders)))))

;; =============================================================================
;; ZoC Movement Cost Tests
;; =============================================================================

(deftest zoc-movement-cost-larger-defender
  (testing "larger defender costs +2 movement"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-2 [2 3])   ; SM elf
                    (f/with-player-at f/away-player-1 [2 4]))] ; LG troll
      (is (= 2 (zoc/zoc-movement-cost state f/home-player-2 f/away-player-1))))))

(deftest zoc-movement-cost-same-size
  (testing "same size defender costs +1 movement"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-3 [2 3])   ; MD dwarf
                    (f/with-player-at f/away-player-3 [2 4]))] ; MD human
      (is (= 1 (zoc/zoc-movement-cost state f/home-player-3 f/away-player-3))))))

(deftest zoc-movement-cost-smaller-defender
  (testing "smaller defender costs +0 movement"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])   ; LG orc
                    (f/with-player-at f/away-player-2 [2 4]))] ; SM goblin
      (is (= 0 (zoc/zoc-movement-cost state f/home-player-1 f/away-player-2))))))

(deftest zoc-movement-cost-exhausted-defender
  (testing "exhausted defender costs +0 movement"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-2 [2 3])
                    (f/with-player-at f/away-player-1 [2 4])
                    (f/with-exhausted f/away-player-1))]
      (is (= 0 (zoc/zoc-movement-cost state f/home-player-2 f/away-player-1))))))

;; =============================================================================
;; Shooting ZoC Disadvantage Tests
;; =============================================================================

(deftest shooting-zoc-larger-defender-double-disadvantage
  (testing "larger defender gives double-disadvantage"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-2 [2 8])   ; SM elf shooter
                    (f/with-player-at f/away-player-1 [2 9]))] ; LG troll defender
      (is (= :double-disadvantage
             (zoc/shooting-zoc-disadvantage state f/home-player-2 f/away-player-1))))))

(deftest shooting-zoc-same-size-disadvantage
  (testing "same size defender gives disadvantage"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-3 [2 8])   ; MD dwarf
                    (f/with-player-at f/away-player-3 [2 9]))] ; MD human
      (is (= :disadvantage
             (zoc/shooting-zoc-disadvantage state f/home-player-3 f/away-player-3))))))

(deftest shooting-zoc-smaller-defender-normal
  (testing "smaller defender gives normal (no ZoC effect)"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 8])   ; LG orc
                    (f/with-player-at f/away-player-2 [2 9]))] ; SM goblin
      (is (= :normal
             (zoc/shooting-zoc-disadvantage state f/home-player-1 f/away-player-2))))))

(deftest shooting-zoc-defender-not-adjacent
  (testing "returns nil when defender not adjacent"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 8])
                    (f/with-player-at f/away-player-1 [2 3]))]
      (is (nil? (zoc/shooting-zoc-disadvantage state f/home-player-1 f/away-player-1))))))

;; =============================================================================
;; Contested Shot Tests
;; =============================================================================

(deftest uncontested-shot-true
  (testing "uncontested when no defenders in ZoC"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 8])
                    (f/with-player-at f/away-player-1 [2 3]))]
      (is (zoc/uncontested-shot? state f/home-player-1 :team/AWAY)))))

(deftest uncontested-shot-false
  (testing "contested when defender in ZoC"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 8])
                    (f/with-player-at f/away-player-1 [2 9]))]
      (is (not (zoc/uncontested-shot? state f/home-player-1 :team/AWAY))))))

;; =============================================================================
;; Pass Path ZoC Tests
;; =============================================================================

(deftest pass-path-zoc-intercepted
  (testing "returns defenders whose ZoC intersects pass path"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-player-at f/away-player-1 [2 6]))]
      ;; Pass from [2 3] to [2 9] passes through [2 6] area
      (is (contains? (zoc/pass-path-zoc state [2 3] [2 9] :team/AWAY)
                     f/away-player-1)))))

(deftest pass-path-zoc-not-intercepted
  (testing "returns empty when no ZoC intersects path"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-player-at f/away-player-1 [0 5]))]
      ;; Pass from [2 3] to [2 9] doesn't go near [0 5]
      (is (empty? (zoc/pass-path-zoc state [2 3] [2 9] :team/AWAY))))))

(deftest pass-path-contested-true
  (testing "contested when defender ZoC intersects"
    (let [state (-> (f/base-game-state)
                    (f/with-player-at f/home-player-1 [2 3])
                    (f/with-player-at f/away-player-1 [2 6]))]
      (is (zoc/pass-path-contested? state f/home-player-1 [2 9] :team/AWAY)))))

;; =============================================================================
;; Collect ZoC Sources Tests
;; =============================================================================

(deftest collect-shooting-zoc-sources-test
  (testing "collects all ZoC disadvantage sources for shooting"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-2 [2 8])   ; SM elf
                      (f/with-player-at f/away-player-1 [2 9])   ; LG troll
                      (f/with-player-at f/away-player-2 [3 8]))  ; SM goblin
          sources (zoc/collect-shooting-zoc-sources state f/home-player-2 :team/AWAY)]
      (is (= 2 (count sources)))
      ;; LG troll vs SM elf = double-disadvantage
      (is (some #(and (= (:defender-id %) f/away-player-1)
                      (= (:disadvantage %) :double-disadvantage))
                sources))
      ;; SM goblin vs SM elf = same size = disadvantage
      (is (some #(and (= (:defender-id %) f/away-player-2)
                      (= (:disadvantage %) :disadvantage))
                sources)))))

(deftest collect-passing-zoc-sources-test
  (testing "collects all ZoC disadvantage sources for passing"
    (let [state   (-> (f/base-game-state)
                      (f/with-player-at f/home-player-1 [2 3])
                      (f/with-player-at f/home-player-2 [2 9])
                      (f/with-player-at f/away-player-1 [2 6]))
          sources (zoc/collect-passing-zoc-sources state f/home-player-1 [2 9] :team/AWAY)]
      (is (= 1 (count sources)))
      (is (= f/away-player-1 (:defender-id (first sources)))))))
