(ns bashketball-game-ui.game.discard-machine-test
  "Tests for the discard state machine."
  (:require
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t :include-macros true])
   [bashketball-game-ui.game.discard-machine :as dm]))

;; =============================================================================
;; Init tests
;; =============================================================================

(t/deftest init-returns-inactive-state-test
  (let [result (dm/init)]
    (t/is (= :inactive (:state result)))
    (t/is (nil? (:data result)))))

;; =============================================================================
;; Inactive state transitions
;; =============================================================================

(t/deftest inactive-enter-transitions-to-selecting-test
  (let [result (dm/transition (dm/init) {:type :enter})]
    (t/is (= :selecting (:state result)))
    (t/is (= #{} (:cards (:data result))))))

(t/deftest inactive-ignores-toggle-card-test
  (let [current (dm/init)
        result  (dm/transition current {:type :toggle-card :data {:instance-id "c1"}})]
    (t/is (= current result))))

(t/deftest inactive-ignores-submit-test
  (let [current (dm/init)
        result  (dm/transition current {:type :submit})]
    (t/is (= current result))))

(t/deftest inactive-ignores-cancel-test
  (let [current (dm/init)
        result  (dm/transition current {:type :cancel})]
    (t/is (= current result))))

;; =============================================================================
;; Selecting state - toggle card
;; =============================================================================

(t/deftest toggle-card-adds-to-empty-set-test
  (let [current {:state :selecting :data {:cards #{}}}
        result  (dm/transition current {:type :toggle-card :data {:instance-id "c1"}})]
    (t/is (= :selecting (:state result)))
    (t/is (= #{"c1"} (get-in result [:data :cards])))))

(t/deftest toggle-card-adds-second-card-test
  (let [current {:state :selecting :data {:cards #{"c1"}}}
        result  (dm/transition current {:type :toggle-card :data {:instance-id "c2"}})]
    (t/is (= #{"c1" "c2"} (get-in result [:data :cards])))))

(t/deftest toggle-card-removes-existing-card-test
  (let [current {:state :selecting :data {:cards #{"c1" "c2"}}}
        result  (dm/transition current {:type :toggle-card :data {:instance-id "c1"}})]
    (t/is (= #{"c2"} (get-in result [:data :cards])))))

(t/deftest toggle-card-removes-last-card-test
  (let [current {:state :selecting :data {:cards #{"c1"}}}
        result  (dm/transition current {:type :toggle-card :data {:instance-id "c1"}})]
    (t/is (= #{} (get-in result [:data :cards])))))

(t/deftest toggle-card-multiple-operations-test
  (let [s0 {:state :selecting :data {:cards #{}}}
        s1 (dm/transition s0 {:type :toggle-card :data {:instance-id "c1"}})
        s2 (dm/transition s1 {:type :toggle-card :data {:instance-id "c2"}})
        s3 (dm/transition s2 {:type :toggle-card :data {:instance-id "c1"}})
        s4 (dm/transition s3 {:type :toggle-card :data {:instance-id "c3"}})]
    (t/is (= #{"c2" "c3"} (get-in s4 [:data :cards])))))

;; =============================================================================
;; Selecting state - submit
;; =============================================================================

(t/deftest submit-with-cards-emits-action-test
  (let [current {:state :selecting :data {:cards #{"c1" "c2"}}}
        result  (dm/transition current {:type :submit})]
    (t/is (= :inactive (:state result)))
    (t/is (nil? (:data result)))
    (t/is (= {:type :discard-cards :cards #{"c1" "c2"}} (:action result)))))

(t/deftest submit-without-cards-does-nothing-test
  (let [current {:state :selecting :data {:cards #{}}}
        result  (dm/transition current {:type :submit})]
    (t/is (= current result))
    (t/is (nil? (:action result)))))

(t/deftest submit-with-nil-cards-does-nothing-test
  (let [current {:state :selecting :data {:cards nil}}
        result  (dm/transition current {:type :submit})]
    (t/is (= current result))))

;; =============================================================================
;; Selecting state - cancel
;; =============================================================================

(t/deftest cancel-returns-to-inactive-test
  (let [current {:state :selecting :data {:cards #{"c1" "c2"}}}
        result  (dm/transition current {:type :cancel})]
    (t/is (= :inactive (:state result)))
    (t/is (nil? (:data result)))
    (t/is (nil? (:action result)))))

(t/deftest cancel-with-empty-cards-returns-to-inactive-test
  (let [current {:state :selecting :data {:cards #{}}}
        result  (dm/transition current {:type :cancel})]
    (t/is (= :inactive (:state result)))))

;; =============================================================================
;; can-submit? predicate tests
;; =============================================================================

(t/deftest can-submit-false-when-inactive-test
  (t/is (not (dm/can-submit? (dm/init)))))

(t/deftest can-submit-false-when-no-cards-test
  (t/is (not (dm/can-submit? {:state :selecting :data {:cards #{}}}))))

(t/deftest can-submit-false-when-nil-cards-test
  (t/is (not (dm/can-submit? {:state :selecting :data {:cards nil}}))))

(t/deftest can-submit-true-when-cards-selected-test
  (t/is (dm/can-submit? {:state :selecting :data {:cards #{"c1"}}})))

;; =============================================================================
;; valid-events tests
;; =============================================================================

(t/deftest valid-events-inactive-test
  (let [valid (dm/valid-events (dm/init))]
    (t/is (= #{:enter} valid))))

(t/deftest valid-events-selecting-no-cards-test
  (let [valid (dm/valid-events {:state :selecting :data {:cards #{}}})]
    (t/is (contains? valid :toggle-card))
    (t/is (contains? valid :cancel))
    (t/is (not (contains? valid :submit)))))

(t/deftest valid-events-selecting-with-cards-test
  (let [valid (dm/valid-events {:state :selecting :data {:cards #{"c1"}}})]
    (t/is (contains? valid :toggle-card))
    (t/is (contains? valid :cancel))
    (t/is (contains? valid :submit))))

;; =============================================================================
;; Structural tests
;; =============================================================================

(t/deftest all-states-in-machine-test
  (doseq [state dm/states]
    (t/is (contains? dm/machine state)
          (str "State " state " should be in machine"))))

(t/deftest all-events-handled-in-each-state-test
  (doseq [state dm/states]
    (doseq [event dm/events]
      (t/is (contains? (get dm/machine state) event)
            (str "State " state " should handle event " event)))))
