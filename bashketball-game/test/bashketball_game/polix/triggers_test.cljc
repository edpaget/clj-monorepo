(ns bashketball-game.polix.triggers-test
  (:require
   [bashketball-game.actions :as actions]
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.fixtures :as fixtures]
   [bashketball-game.polix.triggers :as triggers]
   [bashketball-game.state :as state]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [f]
    (polix/initialize!)
    (f)))

;;; ---------------------------------------------------------------------------
;;; action->events tests
;;; ---------------------------------------------------------------------------

(deftest action->events-generates-before-after-test
  (let [game   (fixtures/base-game-state)
        action {:type :bashketball/move-player
                :player-id fixtures/home-player-1
                :position [2 3]}
        events (triggers/action->events game action)]

    (testing "returns before and after events"
      (is (contains? events :before))
      (is (contains? events :after)))

    (testing "before event has correct type"
      (is (= :bashketball/move-player.before
             (get-in events [:before :type]))))

    (testing "after event has correct type"
      (is (= :bashketball/move-player.after
             (get-in events [:after :type]))))

    (testing "events contain action fields"
      (is (= fixtures/home-player-1 (get-in events [:before :player-id])))
      (is (= [2 3] (get-in events [:before :position]))))

    (testing "events contain game context"
      (is (= 1 (get-in events [:before :turn-number])))
      (is (= :team/HOME (get-in events [:before :active-player])))
      (is (= :phase/SETUP (get-in events [:before :phase]))))))

(deftest action->events-works-for-all-action-types-test
  (let [game (fixtures/base-game-state)]

    (testing "set-phase generates events"
      (let [events (triggers/action->events game {:type :bashketball/set-phase :phase :phase/ACTIONS})]
        (is (= :bashketball/set-phase.before (get-in events [:before :type])))))

    (testing "exhaust-player generates events"
      (let [events (triggers/action->events game {:type :bashketball/exhaust-player :player-id "p1"})]
        (is (= :bashketball/exhaust-player.after (get-in events [:after :type])))))

    (testing "add-score generates events"
      (let [events (triggers/action->events game {:type :bashketball/add-score :team :team/HOME :points 2})]
        (is (= :bashketball/add-score.before (get-in events [:before :type])))
        (is (= :team/HOME (get-in events [:before :team])))
        (is (= 2 (get-in events [:before :points])))))))

;;; ---------------------------------------------------------------------------
;;; Registry management tests
;;; ---------------------------------------------------------------------------

(deftest create-registry-test
  (let [registry (triggers/create-registry)]
    (is (map? registry))
    (is (empty? (triggers/get-triggers registry)))))

(deftest register-trigger-test
  (let [registry  (triggers/create-registry)
        trigger   {:event-types #{:bashketball/move-player.after}
                   :timing :polix.triggers.timing/after
                   :effect {:type :polix.effects/noop}}
        registry' (triggers/register-trigger registry trigger "ability-1" :team/HOME "player-1")]

    (testing "trigger is registered"
      (is (= 1 (count (triggers/get-triggers registry')))))

    (testing "trigger has correct fields"
      (let [t (first (triggers/get-triggers registry'))]
        (is (= "ability-1" (:source t)))
        (is (= :team/HOME (:owner t)))
        (is (= "player-1" (:self t)))))))

(deftest unregister-triggers-by-source-test
  (let [registry  (triggers/create-registry)
        trigger   {:event-types #{:bashketball/move-player.after}
                   :timing :polix.triggers.timing/after
                   :effect {:type :polix.effects/noop}}
        registry' (-> registry
                      (triggers/register-trigger trigger "ability-1" :team/HOME "p1")
                      (triggers/register-trigger trigger "ability-2" :team/HOME "p2")
                      (triggers/register-trigger trigger "ability-1" :team/AWAY "p3"))]

    (testing "all triggers registered"
      (is (= 3 (count (triggers/get-triggers registry')))))

    (testing "unregister removes only matching source"
      (let [registry'' (triggers/unregister-triggers-by-source registry' "ability-1")]
        (is (= 1 (count (triggers/get-triggers registry''))))
        (is (= "ability-2" (:source (first (triggers/get-triggers registry'')))))))))

;;; ---------------------------------------------------------------------------
;;; apply-action with triggers tests
;;; ---------------------------------------------------------------------------

(deftest apply-action-without-registry-test
  (let [game   (fixtures/base-game-state)
        result (actions/apply-action {:state game :registry nil}
                                     {:type :bashketball/set-phase :phase :phase/ACTIONS})]

    (testing "returns expected structure"
      (is (contains? result :state))
      (is (contains? result :registry))
      (is (contains? result :prevented?)))

    (testing "action is applied"
      (is (= :phase/ACTIONS (state/get-phase (:state result)))))

    (testing "not prevented"
      (is (false? (:prevented? result))))))

(deftest apply-action-with-empty-registry-test
  (let [game     (fixtures/base-game-state)
        registry (triggers/create-registry)
        result   (actions/apply-action {:state game :registry registry}
                                       {:type :bashketball/set-phase :phase :phase/ACTIONS})]

    (testing "action is applied"
      (is (= :phase/ACTIONS (state/get-phase (:state result)))))

    (testing "registry is returned"
      (is (some? (:registry result))))))

(deftest apply-action-fires-after-trigger-test
  (let [fired?   (atom false)
        _        (fx/register-effect! :test/mark-fired
                                      (fn [state _params _ctx _opts]
                                        (reset! fired? true)
                                        (fx/success state [])))
        game     (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/set-phase.after}
                       :timing :polix.triggers.timing/after
                       :effect {:type :test/mark-fired}}
                      "test-ability"
                      :team/HOME
                      fixtures/home-player-1))
        result   (actions/apply-action {:state game :registry registry}
                                       {:type :bashketball/set-phase :phase :phase/ACTIONS})]

    (testing "trigger fired"
      (is @fired?))

    (testing "action was applied"
      (is (= :phase/ACTIONS (state/get-phase (:state result)))))))

(deftest apply-action-before-trigger-can-prevent-test
  (let [_        (fx/register-effect! :test/prevent
                                      (fn [state _params _ctx _opts]
                                        {:state state
                                         :applied []
                                         :failed []
                                         :pending nil
                                         :prevented? true}))
        game     (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/set-phase.before}
                       :timing :polix.triggers.timing/before
                       :effect {:type :test/prevent}}
                      "test-ability"
                      :team/HOME
                      fixtures/home-player-1))
        result   (actions/apply-action {:state game :registry registry}
                                       {:type :bashketball/set-phase :phase :phase/ACTIONS})]

    (testing "action was prevented"
      (is (:prevented? result)))

    (testing "state unchanged"
      (is (= :phase/SETUP (state/get-phase (:state result)))))))

(deftest apply-action-trigger-with-condition-test
  (let [fired?   (atom false)
        _        (fx/register-effect! :test/conditional-fire
                                      (fn [state _params _ctx _opts]
                                        (reset! fired? true)
                                        (fx/success state [])))
        game     (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/set-phase.after}
                       :timing :polix.triggers.timing/after
                        ;; Condition: phase must be :phase/ACTIONS
                       :condition [:= :doc/phase [:literal :phase/ACTIONS]]
                       :effect {:type :test/conditional-fire}}
                      "test-ability"
                      :team/HOME
                      fixtures/home-player-1))]

    (testing "trigger fires when condition matches"
      (reset! fired? false)
      (actions/apply-action {:state game :registry registry}
                            {:type :bashketball/set-phase :phase :phase/ACTIONS})
      (is @fired?))

    (testing "trigger does not fire when condition doesn't match"
      (reset! fired? false)
      (actions/apply-action {:state game :registry registry}
                            {:type :bashketball/set-phase :phase :phase/UPKEEP})
      (is (not @fired?)))))

(deftest apply-action-once-trigger-removed-after-fire-test
  (let [fired-count (atom 0)
        _           (fx/register-effect! :test/count-fires
                                         (fn [state _params _ctx _opts]
                                           (swap! fired-count inc)
                                           (fx/success state [])))
        game        (fixtures/base-game-state)
        registry    (-> (triggers/create-registry)
                        (triggers/register-trigger
                         {:event-types #{:bashketball/set-phase.after}
                          :timing :polix.triggers.timing/after
                          :once? true
                          :effect {:type :test/count-fires}}
                         "once-ability"
                         :team/HOME
                         fixtures/home-player-1))
        result1     (actions/apply-action {:state game :registry registry}
                                          {:type :bashketball/set-phase :phase :phase/ACTIONS})
        result2     (actions/apply-action {:state (:state result1) :registry (:registry result1)}
                                          {:type :bashketball/set-phase :phase :phase/UPKEEP})]

    (testing "trigger fired once"
      (is (= 1 @fired-count)))

    (testing "trigger removed from registry after first fire"
      (is (empty? (triggers/get-triggers (:registry result2)))))))
