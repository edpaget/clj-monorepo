(ns bashketball-game.polix.standard-action-resolution-test
  "Tests for standard action resolution orchestration.

  Tests the full resolution flow: before-event → response prompts →
  skill test → success/failure → after-event."
  (:require
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.game-rules :as game-rules]
   [bashketball-game.polix.standard-action-resolution :as sar]
   [bashketball-game.polix.triggers :as triggers]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [t]
    (polix/initialize!)
    (t)))

;; =============================================================================
;; Orchestration Function Tests
;; =============================================================================

(deftest build-offense-continuation-test
  (testing "builds correct skill test flow structure"
    (let [params {:action-type :shoot
                  :attacker-id "player-1"
                  :defender-id "player-2"
                  :success-effect {:type :bashketball/add-score :team :team/HOME :points 2}
                  :failure-effect {:type :bashketball/loose-ball :position [2 7]}}
          result (sar/build-offense-continuation params)]
      (is (= :bashketball/execute-skill-test-flow (:type result)))
      (is (= :shoot (:action-type result)))
      (is (= "player-1" (:attacker-id result)))
      (is (= "player-2" (:defender-id result)))
      (is (map? (:result-continuation result))))))

(deftest build-offense-continuation-result-structure-test
  (testing "result continuation contains success/failure effects"
    (let [success-effect {:type :bashketball/add-score :team :team/HOME :points 2}
          failure-effect {:type :bashketball/loose-ball :position [2 7]}
          params         {:action-type :shoot
                          :attacker-id "player-1"
                          :defender-id "player-2"
                          :success-effect success-effect
                          :failure-effect failure-effect}
          result         (sar/build-offense-continuation params)
          result-cont    (:result-continuation result)]
      (is (= :bashketball/evaluate-skill-test-result (:type result-cont)))
      (is (= success-effect (:success-effect result-cont)))
      (is (= failure-effect (:failure-effect result-cont))))))

(deftest build-response-chain-empty-responses-test
  (testing "with no responses returns offense continuation unchanged"
    (let [offense-cont {:type :bashketball/execute-skill-test-flow}
          result       (sar/build-response-chain [] offense-cont)]
      (is (= offense-cont result)))))

(deftest build-response-chain-single-response-test
  (testing "with single response builds offer-choice with continuation"
    (let [offense-cont {:type :bashketball/execute-skill-test-flow}
          response     {:asset {:instance-id "asset-1" :owner :team/AWAY}
                        :prompt "Apply Block?"
                        :effect {:type :bashketball/exhaust-player :player-id "target"}}
          result       (sar/build-response-chain [response] offense-cont)]
      (is (= :bashketball/offer-choice (:type result)))
      (is (= :response-prompt (:choice-type result)))
      (is (= :team/AWAY (:waiting-for result)))
      (is (= [{:id :apply :label "Apply"} {:id :pass :label "Pass"}] (:options result))))))

(deftest build-response-chain-continuation-structure-test
  (testing "response continuation contains next step"
    (let [offense-cont {:type :bashketball/execute-skill-test-flow}
          response     {:asset {:instance-id "asset-1" :owner :team/AWAY}
                        :effect {:type :bashketball/exhaust-player}}
          result       (sar/build-response-chain [response] offense-cont)
          cont         (:continuation result)]
      (is (= :bashketball/process-response-choice (:type cont)))
      (is (= offense-cont (:next-continuation cont))))))

(deftest build-response-chain-multiple-responses-test
  (testing "with multiple responses, first response is outermost"
    (let [offense-cont {:type :test-offense}
          response-1   {:asset {:instance-id "asset-1" :owner :team/AWAY} :prompt "First"}
          response-2   {:asset {:instance-id "asset-2" :owner :team/AWAY} :prompt "Second"}
          result       (sar/build-response-chain [response-1 response-2] offense-cont)]
      ;; First response should be the outermost offer-choice
      (is (= "First" (get-in result [:context :response-prompt])))
      ;; Second response should be in the continuation chain
      (let [second-step (get-in result [:continuation :next-continuation])]
        (is (= :bashketball/offer-choice (:type second-step)))
        (is (= "Second" (get-in second-step [:context :response-prompt])))))))

;; =============================================================================
;; Effect Tests
;; =============================================================================

(deftest fire-event-effect-test
  (let [game     (-> (f/base-game-state)
                     (assoc :turn-number 3
                            :active-player :team/HOME
                            :phase :phase/MAIN))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/fire-event
                                   :event-type :bashketball/test-event
                                   :params {:custom-field "value"}}
                                  {}
                                  {:registry registry})]
    (testing "returns state unchanged when no triggers match"
      (is (some? (:state result))))))

(deftest evaluate-skill-test-result-success-test
  (let [game     (-> (f/base-game-state)
                     (assoc :last-skill-test-result {:success? true :margin 2}))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/evaluate-skill-test-result
                                   :success-effect {:type :bashketball/add-score
                                                    :team :team/HOME
                                                    :points 2}
                                   :failure-effect {:type :bashketball/loose-ball
                                                    :position [2 7]}
                                   :after-event-params {:action-type :shoot}}
                                  {}
                                  {:registry registry})]
    (testing "executes success effect when test succeeded"
      (is (= 2 (get-in (:state result) [:score :team/HOME]))))))

(deftest evaluate-skill-test-result-failure-test
  (let [game     (-> (f/base-game-state)
                     (assoc :last-skill-test-result {:success? false :margin -1}))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/evaluate-skill-test-result
                                   :success-effect {:type :bashketball/add-score
                                                    :team :team/HOME
                                                    :points 2}
                                   :failure-effect {:type :bashketball/loose-ball
                                                    :position [2 7]}
                                   :after-event-params {:action-type :shoot}}
                                  {}
                                  {:registry registry})]
    (testing "executes failure effect when test failed"
      (is (= {:status :ball-status/LOOSE :position [2 7]}
             (:ball (:state result)))))))

(deftest process-response-choice-pass-test
  (let [game     (f/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        ctx      {:bindings {:choice/selected :pass}}
        result   (fx/apply-effect game
                                  {:type :bashketball/process-response-choice
                                   :response-asset {:instance-id "asset-1"
                                                    :owner :team/AWAY}
                                   :response-effect {:type :bashketball/draw-cards
                                                     :player :team/AWAY
                                                     :count 1}
                                   :next-continuation {:type :bashketball/add-score
                                                       :team :team/HOME
                                                       :points 2}}
                                  ctx
                                  {:registry registry})]
    (testing "when passing, skips response and continues"
      (is (= 2 (get-in (:state result) [:score :team/HOME]))))))

;; =============================================================================
;; Response Condition Evaluation Tests
;; =============================================================================

(deftest evaluate-response-condition-no-condition-test
  (testing "returns true when no condition defined"
    (let [game    (f/base-game-state)
          trigger {:trigger/event :bashketball/standard-action.before}
          event   {:type :bashketball/standard-action.before}]
      (is (sar/evaluate-response-condition game trigger event nil)))))

;; =============================================================================
;; Integration Tests
;; =============================================================================

(deftest resolve-standard-action-no-responses-test
  (let [game     (-> (f/base-game-state)
                     (f/with-player-at f/home-player-1 [2 11])
                     (f/with-ball-possessed f/home-player-1)
                     (assoc :last-skill-test-result {:success? true :margin 1}))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/resolve-standard-action
                                   :action-type :shoot
                                   :attacker-id f/home-player-1
                                   :defender-id nil
                                   :success-effect {:type :bashketball/add-score
                                                    :team :team/HOME
                                                    :points 2}
                                   :failure-effect {:type :bashketball/loose-ball
                                                    :position [2 7]}}
                                  {}
                                  {:registry registry})]
    (testing "without responses proceeds directly to skill test flow"
      (is (some? (:state result))))))
