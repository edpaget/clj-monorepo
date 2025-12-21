(ns bashketball-game.polix.context-test
  (:require
   [bashketball-game.polix.context :as ctx]
   [bashketball-game.polix.fixtures :as fixtures]
   [clojure.test :refer [deftest is testing]]))

(deftest build-game-document-test
  (let [state (fixtures/base-game-state)
        doc   (ctx/build-game-document state)]
    (testing "contains phase"
      (is (= :phase/SETUP (:doc/phase doc))))

    (testing "contains turn number"
      (is (= 1 (:doc/turn-number doc))))

    (testing "contains active player"
      (is (= :team/HOME (:doc/active-player doc))))

    (testing "contains score"
      (is (= {:team/HOME 0 :team/AWAY 0} (:doc/score doc))))

    (testing "contains ball"
      (is (some? (:doc/ball doc))))

    (testing "contains full state"
      (is (= state (:doc/state doc))))))

(deftest build-action-document-test
  (let [state  (fixtures/base-game-state)
        action {:type :bashketball/move-player
                :player-id fixtures/home-player-1
                :position [2 5]}
        doc    (ctx/build-action-document state action)]
    (testing "contains game document fields"
      (is (= :phase/SETUP (:doc/phase doc)))
      (is (= 1 (:doc/turn-number doc))))

    (testing "contains action fields with namespace"
      (is (= :bashketball/move-player (:action/type doc)))
      (is (= fixtures/home-player-1 (:action/player-id doc)))
      (is (= [2 5] (:action/position doc))))))

(deftest build-player-context-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3])
                  (fixtures/with-ball-possessed fixtures/home-player-1)
                  (fixtures/with-exhausted fixtures/home-player-1))
        ctx   (ctx/build-player-context state fixtures/home-player-1)]
    (testing "contains player id"
      (is (= fixtures/home-player-1 (:self/id ctx))))

    (testing "contains position"
      (is (= [2 3] (:self/position ctx))))

    (testing "contains exhausted status"
      (is (true? (:self/exhausted ctx))))

    (testing "contains team"
      (is (= :team/HOME (:self/team ctx))))

    (testing "contains has-ball status"
      (is (true? (:self/has-ball ctx))))))

(deftest build-player-context-off-court-test
  (let [state (fixtures/base-game-state)
        ctx   (ctx/build-player-context state fixtures/home-player-2)]
    (testing "position is nil for off-court player"
      (is (nil? (:self/position ctx))))

    (testing "has-ball is false"
      (is (false? (:self/has-ball ctx))))))

(deftest build-trigger-document-test
  (let [state (-> (fixtures/base-game-state)
                  (fixtures/with-player-at fixtures/home-player-1 [2 3])
                  (fixtures/with-ball-possessed fixtures/home-player-1))
        event {:type :bashketball/move-player.after
               :player-id fixtures/home-player-1
               :position [2 5]}
        doc   (ctx/build-trigger-document state event fixtures/home-player-1)]
    (testing "contains game document fields"
      (is (= :phase/SETUP (:doc/phase doc))))

    (testing "contains event fields"
      (is (= :bashketball/move-player.after (:event/type doc)))
      (is (= fixtures/home-player-1 (:event/player-id doc))))

    (testing "contains self context"
      (is (= fixtures/home-player-1 (:self/id doc)))
      (is (= [2 3] (:self/position doc))))))

(deftest build-effect-context-test
  (testing "with self and target"
    (let [ctx (ctx/build-effect-context {:self-id fixtures/home-player-1
                                         :target-id fixtures/away-player-1
                                         :source "clutch-ability"})]
      (is (= fixtures/home-player-1 (get-in ctx [:bindings :self])))
      (is (= fixtures/away-player-1 (get-in ctx [:bindings :target])))
      (is (= "clutch-ability" (:source ctx)))))

  (testing "with only self"
    (let [ctx (ctx/build-effect-context {:self-id fixtures/home-player-1})]
      (is (= fixtures/home-player-1 (get-in ctx [:bindings :self])))
      (is (not (contains? (:bindings ctx) :target)))))

  (testing "with no bindings"
    (let [ctx (ctx/build-effect-context {})]
      (is (empty? (:bindings ctx))))))
