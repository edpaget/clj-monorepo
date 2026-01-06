(ns bashketball-game.polix.stats-test
  "Tests for event-based stat calculation."
  (:require
   [bashketball-game.polix.core :as polix]
   [bashketball-game.polix.fixtures :as f]
   [bashketball-game.polix.stats :as stats]
   [bashketball-game.polix.triggers :as triggers]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [polix.effects.core :as fx]))

(use-fixtures :once
  (fn [t]
    (polix/initialize!)
    (t)))

(defn- test-ctx
  "Creates test context with state and empty registry."
  [game-state]
  {:state game-state
   :registry (triggers/create-registry)})

;;; ---------------------------------------------------------------------------
;;; Basic Speed Tests
;;; ---------------------------------------------------------------------------

(deftest get-effective-speed-uses-player-stats-test
  (let [game (f/base-game-state)
        ctx  (test-ctx game)]
    (testing "returns speed from player stats"
      (let [result (stats/get-effective-speed ctx f/home-player-1)]
        (is (= 2 (:value result)))))))

(deftest get-effective-speed-different-players-test
  (let [game (f/base-game-state)
        ctx  (test-ctx game)]
    (testing "fast player has higher speed"
      (let [result (stats/get-effective-speed ctx f/home-player-2)]
        (is (= 5 (:value result)))))
    (testing "away medium player has speed 3"
      (let [result (stats/get-effective-speed ctx f/away-player-3)]
        (is (= 3 (:value result)))))))

(deftest get-effective-speed-defaults-to-2-test
  (let [game (-> (f/base-game-state)
                 (update-in [:players :team/HOME :players f/home-player-1 :stats]
                            dissoc :speed))
        ctx  (test-ctx game)]
    (testing "defaults to 2 when no speed in stats"
      (let [result (stats/get-effective-speed ctx f/home-player-1)]
        (is (= 2 (:value result)))))))

;;; ---------------------------------------------------------------------------
;;; Generic Stat Tests
;;; ---------------------------------------------------------------------------

(deftest get-effective-stat-shooting-test
  (let [game (f/base-game-state)
        ctx  (test-ctx game)]
    (testing "returns shooting stat"
      (let [result (stats/get-effective-stat ctx f/home-player-1 :shooting)]
        (is (= 2 (:value result)))))))

(deftest get-effective-stat-defense-test
  (let [game (f/base-game-state)
        ctx  (test-ctx game)]
    (testing "returns defense stat"
      (let [result (stats/get-effective-stat ctx f/home-player-1 :defense)]
        (is (= 4 (:value result)))))))

(deftest get-effective-stat-missing-stat-test
  (let [game (f/base-game-state)
        ctx  (test-ctx game)]
    (testing "returns 0 for missing stat"
      (let [result (stats/get-effective-stat ctx f/home-player-1 :nonexistent)]
        (is (= 0 (:value result)))))))

;;; ---------------------------------------------------------------------------
;;; Modifier Injection Tests
;;; ---------------------------------------------------------------------------

(deftest speed-modifier-injection-test
  (fx/register-effect! :test/inject-speed-boost
                       (fn [state _params _ctx _opts]
                         {:state state
                          :applied []
                          :failed []
                          :pending nil
                          :modifiers [{:stat :speed :amount 2}]}))
  (let [game     (f/base-game-state)
        registry (-> (triggers/create-registry)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/calculate-speed.request}
                       :timing :polix.triggers.timing/before
                       :priority 100
                       :effect {:type :test/inject-speed-boost}}
                      "speed-boost"
                      :team/HOME
                      nil))
        ctx      {:state game :registry registry}]
    (testing "trigger can inject speed modifier"
      (let [result (stats/get-effective-speed ctx f/home-player-1)]
        (is (= 4 (:value result)))))))

(deftest stat-modifier-injection-test
  (fx/register-effect! :test/inject-shooting-boost
                       (fn [state _params _ctx _opts]
                         {:state state
                          :applied []
                          :failed []
                          :pending nil
                          :modifiers [{:stat :shooting :amount 3}]}))
  (let [game     (f/base-game-state)
        registry (-> (triggers/create-registry)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/calculate-stat.request}
                       :timing :polix.triggers.timing/before
                       :priority 100
                       :effect {:type :test/inject-shooting-boost}}
                      "shooting-boost"
                      :team/HOME
                      nil))
        ctx      {:state game :registry registry}]
    (testing "trigger can inject stat modifier"
      (let [result (stats/get-effective-stat ctx f/home-player-1 :shooting)]
        (is (= 5 (:value result)))))))

(deftest multiplier-modifier-test
  (fx/register-effect! :test/inject-speed-multiplier
                       (fn [state _params _ctx _opts]
                         {:state state
                          :applied []
                          :failed []
                          :pending nil
                          :modifiers [{:stat :speed :multiplier 2}]}))
  (let [game     (f/base-game-state)
        registry (-> (triggers/create-registry)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/calculate-speed.request}
                       :timing :polix.triggers.timing/before
                       :priority 100
                       :effect {:type :test/inject-speed-multiplier}}
                      "speed-multiplier"
                      :team/HOME
                      nil))
        ctx      {:state game :registry registry}]
    (testing "multiplier doubles speed"
      (let [result (stats/get-effective-speed ctx f/home-player-1)]
        (is (= 4 (:value result)))))))

(deftest combined-modifiers-test
  (fx/register-effect! :test/inject-combined
                       (fn [state _params _ctx _opts]
                         {:state state
                          :applied []
                          :failed []
                          :pending nil
                          :modifiers [{:stat :speed :amount 1}
                                      {:stat :speed :multiplier 2}]}))
  (let [game     (f/base-game-state)
        registry (-> (triggers/create-registry)
                     (triggers/register-trigger
                      {:event-types #{:bashketball/calculate-speed.request}
                       :timing :polix.triggers.timing/before
                       :priority 100
                       :effect {:type :test/inject-combined}}
                      "combined-boost"
                      :team/HOME
                      nil))
        ctx      {:state game :registry registry}]
    (testing "additives apply before multipliers"
      (let [result (stats/get-effective-speed ctx f/home-player-1)]
        (is (= 6 (:value result)))))))
