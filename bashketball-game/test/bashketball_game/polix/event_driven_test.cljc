(ns bashketball-game.polix.event-driven-test
  "Tests for the event-driven architecture with causation and counters."
  (:require
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.event-context :as event-ctx]
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
;;; Event Context Tests
;;; ---------------------------------------------------------------------------

(deftest counter-key-test
  (let [state {:turn-number 3}
        event {:event-type :bashketball/draw-cards.request :team :team/HOME}]
    (is (= [3 :bashketball/draw-cards.request :team/HOME]
           (event-ctx/counter-key state event)))))

(deftest increment-counter-test
  (let [ctx {:state {:turn-number 1}}
        event {:event-type :bashketball/draw-cards.request :team :team/HOME}
        [ctx' count1] (event-ctx/increment-counter ctx event)
        [_ctx'' count2] (event-ctx/increment-counter ctx' event)]
    (is (= 1 count1))
    (is (= 2 count2))))

(deftest in-causation-test
  (is (event-ctx/in-causation? {:causation ["a" "b" "c"]} "b"))
  (is (not (event-ctx/in-causation? {:causation ["a" "b"]} "c")))
  (is (not (event-ctx/in-causation? {:causation []} "a")))
  (is (not (event-ctx/in-causation? {} "a"))))

(deftest add-to-causation-test
  (is (= ["a"] (event-ctx/add-to-causation nil "a")))
  (is (= ["a" "b"] (event-ctx/add-to-causation ["a"] "b"))))

(deftest can-trigger-fire-test
  (let [ctx {:executing-triggers #{}}
        trigger {:id "test-trigger"}
        event {:causation []}]
    (testing "can fire when not in causation and not locked"
      (is (event-ctx/can-trigger-fire? ctx trigger event)))

    (testing "cannot fire when in causation"
      (is (not (event-ctx/can-trigger-fire? ctx trigger {:causation ["test-trigger"]}))))

    (testing "cannot fire when locked"
      (is (not (event-ctx/can-trigger-fire?
                {:executing-triggers #{"test-trigger"}}
                trigger
                event))))))

;;; ---------------------------------------------------------------------------
;;; Game Rules Tests
;;; ---------------------------------------------------------------------------

(deftest register-game-rules-test
  (let [registry (triggers/create-registry)
        registry' (game-rules/register-game-rules! registry)]
    (testing "draw-cards rule is registered"
      (is (some #(and (contains? (:event-types %) :bashketball/draw-cards.request)
                      (= 1000 (:priority %)))
                (triggers/get-triggers registry'))))))

;;; ---------------------------------------------------------------------------
;;; Terminal Effect Tests
;;; ---------------------------------------------------------------------------

(deftest do-draw-cards-terminal-effect-test
  (let [game (fixtures/base-game-state)
        hand-before (count (state/get-hand game :team/HOME))
        draw-pile-before (count (state/get-draw-pile game :team/HOME))
        result (fx/apply-effect game
                                {:type :bashketball/do-draw-cards
                                 :player :team/HOME
                                 :count 2}
                                {}
                                {:validate? false})]
    (testing "cards moved from draw pile to hand"
      (is (= (+ hand-before 2)
             (count (state/get-hand (:state result) :team/HOME))))
      (is (= (- draw-pile-before 2)
             (count (state/get-draw-pile (:state result) :team/HOME)))))

    (testing "result contains drawn cards info"
      (is (= 2 (count (:drew (first (:applied result)))))))))

;;; ---------------------------------------------------------------------------
;;; Fire Request Event Tests
;;; ---------------------------------------------------------------------------

(deftest fire-request-event-increments-counter-test
  (let [game (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        event {:event-type :bashketball/draw-cards.request
               :team :team/HOME
               :player :team/HOME
               :count 1}
        result (triggers/fire-request-event
                {:state game :registry registry}
                event)]
    (testing "counter is incremented"
      (is (= 1 (get (:event-counters result)
                    [1 :bashketball/draw-cards.request :team/HOME]))))))

(deftest fire-request-event-with-catchall-rule-test
  (let [game (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!))
        hand-before (count (state/get-hand game :team/HOME))
        event {:event-type :bashketball/draw-cards.request
               :team :team/HOME
               :player :team/HOME
               :count 2}
        result (triggers/fire-request-event
                {:state game :registry registry}
                event)]
    (testing "catchall rule fires and draws cards"
      (is (= (+ hand-before 2)
             (count (state/get-hand (:state result) :team/HOME)))))

    (testing "event has occurrence attached"
      (is (= 1 (:occurrence-this-turn (:event result)))))))

;;; ---------------------------------------------------------------------------
;;; Causation Chain Tests
;;; ---------------------------------------------------------------------------

(deftest causation-prevents-self-trigger-test
  (let [fire-count (atom 0)
        _ (fx/register-effect! :test/bonus-draw
                               (fn [state _params _ctx opts]
                                 (swap! fire-count inc)
                                 ;; Try to trigger another draw
                                 (let [event {:event-type :bashketball/draw-cards.request
                                              :team :team/HOME
                                              :player :team/HOME
                                              :count 1
                                              :causation (:causation opts)}]
                                   (triggers/fire-request-event
                                    {:state state
                                     :registry (:registry opts)
                                     :event-counters (:event-counters opts)}
                                    event))))
        game (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/draw-cards.request}
                       :timing :polix.triggers.timing/after
                       :priority 100
                       :effect {:type :test/bonus-draw}}
                      "bonus-draw-ability"
                      :team/HOME
                      nil))]

    (reset! fire-count 0)
    (triggers/fire-request-event
     {:state game :registry registry}
     {:event-type :bashketball/draw-cards.request
      :team :team/HOME
      :player :team/HOME
      :count 1})

    (testing "trigger only fires once due to causation blocking"
      (is (= 1 @fire-count)))))

;;; ---------------------------------------------------------------------------
;;; Occurrence Counter Condition Tests
;;; ---------------------------------------------------------------------------

(deftest second-occurrence-trigger-test
  (let [fired-at-counts (atom [])
        _ (fx/register-effect! :test/record-occurrence
                               (fn [state _params ctx _opts]
                                 (swap! fired-at-counts conj
                                        (get-in ctx [:event :occurrence-this-turn]))
                                 (fx/success state [])))
        game (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (game-rules/register-game-rules!)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/draw-cards.request}
                       :timing :polix.triggers.timing/after
                       :priority 100
                       :condition [:= :doc/occurrence-this-turn 2]
                       :effect {:type :test/record-occurrence}}
                      "second-draw-trigger"
                      :team/HOME
                      nil))]

    (reset! fired-at-counts [])

    ;; First draw
    (let [result1 (triggers/fire-request-event
                   {:state game :registry registry}
                   {:event-type :bashketball/draw-cards.request
                    :team :team/HOME
                    :player :team/HOME
                    :count 1})
          ;; Second draw
          result2 (triggers/fire-request-event
                   {:state (:state result1)
                    :registry (:registry result1)
                    :event-counters (:event-counters result1)}
                   {:event-type :bashketball/draw-cards.request
                    :team :team/HOME
                    :player :team/HOME
                    :count 1})
          ;; Third draw
          _ (triggers/fire-request-event
             {:state (:state result2)
              :registry (:registry result2)
              :event-counters (:event-counters result2)}
             {:event-type :bashketball/draw-cards.request
              :team :team/HOME
              :player :team/HOME
              :count 1})]

      (testing "trigger fires exactly on 2nd occurrence"
        (is (= [2] @fired-at-counts))))))

;;; ---------------------------------------------------------------------------
;;; Depth Limit Tests
;;; ---------------------------------------------------------------------------

(deftest depth-limit-prevents-infinite-recursion-test
  (let [_ (fx/register-effect! :test/infinite-loop
                               (fn [state _params _ctx opts]
                                 ;; Always try to recurse
                                 (triggers/fire-request-event
                                  {:state state
                                   :registry (:registry opts)
                                   :event-counters (:event-counters opts)
                                   :event-depth (:event-depth opts)}
                                  {:event-type :test/loop.request
                                   :team :team/HOME})))
        game (fixtures/base-game-state)
        registry (-> (triggers/create-registry)
                     (triggers/register-trigger
                      {:event-types #{:test/loop.request}
                       :timing :polix.triggers.timing/after
                       :effect {:type :test/infinite-loop}}
                      "loop-trigger"
                      :team/HOME
                      nil))]

    (testing "throws when depth exceeds limit"
      (is (thrown-with-msg? #?(:clj Exception :cljs js/Error)
                            #"Event recursion limit exceeded"
                            (triggers/fire-request-event
                             {:state game :registry registry}
                             {:event-type :test/loop.request
                              :team :team/HOME}))))))
