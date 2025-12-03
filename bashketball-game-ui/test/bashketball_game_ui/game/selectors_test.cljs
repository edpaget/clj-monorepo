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
