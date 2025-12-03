(ns bashketball-game.schema-test
  (:require [bashketball-game.schema :as schema]
            [clojure.test :refer [deftest is testing]]
            [malli.core :as m]))

(deftest hex-position-schema-test
  (testing "valid hex positions"
    (is (m/validate schema/HexPosition [0 0]))
    (is (m/validate schema/HexPosition [2 7]))
    (is (m/validate schema/HexPosition [4 13])))

  (testing "invalid hex positions"
    (is (not (m/validate schema/HexPosition [-1 0])))
    (is (not (m/validate schema/HexPosition [5 0])))
    (is (not (m/validate schema/HexPosition [0 14])))))

(deftest action-schema-test
  (testing "set-phase action"
    (is (schema/valid-action? {:type :bashketball/set-phase :phase :ACTIONS}))
    (is (not (schema/valid-action? {:type :bashketball/set-phase :phase :invalid}))))

  (testing "move-player action"
    (is (schema/valid-action? {:type :bashketball/move-player
                               :player-id "home-orc-center-0"
                               :position [2 3]}))
    (is (not (schema/valid-action? {:type :bashketball/move-player
                                    :player-id "home-orc-center-0"
                                    :position [10 10]}))))

  (testing "draw-cards action"
    (is (schema/valid-action? {:type :bashketball/draw-cards :player :HOME :count 5}))
    (is (not (schema/valid-action? {:type :bashketball/draw-cards :player :HOME :count 0}))))

  (testing "add-score action"
    (is (schema/valid-action? {:type :bashketball/add-score :team :HOME :points 2}))
    (is (not (schema/valid-action? {:type :bashketball/add-score :team :HOME :points 0})))))

(deftest ball-schema-test
  (testing "possessed ball"
    (is (m/validate schema/Ball {:status :POSSESSED :holder-id "player-1"})))

  (testing "loose ball"
    (is (m/validate schema/Ball {:status :LOOSE :position [2 7]})))

  (testing "in-air ball"
    (is (m/validate schema/Ball {:status :IN_AIR
                                 :origin [2 3]
                                 :target [2 13]
                                 :action-type :SHOT}))))

(deftest modifier-schema-test
  (testing "valid modifier"
    (is (m/validate schema/Modifier
                    {:id "buff-1"
                     :stat :SHOOTING
                     :amount 2})))

  (testing "modifier with optional fields"
    (is (m/validate schema/Modifier
                    {:id "buff-1"
                     :stat :DEFENSE
                     :amount -1
                     :source "foul"
                     :expires-at 5}))))
