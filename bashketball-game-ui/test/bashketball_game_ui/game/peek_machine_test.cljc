(ns bashketball-game-ui.game.peek-machine-test
  "Tests for the peek deck state machine."
  (:require
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t :include-macros true])
   [bashketball-game-ui.game.peek-machine :as pm]))

;; =============================================================================
;; Init tests
;; =============================================================================

(t/deftest init-returns-closed-state-test
  (let [result (pm/init)]
    (t/is (= :closed (:state result)))
    (t/is (nil? (:data result)))))

;; =============================================================================
;; Closed state transitions
;; =============================================================================

(t/deftest closed-show-transitions-to-select-count-test
  (let [result (pm/transition (pm/init) {:type :show :data {:team :team/HOME}})]
    (t/is (= :select-count (:state result)))
    (t/is (= :team/HOME (get-in result [:data :target-team])))
    (t/is (= 3 (get-in result [:data :count])))))

(t/deftest closed-ignores-other-events-test
  (let [current (pm/init)]
    (t/is (= current (pm/transition current {:type :set-count :data {:count 5}})))
    (t/is (= current (pm/transition current {:type :proceed})))
    (t/is (= current (pm/transition current {:type :select-card :data {:instance-id "c1"}})))
    (t/is (= current (pm/transition current {:type :place-card :data {:destination "TOP"}})))
    (t/is (= current (pm/transition current {:type :finish})))
    (t/is (= current (pm/transition current {:type :cancel})))))

;; =============================================================================
;; Select count state transitions
;; =============================================================================

(t/deftest select-count-set-count-updates-count-test
  (let [current {:state :select-count :data {:target-team :team/HOME :count 3}}
        result  (pm/transition current {:type :set-count :data {:count 5}})]
    (t/is (= :select-count (:state result)))
    (t/is (= 5 (get-in result [:data :count])))))

(t/deftest select-count-clamps-count-min-test
  (let [current {:state :select-count :data {:target-team :team/HOME :count 3}}
        result  (pm/transition current {:type :set-count :data {:count 0}})]
    (t/is (= 1 (get-in result [:data :count])))))

(t/deftest select-count-clamps-count-max-test
  (let [current {:state :select-count :data {:target-team :team/HOME :count 3}}
        result  (pm/transition current {:type :set-count :data {:count 10}})]
    (t/is (= 5 (get-in result [:data :count])))))

(t/deftest select-count-proceed-transitions-to-place-cards-test
  (let [current {:state :select-count :data {:target-team :team/HOME :count 3}}
        result  (pm/transition current {:type :proceed :data {:cards ["c1" "c2" "c3"]}})]
    (t/is (= :place-cards (:state result)))
    (t/is (= ["c1" "c2" "c3"] (get-in result [:data :cards])))
    (t/is (nil? (get-in result [:data :selected-id])))
    (t/is (= {} (get-in result [:data :placements])))))

(t/deftest select-count-cancel-returns-to-closed-test
  (let [current {:state :select-count :data {:target-team :team/HOME :count 3}}
        result  (pm/transition current {:type :cancel})]
    (t/is (= :closed (:state result)))
    (t/is (nil? (:data result)))))

;; =============================================================================
;; Place cards state - card selection
;; =============================================================================

(t/deftest place-cards-select-card-sets-selected-id-test
  (let [current {:state :place-cards
                 :data  {:cards ["c1" "c2"] :selected-id nil :placements {}}}
        result  (pm/transition current {:type :select-card :data {:instance-id "c1"}})]
    (t/is (= :place-cards (:state result)))
    (t/is (= "c1" (get-in result [:data :selected-id])))))

(t/deftest place-cards-select-different-card-changes-selection-test
  (let [current {:state :place-cards
                 :data  {:cards ["c1" "c2"] :selected-id "c1" :placements {}}}
        result  (pm/transition current {:type :select-card :data {:instance-id "c2"}})]
    (t/is (= "c2" (get-in result [:data :selected-id])))))

(t/deftest place-cards-deselect-clears-selected-id-test
  (let [current {:state :place-cards
                 :data  {:cards ["c1" "c2"] :selected-id "c1" :placements {}}}
        result  (pm/transition current {:type :deselect-card})]
    (t/is (nil? (get-in result [:data :selected-id])))))

;; =============================================================================
;; Place cards state - placement assignment
;; =============================================================================

(t/deftest place-card-assigns-destination-test
  (let [current {:state :place-cards
                 :data  {:cards ["c1" "c2"] :selected-id "c1" :placements {}}}
        result  (pm/transition current {:type :place-card :data {:destination "TOP"}})]
    (t/is (= {"c1" "TOP"} (get-in result [:data :placements])))))

(t/deftest place-card-clears-selection-test
  (let [current {:state :place-cards
                 :data  {:cards ["c1" "c2"] :selected-id "c1" :placements {}}}
        result  (pm/transition current {:type :place-card :data {:destination "TOP"}})]
    (t/is (nil? (get-in result [:data :selected-id])))))

(t/deftest place-card-without-selection-does-nothing-test
  (let [current {:state :place-cards
                 :data  {:cards ["c1" "c2"] :selected-id nil :placements {}}}
        result  (pm/transition current {:type :place-card :data {:destination "TOP"}})]
    (t/is (= current result))))

(t/deftest place-card-with-invalid-destination-does-nothing-test
  (let [current {:state :place-cards
                 :data  {:cards ["c1" "c2"] :selected-id "c1" :placements {}}}
        result  (pm/transition current {:type :place-card :data {:destination "INVALID"}})]
    (t/is (= current result))))

(t/deftest place-card-can-reassign-already-placed-test
  (let [current {:state :place-cards
                 :data  {:cards ["c1" "c2"] :selected-id "c1" :placements {"c1" "TOP"}}}
        result  (pm/transition current {:type :place-card :data {:destination "BOTTOM"}})]
    (t/is (= {"c1" "BOTTOM"} (get-in result [:data :placements])))))

(t/deftest place-card-accumulates-placements-test
  (let [s0 {:state :place-cards
            :data  {:cards ["c1" "c2" "c3"] :selected-id nil :placements {}}}
        s1 (pm/transition s0 {:type :select-card :data {:instance-id "c1"}})
        s2 (pm/transition s1 {:type :place-card :data {:destination "TOP"}})
        s3 (pm/transition s2 {:type :select-card :data {:instance-id "c2"}})
        s4 (pm/transition s3 {:type :place-card :data {:destination "BOTTOM"}})]
    (t/is (= {"c1" "TOP" "c2" "BOTTOM"} (get-in s4 [:data :placements])))))

;; =============================================================================
;; Place cards state - finish
;; =============================================================================

(t/deftest finish-with-all-placed-emits-action-test
  (let [current {:state :place-cards
                 :data  {:target-team :team/HOME
                         :count       2
                         :cards       ["c1" "c2"]
                         :selected-id nil
                         :placements  {"c1" "TOP" "c2" "DISCARD"}}}
        result  (pm/transition current {:type :finish})]
    (t/is (= :closed (:state result)))
    (t/is (nil? (:data result)))
    (t/is (= :resolve-peek (get-in result [:action :type])))
    (t/is (= :team/HOME (get-in result [:action :target-team])))
    (t/is (= [{:instance-id "c1" :destination "TOP"}
              {:instance-id "c2" :destination "DISCARD"}]
             (get-in result [:action :placements])))))

(t/deftest finish-without-all-placed-does-nothing-test
  (let [current {:state :place-cards
                 :data  {:cards       ["c1" "c2"]
                         :selected-id nil
                         :placements  {"c1" "TOP"}}}
        result  (pm/transition current {:type :finish})]
    (t/is (= current result))
    (t/is (nil? (:action result)))))

(t/deftest place-cards-cancel-returns-to-closed-test
  (let [current {:state :place-cards
                 :data  {:cards       ["c1" "c2"]
                         :selected-id "c1"
                         :placements  {"c1" "TOP"}}}
        result  (pm/transition current {:type :cancel})]
    (t/is (= :closed (:state result)))
    (t/is (nil? (:data result)))
    (t/is (nil? (:action result)))))

;; =============================================================================
;; Predicate tests
;; =============================================================================

(t/deftest all-placed-false-when-no-cards-test
  (t/is (not (pm/all-placed? {:cards [] :placements {}}))))

(t/deftest all-placed-false-when-incomplete-test
  (t/is (not (pm/all-placed? {:cards ["c1" "c2"] :placements {"c1" "TOP"}}))))

(t/deftest all-placed-true-when-complete-test
  (t/is (pm/all-placed? {:cards ["c1" "c2"] :placements {"c1" "TOP" "c2" "BOTTOM"}})))

(t/deftest can-finish-false-when-not-place-cards-state-test
  (t/is (not (pm/can-finish? {:state :select-count :data {:cards ["c1"] :placements {"c1" "TOP"}}}))))

(t/deftest can-finish-false-when-incomplete-test
  (t/is (not (pm/can-finish? {:state :place-cards :data {:cards ["c1" "c2"] :placements {"c1" "TOP"}}}))))

(t/deftest can-finish-true-when-complete-test
  (t/is (pm/can-finish? {:state :place-cards :data {:cards ["c1" "c2"] :placements {"c1" "TOP" "c2" "BOTTOM"}}})))

;; =============================================================================
;; valid-events tests
;; =============================================================================

(t/deftest valid-events-closed-test
  (let [valid (pm/valid-events (pm/init))]
    (t/is (= #{:show} valid))))

(t/deftest valid-events-select-count-test
  (let [valid (pm/valid-events {:state :select-count :data {:count 3}})]
    (t/is (= #{:set-count :proceed :cancel} valid))))

(t/deftest valid-events-place-cards-incomplete-test
  (let [valid (pm/valid-events {:state :place-cards
                                :data  {:cards ["c1" "c2"] :placements {"c1" "TOP"}}})]
    (t/is (contains? valid :select-card))
    (t/is (contains? valid :deselect-card))
    (t/is (contains? valid :place-card))
    (t/is (contains? valid :cancel))
    (t/is (not (contains? valid :finish)))))

(t/deftest valid-events-place-cards-complete-test
  (let [valid (pm/valid-events {:state :place-cards
                                :data  {:cards ["c1" "c2"]
                                        :placements {"c1" "TOP" "c2" "BOTTOM"}}})]
    (t/is (contains? valid :finish))))

;; =============================================================================
;; Full workflow tests
;; =============================================================================

(t/deftest full-peek-workflow-test
  (let [s0 (pm/init)
        s1 (pm/transition s0 {:type :show :data {:team :team/AWAY}})
        s2 (pm/transition s1 {:type :set-count :data {:count 2}})
        s3 (pm/transition s2 {:type :proceed :data {:cards ["c1" "c2"]}})
        s4 (pm/transition s3 {:type :select-card :data {:instance-id "c1"}})
        s5 (pm/transition s4 {:type :place-card :data {:destination "TOP"}})
        s6 (pm/transition s5 {:type :select-card :data {:instance-id "c2"}})
        s7 (pm/transition s6 {:type :place-card :data {:destination "DISCARD"}})
        s8 (pm/transition s7 {:type :finish})]
    (t/is (= :select-count (:state s1)))
    (t/is (= :place-cards (:state s3)))
    (t/is (= :closed (:state s8)))
    (t/is (= {:type        :resolve-peek
              :target-team :team/AWAY
              :count       2
              :placements  [{:instance-id "c1" :destination "TOP"}
                            {:instance-id "c2" :destination "DISCARD"}]}
             (:action s8)))))

(t/deftest workflow-cancel-from-place-cards-test
  (let [s0 (pm/init)
        s1 (pm/transition s0 {:type :show :data {:team :team/HOME}})
        s2 (pm/transition s1 {:type :proceed :data {:cards ["c1" "c2"]}})
        s3 (pm/transition s2 {:type :select-card :data {:instance-id "c1"}})
        s4 (pm/transition s3 {:type :place-card :data {:destination "TOP"}})
        s5 (pm/transition s4 {:type :cancel})]
    (t/is (= :closed (:state s5)))
    (t/is (nil? (:action s5)))))

;; =============================================================================
;; Structural tests
;; =============================================================================

(t/deftest all-states-in-machine-test
  (doseq [state pm/states]
    (t/is (contains? pm/machine state)
          (str "State " state " should be in machine"))))

(t/deftest all-events-handled-in-each-state-test
  (doseq [state pm/states]
    (doseq [event pm/events]
      (t/is (contains? (get pm/machine state) event)
            (str "State " state " should handle event " event)))))
