(ns bashketball-game.board-test
  (:require [bashketball-game.board :as board]
            [clojure.test :refer [deftest is testing]]))

(deftest valid-position-test
  (testing "valid positions"
    (is (board/valid-position? [0 0]))
    (is (board/valid-position? [4 13]))
    (is (board/valid-position? [2 7])))

  (testing "invalid positions"
    (is (not (board/valid-position? [-1 0])))
    (is (not (board/valid-position? [5 0])))
    (is (not (board/valid-position? [0 14])))
    (is (not (board/valid-position? [0 -1])))))

(deftest hex-distance-test
  (testing "same position"
    (is (= 0 (board/hex-distance [2 7] [2 7]))))

  (testing "adjacent positions"
    (is (= 1 (board/hex-distance [2 7] [2 8])))
    (is (= 1 (board/hex-distance [2 7] [3 7]))))

  (testing "distant positions"
    (is (= 7 (board/hex-distance [0 0] [0 7])))
    (is (= 4 (board/hex-distance [0 0] [4 0])))))

(deftest hex-neighbors-test
  (testing "center position has 6 neighbors"
    (is (= 6 (count (board/hex-neighbors [2 7])))))

  (testing "corner position has fewer neighbors"
    (is (< (count (board/hex-neighbors [0 0])) 6)))

  (testing "all neighbors are valid positions"
    (is (every? board/valid-position? (board/hex-neighbors [2 7])))))

(deftest hex-range-test
  (testing "range 0 returns only center"
    (is (= [[2 7]] (board/hex-range [2 7] 0))))

  (testing "range 1 returns center and neighbors"
    (let [result (board/hex-range [2 7] 1)]
      (is (some #{[2 7]} result))
      (is (<= (count result) 7)))))

(deftest create-board-test
  (testing "board dimensions"
    (let [b (board/create-board)]
      (is (= 5 (:width b)))
      (is (= 14 (:height b)))))

  (testing "hoops are placed correctly"
    (let [b (board/create-board)]
      (is (= {:terrain :HOOP :side :HOME} (get-in b [:tiles [2 0]])))
      (is (= {:terrain :HOOP :side :AWAY} (get-in b [:tiles [2 13]])))))

  (testing "occupants start empty"
    (let [b (board/create-board)]
      (is (empty? (:occupants b))))))

(deftest occupant-operations-test
  (let [b   (board/create-board)
        occ {:type :BASKETBALL_PLAYER :id "player-1"}]

    (testing "set and get occupant"
      (let [b2 (board/set-occupant b [2 7] occ)]
        (is (= occ (board/occupant-at b2 [2 7])))))

    (testing "remove occupant"
      (let [b2 (-> b
                   (board/set-occupant [2 7] occ)
                   (board/remove-occupant [2 7]))]
        (is (nil? (board/occupant-at b2 [2 7])))))

    (testing "move occupant"
      (let [b2 (-> b
                   (board/set-occupant [2 7] occ)
                   (board/move-occupant [2 7] [3 8]))]
        (is (nil? (board/occupant-at b2 [2 7])))
        (is (= occ (board/occupant-at b2 [3 8])))))

    (testing "find occupant"
      (let [b2 (board/set-occupant b [2 7] occ)]
        (is (= [2 7] (board/find-occupant b2 "player-1")))
        (is (nil? (board/find-occupant b2 "player-2")))))))

(deftest hex-line-test
  (testing "straight line"
    (let [line (board/hex-line [0 0] [0 3])]
      (is (= 4 (count line)))
      (is (= [0 0] (first line)))
      (is (= [0 3] (last line)))))

  (testing "same position"
    (is (= [[2 7]] (board/hex-line [2 7] [2 7])))))

(deftest path-clear-test
  (let [b   (board/create-board)
        occ {:type :BASKETBALL_PLAYER :id "blocker"}]

    (testing "empty path is clear"
      (is (board/path-clear? b [0 0] [0 5])))

    (testing "blocked path"
      (let [b2 (board/set-occupant b [0 2] occ)]
        (is (not (board/path-clear? b2 [0 0] [0 5])))))

    (testing "endpoints don't count as blocking"
      (let [b2 (-> b
                   (board/set-occupant [0 0] occ)
                   (board/set-occupant [0 5] occ))]
        (is (board/path-clear? b2 [0 0] [0 5]))))))

(deftest valid-occupants-empty-board-test
  (let [b (board/create-board)]
    (is (board/valid-occupants? b))))

(deftest valid-occupants-single-player-test
  (let [b (-> (board/create-board)
              (board/set-occupant [2 3] {:type :BASKETBALL_PLAYER :id "player-1"}))]
    (is (board/valid-occupants? b))))

(deftest valid-occupants-multiple-players-test
  (let [b (-> (board/create-board)
              (board/set-occupant [2 3] {:type :BASKETBALL_PLAYER :id "player-1"})
              (board/set-occupant [3 4] {:type :BASKETBALL_PLAYER :id "player-2"}))]
    (is (board/valid-occupants? b))))

(deftest check-occupant-invariants-detects-duplicates-test
  (let [b (-> (board/create-board)
              (board/set-occupant [2 3] {:type :BASKETBALL_PLAYER :id "player-1"})
              (board/set-occupant [3 4] {:type :BASKETBALL_PLAYER :id "player-1"}))]
    (is (not (board/valid-occupants? b)))
    (let [result (board/check-occupant-invariants b)]
      (is (= :duplicate-occupant-ids (:error result)))
      (is (= {"player-1" [[2 3] [3 4]]} (:details result))))))

(deftest move-occupant-maintains-invariants-test
  (let [b (-> (board/create-board)
              (board/set-occupant [2 3] {:type :BASKETBALL_PLAYER :id "player-1"})
              (board/move-occupant [2 3] [4 5]))]
    (is (board/valid-occupants? b))
    (is (nil? (board/occupant-at b [2 3])))
    (is (= {:type :BASKETBALL_PLAYER :id "player-1"} (board/occupant-at b [4 5])))))
