(ns bashketball-game.state-test
  (:require [bashketball-game.state :as state]
            [clojure.test :refer [deftest is testing]]))

(def test-config
  {:home {:deck ["drive-rebound" "shoot-check" "pass-steal" "drive-rebound" "shoot-check"]
          :players [{:card-slug "orc-center"
                     :name "Grukk"
                     :stats {:size :BIG :speed 2 :shooting 2 :passing 1 :dribbling 1 :defense 4}}
                    {:card-slug "elf-point-guard"
                     :name "Lyria"
                     :stats {:size :SMALL :speed 5 :shooting 3 :passing 4 :dribbling 3 :defense 2}}
                    {:card-slug "dwarf-power-forward"
                     :name "Thorin"
                     :stats {:size :MID :speed 2 :shooting 3 :passing 2 :dribbling 2 :defense 4}}]}
   :away {:deck ["drive-rebound" "shoot-check" "pass-steal" "drive-rebound" "shoot-check"]
          :players [{:card-slug "troll-center"
                     :name "Grok"
                     :stats {:size :BIG :speed 1 :shooting 1 :passing 1 :dribbling 1 :defense 5}}
                    {:card-slug "goblin-shooting-guard"
                     :name "Sneek"
                     :stats {:size :SMALL :speed 4 :shooting 4 :passing 3 :dribbling 3 :defense 1}}
                    {:card-slug "human-small-forward"
                     :name "John"
                     :stats {:size :MID :speed 3 :shooting 3 :passing 3 :dribbling 3 :defense 3}}]}})

(deftest create-game-test
  (let [game (state/create-game test-config)]

    (testing "initial phase is setup"
      (is (= :SETUP (state/get-phase game))))

    (testing "turn number starts at 1"
      (is (= 1 (:turn-number game))))

    (testing "home player is active"
      (is (= :HOME (state/get-active-player game))))

    (testing "score starts at 0-0"
      (is (= {:HOME 0 :AWAY 0} (state/get-score game))))

    (testing "ball starts loose at center"
      (is (= {:status :LOOSE :position [2 7]} (state/get-ball game))))

    (testing "events start empty"
      (is (empty? (:events game))))))

(deftest player-id-generation-test
  (let [game (state/create-game test-config)]

    (testing "player IDs are derived from team and slug"
      (is (state/get-basketball-player game "home-orc-center-0"))
      (is (state/get-basketball-player game "home-elf-point-guard-1"))
      (is (state/get-basketball-player game "away-troll-center-0")))))

(deftest get-basketball-player-test
  (let [game   (state/create-game test-config)
        player (state/get-basketball-player game "home-orc-center-0")]

    (testing "player has correct attributes"
      (is (= "home-orc-center-0" (:id player)))
      (is (= "orc-center" (:card-slug player)))
      (is (= "Grukk" (:name player)))
      (is (= {:size :BIG :speed 2 :shooting 2 :passing 1 :dribbling 1 :defense 4}
             (:stats player))))

    (testing "player starts not exhausted"
      (is (not (:exhausted? player))))

    (testing "player starts with no position"
      (is (nil? (:position player))))))

(deftest get-basketball-player-team-test
  (let [game (state/create-game test-config)]

    (testing "finds correct team for home player"
      (is (= :HOME (state/get-basketball-player-team game "home-orc-center-0"))))

    (testing "finds correct team for away player"
      (is (= :AWAY (state/get-basketball-player-team game "away-troll-center-0"))))

    (testing "returns nil for unknown player"
      (is (nil? (state/get-basketball-player-team game "unknown-player"))))))

(deftest get-starters-and-bench-test
  (let [game (state/create-game test-config)]

    (testing "home has 3 starters"
      (is (= 3 (count (state/get-starters game :HOME)))))

    (testing "starters are first 3 players"
      (is (= ["home-orc-center-0" "home-elf-point-guard-1" "home-dwarf-power-forward-2"]
             (state/get-starters game :HOME))))))

(deftest deck-accessors-test
  (let [game (state/create-game test-config)]

    (testing "draw pile has initial cards"
      (is (= 5 (count (state/get-draw-pile game :HOME)))))

    (testing "hand starts empty"
      (is (empty? (state/get-hand game :HOME))))

    (testing "discard starts empty"
      (is (empty? (state/get-discard game :HOME))))))

(deftest update-basketball-player-test
  (let [game    (state/create-game test-config)
        updated (state/update-basketball-player game "home-orc-center-0" assoc :exhausted? true)
        player  (state/get-basketball-player updated "home-orc-center-0")]

    (testing "player is updated"
      (is (:exhausted? player)))))
