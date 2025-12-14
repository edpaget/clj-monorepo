(ns bashketball-game.movement-test
  (:require
   [bashketball-game.board :as board]
   [bashketball-game.movement :as movement]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing]]))

(def test-catalog
  {"fast-guard"  {:slug "fast-guard" :speed 5}
   "slow-center" {:slug "slow-center" :speed 2}
   "mid-forward" {:slug "mid-forward" :speed 3}})

(def test-config
  {:home {:deck []
          :players [{:card-slug "slow-center"
                     :name "Grukk"
                     :stats {:size :size/LG :speed 2 :shooting 2 :passing 1 :dribbling 1 :defense 4}}
                    {:card-slug "fast-guard"
                     :name "Lyria"
                     :stats {:size :size/SM :speed 5 :shooting 3 :passing 4 :dribbling 3 :defense 2}}
                    {:card-slug "mid-forward"
                     :name "Thorin"
                     :stats {:size :size/MD :speed 3 :shooting 3 :passing 2 :dribbling 2 :defense 4}}]}
   :away {:deck []
          :players [{:card-slug "slow-center"
                     :name "Grok"
                     :stats {:size :size/LG :speed 1 :shooting 1 :passing 1 :dribbling 1 :defense 5}}]}})

(defn place-player
  "Places a player on the board at the given position."
  [game-state player-id position]
  (-> game-state
      (update :board board/set-occupant position {:id player-id})
      (state/update-basketball-player player-id assoc :position position)))

(deftest get-player-speed-uses-stats-test
  (let [game (state/create-game test-config)]
    (testing "uses player's stats speed"
      (is (= 2 (movement/get-player-speed game "HOME-slow-center-0")))
      (is (= 5 (movement/get-player-speed game "HOME-fast-guard-1")))
      (is (= 3 (movement/get-player-speed game "HOME-mid-forward-2"))))))

(deftest get-player-speed-catalog-fallback-test
  (let [game (-> (state/create-game test-config)
                 (state/update-basketball-player "HOME-slow-center-0"
                                                 update :stats dissoc :speed))]
    (testing "falls back to catalog when stats missing"
      (is (= 2 (movement/get-player-speed game "HOME-slow-center-0" test-catalog))))))

(deftest get-player-speed-default-test
  (let [game (-> (state/create-game test-config)
                 (state/update-basketball-player "HOME-slow-center-0"
                                                 update :stats dissoc :speed))]
    (testing "defaults to 2 when no data available"
      (is (= 2 (movement/get-player-speed game "HOME-slow-center-0" nil)))
      (is (= 2 (movement/get-player-speed game "HOME-slow-center-0"))))))

(deftest get-player-speed-catalog-wrong-slug-test
  (let [game (-> (state/create-game test-config)
                 (state/update-basketball-player "HOME-slow-center-0"
                                                 assoc :card-slug "unknown-card")
                 (state/update-basketball-player "HOME-slow-center-0"
                                                 update :stats dissoc :speed))]
    (testing "defaults when catalog doesn't have card"
      (is (= 2 (movement/get-player-speed game "HOME-slow-center-0" test-catalog))))))

(deftest valid-move-positions-returns-nil-when-not-on-board-test
  (let [game (state/create-game test-config)]
    (testing "returns nil for player not on board"
      (is (nil? (movement/valid-move-positions game "HOME-slow-center-0"))))))

(deftest valid-move-positions-speed-2-test
  (let [game      (-> (state/create-game test-config)
                      (place-player "HOME-slow-center-0" [2 7]))
        positions (movement/valid-move-positions game "HOME-slow-center-0")]
    (testing "returns set of positions"
      (is (set? positions)))
    (testing "excludes current position"
      (is (not (contains? positions [2 7]))))
    (testing "includes adjacent positions"
      (is (contains? positions [2 6]))
      (is (contains? positions [2 8]))
      (is (contains? positions [1 7]))
      (is (contains? positions [3 7])))))

(deftest valid-move-positions-speed-5-test
  (let [game      (-> (state/create-game test-config)
                      (place-player "HOME-fast-guard-1" [2 7]))
        positions (movement/valid-move-positions game "HOME-fast-guard-1")]
    (testing "fast player can reach further"
      (is (contains? positions [2 2]))
      (is (contains? positions [2 12])))))

(deftest valid-move-positions-excludes-occupied-test
  (let [game      (-> (state/create-game test-config)
                      (place-player "HOME-slow-center-0" [2 7])
                      (place-player "HOME-fast-guard-1" [2 6]))
        positions (movement/valid-move-positions game "HOME-slow-center-0")]
    (testing "excludes occupied positions"
      (is (not (contains? positions [2 6]))))))

(deftest can-move-to-valid-position-test
  (let [game (-> (state/create-game test-config)
                 (place-player "HOME-slow-center-0" [2 7]))]
    (testing "can move to valid empty position within range"
      (is (movement/can-move-to? game "HOME-slow-center-0" [2 6]))
      (is (movement/can-move-to? game "HOME-slow-center-0" [2 8]))
      (is (movement/can-move-to? game "HOME-slow-center-0" [2 5])))))

(deftest can-move-to-out-of-range-test
  (let [game (-> (state/create-game test-config)
                 (place-player "HOME-slow-center-0" [2 7]))]
    (testing "cannot move beyond speed range"
      (is (not (movement/can-move-to? game "HOME-slow-center-0" [2 4])))
      (is (not (movement/can-move-to? game "HOME-slow-center-0" [2 10]))))))

(deftest can-move-to-occupied-test
  (let [game (-> (state/create-game test-config)
                 (place-player "HOME-slow-center-0" [2 7])
                 (place-player "HOME-fast-guard-1" [2 6]))]
    (testing "cannot move to occupied position"
      (is (not (movement/can-move-to? game "HOME-slow-center-0" [2 6]))))))

(deftest can-move-to-invalid-position-test
  (let [game (-> (state/create-game test-config)
                 (place-player "HOME-slow-center-0" [0 0]))]
    (testing "cannot move off board"
      (is (not (movement/can-move-to? game "HOME-slow-center-0" [-1 0])))
      (is (not (movement/can-move-to? game "HOME-slow-center-0" [0 -1]))))))

(deftest can-move-to-player-not-on-board-test
  (let [game (state/create-game test-config)]
    (testing "returns false for player not on board"
      (is (not (movement/can-move-to? game "HOME-slow-center-0" [2 7]))))))

(deftest valid-move-positions-blocked-path-test
  (let [game (-> (state/create-game test-config)
                 (place-player "HOME-mid-forward-2" [2 7])
                 (place-player "HOME-slow-center-0" [2 8])
                 (place-player "HOME-fast-guard-1" [1 8])
                 (place-player "AWAY-slow-center-0" [3 8]))]
    (testing "cannot reach position behind wall of blockers"
      (let [positions (movement/valid-move-positions game "HOME-mid-forward-2")]
        (is (not (contains? positions [2 9])))))))

(deftest valid-move-positions-around-obstacle-test
  (let [game (-> (state/create-game test-config)
                 (place-player "HOME-fast-guard-1" [2 7])
                 (place-player "HOME-slow-center-0" [2 8]))]
    (testing "can reach position via alternate path"
      (let [positions (movement/valid-move-positions game "HOME-fast-guard-1")]
        (is (contains? positions [2 9]))))))

(deftest can-move-to-blocked-path-test
  (let [game (-> (state/create-game test-config)
                 (place-player "HOME-mid-forward-2" [2 7])
                 (place-player "HOME-slow-center-0" [2 8])
                 (place-player "HOME-fast-guard-1" [1 8])
                 (place-player "AWAY-slow-center-0" [3 8]))]
    (testing "cannot move to position blocked by wall"
      (is (not (movement/can-move-to? game "HOME-mid-forward-2" [2 9]))))))

(deftest valid-move-positions-with-adjacent-opponent-test
  (let [game (-> (state/create-game test-config)
                 (place-player "HOME-slow-center-0" [2 7])
                 (place-player "AWAY-slow-center-0" [2 8]))]
    (testing "can move around opponent without penalty"
      (let [positions (movement/valid-move-positions game "HOME-slow-center-0")]
        (is (contains? positions [2 6]))
        (is (contains? positions [1 7]))
        (is (contains? positions [2 9]))))))
