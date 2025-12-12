(ns bashketball-game-ui.game.selectors-test
  (:require
   [bashketball-game-ui.game.selectors :as sut]
   [cljs.test :as t :include-macros true]))

(t/deftest next-phase-test
  (t/testing "phase progression from keywords"
    (t/is (= :phase/ACTIONS (sut/next-phase :phase/UPKEEP)))
    (t/is (= :phase/RESOLUTION (sut/next-phase :phase/ACTIONS)))
    (t/is (= :phase/END_OF_TURN (sut/next-phase :phase/RESOLUTION)))
    (t/is (= :phase/UPKEEP (sut/next-phase :phase/END_OF_TURN))))

  (t/testing "terminal states"
    (t/is (nil? (sut/next-phase :phase/SETUP)))
    (t/is (nil? (sut/next-phase :phase/GAME_OVER))))

  (t/testing "string phases"
    (t/is (= :phase/ACTIONS (sut/next-phase "phase/UPKEEP")))
    (t/is (= :phase/RESOLUTION (sut/next-phase "phase/ACTIONS")))
    (t/is (= :phase/END_OF_TURN (sut/next-phase "phase/RESOLUTION")))
    (t/is (= :phase/UPKEEP (sut/next-phase "phase/END_OF_TURN")))))

(t/deftest can-advance-phase-test
  (t/testing "can advance from normal phases"
    (t/is (true? (sut/can-advance-phase? :phase/UPKEEP)))
    (t/is (true? (sut/can-advance-phase? :phase/ACTIONS)))
    (t/is (true? (sut/can-advance-phase? :phase/RESOLUTION)))
    (t/is (true? (sut/can-advance-phase? :phase/END_OF_TURN))))

  (t/testing "cannot advance from terminal states"
    (t/is (false? (sut/can-advance-phase? :phase/SETUP)))
    (t/is (false? (sut/can-advance-phase? :phase/GAME_OVER)))))

(t/deftest phase-label-test
  (t/testing "keyword phases"
    (t/is (= "Setup" (sut/phase-label :phase/SETUP)))
    (t/is (= "Upkeep" (sut/phase-label :phase/UPKEEP)))
    (t/is (= "Actions" (sut/phase-label :phase/ACTIONS)))
    (t/is (= "Resolution" (sut/phase-label :phase/RESOLUTION)))
    (t/is (= "End of Turn" (sut/phase-label :phase/END_OF_TURN)))
    (t/is (= "Game Over" (sut/phase-label :phase/GAME_OVER))))

  (t/testing "string phases"
    (t/is (= "Upkeep" (sut/phase-label "phase/UPKEEP")))
    (t/is (= "Actions" (sut/phase-label "phase/ACTIONS")))
    (t/is (= "Resolution" (sut/phase-label "phase/RESOLUTION")))))

(t/deftest build-player-index-map-test
  (t/testing "assigns 1-based indices to players sorted by ID"
    (let [players {"home-player-c" {:id "home-player-c" :name "Charlie"}
                   "home-player-a" {:id "home-player-a" :name "Alice"}
                   "home-player-b" {:id "home-player-b" :name "Bob"}}
          result  (sut/build-player-index-map players)]
      (t/is (= {"home-player-a" 1
                "home-player-b" 2
                "home-player-c" 3}
               result))))

  (t/testing "returns empty map for empty input"
    (t/is (= {} (sut/build-player-index-map {}))))

  (t/testing "returns empty map for nil input"
    (t/is (= {} (sut/build-player-index-map nil))))

  (t/testing "single player gets index 1"
    (let [players {"only-player" {:id "only-player"}}]
      (t/is (= {"only-player" 1}
               (sut/build-player-index-map players))))))
