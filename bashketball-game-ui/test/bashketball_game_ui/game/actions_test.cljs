(ns bashketball-game-ui.game.actions-test
  "Tests for game action helpers."
  (:require
   [bashketball-game-ui.game.actions :as actions]
   [bashketball-game.board :as board]
   [cljs.test :as t :include-macros true]))

;; =============================================================================
;; Test fixtures
;; =============================================================================

(def sample-board
  (-> (board/create-board)
      (board/set-occupant [2 5] {:type :basketball-player :id "player-1"})
      (board/set-occupant [3 5] {:type :basketball-player :id "player-2"})))

(def sample-game-state
  {:board sample-board
   :ball {:__typename "BallPossessed" :holder-id "player-1"}
   :players {:HOME {:id :HOME
                    :team {:players {"player-1" {:id "player-1" :name "PG"}
                                     "player-2" {:id "player-2" :name "SG"}}}}
             :AWAY {:id :AWAY
                    :team {:players {"player-3" {:id "player-3" :name "PG"}
                                     "player-4" {:id "player-4" :name "SG"}}}}}
   :exhausted-players #{}})

;; =============================================================================
;; Ball state tests
;; =============================================================================

(t/deftest get-ball-holder-position-possessed-test
  (let [pos (actions/get-ball-holder-position sample-game-state)]
    (t/is (= [2 5] pos))))

(t/deftest get-ball-holder-position-loose-test
  (let [state (assoc sample-game-state :ball {:__typename "BallLoose" :position [1 1]})]
    (t/is (nil? (actions/get-ball-holder-position state)))))

(t/deftest ball-held-by-team-home-test
  (t/is (actions/ball-held-by-team? sample-game-state :HOME)))

(t/deftest ball-held-by-team-away-test
  (t/is (not (actions/ball-held-by-team? sample-game-state :AWAY))))

(t/deftest player-has-ball-test
  (t/is (actions/player-has-ball? sample-game-state "player-1"))
  (t/is (not (actions/player-has-ball? sample-game-state "player-2"))))

;; =============================================================================
;; Player state tests
;; =============================================================================

(t/deftest player-exhausted-false-by-default-test
  (t/is (not (actions/player-exhausted? sample-game-state "player-1"))))

(t/deftest player-exhausted-true-when-in-set-test
  (let [state (assoc sample-game-state :exhausted-players #{"player-1"})]
    (t/is (actions/player-exhausted? state "player-1"))))

;; =============================================================================
;; Can move tests
;; =============================================================================

(t/deftest can-move-player-valid-position-test
  (t/is (actions/can-move-player? sample-game-state "player-1" [2 6])))

(t/deftest can-move-player-occupied-position-test
  (t/is (not (actions/can-move-player? sample-game-state "player-1" [3 5]))))

(t/deftest can-move-player-too-far-test
  (t/is (not (actions/can-move-player? sample-game-state "player-1" [2 10]))))

(t/deftest can-move-player-exhausted-test
  (let [state (assoc sample-game-state :exhausted-players #{"player-1"})]
    (t/is (not (actions/can-move-player? state "player-1" [2 6])))))

;; =============================================================================
;; Can shoot/pass tests
;; =============================================================================

(t/deftest can-pass-with-ball-test
  (t/is (actions/can-pass? sample-game-state :HOME)))

(t/deftest can-pass-without-ball-test
  (t/is (not (actions/can-pass? sample-game-state :AWAY))))

(t/deftest can-shoot-near-hoop-test
  (let [state (-> sample-game-state
                  (update :board board/move-occupant [2 5] [2 12]))]
    (t/is (actions/can-shoot? state :HOME))))

(t/deftest can-shoot-too-far-test
  (t/is (not (actions/can-shoot? sample-game-state :HOME))))

;; =============================================================================
;; Valid moves tests
;; =============================================================================

(t/deftest valid-move-positions-returns-set-test
  (let [moves (actions/valid-move-positions sample-game-state "player-1")]
    (t/is (set? moves))
    (t/is (seq moves))))

(t/deftest valid-move-positions-excludes-occupied-test
  (let [moves (actions/valid-move-positions sample-game-state "player-1")]
    (t/is (not (contains? moves [3 5])))))

(t/deftest valid-move-positions-excludes-current-test
  (let [moves (actions/valid-move-positions sample-game-state "player-1")]
    (t/is (not (contains? moves [2 5])))))

(t/deftest valid-move-positions-nil-when-exhausted-test
  (let [state (assoc sample-game-state :exhausted-players #{"player-1"})
        moves (actions/valid-move-positions state "player-1")]
    (t/is (nil? moves))))

;; =============================================================================
;; Action construction tests
;; =============================================================================

(t/deftest make-move-action-test
  (let [action (actions/make-move-action "player-1" [2 6])]
    (t/is (= "bashketball/move-player" (:type action)))
    (t/is (= "player-1" (:player-id action)))
    (t/is (= [2 6] (:position action)))))

(t/deftest make-shoot-action-test
  (let [action (actions/make-shoot-action [2 10] [2 13])]
    (t/is (= "bashketball/set-ball-in-air" (:type action)))
    (t/is (= [2 10] (:origin action)))
    (t/is (= [2 13] (:target action)))
    (t/is (= "shot" (:action-type action)))))

(t/deftest make-pass-action-position-test
  (let [action (actions/make-pass-action [2 5] [3 5])]
    (t/is (= "bashketball/set-ball-in-air" (:type action)))
    (t/is (= [2 5] (:origin action)))
    (t/is (= [3 5] (:target action)))
    (t/is (= "pass" (:action-type action)))))

(t/deftest make-pass-action-player-id-test
  (let [action (actions/make-pass-action [2 5] "player-2")]
    (t/is (= "player-2" (:target action)))))

(t/deftest make-end-turn-action-test
  (let [action (actions/make-end-turn-action)]
    (t/is (= "bashketball/advance-turn" (:type action)))))

(t/deftest make-set-phase-action-test
  (let [action (actions/make-set-phase-action :actions)]
    (t/is (= "bashketball/set-phase" (:type action)))
    (t/is (= "actions" (:phase action)))))

(t/deftest make-draw-cards-action-test
  (let [action (actions/make-draw-cards-action :HOME 3)]
    (t/is (= "bashketball/draw-cards" (:type action)))
    (t/is (= "HOME" (:player action)))
    (t/is (= 3 (:count action)))))

(t/deftest make-discard-cards-action-test
  (let [action (actions/make-discard-cards-action :AWAY ["uuid-1" "uuid-2"])]
    (t/is (= "bashketball/discard-cards" (:type action)))
    (t/is (= "AWAY" (:player action)))
    (t/is (= ["uuid-1" "uuid-2"] (:instance-ids action)))))

;; =============================================================================
;; Setup mode tests
;; =============================================================================

(def setup-game-state
  "Game state for setup phase testing with starters."
  {:board (board/create-board)
   :phase :setup
   :ball {:__typename "BallLoose" :position [2 7]}
   :players {:HOME {:id :HOME
                    :team {:starters ["HOME-pg-0" "HOME-sg-1" "HOME-c-2"]
                           :bench ["HOME-pf-3"]
                           :players {"HOME-pg-0" {:id "HOME-pg-0" :name "PG" :position nil}
                                     "HOME-sg-1" {:id "HOME-sg-1" :name "SG" :position nil}
                                     "HOME-c-2" {:id "HOME-c-2" :name "C" :position nil}
                                     "HOME-pf-3" {:id "HOME-pf-3" :name "PF" :position nil}}}}
             :AWAY {:id :AWAY
                    :team {:starters ["AWAY-pg-0" "AWAY-sg-1" "AWAY-c-2"]
                           :bench []
                           :players {"AWAY-pg-0" {:id "AWAY-pg-0" :name "PG" :position nil}
                                     "AWAY-sg-1" {:id "AWAY-sg-1" :name "SG" :position nil}
                                     "AWAY-c-2" {:id "AWAY-c-2" :name "C" :position nil}}}}}})

(t/deftest valid-setup-positions-home-test
  (let [positions (actions/valid-setup-positions setup-game-state :HOME)]
    (t/is (set? positions))
    (t/is (seq positions))
    ;; Home team should only have positions in rows 0-6
    (t/is (every? (fn [[_q r]] (<= r 6)) positions))
    ;; Should not include hoops
    (t/is (not (contains? positions [2 0])))))

(t/deftest valid-setup-positions-away-test
  (let [positions (actions/valid-setup-positions setup-game-state :AWAY)]
    (t/is (set? positions))
    (t/is (seq positions))
    ;; Away team should only have positions in rows 7-13
    (t/is (every? (fn [[_q r]] (>= r 7)) positions))
    ;; Should not include hoops
    (t/is (not (contains? positions [2 13])))))

(t/deftest valid-setup-positions-excludes-occupied-test
  (let [state     (update setup-game-state :board board/set-occupant [2 3] {:type :basketball-player :id "p1"})
        positions (actions/valid-setup-positions state :HOME)]
    (t/is (not (contains? positions [2 3])))))

(t/deftest unplaced-starters-all-unplaced-test
  (let [unplaced (actions/unplaced-starters setup-game-state :HOME)]
    (t/is (= 3 (count unplaced)))
    (t/is (contains? unplaced "HOME-pg-0"))
    (t/is (contains? unplaced "HOME-sg-1"))
    (t/is (contains? unplaced "HOME-c-2"))))

(t/deftest unplaced-starters-one-placed-test
  (let [state    (assoc-in setup-game-state [:players :HOME :team :players "HOME-pg-0" :position] [2 3])
        unplaced (actions/unplaced-starters state :HOME)]
    (t/is (= 2 (count unplaced)))
    (t/is (not (contains? unplaced "HOME-pg-0")))
    (t/is (contains? unplaced "HOME-sg-1"))))

(t/deftest all-starters-placed-none-placed-test
  (t/is (not (actions/all-starters-placed? setup-game-state :HOME))))

(t/deftest all-starters-placed-some-placed-test
  (let [state (-> setup-game-state
                  (assoc-in [:players :HOME :team :players "HOME-pg-0" :position] [2 3])
                  (assoc-in [:players :HOME :team :players "HOME-sg-1" :position] [3 3]))]
    (t/is (not (actions/all-starters-placed? state :HOME)))))

(t/deftest all-starters-placed-all-placed-test
  (let [state (-> setup-game-state
                  (assoc-in [:players :HOME :team :players "HOME-pg-0" :position] [2 3])
                  (assoc-in [:players :HOME :team :players "HOME-sg-1" :position] [3 3])
                  (assoc-in [:players :HOME :team :players "HOME-c-2" :position] [1 3]))]
    (t/is (actions/all-starters-placed? state :HOME))))

;; =============================================================================
;; Speed-based movement tests
;; =============================================================================

(def speed-test-board
  (-> (board/create-board)
      (board/set-occupant [2 7] {:type :basketball-player :id "fast-player"})
      (board/set-occupant [2 5] {:type :basketball-player :id "slow-player"})))

(def speed-test-state
  {:board speed-test-board
   :ball {:__typename "BallLoose" :position [0 7]}
   :players {:HOME {:id :HOME
                    :team {:players {"fast-player" {:id "fast-player"
                                                    :name "Fast"
                                                    :stats {:speed 4}}
                                     "slow-player" {:id "slow-player"
                                                    :name "Slow"
                                                    :stats {:speed 1}}}}}}
   :exhausted-players #{}})

(t/deftest valid-move-positions-respects-high-speed-test
  (let [moves (actions/valid-move-positions speed-test-state "fast-player")]
    (t/is (contains? moves [2 11]))
    (t/is (contains? moves [2 3]))))

(t/deftest valid-move-positions-respects-low-speed-test
  (let [moves (actions/valid-move-positions speed-test-state "slow-player")]
    (t/is (contains? moves [2 6]))
    (t/is (contains? moves [2 4]))
    (t/is (not (contains? moves [2 7])))))

(t/deftest can-move-player-respects-high-speed-test
  (t/is (actions/can-move-player? speed-test-state "fast-player" [2 11])))

(t/deftest can-move-player-respects-low-speed-test
  (t/is (not (actions/can-move-player? speed-test-state "slow-player" [2 7]))))

(t/deftest valid-move-positions-defaults-to-speed-2-test
  (let [state (assoc-in speed-test-state
                        [:players :HOME :team :players "no-stats"]
                        {:id "no-stats" :name "NoStats"})
        state (update state :board board/set-occupant [1 7] {:type :basketball-player :id "no-stats"})
        moves (actions/valid-move-positions state "no-stats")]
    (t/is (contains? moves [1 9]))
    (t/is (not (contains? moves [1 10])))))

(t/deftest valid-move-positions-boundary-check-test
  (let [moves (actions/valid-move-positions speed-test-state "slow-player")]
    (t/is (not (contains? moves [2 3])))
    (t/is (not (contains? moves [2 7])))))

(t/deftest valid-move-positions-exact-range-test
  (let [board (-> (board/create-board)
                  (board/set-occupant [2 7] {:type :basketball-player :id "test-player"}))
        state {:board board
               :players {:HOME {:id :HOME
                                :team {:players {"test-player" {:id "test-player"
                                                                :name "Test"
                                                                :stats {:speed 2}}}}}}
               :exhausted-players #{}}
        moves (actions/valid-move-positions state "test-player")]
    (t/is (contains? moves [2 5]))
    (t/is (contains? moves [2 9]))
    (t/is (not (contains? moves [2 4])))
    (t/is (not (contains? moves [2 10])))
    (t/is (not (contains? moves [2 11])))))
