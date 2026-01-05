(ns bashketball-game.polix.turn-events-test
  "Tests for event-driven turn and phase transitions."
  (:require
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.fixtures :as fixtures]
   [bashketball-game.polix.game-rules :as game-rules]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [f]
    (polix/initialize!)
    (f)))

;;; ---------------------------------------------------------------------------
;;; Phase Transition Tests
;;; ---------------------------------------------------------------------------

(deftest transition-phase-effect-fires-events-test
  (let [game     (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        fired    (atom [])
        _        (fx/register-effect! :test/capture-phase-event
                                      (fn [s _params ctx _opts]
                                        (swap! fired conj (get-in ctx [:event :event-type]))
                                        (fx/success s [])))
        registry (-> registry
                     (triggers/register-trigger
                      {:event-types #{:bashketball/phase-starting.request}
                       :timing :polix.triggers.timing/after
                       :priority 50
                       :effect {:type :test/capture-phase-event}}
                      "test-capture" nil nil))
        result   (fx/apply-effect game
                                  {:type :bashketball/transition-phase
                                   :to-phase :phase/TIP_OFF}
                                  {}
                                  {:registry registry})]
    (testing "fires phase-starting.request event"
      (is (some #{:bashketball/phase-starting.request} @fired)))

    (testing "sets phase via catchall rule"
      (is (= :phase/TIP_OFF (:phase (:state result)))))))

(deftest transition-phase-from-existing-phase-test
  (let [game     (-> (fixtures/base-game-state)
                     (assoc :phase :phase/SETUP))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        fired    (atom [])
        _        (fx/register-effect! :test/capture-ending
                                      (fn [s _params _ctx _opts]
                                        (swap! fired conj :ending)
                                        (fx/success s [])))
        _        (fx/register-effect! :test/capture-starting
                                      (fn [s _params _ctx _opts]
                                        (swap! fired conj :starting)
                                        (fx/success s [])))
        registry (-> registry
                     (triggers/register-trigger
                      {:event-types #{:bashketball/phase-ending.request}
                       :timing :polix.triggers.timing/after
                       :priority 50
                       :effect {:type :test/capture-ending}}
                      "test-ending" nil nil)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/phase-starting.request}
                       :timing :polix.triggers.timing/after
                       :priority 50
                       :effect {:type :test/capture-starting}}
                      "test-starting" nil nil))
        result   (fx/apply-effect game
                                  {:type :bashketball/transition-phase
                                   :to-phase :phase/TIP_OFF}
                                  {}
                                  {:registry registry})]
    (testing "fires both ending and starting events"
      (is (= [:ending :starting] @fired)))

    (testing "sets new phase"
      (is (= :phase/TIP_OFF (:phase (:state result)))))))

;;; ---------------------------------------------------------------------------
;;; Turn Transition Tests
;;; ---------------------------------------------------------------------------

(deftest end-turn-effect-fires-events-test
  (let [game     (-> (fixtures/base-game-state)
                     (assoc :phase :phase/END_OF_TURN))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        fired    (atom [])
        _        (fx/register-effect! :test/capture-turn-event
                                      (fn [s _params ctx _opts]
                                        (swap! fired conj (get-in ctx [:event :event-type]))
                                        (fx/success s [])))
        registry (-> registry
                     (triggers/register-trigger
                      {:event-types #{:bashketball/turn-ending.request}
                       :timing :polix.triggers.timing/after
                       :priority 50
                       :effect {:type :test/capture-turn-event}}
                      "test-ending" nil nil)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/turn-starting.request}
                       :timing :polix.triggers.timing/after
                       :priority 50
                       :effect {:type :test/capture-turn-event}}
                      "test-starting" nil nil))
        _        (fx/apply-effect game
                                  {:type :bashketball/end-turn}
                                  {}
                                  {:registry registry})]
    (testing "fires turn-ending and turn-starting events"
      (is (= [:bashketball/turn-ending.request
              :bashketball/turn-starting.request]
             @fired)))))

(deftest end-turn-advances-turn-counter-test
  (let [game     (-> (fixtures/base-game-state)
                     (assoc :phase :phase/END_OF_TURN)
                     (assoc :turn-number 5)
                     (assoc :active-player :team/HOME))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/end-turn}
                                  {}
                                  {:registry registry})]
    (testing "increments turn number"
      (is (= 6 (:turn-number (:state result)))))

    (testing "swaps active player"
      (is (= :team/AWAY (:active-player (:state result)))))))

(deftest end-turn-auto-sequences-to-upkeep-test
  (let [game     (-> (fixtures/base-game-state)
                     (assoc :phase :phase/END_OF_TURN))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/end-turn}
                                  {}
                                  {:registry registry})]
    (testing "transitions to UPKEEP phase"
      (is (= :phase/UPKEEP (:phase (:state result)))))))

;;; ---------------------------------------------------------------------------
;;; Catchall Rule Tests
;;; ---------------------------------------------------------------------------

(deftest phase-starting-rule-sets-phase-test
  (let [game     (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (triggers/fire-request-event
                  {:state game :registry registry}
                  {:event-type :bashketball/phase-starting.request
                   :to-phase :phase/ACTIONS})]
    (testing "catchall rule sets the phase"
      (is (= :phase/ACTIONS (:phase (:state result)))))))

(deftest turn-ending-rule-advances-turn-test
  (let [game     (-> (fixtures/base-game-state)
                     (assoc :turn-number 3)
                     (assoc :active-player :team/AWAY))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (triggers/fire-request-event
                  {:state game :registry registry}
                  {:event-type :bashketball/turn-ending.request})]
    (testing "catchall rule advances turn"
      (is (= 4 (:turn-number (:state result)))))

    (testing "catchall rule swaps active player"
      (is (= :team/HOME (:active-player (:state result)))))))

;;; ---------------------------------------------------------------------------
;;; Function Registry Integration Tests
;;; ---------------------------------------------------------------------------

(deftest draw-cards-uses-draw-count-function-test
  (let [game     (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (triggers/fire-request-event
                  {:state game :registry registry}
                  {:event-type :bashketball/draw-cards.request
                   :player :team/HOME
                   :count 3})
        hand     (state/get-hand (:state result) :team/HOME)]
    (testing "draws the correct number of cards"
      (is (= 3 (count hand))))))

(deftest hand-limit-check-effect-detects-excess-test
  (let [cards    (vec (for [i (range 10)] {:instance-id (str "card-" i) :card-slug (str "test-" i)}))
        game     (assoc-in (fixtures/base-game-state)
                           [:players :team/HOME :deck :hand] cards)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/check-hand-limit
                                   :team :team/HOME}
                                  {}
                                  {:registry registry})]
    (testing "offers discard choice when over limit"
      (is (some? (get-in (:state result) [:pending-choice])))
      (is (= :discard-to-hand-limit (get-in (:state result) [:pending-choice :type]))))

    (testing "returns pending flag for choice"
      (is (= :choice (get-in result [:pending :type]))))))

(deftest hand-limit-check-effect-no-action-when-under-limit-test
  (let [game     (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/check-hand-limit
                                   :team :team/HOME}
                                  {}
                                  {:registry registry})]
    (testing "does not offer choice when under limit"
      (is (nil? (get-in (:state result) [:pending-choice]))))))
