(ns bashketball-game-ui.game.board-utils-test
  "Tests for board rendering utilities."
  (:require
   [bashketball-game-ui.game.board-utils :as board-utils]
   [cljs.test :as t :include-macros true]))

;; =============================================================================
;; Constants tests
;; =============================================================================

(t/deftest board-dimensions-constants-test
  (t/is (= 5 board-utils/board-width))
  (t/is (= 14 board-utils/board-height))
  (t/is (= 40 board-utils/hex-size)))

(t/deftest hoop-positions-test
  (t/is (= [2 0] board-utils/home-hoop))
  (t/is (= [2 13] board-utils/away-hoop)))

;; =============================================================================
;; Coordinate conversion tests
;; =============================================================================

(t/deftest hex->pixel-origin-test
  (let [[x y] (board-utils/hex->pixel [0 0])]
    (t/is (< (abs x) 0.001))
    (t/is (< (abs y) 0.001))))

(t/deftest hex->pixel-returns-vector-test
  (let [result (board-utils/hex->pixel [2 5])]
    (t/is (vector? result))
    (t/is (= 2 (count result)))))

(t/deftest pixel->hex-roundtrip-test
  (let [original  [2 5]
        pixel     (board-utils/hex->pixel original)
        converted (board-utils/pixel->hex pixel)]
    (t/is (= original converted))))

(t/deftest pixel->hex-roundtrip-edge-test
  (let [original  [4 13]
        pixel     (board-utils/hex->pixel original)
        converted (board-utils/pixel->hex pixel)]
    (t/is (= original converted))))

;; =============================================================================
;; Valid position tests
;; =============================================================================

(t/deftest valid-position-within-bounds-test
  (t/is (board-utils/valid-position? [0 0]))
  (t/is (board-utils/valid-position? [4 13]))
  (t/is (board-utils/valid-position? [2 7])))

(t/deftest valid-position-outside-bounds-test
  (t/is (not (board-utils/valid-position? [-1 0])))
  (t/is (not (board-utils/valid-position? [5 0])))
  (t/is (not (board-utils/valid-position? [0 -1])))
  (t/is (not (board-utils/valid-position? [0 14]))))

;; =============================================================================
;; Terrain tests
;; =============================================================================

(t/deftest terrain-at-home-hoop-test
  (let [terrain (board-utils/terrain-at [2 0])]
    (t/is (= :hoop (:terrain terrain)))
    (t/is (= :HOME (:side terrain)))))

(t/deftest terrain-at-away-hoop-test
  (let [terrain (board-utils/terrain-at [2 13])]
    (t/is (= :hoop (:terrain terrain)))
    (t/is (= :AWAY (:side terrain)))))

(t/deftest terrain-at-home-paint-test
  (let [terrain (board-utils/terrain-at [1 1])]
    (t/is (= :paint (:terrain terrain)))
    (t/is (= :HOME (:side terrain)))))

(t/deftest terrain-at-away-paint-test
  (let [terrain (board-utils/terrain-at [1 12])]
    (t/is (= :paint (:terrain terrain)))
    (t/is (= :AWAY (:side terrain)))))

(t/deftest terrain-at-three-point-line-test
  (let [home-3pt (board-utils/terrain-at [2 3])
        away-3pt (board-utils/terrain-at [2 10])]
    (t/is (= :three-point-line (:terrain home-3pt)))
    (t/is (= :HOME (:side home-3pt)))
    (t/is (= :three-point-line (:terrain away-3pt)))
    (t/is (= :AWAY (:side away-3pt)))))

(t/deftest terrain-at-court-test
  (let [terrain (board-utils/terrain-at [2 5])]
    (t/is (= :court (:terrain terrain)))
    (t/is (nil? (:side terrain)))))

(t/deftest terrain-at-center-court-test
  (let [terrain (board-utils/terrain-at [2 7])]
    (t/is (= :center-court (:terrain terrain)))
    (t/is (nil? (:side terrain)))))

;; =============================================================================
;; Terrain side tests
;; =============================================================================

(t/deftest terrain-side-home-test
  (t/is (= :HOME (board-utils/terrain-side [2 0])))
  (t/is (= :HOME (board-utils/terrain-side [2 3])))
  (t/is (= :HOME (board-utils/terrain-side [2 6]))))

(t/deftest terrain-side-away-test
  (t/is (= :AWAY (board-utils/terrain-side [2 13])))
  (t/is (= :AWAY (board-utils/terrain-side [2 10])))
  (t/is (= :AWAY (board-utils/terrain-side [2 7]))))

;; =============================================================================
;; Hex geometry tests
;; =============================================================================

(t/deftest hex-neighbors-center-test
  (let [neighbors (board-utils/hex-neighbors [2 7])]
    (t/is (= 6 (count neighbors)))
    (t/is (every? board-utils/valid-position? neighbors))))

(t/deftest hex-neighbors-corner-test
  (let [neighbors (board-utils/hex-neighbors [0 0])]
    (t/is (< (count neighbors) 6))
    (t/is (every? board-utils/valid-position? neighbors))))

(t/deftest hex-distance-same-position-test
  (t/is (= 0 (board-utils/hex-distance [2 7] [2 7]))))

(t/deftest hex-distance-adjacent-test
  (t/is (= 1 (board-utils/hex-distance [2 7] [2 8])))
  (t/is (= 1 (board-utils/hex-distance [2 7] [3 7]))))

(t/deftest hex-distance-across-board-test
  (t/is (pos? (board-utils/hex-distance [0 0] [4 13]))))

;; =============================================================================
;; SVG helpers tests
;; =============================================================================

(t/deftest hex-corners-returns-6-points-test
  (let [corners (board-utils/hex-corners [100 100])]
    (t/is (= 6 (count corners)))
    (t/is (every? #(= 2 (count %)) corners))))

(t/deftest hex-points-str-format-test
  (let [points-str (board-utils/hex-points-str [100 100])]
    (t/is (string? points-str))
    (t/is (re-find #"\d+\.?\d*,\d+\.?\d*" points-str))))

;; =============================================================================
;; Board dimensions tests
;; =============================================================================

(t/deftest board-dimensions-returns-vector-test
  (let [[w h] (board-utils/board-dimensions)]
    (t/is (pos? w))
    (t/is (pos? h))))

(t/deftest all-positions-count-test
  (let [positions (board-utils/all-positions)]
    (t/is (= 70 (count positions)))
    (t/is (every? board-utils/valid-position? positions))))
