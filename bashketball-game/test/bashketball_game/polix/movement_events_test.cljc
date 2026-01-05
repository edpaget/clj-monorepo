(ns bashketball-game.polix.movement-events-test
  "Tests for step-by-step movement with event-driven architecture."
  (:require
   [bashketball-game.actions :as actions]
   [bashketball-game.movement :as movement]
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
;;; Movement Context Tests
;;; ---------------------------------------------------------------------------

(deftest begin-movement-action-test
  (let [game     (-> (fixtures/base-game-state)
                     (fixtures/with-player-at fixtures/home-player-1 [2 3]))
        result   (actions/do-action game
                                    {:type :bashketball/begin-movement
                                     :player-id fixtures/home-player-1
                                     :speed 3})
        movement (state/get-pending-movement result)]
    (testing "creates pending movement context"
      (is (some? movement))
      (is (string? (:id movement))))

    (testing "sets initial movement values"
      (is (= fixtures/home-player-1 (:player-id movement)))
      (is (= :team/HOME (:team movement)))
      (is (= [2 3] (:starting-position movement)))
      (is (= [2 3] (:current-position movement)))
      (is (= 3 (:initial-speed movement)))
      (is (= 3 (:remaining-speed movement)))
      (is (= [[2 3]] (:path-taken movement)))
      (is (= 0 (:step-number movement))))))

(deftest do-move-step-action-test
  (let [game     (-> (fixtures/base-game-state)
                     (fixtures/with-player-at fixtures/home-player-1 [2 3])
                     (actions/do-action {:type :bashketball/begin-movement
                                         :player-id fixtures/home-player-1
                                         :speed 3}))
        result   (actions/do-action game
                                    {:type :bashketball/do-move-step
                                     :player-id fixtures/home-player-1
                                     :to-position [2 4]
                                     :cost 1})
        movement (state/get-pending-movement result)
        player   (state/get-basketball-player result fixtures/home-player-1)]
    (testing "moves player to new position"
      (is (= [2 4] (:position player))))

    (testing "updates movement context"
      (is (= [2 4] (:current-position movement)))
      (is (= 2 (:remaining-speed movement)))
      (is (= [[2 3] [2 4]] (:path-taken movement)))
      (is (= 1 (:step-number movement))))))

(deftest end-movement-action-test
  (let [game   (-> (fixtures/base-game-state)
                   (fixtures/with-player-at fixtures/home-player-1 [2 3])
                   (actions/do-action {:type :bashketball/begin-movement
                                       :player-id fixtures/home-player-1
                                       :speed 3})
                   (actions/do-action {:type :bashketball/do-move-step
                                       :player-id fixtures/home-player-1
                                       :to-position [2 4]
                                       :cost 1}))
        result (actions/do-action game
                                  {:type :bashketball/end-movement
                                   :player-id fixtures/home-player-1})]
    (testing "clears pending movement"
      (is (nil? (state/get-pending-movement result))))))

;;; ---------------------------------------------------------------------------
;;; Movement Cost Tests
;;; ---------------------------------------------------------------------------

(deftest compute-step-cost-no-zoc-test
  (let [game (-> (fixtures/base-game-state)
                 (fixtures/with-player-at fixtures/home-player-1 [2 3]))
        cost (movement/compute-step-cost game fixtures/home-player-1 [2 4])]
    (testing "base cost is 1 with no defenders"
      (is (= 1 (:base-cost cost)))
      (is (= 0 (:zoc-cost cost)))
      (is (= 1 (:total-cost cost)))
      (is (empty? (:zoc-defender-ids cost))))))

(deftest compute-step-cost-with-zoc-test
  (let [game (-> (fixtures/base-game-state)
                 (fixtures/with-player-at fixtures/home-player-2 [2 3])    ; SM mover
                 (fixtures/with-player-at fixtures/away-player-1 [2 4]))   ; LG defender
        cost (movement/compute-step-cost game fixtures/home-player-2 [2 4])]
    (testing "larger defender adds +2 ZoC cost"
      (is (= 1 (:base-cost cost)))
      (is (= 2 (:zoc-cost cost)))
      (is (= 3 (:total-cost cost)))
      (is (= [fixtures/away-player-1] (:zoc-defender-ids cost))))))

(deftest compute-step-cost-same-size-zoc-test
  (let [game (-> (fixtures/base-game-state)
                 (fixtures/with-player-at fixtures/home-player-3 [2 3])    ; MD mover
                 (fixtures/with-player-at fixtures/away-player-3 [2 4]))   ; MD defender
        cost (movement/compute-step-cost game fixtures/home-player-3 [2 4])]
    (testing "same size defender adds +1 ZoC cost"
      (is (= 1 (:base-cost cost)))
      (is (= 1 (:zoc-cost cost)))
      (is (= 2 (:total-cost cost))))))

(deftest compute-step-cost-smaller-defender-test
  (let [game (-> (fixtures/base-game-state)
                 (fixtures/with-player-at fixtures/home-player-1 [2 3])    ; LG mover
                 (fixtures/with-player-at fixtures/away-player-2 [2 4]))   ; SM defender
        cost (movement/compute-step-cost game fixtures/home-player-1 [2 4])]
    (testing "smaller defender adds +0 ZoC cost"
      (is (= 1 (:base-cost cost)))
      (is (= 0 (:zoc-cost cost)))
      (is (= 1 (:total-cost cost))))))

(deftest compute-step-cost-exhausted-defender-test
  (let [game (-> (fixtures/base-game-state)
                 (fixtures/with-player-at fixtures/home-player-2 [2 3])
                 (fixtures/with-player-at fixtures/away-player-1 [2 4])
                 (fixtures/with-exhausted fixtures/away-player-1))
        cost (movement/compute-step-cost game fixtures/home-player-2 [2 4])]
    (testing "exhausted defender exerts no ZoC"
      (is (= 0 (:zoc-cost cost)))
      (is (= 1 (:total-cost cost))))))

;;; ---------------------------------------------------------------------------
;;; Terminal Effect Tests
;;; ---------------------------------------------------------------------------

(deftest begin-movement-effect-test
  (let [game     (-> (fixtures/base-game-state)
                     (fixtures/with-player-at fixtures/home-player-1 [2 3]))
        result   (fx/apply-effect game
                                  {:type :bashketball/begin-movement
                                   :player-id fixtures/home-player-1
                                   :speed 3}
                                  {}
                                  {:validate? false})
        movement (state/get-pending-movement (:state result))]
    (testing "effect creates movement context"
      (is (some? movement))
      (is (= 3 (:remaining-speed movement))))))

(deftest do-move-step-effect-test
  (let [game     (-> (fixtures/base-game-state)
                     (fixtures/with-player-at fixtures/home-player-1 [2 3])
                     (actions/do-action {:type :bashketball/begin-movement
                                         :player-id fixtures/home-player-1
                                         :speed 3}))
        result   (fx/apply-effect game
                                  {:type :bashketball/do-move-step
                                   :player-id fixtures/home-player-1
                                   :to-position [2 4]
                                   :cost 2}
                                  {}
                                  {:validate? false})
        movement (state/get-pending-movement (:state result))]
    (testing "effect moves player and deducts cost"
      (is (= [2 4] (:position (state/get-basketball-player (:state result) fixtures/home-player-1))))
      (is (= 1 (:remaining-speed movement))))))

;;; ---------------------------------------------------------------------------
;;; Event-Driven Move Step Tests
;;; ---------------------------------------------------------------------------

(deftest move-step-fires-exit-event-test
  (let [exit-events (atom [])
        _           (fx/register-effect! :test/record-exit
                                         (fn [state _params ctx _opts]
                                           (swap! exit-events conj (:event ctx))
                                           (fx/success state [])))
        game        (-> (fixtures/base-game-state)
                        (fixtures/with-player-at fixtures/home-player-1 [2 3])
                        (actions/do-action {:type :bashketball/begin-movement
                                            :player-id fixtures/home-player-1
                                            :speed 3}))
        registry    (-> (triggers/create-registry)
                        (game-rules/register-game-rules!)
                        (triggers/register-trigger
                         {:event-types #{:bashketball/player-exiting-hex.request}
                          :timing :polix.triggers.timing/before
                          :priority 100
                          :effect {:type :test/record-exit}}
                         "exit-recorder"
                         :team/HOME
                         nil))]

    (reset! exit-events [])
    (fx/apply-effect game
                     {:type :bashketball/move-step
                      :player-id fixtures/home-player-1
                      :to-position [2 4]}
                     {}
                     {:registry registry
                      :validate? false})

    (testing "fires exit event with correct data"
      (is (= 1 (count @exit-events)))
      (let [event (first @exit-events)]
        (is (= :bashketball/player-exiting-hex.request (:event-type event)))
        (is (= [2 3] (:from-position event)))
        (is (= [2 4] (:to-position event)))
        (is (= fixtures/home-player-1 (:player-id event)))))))

(deftest move-step-fires-enter-event-test
  (let [enter-events (atom [])
        _            (fx/register-effect! :test/record-enter
                                          (fn [state _params ctx _opts]
                                            (swap! enter-events conj (:event ctx))
                                            (fx/success state [])))
        game         (-> (fixtures/base-game-state)
                         (fixtures/with-player-at fixtures/home-player-1 [2 3])
                         (actions/do-action {:type :bashketball/begin-movement
                                             :player-id fixtures/home-player-1
                                             :speed 3}))
        registry     (-> (triggers/create-registry)
                         (game-rules/register-game-rules!)
                         (triggers/register-trigger
                          {:event-types #{:bashketball/player-entering-hex.request}
                           :timing :polix.triggers.timing/before
                           :priority 100
                           :effect {:type :test/record-enter}}
                          "enter-recorder"
                          :team/HOME
                          nil))]

    (reset! enter-events [])
    (fx/apply-effect game
                     {:type :bashketball/move-step
                      :player-id fixtures/home-player-1
                      :to-position [2 4]}
                     {}
                     {:registry registry
                      :validate? false})

    (testing "fires enter event with position data"
      (is (= 1 (count @enter-events)))
      (let [event (first @enter-events)]
        (is (= :bashketball/player-entering-hex.request (:event-type event)))
        (is (= [2 3] (:from-position event)))
        (is (= [2 4] (:to-position event)))
        (is (= fixtures/home-player-1 (:player-id event)))))))

(deftest move-step-catchall-rule-moves-player-test
  (let [game     (-> (fixtures/base-game-state)
                     (fixtures/with-player-at fixtures/home-player-1 [2 3])
                     (actions/do-action {:type :bashketball/begin-movement
                                         :player-id fixtures/home-player-1
                                         :speed 3}))
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        result   (fx/apply-effect game
                                  {:type :bashketball/move-step
                                   :player-id fixtures/home-player-1
                                   :to-position [2 4]}
                                  {}
                                  {:registry registry
                                   :validate? false})
        player   (state/get-basketball-player (:state result) fixtures/home-player-1)]
    (testing "catchall rule produces do-move-step effect"
      (is (= [2 4] (:position player))))))

;;; ---------------------------------------------------------------------------
;;; Full Movement Flow Test
;;; ---------------------------------------------------------------------------

(deftest complete-movement-flow-test
  (let [game        (-> (fixtures/base-game-state)
                        (fixtures/with-player-at fixtures/home-player-1 [2 3]))
        registry    (-> (triggers/create-registry)
                        (game-rules/register-game-rules!))

        ;; Begin movement
        game-1      (actions/do-action game
                                       {:type :bashketball/begin-movement
                                        :player-id fixtures/home-player-1
                                        :speed 3})

        ;; Take first step
        result-1    (fx/apply-effect game-1
                                     {:type :bashketball/move-step
                                      :player-id fixtures/home-player-1
                                      :to-position [2 4]}
                                     {}
                                     {:registry registry
                                      :validate? false})

        ;; Take second step
        result-2    (fx/apply-effect (:state result-1)
                                     {:type :bashketball/move-step
                                      :player-id fixtures/home-player-1
                                      :to-position [2 5]}
                                     {}
                                     {:registry (:registry result-1)
                                      :validate? false})

        ;; End movement
        final-state (actions/do-action (:state result-2)
                                       {:type :bashketball/end-movement
                                        :player-id fixtures/home-player-1})]

    (testing "player ends at final position"
      (is (= [2 5] (:position (state/get-basketball-player final-state fixtures/home-player-1)))))

    (testing "movement context is cleared"
      (is (nil? (state/get-pending-movement final-state))))))
