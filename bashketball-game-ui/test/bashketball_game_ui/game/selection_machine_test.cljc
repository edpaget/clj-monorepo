(ns bashketball-game-ui.game.selection-machine-test
  "Tests for the selection state machine."
  (:require
   [bashketball-game-ui.game.selection-machine :as sm]
   #?(:clj [clojure.test :as t]
      :cljs [cljs.test :as t :include-macros true])))

;; =============================================================================
;; Init tests
;; =============================================================================

(t/deftest init-returns-idle-state-test
  (let [result (sm/init)]
    (t/is (= :idle (:state result)))
    (t/is (nil? (:data result)))))

;; =============================================================================
;; Idle state transitions
;; =============================================================================

(t/deftest idle-click-player-transitions-to-player-selected-test
  (let [result (sm/transition (sm/init)
                              {:type :click-player
                               :data {:player-id "p1"}})]
    (t/is (= :player-selected (:state result)))
    (t/is (= {:player-id "p1"} (:data result)))
    (t/is (nil? (:action result)))))

(t/deftest idle-click-ball-transitions-to-ball-selected-test
  (let [result (sm/transition (sm/init)
                              {:type :click-ball})]
    (t/is (= :ball-selected (:state result)))
    (t/is (nil? (:action result)))))

(t/deftest idle-click-hex-does-nothing-test
  (let [current (sm/init)
        result  (sm/transition current {:type :click-hex :data {:q 0 :r 0}})]
    (t/is (= current result))))

(t/deftest idle-enter-standard-transitions-to-selecting-test
  (let [result (sm/transition (sm/init) {:type :enter-standard})]
    (t/is (= :standard-action-selecting (:state result)))))

(t/deftest idle-escape-stays-idle-test
  (let [result (sm/transition (sm/init) {:type :escape})]
    (t/is (= :idle (:state result)))))

;; =============================================================================
;; Player selected state transitions
;; =============================================================================

(t/deftest player-selected-click-hex-emits-move-action-test
  (let [current {:state :player-selected
                 :data  {:player-id "p1"}}
        result  (sm/transition current {:type :click-hex :data {:q 2 :r 3}})]
    (t/is (= :idle (:state result)))
    (t/is (nil? (:data result)))
    (t/is (= {:type :move-player
              :from {:player-id "p1"}
              :to   {:q 2 :r 3}}
             (:action result)))))

(t/deftest player-selected-click-different-player-switches-selection-test
  (let [current {:state :player-selected
                 :data  {:player-id "p1"}}
        result  (sm/transition current {:type :click-player :data {:player-id "p2"}})]
    (t/is (= :player-selected (:state result)))
    (t/is (= {:player-id "p2"} (:data result)))
    (t/is (nil? (:action result)))))

(t/deftest player-selected-click-ball-transitions-to-ball-selected-test
  (let [current {:state :player-selected :data {:player-id "p1"}}
        result  (sm/transition current {:type :click-ball})]
    (t/is (= :ball-selected (:state result)))))

(t/deftest player-selected-start-pass-transitions-to-targeting-test
  (let [current {:state :player-selected :data {:player-id "p1"}}
        result  (sm/transition current {:type :start-pass})]
    (t/is (= :targeting-pass (:state result)))
    (t/is (= {:player-id "p1"} (:data result)))))

(t/deftest player-selected-enter-standard-transitions-test
  (let [current {:state :player-selected :data {:player-id "p1"}}
        result  (sm/transition current {:type :enter-standard})]
    (t/is (= :standard-action-selecting (:state result)))))

(t/deftest player-selected-escape-returns-to-idle-test
  (let [current {:state :player-selected :data {:player-id "p1"}}
        result  (sm/transition current {:type :escape})]
    (t/is (= :idle (:state result)))
    (t/is (nil? (:data result)))))

;; =============================================================================
;; Ball selected state transitions
;; =============================================================================

(t/deftest ball-selected-click-hex-emits-set-ball-loose-test
  (let [current {:state :ball-selected :data nil}
        result  (sm/transition current {:type :click-hex :data {:q 1 :r 1}})]
    (t/is (= :idle (:state result)))
    (t/is (= :set-ball-loose (get-in result [:action :type])))))

(t/deftest ball-selected-click-player-emits-set-ball-possessed-test
  (let [current {:state :ball-selected :data nil}
        result  (sm/transition current {:type :click-player :data {:player-id "p1"}})]
    (t/is (= :idle (:state result)))
    (t/is (= :set-ball-possessed (get-in result [:action :type])))))

(t/deftest ball-selected-click-ball-returns-to-idle-test
  (let [current {:state :ball-selected :data nil}
        result  (sm/transition current {:type :click-ball})]
    (t/is (= :idle (:state result)))
    (t/is (nil? (:action result)))))

(t/deftest ball-selected-escape-returns-to-idle-test
  (let [current {:state :ball-selected :data nil}
        result  (sm/transition current {:type :escape})]
    (t/is (= :idle (:state result)))))

;; =============================================================================
;; Targeting pass state transitions
;; =============================================================================

(t/deftest targeting-pass-click-player-emits-pass-action-test
  (let [current {:state :targeting-pass
                 :data  {:player-id "p1" :position [0 0]}}
        result  (sm/transition current {:type :click-player :data {:player-id "p2"}})]
    (t/is (= :idle (:state result)))
    (t/is (= :pass-to-player (get-in result [:action :type])))
    (t/is (= {:player-id "p1" :position [0 0]} (get-in result [:action :from])))
    (t/is (= {:player-id "p2"} (get-in result [:action :to])))))

(t/deftest targeting-pass-click-hex-emits-pass-to-hex-test
  (let [current {:state :targeting-pass :data {:player-id "p1"}}
        result  (sm/transition current {:type :click-hex :data {:q 3 :r 4}})]
    (t/is (= :idle (:state result)))
    (t/is (= :pass-to-hex (get-in result [:action :type])))))

(t/deftest targeting-pass-back-returns-to-player-selected-test
  (let [current {:state :targeting-pass :data {:player-id "p1"}}
        result  (sm/transition current {:type :back})]
    (t/is (= :player-selected (:state result)))
    (t/is (= {:player-id "p1"} (:data result)))))

(t/deftest targeting-pass-escape-returns-to-idle-test
  (let [current {:state :targeting-pass :data {:player-id "p1"}}
        result  (sm/transition current {:type :escape})]
    (t/is (= :idle (:state result)))
    (t/is (nil? (:data result)))))

;; =============================================================================
;; Standard action selecting state transitions
;; =============================================================================

(t/deftest standard-action-selecting-select-cards-proceeds-test
  (let [current {:state :standard-action-selecting :data nil}
        result  (sm/transition current {:type :select-cards :data {:cards #{"c1" "c2"}}})]
    (t/is (= :standard-action-confirming (:state result)))
    (t/is (= {:cards #{"c1" "c2"}} (:data result)))))

(t/deftest standard-action-selecting-escape-returns-to-idle-test
  (let [current {:state :standard-action-selecting :data nil}
        result  (sm/transition current {:type :escape})]
    (t/is (= :idle (:state result)))))

(t/deftest standard-action-selecting-ignores-click-events-test
  (let [current {:state :standard-action-selecting :data nil}]
    (t/is (= current (sm/transition current {:type :click-player :data {:player-id "p1"}})))
    (t/is (= current (sm/transition current {:type :click-hex :data {:q 0 :r 0}})))
    (t/is (= current (sm/transition current {:type :click-ball})))))

;; =============================================================================
;; Standard action confirming state transitions
;; =============================================================================

(t/deftest standard-action-confirming-select-action-emits-action-test
  (let [current {:state :standard-action-confirming
                 :data  {:cards #{"c1" "c2"}}}
        result  (sm/transition current {:type :select-action :data {:card-slug "move"}})]
    (t/is (= :idle (:state result)))
    (t/is (= :standard-action (get-in result [:action :type])))
    (t/is (= {:cards #{"c1" "c2"}} (get-in result [:action :from])))
    (t/is (= {:card-slug "move"} (get-in result [:action :to])))))

(t/deftest standard-action-confirming-back-returns-to-selecting-test
  (let [current {:state :standard-action-confirming :data {:cards #{"c1" "c2"}}}
        result  (sm/transition current {:type :back})]
    (t/is (= :standard-action-selecting (:state result)))))

(t/deftest standard-action-confirming-escape-returns-to-idle-test
  (let [current {:state :standard-action-confirming :data {:cards #{"c1" "c2"}}}
        result  (sm/transition current {:type :escape})]
    (t/is (= :idle (:state result)))
    (t/is (nil? (:data result)))))

;; =============================================================================
;; Card selection tests
;; =============================================================================

(t/deftest idle-click-card-toggles-selection-test
  (let [current (sm/init)
        result  (sm/transition current {:type :click-card :data {:instance-id "card-1"}})]
    (t/is (= :idle (:state result)))
    (t/is (= "card-1" (get-in result [:data :selected-card])))))

(t/deftest click-card-toggles-off-when-same-test
  (let [current {:state :idle :data {:selected-card "card-1"}}
        result  (sm/transition current {:type :click-card :data {:instance-id "card-1"}})]
    (t/is (= :idle (:state result)))
    (t/is (nil? (get-in result [:data :selected-card])))))

(t/deftest click-card-switches-selection-test
  (let [current {:state :idle :data {:selected-card "card-1"}}
        result  (sm/transition current {:type :click-card :data {:instance-id "card-2"}})]
    (t/is (= :idle (:state result)))
    (t/is (= "card-2" (get-in result [:data :selected-card])))))

(t/deftest player-selected-click-card-preserves-player-test
  (let [current {:state :player-selected :data {:player-id "p1"}}
        result  (sm/transition current {:type :click-card :data {:instance-id "card-1"}})]
    (t/is (= :player-selected (:state result)))
    (t/is (= "p1" (get-in result [:data :player-id])))
    (t/is (= "card-1" (get-in result [:data :selected-card])))))

;; =============================================================================
;; Standard action card toggle tests
;; =============================================================================

(t/deftest standard-action-toggle-card-adds-to-set-test
  (let [current {:state :standard-action-selecting :data nil}
        result  (sm/transition current {:type :toggle-standard-card :data {:instance-id "c1"}})]
    (t/is (= :standard-action-selecting (:state result)))
    (t/is (= #{"c1"} (get-in result [:data :cards])))))

(t/deftest standard-action-toggle-card-removes-from-set-test
  (let [current {:state :standard-action-selecting :data {:cards #{"c1" "c2"}}}
        result  (sm/transition current {:type :toggle-standard-card :data {:instance-id "c1"}})]
    (t/is (= :standard-action-selecting (:state result)))
    (t/is (= #{"c2"} (get-in result [:data :cards])))))

(t/deftest standard-action-toggle-card-multiple-test
  (let [s0      {:state :standard-action-selecting :data nil}
        s1      (sm/transition s0 {:type :toggle-standard-card :data {:instance-id "c1"}})
        s2      (sm/transition s1 {:type :toggle-standard-card :data {:instance-id "c2"}})]
    (t/is (= #{"c1" "c2"} (get-in s2 [:data :cards])))))

;; =============================================================================
;; Valid events helper tests
;; =============================================================================

(t/deftest valid-events-returns-non-nil-transitions-test
  (t/is (contains? (sm/valid-events :idle) :click-player))
  (t/is (contains? (sm/valid-events :idle) :click-ball))
  (t/is (contains? (sm/valid-events :idle) :enter-standard))
  (t/is (not (contains? (sm/valid-events :idle) :click-hex)))
  (t/is (not (contains? (sm/valid-events :idle) :start-pass))))

(t/deftest valid-events-for-player-selected-test
  (let [valid (sm/valid-events :player-selected)]
    (t/is (contains? valid :click-player))
    (t/is (contains? valid :click-ball))
    (t/is (contains? valid :click-hex))
    (t/is (contains? valid :start-pass))
    (t/is (contains? valid :escape))))

(t/deftest valid-events-for-targeting-pass-test
  (let [valid (sm/valid-events :targeting-pass)]
    (t/is (contains? valid :click-player))
    (t/is (contains? valid :click-hex))
    (t/is (contains? valid :back))
    (t/is (contains? valid :escape))))

;; =============================================================================
;; Structural tests
;; =============================================================================

(t/deftest all-states-in-machine-test
  (doseq [state sm/states]
    (t/is (contains? sm/machine state)
          (str "State " state " should be in machine"))))

(t/deftest all-states-reachable-from-idle-test
  (let [;; Special transition markers that are not actual states
        special-markers #{:toggle-card :toggle-standard-card}
        reachable       (loop [visited #{:idle}
                               queue   [:idle]]
                          (if (empty? queue)
                            visited
                            (let [state       (first queue)
                                  transitions (vals (get sm/machine state))
                                  next-states (->> transitions
                                                   (keep (fn [spec]
                                                           (cond
                                                             (keyword? spec) spec
                                                             (vector? spec)  (or (nth spec 2 nil) :idle)
                                                             :else           nil)))
                                                   (remove special-markers)
                                                   (remove visited))]
                              (recur (into visited next-states)
                                     (into (vec (rest queue)) next-states)))))]
    (t/is (= sm/states reachable)
          "Every state should be reachable from :idle")))

(t/deftest all-non-idle-states-have-escape-test
  (doseq [state (disj sm/states :idle)]
    (t/is (contains? (sm/valid-events state) :escape)
          (str state " should have escape event"))))

(t/deftest escape-from-any-state-reaches-idle-test
  (doseq [state sm/states]
    (let [result (sm/transition {:state state :data {:test true}}
                                {:type :escape})]
      (t/is (= :idle (:state result))
            (str "Escape from " state " should reach :idle")))))
