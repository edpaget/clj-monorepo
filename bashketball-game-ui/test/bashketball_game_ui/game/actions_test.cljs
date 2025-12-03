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
   :ball {:status :possessed :holder-id "player-1"}
   :players {:home {:id :home
                    :team {:players {"player-1" {:id "player-1" :name "PG"}
                                     "player-2" {:id "player-2" :name "SG"}}}}
             :away {:id :away
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
  (let [state (assoc sample-game-state :ball {:status :loose :position [1 1]})]
    (t/is (nil? (actions/get-ball-holder-position state)))))

(t/deftest ball-held-by-team-home-test
  (t/is (actions/ball-held-by-team? sample-game-state :home)))

(t/deftest ball-held-by-team-away-test
  (t/is (not (actions/ball-held-by-team? sample-game-state :away))))

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
  (t/is (actions/can-pass? sample-game-state :home)))

(t/deftest can-pass-without-ball-test
  (t/is (not (actions/can-pass? sample-game-state :away))))

(t/deftest can-shoot-near-hoop-test
  (let [state (-> sample-game-state
                  (update :board board/move-occupant [2 5] [2 12]))]
    (t/is (actions/can-shoot? state :home))))

(t/deftest can-shoot-too-far-test
  (t/is (not (actions/can-shoot? sample-game-state :home))))

;; =============================================================================
;; Valid moves tests
;; =============================================================================

(t/deftest valid-move-positions-returns-set-test
  (let [moves (actions/valid-move-positions sample-game-state "player-1")]
    (t/is (set? moves))
    (t/is (not (empty? moves)))))

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
  (let [action (actions/make-draw-cards-action :home 3)]
    (t/is (= "bashketball/draw-cards" (:type action)))
    (t/is (= "home" (:player action)))
    (t/is (= 3 (:count action)))))

(t/deftest make-discard-cards-action-test
  (let [action (actions/make-discard-cards-action :away ["card-1" "card-2"])]
    (t/is (= "bashketball/discard-cards" (:type action)))
    (t/is (= "away" (:player action)))
    (t/is (= ["card-1" "card-2"] (:card-slugs action)))))

;; =============================================================================
;; Setup mode tests
;; =============================================================================

(def setup-game-state
  "Game state for setup phase testing with starters."
  {:board (board/create-board)
   :phase :setup
   :ball {:status :loose :position [2 7]}
   :players {:home {:id :home
                    :team {:starters ["home-pg-0" "home-sg-1" "home-c-2"]
                           :bench ["home-pf-3"]
                           :players {"home-pg-0" {:id "home-pg-0" :name "PG" :position nil}
                                     "home-sg-1" {:id "home-sg-1" :name "SG" :position nil}
                                     "home-c-2" {:id "home-c-2" :name "C" :position nil}
                                     "home-pf-3" {:id "home-pf-3" :name "PF" :position nil}}}}
             :away {:id :away
                    :team {:starters ["away-pg-0" "away-sg-1" "away-c-2"]
                           :bench []
                           :players {"away-pg-0" {:id "away-pg-0" :name "PG" :position nil}
                                     "away-sg-1" {:id "away-sg-1" :name "SG" :position nil}
                                     "away-c-2" {:id "away-c-2" :name "C" :position nil}}}}}})

(t/deftest valid-setup-positions-home-test
  (let [positions (actions/valid-setup-positions setup-game-state :home)]
    (t/is (set? positions))
    (t/is (not (empty? positions)))
    ;; Home team should only have positions in rows 0-6
    (t/is (every? (fn [[_q r]] (<= r 6)) positions))
    ;; Should not include hoops
    (t/is (not (contains? positions [2 0])))))

(t/deftest valid-setup-positions-away-test
  (let [positions (actions/valid-setup-positions setup-game-state :away)]
    (t/is (set? positions))
    (t/is (not (empty? positions)))
    ;; Away team should only have positions in rows 7-13
    (t/is (every? (fn [[_q r]] (>= r 7)) positions))
    ;; Should not include hoops
    (t/is (not (contains? positions [2 13])))))

(t/deftest valid-setup-positions-excludes-occupied-test
  (let [state (update setup-game-state :board board/set-occupant [2 3] {:type :basketball-player :id "p1"})
        positions (actions/valid-setup-positions state :home)]
    (t/is (not (contains? positions [2 3])))))

(t/deftest unplaced-starters-all-unplaced-test
  (let [unplaced (actions/unplaced-starters setup-game-state :home)]
    (t/is (= 3 (count unplaced)))
    (t/is (contains? unplaced "home-pg-0"))
    (t/is (contains? unplaced "home-sg-1"))
    (t/is (contains? unplaced "home-c-2"))))

(t/deftest unplaced-starters-one-placed-test
  (let [state (assoc-in setup-game-state [:players :home :team :players "home-pg-0" :position] [2 3])
        unplaced (actions/unplaced-starters state :home)]
    (t/is (= 2 (count unplaced)))
    (t/is (not (contains? unplaced "home-pg-0")))
    (t/is (contains? unplaced "home-sg-1"))))

(t/deftest all-starters-placed-none-placed-test
  (t/is (not (actions/all-starters-placed? setup-game-state :home))))

(t/deftest all-starters-placed-some-placed-test
  (let [state (-> setup-game-state
                  (assoc-in [:players :home :team :players "home-pg-0" :position] [2 3])
                  (assoc-in [:players :home :team :players "home-sg-1" :position] [3 3]))]
    (t/is (not (actions/all-starters-placed? state :home)))))

(t/deftest all-starters-placed-all-placed-test
  (let [state (-> setup-game-state
                  (assoc-in [:players :home :team :players "home-pg-0" :position] [2 3])
                  (assoc-in [:players :home :team :players "home-sg-1" :position] [3 3])
                  (assoc-in [:players :home :team :players "home-c-2" :position] [1 3]))]
    (t/is (actions/all-starters-placed? state :home))))
