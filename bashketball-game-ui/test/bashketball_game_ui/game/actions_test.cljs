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
