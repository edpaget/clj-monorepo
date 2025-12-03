(ns bashketball-game.actions-test
  (:require [bashketball-game.actions :as actions]
            [bashketball-game.board :as board]
            [bashketball-game.state :as state]
            [clojure.test :refer [deftest is testing]]))

(def test-config
  {:home {:deck ["card-1" "card-2" "card-3" "card-4" "card-5"]
          :players [{:card-slug "orc-center"
                     :name "Grukk"
                     :stats {:size :BIG :speed 2 :shooting 2 :passing 1 :dribbling 1 :defense 4}}
                    {:card-slug "elf-point-guard"
                     :name "Lyria"
                     :stats {:size :SMALL :speed 5 :shooting 3 :passing 4 :dribbling 3 :defense 2}}
                    {:card-slug "dwarf-power-forward"
                     :name "Thorin"
                     :stats {:size :MID :speed 2 :shooting 3 :passing 2 :dribbling 2 :defense 4}}]}
   :away {:deck ["card-a" "card-b" "card-c" "card-d" "card-e"]
          :players [{:card-slug "troll-center"
                     :name "Grok"
                     :stats {:size :BIG :speed 1 :shooting 1 :passing 1 :dribbling 1 :defense 5}}
                    {:card-slug "goblin-shooting-guard"
                     :name "Sneek"
                     :stats {:size :SMALL :speed 4 :shooting 4 :passing 3 :dribbling 3 :defense 1}}
                    {:card-slug "human-small-forward"
                     :name "John"
                     :stats {:size :MID :speed 3 :shooting 3 :passing 3 :dribbling 3 :defense 3}}]}})

(deftest apply-action-validation-test
  (let [game (state/create-game test-config)]

    (testing "valid action is applied"
      (is (actions/apply-action game {:type :bashketball/set-phase :phase :ACTIONS})))

    (testing "invalid action throws"
      (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                   (actions/apply-action game {:type :bashketball/set-phase :phase :invalid}))))))

(deftest apply-action-event-logging-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/set-phase :phase :ACTIONS})]

    (testing "event is logged"
      (is (= 1 (count (:events updated)))))

    (testing "event has timestamp"
      (is (string? (:timestamp (first (:events updated))))))

    (testing "event has action data"
      (is (= :bashketball/set-phase (:type (first (:events updated))))))))

(deftest set-phase-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/set-phase :phase :ACTIONS})]
    (is (= :ACTIONS (state/get-phase updated)))))

(deftest advance-turn-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/advance-turn})]

    (testing "turn number increments"
      (is (= 2 (:turn-number updated))))

    (testing "active player switches"
      (is (= :AWAY (state/get-active-player updated))))))

(deftest set-actions-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/set-actions :player :HOME :amount 5})]
    (is (= 5 (get-in updated [:players :HOME :actions-remaining])))))

(deftest draw-cards-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/draw-cards :player :HOME :count 3})]

    (testing "cards move to hand"
      (is (= 3 (count (state/get-hand updated :HOME)))))

    (testing "draw pile shrinks"
      (is (= 2 (count (state/get-draw-pile updated :HOME)))))

    (testing "correct cards drawn"
      (is (= ["card-1" "card-2" "card-3"] (state/get-hand updated :HOME))))))

(deftest discard-cards-action-test
  (let [game    (-> (state/create-game test-config)
                    (actions/apply-action {:type :bashketball/draw-cards :player :HOME :count 3}))
        updated (actions/apply-action game {:type :bashketball/discard-cards
                                            :player :HOME
                                            :card-slugs ["card-1" "card-3"]})]

    (testing "cards removed from hand"
      (is (= ["card-2"] (state/get-hand updated :HOME))))

    (testing "cards added to discard"
      (is (= ["card-1" "card-3"] (state/get-discard updated :HOME))))))

(deftest move-player-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/move-player
                                            :player-id "home-orc-center-0"
                                            :position [2 3]})]

    (testing "player position updated"
      (is (= [2 3] (:position (state/get-basketball-player updated "home-orc-center-0")))))

    (testing "board occupant set"
      (is (= {:type :BASKETBALL_PLAYER :id "home-orc-center-0"}
             (board/occupant-at (:board updated) [2 3]))))))

(deftest exhaust-refresh-player-test
  (let [game      (state/create-game test-config)
        exhausted (actions/apply-action game {:type :bashketball/exhaust-player
                                              :player-id "home-orc-center-0"})
        refreshed (actions/apply-action exhausted {:type :bashketball/refresh-player
                                                   :player-id "home-orc-center-0"})]

    (testing "exhaust sets exhausted? true"
      (is (:exhausted? (state/get-basketball-player exhausted "home-orc-center-0"))))

    (testing "refresh sets exhausted? false"
      (is (not (:exhausted? (state/get-basketball-player refreshed "home-orc-center-0")))))))

(deftest ball-actions-test
  (let [game (state/create-game test-config)]

    (testing "set-ball-possessed"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-possessed
                                                :holder-id "home-orc-center-0"})]
        (is (= {:status :POSSESSED :holder-id "home-orc-center-0"}
               (state/get-ball updated)))))

    (testing "set-ball-loose"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-loose
                                                :position [3 5]})]
        (is (= {:status :LOOSE :position [3 5]}
               (state/get-ball updated)))))

    (testing "set-ball-in-air"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-in-air
                                                :origin [2 3]
                                                :target [2 13]
                                                :action-type :SHOT})]
        (is (= {:status :IN_AIR :origin [2 3] :target [2 13] :action-type :SHOT}
               (state/get-ball updated)))))))

(deftest add-score-action-test
  (let [game    (state/create-game test-config)
        updated (-> game
                    (actions/apply-action {:type :bashketball/add-score :team :HOME :points 2})
                    (actions/apply-action {:type :bashketball/add-score :team :AWAY :points 3}))]
    (is (= {:HOME 2 :AWAY 3} (state/get-score updated)))))

(deftest add-modifier-action-test
  (let [game     (state/create-game test-config)
        modifier {:id "buff-1" :stat :SHOOTING :amount 2}
        updated  (actions/apply-action game {:type :bashketball/add-modifier
                                             :player-id "home-orc-center-0"
                                             :modifier modifier})
        player   (state/get-basketball-player updated "home-orc-center-0")]
    (is (= [modifier] (:modifiers player)))))

(deftest remove-modifier-action-test
  (let [modifier {:id "buff-1" :stat :SHOOTING :amount 2}
        game     (-> (state/create-game test-config)
                     (actions/apply-action {:type :bashketball/add-modifier
                                            :player-id "home-orc-center-0"
                                            :modifier modifier}))
        updated  (actions/apply-action game {:type :bashketball/remove-modifier
                                             :player-id "home-orc-center-0"
                                             :modifier-id "buff-1"})
        player   (state/get-basketball-player updated "home-orc-center-0")]
    (is (empty? (:modifiers player)))))

(deftest reveal-fate-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/reveal-fate :player :HOME})]

    (testing "top card moved to discard"
      (is (= ["card-1"] (state/get-discard updated :HOME))))

    (testing "draw pile shrinks"
      (is (= 4 (count (state/get-draw-pile updated :HOME)))))))

(deftest stack-actions-test
  (let [game   (state/create-game test-config)
        effect {:id "effect-1" :type :damage :data {:amount 5}}]

    (testing "push-stack"
      (let [updated (actions/apply-action game {:type :bashketball/push-stack :effect effect})]
        (is (= [effect] (:stack updated)))))

    (testing "pop-stack"
      (let [updated (-> game
                        (actions/apply-action {:type :bashketball/push-stack :effect effect})
                        (actions/apply-action {:type :bashketball/pop-stack}))]
        (is (empty? (:stack updated)))))

    (testing "clear-stack"
      (let [updated (-> game
                        (actions/apply-action {:type :bashketball/push-stack :effect effect})
                        (actions/apply-action {:type :bashketball/push-stack :effect effect})
                        (actions/apply-action {:type :bashketball/clear-stack}))]
        (is (empty? (:stack updated)))))))
