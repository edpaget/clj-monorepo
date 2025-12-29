(ns bashketball-game-api.services.catalog-test
  "Tests for card catalog service."
  (:require
   [bashketball-game-api.services.catalog :as catalog]
   [clojure.test :refer [deftest is testing]]))

(def test-sets
  [{:slug "test-set"
    :name "Test Set"
    :description "A test set for unit tests"}
   {:slug "other-set"
    :name "Other Set"
    :description "Another test set"}])

(def test-cards
  [{:slug "test-player"
    :name "Test Player"
    :set-slug "test-set"
    :card-type :card-type/PLAYER_CARD
    :sht 5
    :pss 4
    :def 3
    :speed 3
    :size :size/MD
    :abilities []
    :player-subtypes [:player-subtype/HUMAN]}
   {:slug "test-guard"
    :name "Test Guard"
    :set-slug "test-set"
    :card-type :card-type/PLAYER_CARD
    :sht 3
    :pss 5
    :def 2
    :speed 4
    :size :size/SM
    :abilities []
    :player-subtypes [:player-subtype/ELF]}
   {:slug "test-action"
    :name "Test Action"
    :set-slug "test-set"
    :card-type :card-type/STANDARD_ACTION_CARD
    :fate 4
    :offense {:action/id "test-offense"
              :action/description "Test offense"
              :action/effect {:effect/type :bashketball/shoot}}
    :defense {:action/id "test-defense"
              :action/description "Test defense"
              :action/effect {:effect/type :bashketball/defend}}}
   {:slug "other-player"
    :name "Other Player"
    :set-slug "other-set"
    :card-type :card-type/PLAYER_CARD
    :sht 2
    :pss 2
    :def 4
    :speed 2
    :size :size/LG
    :abilities []
    :player-subtypes [:player-subtype/ORC]}])

(def test-catalog (catalog/create-catalog-from-data test-cards test-sets))

(deftest get-sets-test
  (testing "Returns all sets"
    (let [sets (catalog/get-sets test-catalog)]
      (is (= 2 (count sets)))
      (is (some #(= "test-set" (:slug %)) sets))
      (is (some #(= "other-set" (:slug %)) sets)))))

(deftest get-set-test
  (testing "Returns set by slug"
    (let [test-set (catalog/get-set test-catalog "test-set")]
      (is (some? test-set))
      (is (= "test-set" (:slug test-set)))
      (is (= "Test Set" (:name test-set)))))

  (testing "Returns nil for unknown set"
    (is (nil? (catalog/get-set test-catalog "nonexistent")))))

(deftest get-cards-test
  (testing "Returns all cards"
    (let [cards (catalog/get-cards test-catalog)]
      (is (= 4 (count cards)))
      (is (some #(= "test-player" (:slug %)) cards)))))

(deftest get-card-test
  (testing "Returns card by slug"
    (let [player (catalog/get-card test-catalog "test-player")]
      (is (some? player))
      (is (= "test-player" (:slug player)))
      (is (= "Test Player" (:name player)))
      (is (= :card-type/PLAYER_CARD (:card-type player)))
      (is (= 5 (:sht player)))))

  (testing "Returns nil for unknown card"
    (is (nil? (catalog/get-card test-catalog "nonexistent")))))

(deftest get-cards-by-set-test
  (testing "Returns cards filtered by set"
    (let [set-cards (catalog/get-cards-by-set test-catalog "test-set")]
      (is (= 3 (count set-cards)))
      (is (every? #(= "test-set" (:set-slug %)) set-cards))))

  (testing "Returns empty for unknown set"
    (is (empty? (catalog/get-cards-by-set test-catalog "nonexistent")))))

(deftest get-cards-by-type-test
  (testing "Returns cards filtered by type"
    (let [player-cards (catalog/get-cards-by-type test-catalog :card-type/PLAYER_CARD)]
      (is (= 3 (count player-cards)))
      (is (every? #(= :card-type/PLAYER_CARD (:card-type %)) player-cards))))

  (testing "Returns cards of other types"
    (let [action-cards (catalog/get-cards-by-type test-catalog :card-type/STANDARD_ACTION_CARD)]
      (is (= 1 (count action-cards)))
      (is (every? #(= :card-type/STANDARD_ACTION_CARD (:card-type %)) action-cards)))))
