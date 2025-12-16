(ns bashketball-game.actions-test
  (:require [bashketball-game.actions :as actions]
            [bashketball-game.board :as board]
            [bashketball-game.state :as state]
            [clojure.test :refer [deftest is testing]]))

(def test-config
  {:home {:deck ["card-1" "card-2" "card-3" "card-4" "card-5"]
          :players [{:card-slug "orc-center"
                     :name "Grukk"
                     :stats {:size :size/LG :speed 2 :shooting 2 :passing 1 :defense 4}}
                    {:card-slug "elf-point-guard"
                     :name "Lyria"
                     :stats {:size :size/SM :speed 5 :shooting 3 :passing 4 :defense 2}}
                    {:card-slug "dwarf-power-forward"
                     :name "Thorin"
                     :stats {:size :size/MD :speed 2 :shooting 3 :passing 2 :defense 4}}]}
   :away {:deck ["card-a" "card-b" "card-c" "card-d" "card-e"]
          :players [{:card-slug "troll-center"
                     :name "Grok"
                     :stats {:size :size/LG :speed 1 :shooting 1 :passing 1 :defense 5}}
                    {:card-slug "goblin-shooting-guard"
                     :name "Sneek"
                     :stats {:size :size/SM :speed 4 :shooting 4 :passing 3 :defense 1}}
                    {:card-slug "human-small-forward"
                     :name "John"
                     :stats {:size :size/MD :speed 3 :shooting 3 :passing 3 :defense 3}}]}})

(deftest apply-action-validation-test
  (let [game (state/create-game test-config)]

    (testing "valid action is applied"
      (is (actions/apply-action game {:type :bashketball/set-phase :phase :phase/ACTIONS})))

    (testing "invalid action throws"
      (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                   (actions/apply-action game {:type :bashketball/set-phase :phase :invalid}))))))

(deftest apply-action-event-logging-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/set-phase :phase :phase/ACTIONS})]

    (testing "event is logged"
      (is (= 1 (count (:events updated)))))

    (testing "event has timestamp"
      (is (string? (:timestamp (first (:events updated))))))

    (testing "event has action data"
      (is (= :bashketball/set-phase (:type (first (:events updated))))))))

(deftest set-phase-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/set-phase :phase :phase/ACTIONS})]
    (is (= :phase/ACTIONS (state/get-phase updated)))))

(deftest advance-turn-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/advance-turn})]

    (testing "turn number increments"
      (is (= 2 (:turn-number updated))))

    (testing "active player switches"
      (is (= :team/AWAY (state/get-active-player updated))))))

(deftest set-actions-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/set-actions :player :team/HOME :amount 5})]
    (is (= 5 (get-in updated [:players :team/HOME :actions-remaining])))))

(deftest draw-cards-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/draw-cards :player :team/HOME :count 3})]

    (testing "cards move to hand"
      (is (= 3 (count (state/get-hand updated :team/HOME)))))

    (testing "draw pile shrinks"
      (is (= 2 (count (state/get-draw-pile updated :team/HOME)))))

    (testing "correct cards drawn by slug"
      (is (= ["card-1" "card-2" "card-3"]
             (mapv :card-slug (state/get-hand updated :team/HOME)))))

    (testing "drawn cards preserve instance-ids"
      (is (every? :instance-id (state/get-hand updated :team/HOME))))))

(deftest discard-cards-action-test
  (let [game      (-> (state/create-game test-config)
                      (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand      (state/get-hand game :team/HOME)
        card-1-id (:instance-id (first hand))
        card-3-id (:instance-id (nth hand 2))
        updated   (actions/apply-action game {:type :bashketball/discard-cards
                                              :player :team/HOME
                                              :instance-ids [card-1-id card-3-id]})]

    (testing "cards removed from hand"
      (is (= ["card-2"] (mapv :card-slug (state/get-hand updated :team/HOME)))))

    (testing "cards added to discard"
      (is (= ["card-1" "card-3"] (mapv :card-slug (state/get-discard updated :team/HOME)))))

    (testing "discarded cards retain instance-ids"
      (is (= #{card-1-id card-3-id}
             (set (map :instance-id (state/get-discard updated :team/HOME))))))))

(deftest move-player-action-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type :bashketball/move-player
                                            :player-id "HOME-orc-center-0"
                                            :position [2 3]})]

    (testing "player position updated"
      (is (= [2 3] (:position (state/get-basketball-player updated "HOME-orc-center-0")))))

    (testing "board occupant set"
      (is (= {:type :occupant/BASKETBALL_PLAYER :id "HOME-orc-center-0"}
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
      (is (= {:type :occupant/BASKETBALL_PLAYER :id "HOME-orc-center-0"}
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
      (is (= {:type :occupant/BASKETBALL_PLAYER :id "HOME-orc-center-0"}
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
      (is (= {:type :occupant/BASKETBALL_PLAYER :id "HOME-orc-center-0"}
             (board/occupant-at (:board move-p2) [2 3])))
      (is (= {:type :occupant/BASKETBALL_PLAYER :id "HOME-elf-point-guard-1"}
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
                      (update :board board/set-occupant [2 3] {:type :occupant/BASKETBALL_PLAYER :id "HOME-orc-center-0"})
                      (update :board board/set-occupant [4 5] {:type :occupant/BASKETBALL_PLAYER :id "HOME-orc-center-0"}))]

    (testing "apply-action throws when invariant would be violated"
      (is (thrown-with-msg?
           #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
           #"Board invariant violation"
           (actions/apply-action corrupted {:type :bashketball/set-phase :phase :phase/ACTIONS}))))))

(deftest exhaust-refresh-player-test
  (let [game      (state/create-game test-config)
        exhausted (actions/apply-action game {:type :bashketball/exhaust-player
                                              :player-id "HOME-orc-center-0"})
        refreshed (actions/apply-action exhausted {:type :bashketball/refresh-player
                                                   :player-id "HOME-orc-center-0"})]

    (testing "exhaust sets exhausted? true"
      (is (:exhausted (state/get-basketball-player exhausted "HOME-orc-center-0"))))

    (testing "refresh sets exhausted? false"
      (is (not (:exhausted (state/get-basketball-player refreshed "HOME-orc-center-0")))))))

(deftest ball-actions-test
  (let [game (state/create-game test-config)]

    (testing "set-ball-possessed"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-possessed
                                                :holder-id "HOME-orc-center-0"})]
        (is (= {:status :ball-status/POSSESSED :holder-id "HOME-orc-center-0"}
               (state/get-ball updated)))))

    (testing "set-ball-loose"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-loose
                                                :position [3 5]})]
        (is (= {:status :ball-status/LOOSE :position [3 5]}
               (state/get-ball updated)))))

    (testing "set-ball-in-air with position target"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-in-air
                                                :origin [2 3]
                                                :target {:type :position :position [2 13]}
                                                :action-type :ball-action/SHOT})]
        (is (= {:status :ball-status/IN_AIR :origin [2 3] :target {:type :position :position [2 13]} :action-type :ball-action/SHOT}
               (state/get-ball updated)))))

    (testing "set-ball-in-air with player target"
      (let [updated (actions/apply-action game {:type :bashketball/set-ball-in-air
                                                :origin [2 3]
                                                :target {:type :player :player-id "HOME-1"}
                                                :action-type :ball-action/PASS})]
        (is (= {:status :ball-status/IN_AIR :origin [2 3] :target {:type :player :player-id "HOME-1"} :action-type :ball-action/PASS}
               (state/get-ball updated)))))))

(deftest add-score-action-test
  (let [game    (state/create-game test-config)
        updated (-> game
                    (actions/apply-action {:type :bashketball/add-score :team :team/HOME :points 2})
                    (actions/apply-action {:type :bashketball/add-score :team :team/AWAY :points 3}))]
    (is (= {:team/HOME 2 :team/AWAY 3} (state/get-score updated)))))

(deftest add-modifier-action-test
  (let [game     (state/create-game test-config)
        modifier {:id "buff-1" :stat :stat/SHOOTING :amount 2}
        updated  (actions/apply-action game {:type :bashketball/add-modifier
                                             :player-id "HOME-orc-center-0"
                                             :modifier modifier})
        player   (state/get-basketball-player updated "HOME-orc-center-0")]
    (is (= [modifier] (:modifiers player)))))

(deftest remove-modifier-action-test
  (let [modifier {:id "buff-1" :stat :stat/SHOOTING :amount 2}
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
                      (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand      (state/get-hand game :team/HOME)
        card-2-id (:instance-id (second hand))
        updated   (actions/apply-action game {:type :bashketball/remove-cards
                                              :player :team/HOME
                                              :instance-ids [card-2-id]})]

    (testing "card removed from hand"
      (is (= ["card-1" "card-3"] (mapv :card-slug (state/get-hand updated :team/HOME)))))

    (testing "card added to removed pile"
      (is (= ["card-2"] (mapv :card-slug (get-in updated [:players :team/HOME :deck :removed])))))

    (testing "removed card retains instance-id"
      (is (= card-2-id
             (-> updated (get-in [:players :team/HOME :deck :removed]) first :instance-id))))))

(deftest shuffle-deck-action-test
  (let [game           (state/create-game test-config)
        original-ids   (set (map :instance-id (state/get-draw-pile game :team/HOME)))
        original-slugs (set (map :card-slug (state/get-draw-pile game :team/HOME)))
        updated        (actions/apply-action game {:type :bashketball/shuffle-deck :player :team/HOME})
        shuffled-ids   (set (map :instance-id (state/get-draw-pile updated :team/HOME)))
        shuffled-slugs (set (map :card-slug (state/get-draw-pile updated :team/HOME)))]

    (testing "shuffle preserves all instance-ids"
      (is (= original-ids shuffled-ids)))

    (testing "shuffle preserves all card-slugs"
      (is (= original-slugs shuffled-slugs)))

    (testing "draw pile count unchanged"
      (is (= 5 (count (state/get-draw-pile updated :team/HOME)))))))

(deftest reveal-fate-action-test
  (let [game         (state/create-game test-config)
        original-top (-> game (state/get-draw-pile :team/HOME) first)
        updated      (actions/apply-action game {:type :bashketball/reveal-fate :player :team/HOME})]

    (testing "top card moved to discard"
      (is (= ["card-1"] (mapv :card-slug (state/get-discard updated :team/HOME)))))

    (testing "draw pile shrinks"
      (is (= 4 (count (state/get-draw-pile updated :team/HOME)))))

    (testing "revealed card retains instance-id"
      (let [discard-id (-> updated (state/get-discard :team/HOME) first :instance-id)]
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
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card   (first hand)
        result (actions/apply-action game {:type        :bashketball/play-card
                                           :player      :team/HOME
                                           :instance-id (:instance-id card)})]
    (is (= 2 (count (state/get-hand result :team/HOME))))
    (is (not (some #(= (:instance-id %) (:instance-id card))
                   (state/get-hand result :team/HOME))))))

(deftest play-card-adds-to-discard-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card   (first hand)
        result (actions/apply-action game {:type        :bashketball/play-card
                                           :player      :team/HOME
                                           :instance-id (:instance-id card)})]
    (is (= 1 (count (state/get-discard result :team/HOME))))
    (is (= card (first (state/get-discard result :team/HOME))))))

(deftest play-card-logs-event-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card   (first hand)
        result (actions/apply-action game {:type        :bashketball/play-card
                                           :player      :team/HOME
                                           :instance-id (:instance-id card)})
        event  (last (:events result))]
    (is (= :bashketball/play-card (:type event)))
    (is (= card (:played-card event)))))

(deftest play-card-team-asset-goes-to-assets-test
  (let [card-instance {:instance-id "asset-1"
                       :card-slug   "team-asset-speed"}
        card-catalog  [{:slug      "team-asset-speed"
                        :name      "Speed Boost"
                        :card-type :card-type/TEAM_ASSET_CARD}]
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance])
                          (assoc-in [:players :team/HOME :deck :cards] card-catalog))
        result        (actions/apply-action game {:type        :bashketball/play-card
                                                  :player      :team/HOME
                                                  :instance-id "asset-1"})]

    (testing "team asset added to assets"
      (is (= [card-instance] (get-in result [:players :team/HOME :assets]))))

    (testing "team asset not in discard"
      (is (empty? (state/get-discard result :team/HOME))))))

(deftest play-card-non-asset-goes-to-discard-test
  (let [card-instance {:instance-id "play-1"
                       :card-slug   "fast-break"}
        card-catalog  [{:slug      "fast-break"
                        :name      "Fast Break"
                        :card-type :card-type/PLAY_CARD}]
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance])
                          (assoc-in [:players :team/HOME :deck :cards] card-catalog))
        result        (actions/apply-action game {:type        :bashketball/play-card
                                                  :player      :team/HOME
                                                  :instance-id "play-1"})]

    (testing "non-asset card not in assets"
      (is (empty? (get-in result [:players :team/HOME :assets]))))

    (testing "non-asset card in discard"
      (is (= [card-instance] (state/get-discard result :team/HOME))))))

;; -----------------------------------------------------------------------------
;; Stage Card Action Tests

(deftest stage-card-removes-from-hand-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card   (first hand)
        result (actions/apply-action game {:type        :bashketball/stage-card
                                           :player      :team/HOME
                                           :instance-id (:instance-id card)})]
    (is (= 2 (count (state/get-hand result :team/HOME))))
    (is (not (some #(= (:instance-id %) (:instance-id card))
                   (state/get-hand result :team/HOME))))))

(deftest stage-card-adds-to-play-area-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card   (first hand)
        result (actions/apply-action game {:type        :bashketball/stage-card
                                           :player      :team/HOME
                                           :instance-id (:instance-id card)})]
    (is (= 1 (count (:play-area result))))
    (let [staged (first (:play-area result))]
      (is (= (:instance-id card) (:instance-id staged)))
      (is (= (:card-slug card) (:card-slug staged)))
      (is (= :team/HOME (:played-by staged))))))

(deftest stage-card-logs-event-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card   (first hand)
        result (actions/apply-action game {:type        :bashketball/stage-card
                                           :player      :team/HOME
                                           :instance-id (:instance-id card)})
        event  (last (:events result))]
    (is (= :bashketball/stage-card (:type event)))
    (is (= card (:staged-card event)))))

;; -----------------------------------------------------------------------------
;; Resolve Card Action Tests

(deftest resolve-card-removes-from-play-area-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card   (first hand)
        staged (actions/apply-action game {:type        :bashketball/stage-card
                                           :player      :team/HOME
                                           :instance-id (:instance-id card)})
        result (actions/apply-action staged {:type        :bashketball/resolve-card
                                             :instance-id (:instance-id card)})]
    (is (empty? (:play-area result)))))

(deftest resolve-card-moves-to-discard-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card   (first hand)
        staged (actions/apply-action game {:type        :bashketball/stage-card
                                           :player      :team/HOME
                                           :instance-id (:instance-id card)})
        result (actions/apply-action staged {:type        :bashketball/resolve-card
                                             :instance-id (:instance-id card)})]
    (is (= 1 (count (state/get-discard result :team/HOME))))
    (is (= (:card-slug card) (:card-slug (first (state/get-discard result :team/HOME)))))))

(deftest resolve-card-team-asset-goes-to-assets-test
  (let [card-instance {:instance-id "asset-1" :card-slug "team-asset-speed"}
        card-catalog  [{:slug      "team-asset-speed"
                        :name      "Speed Boost"
                        :card-type :card-type/TEAM_ASSET_CARD}]
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance])
                          (assoc-in [:players :team/HOME :deck :cards] card-catalog))
        staged        (actions/apply-action game {:type        :bashketball/stage-card
                                                  :player      :team/HOME
                                                  :instance-id "asset-1"})
        result        (actions/apply-action staged {:type        :bashketball/resolve-card
                                                    :instance-id "asset-1"})]
    (is (= 1 (count (get-in result [:players :team/HOME :assets]))))
    (is (empty? (state/get-discard result :team/HOME)))))

(deftest resolve-card-logs-event-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card   (first hand)
        staged (actions/apply-action game {:type        :bashketball/stage-card
                                           :player      :team/HOME
                                           :instance-id (:instance-id card)})
        result (actions/apply-action staged {:type        :bashketball/resolve-card
                                             :instance-id (:instance-id card)})
        event  (last (:events result))]
    (is (= :bashketball/resolve-card (:type event)))
    (is (some? (:resolved-card event)))))

;; -----------------------------------------------------------------------------
;; Move Asset Action Tests

(deftest move-asset-to-discard-test
  (let [card-instance {:instance-id "asset-1" :card-slug "team-asset-speed"}
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :assets] [card-instance]))
        result        (actions/apply-action game {:type        :bashketball/move-asset
                                                  :player      :team/HOME
                                                  :instance-id "asset-1"
                                                  :destination :DISCARD})]

    (testing "asset removed from assets"
      (is (empty? (get-in result [:players :team/HOME :assets]))))

    (testing "asset added to discard"
      (is (= [card-instance] (state/get-discard result :team/HOME))))))

(deftest move-asset-to-removed-test
  (let [card-instance {:instance-id "asset-1" :card-slug "team-asset-speed"}
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :assets] [card-instance]))
        result        (actions/apply-action game {:type        :bashketball/move-asset
                                                  :player      :team/HOME
                                                  :instance-id "asset-1"
                                                  :destination :REMOVED})]

    (testing "asset removed from assets"
      (is (empty? (get-in result [:players :team/HOME :assets]))))

    (testing "asset added to removed zone"
      (is (= [card-instance] (get-in result [:players :team/HOME :deck :removed]))))))

(deftest move-asset-logs-event-test
  (let [card-instance {:instance-id "asset-1" :card-slug "team-asset-speed"}
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :assets] [card-instance]))
        result        (actions/apply-action game {:type        :bashketball/move-asset
                                                  :player      :team/HOME
                                                  :instance-id "asset-1"
                                                  :destination :DISCARD})
        event         (last (:events result))]
    (is (= :bashketball/move-asset (:type event)))
    (is (= card-instance (:moved-asset event)))
    (is (= :DISCARD (:destination event)))))

;; -----------------------------------------------------------------------------
;; Attach Ability Action Tests

(deftest attach-ability-removes-from-hand-test
  (let [card-instance {:instance-id "ability-1" :card-slug "power-shot"}
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance]))
        result        (actions/apply-action game {:type            :bashketball/attach-ability
                                                  :player          :team/HOME
                                                  :instance-id     "ability-1"
                                                  :target-player-id "HOME-orc-center-0"})]
    (is (empty? (state/get-hand result :team/HOME)))))

(deftest attach-ability-adds-to-player-test
  (let [card-instance {:instance-id "ability-1" :card-slug "power-shot"}
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance]))
        result        (actions/apply-action game {:type            :bashketball/attach-ability
                                                  :player          :team/HOME
                                                  :instance-id     "ability-1"
                                                  :target-player-id "HOME-orc-center-0"})
        attachments   (state/get-attachments result "HOME-orc-center-0")]
    (is (= 1 (count attachments)))
    (is (= "ability-1" (:instance-id (first attachments))))
    (is (= "power-shot" (:card-slug (first attachments))))))

(deftest attach-ability-default-removable-test
  (let [card-instance {:instance-id "ability-1" :card-slug "power-shot"}
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance]))
        result        (actions/apply-action game {:type            :bashketball/attach-ability
                                                  :player          :team/HOME
                                                  :instance-id     "ability-1"
                                                  :target-player-id "HOME-orc-center-0"})
        attachment    (first (state/get-attachments result "HOME-orc-center-0"))]
    (is (true? (:removable attachment)))
    (is (= :detach/DISCARD (:detach-destination attachment)))))

(deftest attach-ability-uses-card-properties-test
  (let [card-instance {:instance-id "ability-1" :card-slug "power-shot"}
        card-catalog  [{:slug              "power-shot"
                        :removable        false
                        :detach-destination :detach/REMOVED}]
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance])
                          (assoc-in [:players :team/HOME :deck :cards] card-catalog))
        result        (actions/apply-action game {:type            :bashketball/attach-ability
                                                  :player          :team/HOME
                                                  :instance-id     "ability-1"
                                                  :target-player-id "HOME-orc-center-0"})
        attachment    (first (state/get-attachments result "HOME-orc-center-0"))]
    (is (false? (:removable attachment)))
    (is (= :detach/REMOVED (:detach-destination attachment)))))

(deftest attach-ability-logs-event-test
  (let [card-instance {:instance-id "ability-1" :card-slug "power-shot"}
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance]))
        result        (actions/apply-action game {:type            :bashketball/attach-ability
                                                  :player          :team/HOME
                                                  :instance-id     "ability-1"
                                                  :target-player-id "HOME-orc-center-0"})
        event         (last (:events result))]
    (is (= :bashketball/attach-ability (:type event)))
    (is (= card-instance (:attached-card event)))
    (is (= "HOME-orc-center-0" (:target-player-id event)))))

;; -----------------------------------------------------------------------------
;; Detach Ability Action Tests

(deftest detach-ability-removes-from-player-test
  (let [attachment {:instance-id       "ability-1"
                    :card-slug         "power-shot"
                    :removable        true
                    :detach-destination :detach/DISCARD
                    :attached-at       "2024-01-01T00:00:00Z"}
        game       (-> (state/create-game test-config)
                       (state/update-basketball-player "HOME-orc-center-0"
                                                       assoc :attachments [attachment]))
        result     (actions/apply-action game {:type            :bashketball/detach-ability
                                               :player          :team/HOME
                                               :target-player-id "HOME-orc-center-0"
                                               :instance-id     "ability-1"})]
    (is (empty? (state/get-attachments result "HOME-orc-center-0")))))

(deftest detach-ability-goes-to-discard-test
  (let [attachment {:instance-id       "ability-1"
                    :card-slug         "power-shot"
                    :removable        true
                    :detach-destination :detach/DISCARD
                    :attached-at       "2024-01-01T00:00:00Z"}
        game       (-> (state/create-game test-config)
                       (state/update-basketball-player "HOME-orc-center-0"
                                                       assoc :attachments [attachment]))
        result     (actions/apply-action game {:type            :bashketball/detach-ability
                                               :player          :team/HOME
                                               :target-player-id "HOME-orc-center-0"
                                               :instance-id     "ability-1"})
        discard    (state/get-discard result :team/HOME)]
    (is (= 1 (count discard)))
    (is (= "ability-1" (:instance-id (first discard))))
    (is (= "power-shot" (:card-slug (first discard))))))

(deftest detach-ability-goes-to-removed-test
  (let [attachment {:instance-id       "ability-1"
                    :card-slug         "power-shot"
                    :removable        true
                    :detach-destination :detach/REMOVED
                    :attached-at       "2024-01-01T00:00:00Z"}
        game       (-> (state/create-game test-config)
                       (state/update-basketball-player "HOME-orc-center-0"
                                                       assoc :attachments [attachment]))
        result     (actions/apply-action game {:type            :bashketball/detach-ability
                                               :player          :team/HOME
                                               :target-player-id "HOME-orc-center-0"
                                               :instance-id     "ability-1"})
        removed    (get-in result [:players :team/HOME :deck :removed])]
    (is (= 1 (count removed)))
    (is (= "ability-1" (:instance-id (first removed))))))

(deftest detach-ability-logs-event-test
  (let [attachment {:instance-id       "ability-1"
                    :card-slug         "power-shot"
                    :removable        true
                    :detach-destination :detach/DISCARD
                    :attached-at       "2024-01-01T00:00:00Z"}
        game       (-> (state/create-game test-config)
                       (state/update-basketball-player "HOME-orc-center-0"
                                                       assoc :attachments [attachment]))
        result     (actions/apply-action game {:type            :bashketball/detach-ability
                                               :player          :team/HOME
                                               :target-player-id "HOME-orc-center-0"
                                               :instance-id     "ability-1"})
        event      (last (:events result))]
    (is (= :bashketball/detach-ability (:type event)))
    (is (= attachment (:detached-card event)))
    (is (= "HOME-orc-center-0" (:target-player-id event)))
    (is (= :detach/DISCARD (:destination event)))))

(deftest substitute-preserves-attachments-test
  (let [attachment  {:instance-id       "ability-1"
                     :card-slug         "power-shot"
                     :removable        true
                     :detach-destination :detach/DISCARD
                     :attached-at       "2024-01-01T00:00:00Z"}
        game        (-> (state/create-game test-config)
                        (state/update-basketball-player "HOME-orc-center-0"
                                                        assoc :attachments [attachment])
                        (actions/apply-action {:type :bashketball/move-player
                                               :player-id "HOME-orc-center-0"
                                               :position [2 3]}))
        result      (actions/apply-action game {:type         :bashketball/substitute
                                                :on-court-id  "HOME-orc-center-0"
                                                :off-court-id "HOME-dwarf-power-forward-2"})
        attachments (state/get-attachments result "HOME-orc-center-0")]
    (is (= 1 (count attachments)))
    (is (= "ability-1" (:instance-id (first attachments))))))

;; -----------------------------------------------------------------------------
;; Token Card Action Tests

(deftest create-token-to-assets-test
  (let [token-card {:slug      "speed-boost-token"
                    :name      "Speed Boost"
                    :card-type :card-type/TEAM_ASSET_CARD}
        game       (state/create-game test-config)
        result     (actions/apply-action game {:type      :bashketball/create-token
                                               :player    :team/HOME
                                               :card      token-card
                                               :placement :placement/ASSET})
        assets     (get-in result [:players :team/HOME :assets])]

    (testing "token added to assets"
      (is (= 1 (count assets))))

    (testing "token has token? flag"
      (is (true? (:token (first assets)))))

    (testing "token has inline card definition with defaults filled in"
      (let [stored-card (:card (first assets))]
        (is (= "speed-boost-token" (:slug stored-card)))
        (is (= "Speed Boost" (:name stored-card)))
        (is (= :card-type/TEAM_ASSET_CARD (:card-type stored-card)))
        (is (= "tokens" (:set-slug stored-card)))
        (is (= "" (:asset-power stored-card)))))

    (testing "token has instance-id"
      (is (string? (:instance-id (first assets)))))))

(deftest create-token-attach-test
  (let [token-card  {:slug               "shield-token"
                     :name               "Shield"
                     :card-type          :card-type/ABILITY_CARD
                     :removable          false
                     :detach-destination :detach/REMOVED}
        game        (state/create-game test-config)
        result      (actions/apply-action game {:type             :bashketball/create-token
                                                :player           :team/HOME
                                                :card             token-card
                                                :placement        :placement/ATTACH
                                                :target-player-id "HOME-orc-center-0"})
        attachments (state/get-attachments result "HOME-orc-center-0")]

    (testing "token attached to player"
      (is (= 1 (count attachments))))

    (testing "attachment has token? flag"
      (is (true? (:token (first attachments)))))

    (testing "attachment has inline card definition with defaults"
      (let [stored-card (:card (first attachments))]
        (is (= "shield-token" (:slug stored-card)))
        (is (= "Shield" (:name stored-card)))
        (is (= :card-type/ABILITY_CARD (:card-type stored-card)))
        (is (= "tokens" (:set-slug stored-card)))
        (is (= 0 (:fate stored-card)))
        (is (= [] (:abilities stored-card)))))

    (testing "attachment uses card properties"
      (is (false? (:removable (first attachments))))
      (is (= :detach/REMOVED (:detach-destination (first attachments)))))))

(deftest create-token-logs-event-test
  (let [token-card {:slug "test-token" :name "Test" :card-type :card-type/TEAM_ASSET_CARD}
        game       (state/create-game test-config)
        result     (actions/apply-action game {:type      :bashketball/create-token
                                               :player    :team/HOME
                                               :card      token-card
                                               :placement :placement/ASSET})
        event      (last (:events result))]

    (testing "event type is create-token"
      (is (= :bashketball/create-token (:type event))))

    (testing "event includes created token"
      (is (some? (:created-token event)))
      (is (true? (:token (:created-token event)))))

    (testing "event includes placement"
      (is (= :placement/ASSET (:placement event))))))

(deftest detach-token-deletes-entirely-test
  (let [token-attachment {:instance-id        "token-1"
                          :token             true
                          :card               {:slug "shield-token"}
                          :removable         true
                          :detach-destination :detach/DISCARD
                          :attached-at        "2024-01-01T00:00:00Z"}
        game             (-> (state/create-game test-config)
                             (state/update-basketball-player "HOME-orc-center-0"
                                                             assoc :attachments [token-attachment]))
        result           (actions/apply-action game {:type             :bashketball/detach-ability
                                                     :player           :team/HOME
                                                     :target-player-id "HOME-orc-center-0"
                                                     :instance-id      "token-1"})]

    (testing "token removed from player"
      (is (empty? (state/get-attachments result "HOME-orc-center-0"))))

    (testing "token NOT added to discard"
      (is (empty? (state/get-discard result :team/HOME))))

    (testing "token NOT added to removed"
      (is (empty? (get-in result [:players :team/HOME :deck :removed]))))))

(deftest detach-token-event-shows-deleted-test
  (let [token-attachment {:instance-id        "token-1"
                          :token             true
                          :card               {:slug "shield-token"}
                          :removable         true
                          :detach-destination :detach/DISCARD
                          :attached-at        "2024-01-01T00:00:00Z"}
        game             (-> (state/create-game test-config)
                             (state/update-basketball-player "HOME-orc-center-0"
                                                             assoc :attachments [token-attachment]))
        result           (actions/apply-action game {:type             :bashketball/detach-ability
                                                     :player           :team/HOME
                                                     :target-player-id "HOME-orc-center-0"
                                                     :instance-id      "token-1"})
        event            (last (:events result))]

    (testing "event shows destination as deleted"
      (is (= :deleted (:destination event))))

    (testing "event shows token-deleted flag"
      (is (true? (:token-deleted? event))))))

(deftest detach-regular-card-still-moves-test
  (let [attachment {:instance-id        "ability-1"
                    :card-slug          "power-shot"
                    :removable         true
                    :detach-destination :detach/DISCARD
                    :attached-at        "2024-01-01T00:00:00Z"}
        game       (-> (state/create-game test-config)
                       (state/update-basketball-player "HOME-orc-center-0"
                                                       assoc :attachments [attachment]))
        result     (actions/apply-action game {:type             :bashketball/detach-ability
                                               :player           :team/HOME
                                               :target-player-id "HOME-orc-center-0"
                                               :instance-id      "ability-1"})]

    (testing "regular card still goes to discard"
      (is (= 1 (count (state/get-discard result :team/HOME)))))))

(deftest move-token-asset-deletes-entirely-test
  (let [token-asset {:instance-id "token-1"
                     :token      true
                     :card        {:slug "speed-token" :card-type :card-type/TEAM_ASSET_CARD}}
        game        (-> (state/create-game test-config)
                        (assoc-in [:players :team/HOME :assets] [token-asset]))
        result      (actions/apply-action game {:type        :bashketball/move-asset
                                                :player      :team/HOME
                                                :instance-id "token-1"
                                                :destination :DISCARD})]

    (testing "token removed from assets"
      (is (empty? (get-in result [:players :team/HOME :assets]))))

    (testing "token NOT added to discard"
      (is (empty? (state/get-discard result :team/HOME))))

    (testing "token NOT added to removed"
      (is (empty? (get-in result [:players :team/HOME :deck :removed]))))))

(deftest move-token-asset-event-shows-deleted-test
  (let [token-asset {:instance-id "token-1"
                     :token      true
                     :card        {:slug "speed-token"}}
        game        (-> (state/create-game test-config)
                        (assoc-in [:players :team/HOME :assets] [token-asset]))
        result      (actions/apply-action game {:type        :bashketball/move-asset
                                                :player      :team/HOME
                                                :instance-id "token-1"
                                                :destination :DISCARD})
        event       (last (:events result))]

    (testing "event shows destination as deleted"
      (is (= :deleted (:destination event))))

    (testing "event shows token-deleted flag"
      (is (true? (:token-deleted? event))))))

(deftest move-regular-asset-still-moves-test
  (let [card-instance {:instance-id "asset-1" :card-slug "team-asset-speed"}
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :assets] [card-instance]))
        result        (actions/apply-action game {:type        :bashketball/move-asset
                                                  :player      :team/HOME
                                                  :instance-id "asset-1"
                                                  :destination :DISCARD})]

    (testing "regular asset still goes to discard"
      (is (= 1 (count (state/get-discard result :team/HOME)))))))

;; -----------------------------------------------------------------------------
;; Resolve Ability Card from Play Area Tests

(deftest resolve-ability-card-attaches-to-player-test
  (let [card-instance {:instance-id "ability-1" :card-slug "power-shot"}
        card-catalog  [{:slug      "power-shot"
                        :name      "Power Shot"
                        :card-type :card-type/ABILITY_CARD
                        :fate      2}]
        game          (-> (state/create-game test-config)
                          (assoc-in [:players :team/HOME :deck :hand] [card-instance])
                          (assoc-in [:players :team/HOME :deck :cards] card-catalog))
        staged        (actions/apply-action game {:type        :bashketball/stage-card
                                                  :player      :team/HOME
                                                  :instance-id "ability-1"})
        result        (actions/apply-action staged {:type             :bashketball/resolve-card
                                                    :instance-id      "ability-1"
                                                    :target-player-id "HOME-orc-center-0"})]

    (testing "ability card removed from play area"
      (is (empty? (:play-area result))))

    (testing "ability card attached to target player"
      (let [attachments (state/get-attachments result "HOME-orc-center-0")]
        (is (= 1 (count attachments)))
        (is (= "ability-1" (:instance-id (first attachments))))
        (is (= "power-shot" (:card-slug (first attachments))))))

    (testing "ability card not in discard"
      (is (empty? (state/get-discard result :team/HOME))))))

;; -----------------------------------------------------------------------------
;; Virtual Standard Action Tests

(deftest stage-virtual-standard-action-discards-two-cards-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card-1 (:instance-id (first hand))
        card-2 (:instance-id (second hand))
        result (actions/apply-action game {:type                 :bashketball/stage-virtual-standard-action
                                           :player               :team/HOME
                                           :discard-instance-ids [card-1 card-2]
                                           :card-slug            "shoot-block"})]

    (testing "two cards removed from hand"
      (is (= 1 (count (state/get-hand result :team/HOME)))))

    (testing "two cards added to discard"
      (is (= 2 (count (state/get-discard result :team/HOME)))))))

(deftest stage-virtual-standard-action-creates-virtual-card-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card-1 (:instance-id (first hand))
        card-2 (:instance-id (second hand))
        result (actions/apply-action game {:type                 :bashketball/stage-virtual-standard-action
                                           :player               :team/HOME
                                           :discard-instance-ids [card-1 card-2]
                                           :card-slug            "shoot-block"})]

    (testing "play area has one card"
      (is (= 1 (count (:play-area result)))))

    (testing "play area card has :virtual true"
      (is (true? (:virtual (first (:play-area result))))))

    (testing "play area card has correct slug"
      (is (= "shoot-block" (:card-slug (first (:play-area result))))))

    (testing "play area card has correct owner"
      (is (= :team/HOME (:played-by (first (:play-area result))))))))

(deftest stage-virtual-standard-action-event-data-test
  (let [game   (-> (state/create-game test-config)
                   (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand   (state/get-hand game :team/HOME)
        card-1 (:instance-id (first hand))
        card-2 (:instance-id (second hand))
        result (actions/apply-action game {:type                 :bashketball/stage-virtual-standard-action
                                           :player               :team/HOME
                                           :discard-instance-ids [card-1 card-2]
                                           :card-slug            "shoot-block"})
        event  (last (:events result))]

    (testing "event has discarded cards"
      (is (= 2 (count (:discarded-cards event)))))

    (testing "event has virtual card"
      (is (:virtual-card event)))))

(deftest resolve-virtual-card-disappears-test
  (let [game       (-> (state/create-game test-config)
                       (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand       (state/get-hand game :team/HOME)
        card-1     (:instance-id (first hand))
        card-2     (:instance-id (second hand))
        staged     (actions/apply-action game {:type                 :bashketball/stage-virtual-standard-action
                                               :player               :team/HOME
                                               :discard-instance-ids [card-1 card-2]
                                               :card-slug            "shoot-block"})
        virtual-id (:instance-id (first (:play-area staged)))
        result     (actions/apply-action staged {:type        :bashketball/resolve-card
                                                 :instance-id virtual-id})]

    (testing "virtual card removed from play area"
      (is (empty? (:play-area result))))

    (testing "virtual card NOT added to discard"
      (is (= 2 (count (state/get-discard result :team/HOME)))))

    (testing "virtual card NOT added to assets"
      (is (empty? (get-in result [:players :team/HOME :assets]))))

    (testing "virtual card NOT added to removed"
      (is (empty? (get-in result [:players :team/HOME :deck :removed]))))))

(deftest resolve-virtual-card-event-shows-virtual-flag-test
  (let [game       (-> (state/create-game test-config)
                       (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand       (state/get-hand game :team/HOME)
        card-1     (:instance-id (first hand))
        card-2     (:instance-id (second hand))
        staged     (actions/apply-action game {:type                 :bashketball/stage-virtual-standard-action
                                               :player               :team/HOME
                                               :discard-instance-ids [card-1 card-2]
                                               :card-slug            "shoot-block"})
        virtual-id (:instance-id (first (:play-area staged)))
        result     (actions/apply-action staged {:type        :bashketball/resolve-card
                                                 :instance-id virtual-id})
        event      (last (:events result))]

    (testing "event shows virtual flag"
      (is (true? (:virtual event))))))

;; -----------------------------------------------------------------------------
;; Missing Card Validation Tests

(deftest stage-card-throws-when-card-not-in-hand-test
  (let [game (-> (state/create-game test-config)
                 (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
         #"Card not in hand"
         (actions/apply-action game {:type        :bashketball/stage-card
                                     :player      :team/HOME
                                     :instance-id "nonexistent-card-id"})))))

(deftest stage-card-throws-after-discard-test
  (let [game      (-> (state/create-game test-config)
                      (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand      (state/get-hand game :team/HOME)
        card-id   (:instance-id (first hand))
        discarded (actions/apply-action game {:type         :bashketball/discard-cards
                                              :player       :team/HOME
                                              :instance-ids [card-id]})]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
         #"Card not in hand"
         (actions/apply-action discarded {:type        :bashketball/stage-card
                                          :player      :team/HOME
                                          :instance-id card-id})))))

(deftest play-card-throws-when-card-not-in-hand-test
  (let [game (-> (state/create-game test-config)
                 (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
         #"Card not in hand"
         (actions/apply-action game {:type        :bashketball/play-card
                                     :player      :team/HOME
                                     :instance-id "nonexistent-card-id"})))))

(deftest play-card-throws-after-discard-test
  (let [game      (-> (state/create-game test-config)
                      (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 3}))
        hand      (state/get-hand game :team/HOME)
        card-id   (:instance-id (first hand))
        discarded (actions/apply-action game {:type         :bashketball/discard-cards
                                              :player       :team/HOME
                                              :instance-ids [card-id]})]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
         #"Card not in hand"
         (actions/apply-action discarded {:type        :bashketball/play-card
                                          :player      :team/HOME
                                          :instance-id card-id})))))

;; -----------------------------------------------------------------------------
;; Examine Cards Action Tests

(deftest examine-cards-moves-to-examined-zone-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type   :bashketball/examine-cards
                                            :player :team/HOME
                                            :count  3})]
    (testing "cards moved to examined zone"
      (is (= 3 (count (state/get-examined updated :team/HOME)))))

    (testing "draw pile shrinks"
      (is (= 2 (count (state/get-draw-pile updated :team/HOME)))))

    (testing "correct cards examined by slug"
      (is (= ["card-1" "card-2" "card-3"]
             (mapv :card-slug (state/get-examined updated :team/HOME)))))))

(deftest examine-cards-event-logging-test
  (let [game    (state/create-game test-config)
        updated (actions/apply-action game {:type   :bashketball/examine-cards
                                            :player :team/HOME
                                            :count  3})
        event   (last (:events updated))]
    (is (= :bashketball/examine-cards (:type event)))
    (is (= 3 (:requested-count event)))
    (is (= 3 (:actual-count event)))
    (is (= 3 (count (:examined-cards event))))))

(deftest examine-cards-partial-deck-test
  (let [game    (-> (state/create-game test-config)
                    (actions/apply-action {:type :bashketball/draw-cards :player :team/HOME :count 4}))
        updated (actions/apply-action game {:type   :bashketball/examine-cards
                                            :player :team/HOME
                                            :count  3})]
    (testing "examines available cards only"
      (is (= 1 (count (state/get-examined updated :team/HOME)))))

    (testing "draw pile empty"
      (is (empty? (state/get-draw-pile updated :team/HOME))))

    (testing "event shows actual vs requested"
      (let [event (last (:events updated))]
        (is (= 3 (:requested-count event)))
        (is (= 1 (:actual-count event)))))))

(deftest resolve-examined-to-top-test
  (let [game     (state/create-game test-config)
        examined (actions/apply-action game {:type   :bashketball/examine-cards
                                             :player :team/HOME
                                             :count  3})
        cards    (state/get-examined examined :team/HOME)
        resolved (actions/apply-action examined
                                       {:type       :bashketball/resolve-examined-cards
                                        :player     :team/HOME
                                        :placements [{:instance-id (:instance-id (nth cards 2))
                                                      :destination :examine/TOP}
                                                     {:instance-id (:instance-id (nth cards 0))
                                                      :destination :examine/TOP}
                                                     {:instance-id (:instance-id (nth cards 1))
                                                      :destination :examine/DISCARD}]})]
    (testing "examined zone cleared"
      (is (empty? (state/get-examined resolved :team/HOME))))

    (testing "top cards on deck in order"
      (is (= ["card-3" "card-1" "card-4" "card-5"]
             (mapv :card-slug (state/get-draw-pile resolved :team/HOME)))))

    (testing "discarded card in discard"
      (is (= ["card-2"]
             (mapv :card-slug (state/get-discard resolved :team/HOME)))))))

(deftest resolve-examined-to-bottom-test
  (let [game     (state/create-game test-config)
        examined (actions/apply-action game {:type   :bashketball/examine-cards
                                             :player :team/HOME
                                             :count  2})
        cards    (state/get-examined examined :team/HOME)
        resolved (actions/apply-action examined
                                       {:type       :bashketball/resolve-examined-cards
                                        :player     :team/HOME
                                        :placements [{:instance-id (:instance-id (first cards))
                                                      :destination :examine/BOTTOM}
                                                     {:instance-id (:instance-id (second cards))
                                                      :destination :examine/BOTTOM}]})]
    (testing "cards at bottom of deck in order"
      (let [pile (state/get-draw-pile resolved :team/HOME)]
        (is (= "card-1" (:card-slug (nth pile 3))))
        (is (= "card-2" (:card-slug (nth pile 4))))))))

(deftest resolve-examined-all-destinations-test
  (let [game     (state/create-game test-config)
        examined (actions/apply-action game {:type   :bashketball/examine-cards
                                             :player :team/HOME
                                             :count  3})
        cards    (state/get-examined examined :team/HOME)
        resolved (actions/apply-action examined
                                       {:type       :bashketball/resolve-examined-cards
                                        :player     :team/HOME
                                        :placements [{:instance-id (:instance-id (first cards))
                                                      :destination :examine/TOP}
                                                     {:instance-id (:instance-id (second cards))
                                                      :destination :examine/BOTTOM}
                                                     {:instance-id (:instance-id (nth cards 2))
                                                      :destination :examine/DISCARD}]})]
    (testing "top card is first in draw pile"
      (is (= "card-1" (:card-slug (first (state/get-draw-pile resolved :team/HOME))))))

    (testing "bottom card is last in draw pile"
      (is (= "card-2" (:card-slug (last (state/get-draw-pile resolved :team/HOME))))))

    (testing "discarded card in discard"
      (is (= ["card-3"] (mapv :card-slug (state/get-discard resolved :team/HOME)))))))

(deftest resolve-examined-event-logging-test
  (let [game     (state/create-game test-config)
        examined (actions/apply-action game {:type   :bashketball/examine-cards
                                             :player :team/HOME
                                             :count  3})
        cards    (state/get-examined examined :team/HOME)
        resolved (actions/apply-action examined
                                       {:type       :bashketball/resolve-examined-cards
                                        :player     :team/HOME
                                        :placements [{:instance-id (:instance-id (first cards))
                                                      :destination :examine/TOP}
                                                     {:instance-id (:instance-id (second cards))
                                                      :destination :examine/BOTTOM}
                                                     {:instance-id (:instance-id (nth cards 2))
                                                      :destination :examine/DISCARD}]})
        event    (last (:events resolved))]
    (is (= :bashketball/resolve-examined-cards (:type event)))
    (is (= 3 (count (:resolved-placements event))))
    (is (= 1 (:top-count event)))
    (is (= 1 (:bottom-count event)))
    (is (= 1 (:discard-count event)))))

(deftest resolve-examined-missing-placements-throws-test
  (let [game     (state/create-game test-config)
        examined (actions/apply-action game {:type   :bashketball/examine-cards
                                             :player :team/HOME
                                             :count  3})
        cards    (state/get-examined examined :team/HOME)]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
         #"Placements must include all examined cards"
         (actions/apply-action examined
                               {:type       :bashketball/resolve-examined-cards
                                :player     :team/HOME
                                :placements [{:instance-id (:instance-id (first cards))
                                              :destination :examine/TOP}]})))))

(deftest resolve-examined-extra-placements-throws-test
  (let [game     (state/create-game test-config)
        examined (actions/apply-action game {:type   :bashketball/examine-cards
                                             :player :team/HOME
                                             :count  2})
        cards    (state/get-examined examined :team/HOME)]
    (is (thrown-with-msg?
         #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
         #"Placements must include all examined cards"
         (actions/apply-action examined
                               {:type       :bashketball/resolve-examined-cards
                                :player     :team/HOME
                                :placements [{:instance-id (:instance-id (first cards))
                                              :destination :examine/TOP}
                                             {:instance-id (:instance-id (second cards))
                                              :destination :examine/TOP}
                                             {:instance-id "fake-id"
                                              :destination :examine/DISCARD}]})))))
