(ns bashketball-game-ui.game.substitute-machine-test
  "Tests for the substitute state machine."
  (:require
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t :include-macros true])
   [bashketball-game-ui.game.substitute-machine :as sm]))

;; =============================================================================
;; Init tests
;; =============================================================================

(t/deftest init-returns-inactive-state-test
  (let [result (sm/init)]
    (t/is (= :inactive (:state result)))
    (t/is (nil? (:data result)))))

;; =============================================================================
;; Inactive state transitions
;; =============================================================================

(t/deftest inactive-enter-transitions-to-selecting-on-court-test
  (let [result (sm/transition (sm/init) {:type :enter})]
    (t/is (= :selecting-on-court (:state result)))
    (t/is (nil? (:data result)))))

(t/deftest inactive-ignores-select-on-court-test
  (let [current (sm/init)
        result  (sm/transition current {:type :select-on-court :data {:player-id "p1"}})]
    (t/is (= current result))))

(t/deftest inactive-ignores-select-off-court-test
  (let [current (sm/init)
        result  (sm/transition current {:type :select-off-court :data {:player-id "p1"}})]
    (t/is (= current result))))

(t/deftest inactive-ignores-back-test
  (let [current (sm/init)
        result  (sm/transition current {:type :back})]
    (t/is (= current result))))

(t/deftest inactive-ignores-cancel-test
  (let [current (sm/init)
        result  (sm/transition current {:type :cancel})]
    (t/is (= current result))))

;; =============================================================================
;; Selecting on-court state transitions
;; =============================================================================

(t/deftest selecting-on-court-select-transitions-to-off-court-test
  (let [current {:state :selecting-on-court :data nil}
        result  (sm/transition current {:type :select-on-court :data {:player-id "p1"}})]
    (t/is (= :selecting-off-court (:state result)))
    (t/is (= {:on-court-id "p1"} (:data result)))))

(t/deftest selecting-on-court-cancel-returns-to-inactive-test
  (let [current {:state :selecting-on-court :data nil}
        result  (sm/transition current {:type :cancel})]
    (t/is (= :inactive (:state result)))
    (t/is (nil? (:data result)))))

(t/deftest selecting-on-court-ignores-select-off-court-test
  (let [current {:state :selecting-on-court :data nil}
        result  (sm/transition current {:type :select-off-court :data {:player-id "p2"}})]
    (t/is (= current result))))

(t/deftest selecting-on-court-ignores-back-test
  (let [current {:state :selecting-on-court :data nil}
        result  (sm/transition current {:type :back})]
    (t/is (= current result))))

;; =============================================================================
;; Selecting off-court state transitions
;; =============================================================================

(t/deftest selecting-off-court-select-emits-substitute-action-test
  (let [current {:state :selecting-off-court :data {:on-court-id "p1"}}
        result  (sm/transition current {:type :select-off-court :data {:player-id "p2"}})]
    (t/is (= :inactive (:state result)))
    (t/is (nil? (:data result)))
    (t/is (= {:type         :substitute
              :on-court-id  "p1"
              :off-court-id "p2"}
             (:action result)))))

(t/deftest selecting-off-court-back-returns-to-on-court-test
  (let [current {:state :selecting-off-court :data {:on-court-id "p1"}}
        result  (sm/transition current {:type :back})]
    (t/is (= :selecting-on-court (:state result)))
    (t/is (nil? (:data result)))))

(t/deftest selecting-off-court-cancel-returns-to-inactive-test
  (let [current {:state :selecting-off-court :data {:on-court-id "p1"}}
        result  (sm/transition current {:type :cancel})]
    (t/is (= :inactive (:state result)))
    (t/is (nil? (:data result)))
    (t/is (nil? (:action result)))))

(t/deftest selecting-off-court-ignores-select-on-court-test
  (let [current {:state :selecting-off-court :data {:on-court-id "p1"}}
        result  (sm/transition current {:type :select-on-court :data {:player-id "p3"}})]
    (t/is (= current result))))

;; =============================================================================
;; Full workflow tests
;; =============================================================================

(t/deftest full-substitution-workflow-test
  (let [s0 (sm/init)
        s1 (sm/transition s0 {:type :enter})
        s2 (sm/transition s1 {:type :select-on-court :data {:player-id "starter-1"}})
        s3 (sm/transition s2 {:type :select-off-court :data {:player-id "bench-1"}})]
    (t/is (= :inactive (:state s3)))
    (t/is (= {:type         :substitute
              :on-court-id  "starter-1"
              :off-court-id "bench-1"}
             (:action s3)))))

(t/deftest workflow-with-back-and-reselect-test
  (let [s0 (sm/init)
        s1 (sm/transition s0 {:type :enter})
        s2 (sm/transition s1 {:type :select-on-court :data {:player-id "p1"}})
        s3 (sm/transition s2 {:type :back})
        s4 (sm/transition s3 {:type :select-on-court :data {:player-id "p2"}})
        s5 (sm/transition s4 {:type :select-off-court :data {:player-id "bench-1"}})]
    (t/is (= :selecting-on-court (:state s3)))
    (t/is (= {:type         :substitute
              :on-court-id  "p2"
              :off-court-id "bench-1"}
             (:action s5)))))

;; =============================================================================
;; valid-events tests
;; =============================================================================

(t/deftest valid-events-inactive-test
  (let [valid (sm/valid-events (sm/init))]
    (t/is (= #{:enter} valid))))

(t/deftest valid-events-selecting-on-court-test
  (let [valid (sm/valid-events {:state :selecting-on-court :data nil})]
    (t/is (= #{:select-on-court :cancel} valid))))

(t/deftest valid-events-selecting-off-court-test
  (let [valid (sm/valid-events {:state :selecting-off-court :data {:on-court-id "p1"}})]
    (t/is (= #{:select-off-court :back :cancel} valid))))

;; =============================================================================
;; Structural tests
;; =============================================================================

(t/deftest all-states-in-machine-test
  (doseq [state sm/states]
    (t/is (contains? sm/machine state)
          (str "State " state " should be in machine"))))

(t/deftest all-events-handled-in-each-state-test
  (doseq [state sm/states]
    (doseq [event sm/events]
      (t/is (contains? (get sm/machine state) event)
            (str "State " state " should handle event " event)))))
