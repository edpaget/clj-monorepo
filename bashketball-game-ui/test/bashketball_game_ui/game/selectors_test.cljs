(ns bashketball-game-ui.game.selectors-test
  (:require
   [bashketball-game-ui.game.selectors :as sut]
   [cljs.test :as t :include-macros true]))

(t/deftest next-phase-test
  (t/testing "phase progression from keywords"
    (t/is (= :ACTIONS (sut/next-phase :UPKEEP)))
    (t/is (= :RESOLUTION (sut/next-phase :ACTIONS)))
    (t/is (= :END_OF_TURN (sut/next-phase :RESOLUTION)))
    (t/is (= :UPKEEP (sut/next-phase :END_OF_TURN))))

  (t/testing "terminal states"
    (t/is (nil? (sut/next-phase :SETUP)))
    (t/is (nil? (sut/next-phase :GAME_OVER))))

  (t/testing "string phases"
    (t/is (= :ACTIONS (sut/next-phase "UPKEEP")))
    (t/is (= :RESOLUTION (sut/next-phase "ACTIONS")))
    (t/is (= :END_OF_TURN (sut/next-phase "RESOLUTION")))
    (t/is (= :UPKEEP (sut/next-phase "END_OF_TURN")))))

(t/deftest can-advance-phase-test
  (t/testing "can advance from normal phases"
    (t/is (true? (sut/can-advance-phase? :UPKEEP)))
    (t/is (true? (sut/can-advance-phase? :ACTIONS)))
    (t/is (true? (sut/can-advance-phase? :RESOLUTION)))
    (t/is (true? (sut/can-advance-phase? :END_OF_TURN))))

  (t/testing "cannot advance from terminal states"
    (t/is (false? (sut/can-advance-phase? :SETUP)))
    (t/is (false? (sut/can-advance-phase? :GAME_OVER)))))

(t/deftest phase-label-test
  (t/testing "keyword phases"
    (t/is (= "Setup" (sut/phase-label :SETUP)))
    (t/is (= "Upkeep" (sut/phase-label :UPKEEP)))
    (t/is (= "Actions" (sut/phase-label :ACTIONS)))
    (t/is (= "Resolution" (sut/phase-label :RESOLUTION)))
    (t/is (= "End of Turn" (sut/phase-label :END_OF_TURN)))
    (t/is (= "Game Over" (sut/phase-label :GAME_OVER))))

  (t/testing "string phases"
    (t/is (= "Upkeep" (sut/phase-label "UPKEEP")))
    (t/is (= "Actions" (sut/phase-label "ACTIONS")))
    (t/is (= "Resolution" (sut/phase-label "RESOLUTION")))))

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
