(ns bashketball-game.movement-constraints-test
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.movement :as movement]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing]]))

(def test-config
  {:home {:deck []
          :players [{:card-slug "home-guard"
                     :name "Guard"
                     :stats {:size :size/SM :speed 5 :shooting 3 :passing 4 :defense 2}}]}
   :away {:deck []
          :players [{:card-slug "away-center"
                     :name "Center"
                     :stats {:size :size/LG :speed 2 :shooting 1 :passing 1 :defense 5}}]}})

(defn- test-ctx
  "Creates a test context with state and empty registry."
  [game-state]
  {:state game-state
   :registry (triggers/create-registry)})

(defn place-player
  "Places a player on the board at the given position."
  [game-state player-id position]
  (-> game-state
      (update :board board/set-occupant position {:id player-id})
      (state/update-basketball-player player-id assoc :position position)))

(defn set-ball-loose
  "Sets the ball to loose at a position."
  [game-state position]
  (assoc game-state :ball {:status :ball-status/LOOSE :position position}))

(defn set-ball-possessed
  "Sets the ball as possessed by a player."
  [game-state holder-id]
  (assoc game-state :ball {:status :ball-status/POSSESSED :holder-id holder-id}))

;; =============================================================================
;; Toward/Away Basket Tests
;; =============================================================================

(deftest filter-toward-basket-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 7]))
        ctx      (test-ctx game)
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" :toward-basket)]
    (testing "HOME team moves toward right basket [2 13]"
      (is (every? #(> (second %) 7) filtered)
          "All positions should have higher column than starting position"))))

(deftest filter-away-from-basket-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 7]))
        ctx      (test-ctx game)
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" :away-from-basket)]
    (testing "HOME team moves away from right basket"
      (is (every? #(< (second %) 7) filtered)
          "All positions should have lower column than starting position"))))

;; =============================================================================
;; Toward/Adjacent Ball Tests
;; =============================================================================

(deftest filter-toward-ball-loose-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 7])
                     (set-ball-loose [2 10]))
        ctx      (test-ctx game)
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" :toward-ball)]
    (testing "filters to positions closer to loose ball"
      (is (every? #(> (second %) 7) filtered)
          "All positions should be toward column 10"))))

(deftest filter-toward-ball-possessed-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 7])
                     (place-player "AWAY-away-center-0" [2 10])
                     (set-ball-possessed "AWAY-away-center-0"))
        ctx      (test-ctx game)
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" :toward-ball)]
    (testing "filters to positions closer to ball holder"
      (is (every? #(> (second %) 7) filtered)
          "All positions should be toward the holder at column 10"))))

(deftest filter-adjacent-to-ball-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 7])
                     (set-ball-loose [2 9]))
        ctx      (test-ctx game)
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" :adjacent-to-ball)]
    (testing "filters to positions adjacent to ball"
      (is (every? #(= 1 (board/hex-distance % [2 9])) filtered)
          "All positions should be adjacent to ball at [2 9]"))))

;; =============================================================================
;; Toward Player Tests
;; =============================================================================

(deftest filter-toward-player-test
  (let [game       (-> (state/create-game test-config)
                       (place-player "HOME-home-guard-0" [2 7])
                       (place-player "AWAY-away-center-0" [2 10]))
        ctx        (test-ctx game)
        constraint {:type :toward-player :target-player-id "AWAY-away-center-0"}
        filtered   (movement/constrained-move-positions ctx "HOME-home-guard-0" constraint)]
    (testing "filters to positions closer to target player"
      (is (every? #(> (second %) 7) filtered)
          "All positions should be toward player at column 10"))))

;; =============================================================================
;; ZoC Tests
;; =============================================================================

(deftest filter-into-zoc-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 5])
                     (place-player "AWAY-away-center-0" [2 7]))
        ctx      (test-ctx game)
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" :into-zoc)]
    (testing "filters to positions inside opponent ZoC"
      (is (every? #(<= (board/hex-distance % [2 7]) 1) filtered)
          "All positions should be within ZoC range of defender"))))

(deftest filter-out-of-zoc-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 7])
                     (place-player "AWAY-away-center-0" [2 8])
                      ;; Ensure defender is not exhausted
                     (state/update-basketball-player "AWAY-away-center-0" assoc :exhausted false))
        ctx      (test-ctx game)
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" :out-of-zoc)]
    (testing "filters to positions outside opponent ZoC"
      (is (every? #(> (board/hex-distance % [2 8]) 1) filtered)
          "All positions should be outside ZoC range of defender"))))

;; =============================================================================
;; Fallback Behavior Tests
;; =============================================================================

(deftest constraint-empty-result-returns-all-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 13]))
        ctx      (test-ctx game)
        all-pos  (movement/valid-move-positions ctx "HOME-home-guard-0")
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" :toward-basket)]
    (testing "when at basket, toward-basket filters nothing - returns all positions"
      (is (= all-pos filtered)
          "Should return original positions when constraint filters everything"))))

(deftest nil-constraint-returns-all-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 7]))
        ctx      (test-ctx game)
        all-pos  (movement/valid-move-positions ctx "HOME-home-guard-0")
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" nil)]
    (testing "nil constraint returns all valid positions"
      (is (= all-pos filtered)))))

(deftest unknown-constraint-returns-all-test
  (let [game     (-> (state/create-game test-config)
                     (place-player "HOME-home-guard-0" [2 7]))
        ctx      (test-ctx game)
        all-pos  (movement/valid-move-positions ctx "HOME-home-guard-0")
        filtered (movement/constrained-move-positions ctx "HOME-home-guard-0" :unknown-constraint)]
    (testing "unknown constraint returns all valid positions"
      (is (= all-pos filtered)))))
