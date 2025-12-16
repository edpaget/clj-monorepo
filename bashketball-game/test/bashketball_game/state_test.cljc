(ns bashketball-game.state-test
  (:require [bashketball-game.state :as state]
            [clojure.test :refer [deftest is testing]]))

(def test-config
  {:home {:deck ["drive-rebound" "shoot-check" "pass-steal" "drive-rebound" "shoot-check"]
          :players [{:card-slug "orc-center"
                     :name "Grukk"
                     :stats {:size :size/LG :speed 2 :shooting 2 :passing 1 :defense 4}}
                    {:card-slug "elf-point-guard"
                     :name "Lyria"
                     :stats {:size :size/SM :speed 5 :shooting 3 :passing 4 :defense 2}}
                    {:card-slug "dwarf-power-forward"
                     :name "Thorin"
                     :stats {:size :size/MD :speed 2 :shooting 3 :passing 2 :defense 4}}]}
   :away {:deck ["drive-rebound" "shoot-check" "pass-steal" "drive-rebound" "shoot-check"]
          :players [{:card-slug "troll-center"
                     :name "Grok"
                     :stats {:size :size/LG :speed 1 :shooting 1 :passing 1 :defense 5}}
                    {:card-slug "goblin-shooting-guard"
                     :name "Sneek"
                     :stats {:size :size/SM :speed 4 :shooting 4 :passing 3 :defense 1}}
                    {:card-slug "human-small-forward"
                     :name "John"
                     :stats {:size :size/MD :speed 3 :shooting 3 :passing 3 :defense 3}}]}})

(deftest create-game-test
  (let [game (state/create-game test-config)]

    (testing "initial phase is setup"
      (is (= :phase/SETUP (state/get-phase game))))

    (testing "turn number starts at 1"
      (is (= 1 (:turn-number game))))

    (testing "home player is active"
      (is (= :team/HOME (state/get-active-player game))))

    (testing "score starts at 0-0"
      (is (= {:team/HOME 0 :team/AWAY 0} (state/get-score game))))

    (testing "ball starts loose at center"
      (is (= {:status :ball-status/LOOSE :position [2 7]} (state/get-ball game))))

    (testing "events start empty"
      (is (empty? (:events game))))))

(deftest player-id-generation-test
  (let [game (state/create-game test-config)]

    (testing "player IDs are derived from team and slug"
      (is (state/get-basketball-player game "HOME-orc-center-0"))
      (is (state/get-basketball-player game "HOME-elf-point-guard-1"))
      (is (state/get-basketball-player game "AWAY-troll-center-0")))))

(deftest get-basketball-player-test
  (let [game   (state/create-game test-config)
        player (state/get-basketball-player game "HOME-orc-center-0")]

    (testing "player has correct attributes"
      (is (= "HOME-orc-center-0" (:id player)))
      (is (= "orc-center" (:card-slug player)))
      (is (= "Grukk" (:name player)))
      (is (= {:size :size/LG :speed 2 :shooting 2 :passing 1 :defense 4}
             (:stats player))))

    (testing "player starts not exhausted"
      (is (not (:exhausted player))))

    (testing "player starts with no position"
      (is (nil? (:position player))))))

(deftest get-basketball-player-team-test
  (let [game (state/create-game test-config)]

    (testing "finds correct team for home player"
      (is (= :team/HOME (state/get-basketball-player-team game "HOME-orc-center-0"))))

    (testing "finds correct team for away player"
      (is (= :team/AWAY (state/get-basketball-player-team game "AWAY-troll-center-0"))))

    (testing "returns nil for unknown player"
      (is (nil? (state/get-basketball-player-team game "unknown-player"))))))

(deftest get-all-players-test
  (let [game (state/create-game test-config)]

    (testing "home has 3 players"
      (is (= 3 (count (state/get-all-players game :team/HOME)))))

    (testing "all players start off-court (no position)"
      (is (= 3 (count (state/get-off-court-players game :team/HOME))))
      (is (empty? (state/get-on-court-players game :team/HOME))))))

(deftest deck-accessors-test
  (let [game (state/create-game test-config)]

    (testing "draw pile has initial cards"
      (is (= 5 (count (state/get-draw-pile game :team/HOME)))))

    (testing "hand starts empty"
      (is (empty? (state/get-hand game :team/HOME))))

    (testing "discard starts empty"
      (is (empty? (state/get-discard game :team/HOME))))))

(deftest card-instance-test
  (let [game      (state/create-game test-config)
        draw-pile (state/get-draw-pile game :team/HOME)]

    (testing "cards in draw pile are CardInstance maps"
      (is (every? map? draw-pile)))

    (testing "each card has instance-id"
      (is (every? :instance-id draw-pile)))

    (testing "each card has card-slug"
      (is (every? :card-slug draw-pile)))

    (testing "instance-ids are unique"
      (let [ids (map :instance-id draw-pile)]
        (is (= (count ids) (count (set ids))))))

    (testing "card-slugs match original deck order"
      (is (= ["drive-rebound" "shoot-check" "pass-steal" "drive-rebound" "shoot-check"]
             (mapv :card-slug draw-pile))))))

(deftest update-basketball-player-test
  (let [game    (state/create-game test-config)
        updated (state/update-basketball-player game "HOME-orc-center-0" assoc :exhausted true)
        player  (state/get-basketball-player updated "HOME-orc-center-0")]

    (testing "player is updated"
      (is (:exhausted player)))))
