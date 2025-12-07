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

    (testing "correct cards drawn by slug"
      (is (= ["card-1" "card-2" "card-3"]
             (mapv :card-slug (state/get-hand updated :HOME)))))

    (testing "drawn cards preserve instance-ids"
      (is (every? :instance-id (state/get-hand updated :HOME))))))

(deftest discard-cards-action-test
  (let [game      (-> (state/create-game test-config)
                      (actions/apply-action {:type :bashketball/draw-cards :player :HOME :count 3}))
        hand      (state/get-hand game :HOME)
        card-1-id (:instance-id (first hand))
        card-3-id (:instance-id (nth hand 2))
        updated   (actions/apply-action game {:type :bashketball/discard-cards
                                              :player :HOME
                                              :instance-ids [card-1-id card-3-id]})]

    (testing "cards removed from hand"
      (is (= ["card-2"] (mapv :card-slug (state/get-hand updated :HOME)))))

    (testing "cards added to discard"
      (is (= ["card-1" "card-3"] (mapv :card-slug (state/get-discard updated :HOME)))))

    (testing "discarded cards retain instance-ids"
      (is (= #{card-1-id card-3-id}
             (set (map :instance-id (state/get-discard updated :HOME))))))))

(deftest move-player-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/move-player
                                            :player-id "HOME-orc-center-0"
                                            :position [2 3]})]

    (testing "player position updated"
      (is (= [2 3] (:position (state/get-basketball-player updated "HOME-orc-center-0")))))

    (testing "board occupant set"
      (is (= {:type :BASKETBALL_PLAYER :id "HOME-orc-center-0"}
             (board/occupant-at (:board updated) [2 3]))))))

(deftest move-player-initial-state-test
  (let [game (state/create-game test-config)]

    (testing "player starts with nil position"
      (is (nil? (:position (state/get-basketball-player game "HOME-orc-center-0")))))

    (testing "board occupants start empty"
      (is (empty? (:occupants (:board game)))))))

(deftest move-player-first-move-test
  (let [game  (state/create-game test-config)
        move1 (actions/apply-action game {:type :bashketball/move-player
                                          :player-id "HOME-orc-center-0"
                                          :position [2 3]})]

    (testing "player position updated"
      (is (= [2 3] (:position (state/get-basketball-player move1 "HOME-orc-center-0")))))

    (testing "board has exactly one occupant"
      (is (= 1 (count (:occupants (:board move1))))))

    (testing "occupant set at new position"
      (is (= {:type :BASKETBALL_PLAYER :id "HOME-orc-center-0"}
             (board/occupant-at (:board move1) [2 3]))))

    (testing "find-occupant returns new position"
      (is (= [2 3] (board/find-occupant (:board move1) "HOME-orc-center-0"))))))

(deftest move-player-consecutive-moves-test
  (let [game  (state/create-game test-config)
        move1 (actions/apply-action game {:type :bashketball/move-player
                                          :player-id "HOME-orc-center-0"
                                          :position [2 3]})
        move2 (actions/apply-action move1 {:type :bashketball/move-player
                                           :player-id "HOME-orc-center-0"
                                           :position [2 5]})]

    (testing "player position updated to new position"
      (is (= [2 5] (:position (state/get-basketball-player move2 "HOME-orc-center-0")))))

    (testing "board still has exactly one occupant"
      (is (= 1 (count (:occupants (:board move2))))))

    (testing "old position is cleared from board"
      (is (nil? (board/occupant-at (:board move2) [2 3]))))

    (testing "new position has occupant on board"
      (is (= {:type :BASKETBALL_PLAYER :id "HOME-orc-center-0"}
             (board/occupant-at (:board move2) [2 5]))))

    (testing "player position matches board find-occupant"
      (is (= [2 5] (board/find-occupant (:board move2) "HOME-orc-center-0"))))))

(deftest move-player-no-duplicate-occupants-test
  (let [game  (state/create-game test-config)
        move1 (actions/apply-action game {:type :bashketball/move-player
                                          :player-id "HOME-orc-center-0"
                                          :position [2 3]})
        move2 (actions/apply-action move1 {:type :bashketball/move-player
                                           :player-id "HOME-orc-center-0"
                                           :position [2 5]})
        move3 (actions/apply-action move2 {:type :bashketball/move-player
                                           :player-id "HOME-orc-center-0"
                                           :position [1 7]})]

    (testing "only one occupant entry for player after multiple moves"
      (let [occupants      (:occupants (:board move3))
            player-entries (filter #(= "HOME-orc-center-0" (:id (val %))) occupants)]
        (is (= 1 (count player-entries)))))

    (testing "find-occupant returns current position"
      (is (= [1 7] (board/find-occupant (:board move3) "HOME-orc-center-0"))))))

(deftest move-multiple-players-test
  (let [game    (state/create-game test-config)
        move-p1 (actions/apply-action game {:type :bashketball/move-player
                                            :player-id "HOME-orc-center-0"
                                            :position [2 3]})
        move-p2 (actions/apply-action move-p1 {:type :bashketball/move-player
                                               :player-id "HOME-elf-point-guard-1"
                                               :position [3 4]})]

    (testing "both players have correct positions"
      (is (= [2 3] (:position (state/get-basketball-player move-p2 "HOME-orc-center-0"))))
      (is (= [3 4] (:position (state/get-basketball-player move-p2 "HOME-elf-point-guard-1")))))

    (testing "both players on board"
      (is (= {:type :BASKETBALL_PLAYER :id "HOME-orc-center-0"}
             (board/occupant-at (:board move-p2) [2 3])))
      (is (= {:type :BASKETBALL_PLAYER :id "HOME-elf-point-guard-1"}
             (board/occupant-at (:board move-p2) [3 4]))))))

(deftest move-player-invariants-test
  (let [game  (state/create-game test-config)
        move1 (actions/apply-action game {:type :bashketball/move-player
                                          :player-id "HOME-orc-center-0"
                                          :position [2 3]})
        move2 (actions/apply-action move1 {:type :bashketball/move-player
                                           :player-id "HOME-orc-center-0"
                                           :position [2 5]})
        move3 (actions/apply-action move2 {:type :bashketball/move-player
                                           :player-id "HOME-elf-point-guard-1"
                                           :position [3 4]})]

    (testing "board satisfies invariants after moves"
      (is (board/valid-occupants? (:board move1)))
      (is (board/valid-occupants? (:board move2)))
      (is (board/valid-occupants? (:board move3))))))

(deftest apply-action-throws-on-invariant-violation-test
  (let [game      (state/create-game test-config)
        ;; Manually corrupt the board by adding duplicate occupant
        corrupted (-> game
                      (update :board board/set-occupant [2 3] {:type :BASKETBALL_PLAYER :id "HOME-orc-center-0"})
                      (update :board board/set-occupant [4 5] {:type :BASKETBALL_PLAYER :id "HOME-orc-center-0"}))]

    (testing "apply-action throws when invariant would be violated"
      (is (thrown-with-msg?
           #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
           #"Board invariant violation"
           (actions/apply-action corrupted {:type :bashketball/set-phase :phase :ACTIONS}))))))

(deftest exhaust-refresh-player-test
  (let [game      (state/create-game test-config)
        exhausted (actions/apply-action game {:type :bashketball/exhaust-player
                                              :player-id "HOME-orc-center-0"})
        refreshed (actions/apply-action exhausted {:type :bashketball/refresh-player
                                                   :player-id "HOME-orc-center-0"})]

    (testing "exhaust sets exhausted? true"
      (is (:exhausted? (state/get-basketball-player exhausted "HOME-orc-center-0"))))

    (testing "refresh sets exhausted? false"
      (is (not (:exhausted? (state/get-basketball-player refreshed "HOME-orc-center-0")))))))

(deftest ball-actions-test
  (let [game (state/create-game test-config)]

    (testing "set-ball-possessed"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-possessed
                                                :holder-id "HOME-orc-center-0"})]
        (is (= {:status :POSSESSED :holder-id "HOME-orc-center-0"}
               (state/get-ball updated)))))

    (testing "set-ball-loose"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-loose
                                                :position [3 5]})]
        (is (= {:status :LOOSE :position [3 5]}
               (state/get-ball updated)))))

    (testing "set-ball-in-air with position target"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-in-air
                                                :origin [2 3]
                                                :target {:type :position :position [2 13]}
                                                :action-type :SHOT})]
        (is (= {:status :IN_AIR :origin [2 3] :target {:type :position :position [2 13]} :action-type :SHOT}
               (state/get-ball updated)))))

    (testing "set-ball-in-air with player target"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-in-air
                                                :origin [2 3]
                                                :target {:type :player :player-id "HOME-1"}
                                                :action-type :PASS})]
        (is (= {:status :IN_AIR :origin [2 3] :target {:type :player :player-id "HOME-1"} :action-type :PASS}
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
                                             :player-id "HOME-orc-center-0"
                                             :modifier modifier})
        player   (state/get-basketball-player updated "HOME-orc-center-0")]
    (is (= [modifier] (:modifiers player)))))

(deftest remove-modifier-action-test
  (let [modifier {:id "buff-1" :stat :SHOOTING :amount 2}
        game     (-> (state/create-game test-config)
                     (actions/apply-action {:type :bashketball/add-modifier
                                            :player-id "HOME-orc-center-0"
                                            :modifier modifier}))
        updated  (actions/apply-action game {:type :bashketball/remove-modifier
                                             :player-id "HOME-orc-center-0"
                                             :modifier-id "buff-1"})
        player   (state/get-basketball-player updated "HOME-orc-center-0")]
    (is (empty? (:modifiers player)))))

(deftest remove-cards-action-test
  (let [game      (-> (state/create-game test-config)
                      (actions/apply-action {:type :bashketball/draw-cards :player :HOME :count 3}))
        hand      (state/get-hand game :HOME)
        card-2-id (:instance-id (second hand))
        updated   (actions/apply-action game {:type :bashketball/remove-cards
                                              :player :HOME
                                              :instance-ids [card-2-id]})]

    (testing "card removed from hand"
      (is (= ["card-1" "card-3"] (mapv :card-slug (state/get-hand updated :HOME)))))

    (testing "card added to removed pile"
      (is (= ["card-2"] (mapv :card-slug (get-in updated [:players :HOME :deck :removed])))))

    (testing "removed card retains instance-id"
      (is (= card-2-id
             (-> updated (get-in [:players :HOME :deck :removed]) first :instance-id))))))

(deftest shuffle-deck-action-test
  (let [game           (state/create-game test-config)
        original-ids   (set (map :instance-id (state/get-draw-pile game :HOME)))
        original-slugs (set (map :card-slug (state/get-draw-pile game :HOME)))
        updated        (actions/apply-action game {:type :bashketball/shuffle-deck :player :HOME})
        shuffled-ids   (set (map :instance-id (state/get-draw-pile updated :HOME)))
        shuffled-slugs (set (map :card-slug (state/get-draw-pile updated :HOME)))]

    (testing "shuffle preserves all instance-ids"
      (is (= original-ids shuffled-ids)))

    (testing "shuffle preserves all card-slugs"
      (is (= original-slugs shuffled-slugs)))

    (testing "draw pile count unchanged"
      (is (= 5 (count (state/get-draw-pile updated :HOME)))))))

(deftest reveal-fate-action-test
  (let [game         (state/create-game test-config)
        original-top (-> game (state/get-draw-pile :HOME) first)
        updated      (actions/apply-action game {:type :bashketball/reveal-fate :player :HOME})]

    (testing "top card moved to discard"
      (is (= ["card-1"] (mapv :card-slug (state/get-discard updated :HOME)))))

    (testing "draw pile shrinks"
      (is (= 4 (count (state/get-draw-pile updated :HOME)))))

    (testing "revealed card retains instance-id"
      (let [discard-id (-> updated (state/get-discard :HOME) first :instance-id)]
        (is (= (:instance-id original-top) discard-id))))

    (testing "event includes revealed card"
      (let [event (last (:events updated))]
        (is (= :bashketball/reveal-fate (:type event)))
        (is (= original-top (:revealed-card event)))))))

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

(deftest play-card-removes-from-hand-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :HOME :count 3}))
        hand   (state/get-hand game :HOME)
        card   (first hand)
        result (actions/apply-action game {:type        :bashketball/play-card
                                           :player      :HOME
                                           :instance-id (:instance-id card)})]
    (is (= 2 (count (state/get-hand result :HOME))))
    (is (not (some #(= (:instance-id %) (:instance-id card))
                   (state/get-hand result :HOME))))))

(deftest play-card-adds-to-discard-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :HOME :count 3}))
        hand   (state/get-hand game :HOME)
        card   (first hand)
        result (actions/apply-action game {:type        :bashketball/play-card
                                           :player      :HOME
                                           :instance-id (:instance-id card)})]
    (is (= 1 (count (state/get-discard result :HOME))))
    (is (= card (first (state/get-discard result :HOME))))))

(deftest play-card-logs-event-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :HOME :count 3}))
        hand   (state/get-hand game :HOME)
        card   (first hand)
        result (actions/apply-action game {:type        :bashketball/play-card
                                           :player      :HOME
                                           :instance-id (:instance-id card)})
        event  (last (:events result))]
    (is (= :bashketball/play-card (:type event)))
    (is (= card (:played-card event)))))
