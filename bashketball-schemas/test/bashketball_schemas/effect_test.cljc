(ns bashketball-schemas.effect-test
  (:require [bashketball-schemas.effect :as effect]
            [clojure.test :refer [deftest is testing]]
            [malli.core :as m]))

(deftest policy-expr-test
  (testing "validates simple values"
    (is (m/validate effect/PolicyExpr :doc/phase))
    (is (m/validate effect/PolicyExpr "some-string"))
    (is (m/validate effect/PolicyExpr 42))
    (is (m/validate effect/PolicyExpr true)))

  (testing "validates vector expressions"
    (is (m/validate effect/PolicyExpr [:= :doc/phase :phase/PLAY]))
    (is (m/validate effect/PolicyExpr [:and [:= :a 1] [:= :b 2]]))
    (is (m/validate effect/PolicyExpr [:bashketball/has-ball? :doc/state :self/id]))))

(deftest trigger-def-test
  (testing "validates minimal trigger"
    (is (m/validate effect/TriggerDef
                    {:trigger/event :bashketball/shoot.after})))

  (testing "validates full trigger"
    (is (m/validate effect/TriggerDef
                    {:trigger/event :bashketball/skill-test.before
                     :trigger/condition [:= :event/test-type :shoot]
                     :trigger/timing :before
                     :trigger/priority 10
                     :trigger/once? true})))

  (testing "rejects invalid timing"
    (is (not (m/validate effect/TriggerDef
                         {:trigger/event :bashketball/shoot.after
                          :trigger/timing :invalid})))))

(deftest effect-def-test
  (testing "validates effect with type"
    (is (m/validate effect/EffectDef
                    {:effect/type :bashketball/move-player})))

  (testing "validates effect with additional keys"
    (is (m/validate effect/EffectDef
                    {:effect/type :bashketball/move-player
                     :player-id :self/id
                     :position [2 3]}))))

(deftest ability-def-test
  (testing "validates triggered ability"
    (is (m/validate effect/AbilityDef
                    {:ability/id "clutch"
                     :ability/name "Clutch"
                     :ability/trigger {:trigger/event :bashketball/skill-test.before
                                       :trigger/condition [:= :doc/quarter 4]}
                     :ability/effect {:effect/type :bashketball/add-modifier
                                      :stat :stat/SHOOTING
                                      :amount 2}})))

  (testing "validates passive ability"
    (is (m/validate effect/AbilityDef
                    {:ability/id "fadeaway"
                     :ability/condition [:bashketball/has-ball? :doc/state :self/id]})))

  (testing "validates minimal ability"
    (is (m/validate effect/AbilityDef
                    {:ability/id "basic"}))))

(deftest play-def-test
  (testing "validates play definition"
    (is (m/validate effect/PlayDef
                    {:play/id "fast-break"
                     :play/name "Fast Break"
                     :play/effect {:effect/type :bashketball/sequence
                                   :effects []}})))

  (testing "validates play with requirements"
    (is (m/validate effect/PlayDef
                    {:play/id "alley-oop"
                     :play/requires [:bashketball/has-ball? :doc/state :actor/id]
                     :play/targets [:target/player-id]
                     :play/effect {:effect/type :bashketball/pass-and-shoot}}))))

(deftest action-mode-def-test
  (testing "validates offense mode"
    (is (m/validate effect/ActionModeDef
                    {:action/id "shoot"
                     :action/name "Shoot"
                     :action/requires [:bashketball/has-ball? :doc/state :actor/id]
                     :action/effect {:effect/type :bashketball/initiate-skill-test
                                     :test-type :shoot}})))

  (testing "validates defense mode"
    (is (m/validate effect/ActionModeDef
                    {:action/id "block"
                     :action/effect {:effect/type :bashketball/force-choice}}))))

(deftest call-def-test
  (testing "validates call definition"
    (is (m/validate effect/CallDef
                    {:call/id "zone-defense"
                     :call/effect {:effect/type :bashketball/add-team-modifier}}))))

(deftest signal-def-test
  (testing "validates signal definition"
    (is (m/validate effect/SignalDef
                    {:signal/id "quick-release-signal"
                     :signal/effect {:effect/type :bashketball/add-modifier
                                     :stat :stat/SHOOTING
                                     :amount 1}}))))

(deftest response-def-test
  (testing "validates response definition"
    (is (m/validate effect/ResponseDef
                    {:response/trigger {:trigger/event :bashketball/skill-test.before
                                        :trigger/condition [:= :event/defender-team :self/team]}
                     :response/prompt "Call Defensive Timeout?"
                     :response/effect {:effect/type :bashketball/add-modifier
                                       :stat :stat/DEFENSE
                                       :amount 2}}))))

(deftest asset-power-def-test
  (testing "validates asset with triggers"
    (is (m/validate effect/AssetPowerDef
                    {:asset/id "home-court"
                     :asset/triggers [{:trigger {:trigger/event :bashketball/turn-started.at
                                                 :trigger/condition [:= :doc/active-player :self/team]}
                                       :effect {:effect/type :bashketball/draw-cards
                                                :count 1}}]})))

  (testing "validates asset with activated ability"
    (is (m/validate effect/AssetPowerDef
                    {:asset/id "timeout"
                     :asset/activated {:cost {:effect/type :bashketball/exhaust-asset}
                                       :effect {:effect/type :bashketball/refresh-all}}})))

  (testing "validates response asset"
    (is (m/validate effect/AssetPowerDef
                    {:asset/id "trap-play"
                     :asset/response {:response/trigger {:trigger/event :bashketball/pass.before}
                                      :response/prompt "Spring trap?"
                                      :response/effect {:effect/type :bashketball/steal-ball}}}))))
