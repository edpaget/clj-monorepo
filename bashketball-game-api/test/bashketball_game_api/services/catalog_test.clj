(ns bashketball-game-api.services.catalog-test
  "Tests for card catalog service."
  (:require
   [bashketball-game-api.services.catalog :as catalog]
   [clojure.test :refer [deftest is testing]]))

(def test-catalog (catalog/create-card-catalog))

(deftest get-sets-test
  (testing "Returns all sets"
    (let [sets (catalog/get-sets test-catalog)]
      (is (>= (count sets) 2))
      (is (some #(= "base" (:slug %)) sets))
      (is (some #(= "demo-set" (:slug %)) sets)))))

(deftest get-set-test
  (testing "Returns set by slug"
    (let [base-set (catalog/get-set test-catalog "base")]
      (is (some? base-set))
      (is (= "base" (:slug base-set)))
      (is (= "Base Set" (:name base-set)))))

  (testing "Returns nil for unknown set"
    (is (nil? (catalog/get-set test-catalog "nonexistent")))))

(deftest get-cards-test
  (testing "Returns all cards"
    (let [cards (catalog/get-cards test-catalog)]
      (is (>= (count cards) 20))
      (is (some #(= "michael-jordan" (:slug %)) cards)))))

(deftest get-card-test
  (testing "Returns card by slug"
    (let [mj (catalog/get-card test-catalog "michael-jordan")]
      (is (some? mj))
      (is (= "michael-jordan" (:slug mj)))
      (is (= "Michael Jordan" (:name mj)))
      (is (= :card-type/PLAYER_CARD (:card-type mj)))
      (is (= 5 (:sht mj)))))

  (testing "Returns nil for unknown card"
    (is (nil? (catalog/get-card test-catalog "nonexistent")))))

(deftest get-cards-by-set-test
  (testing "Returns cards filtered by set"
    (let [demo-cards (catalog/get-cards-by-set test-catalog "demo-set")]
      (is (>= (count demo-cards) 5))
      (is (every? #(= "demo-set" (:set-slug %)) demo-cards))))

  (testing "Returns empty for unknown set"
    (is (empty? (catalog/get-cards-by-set test-catalog "nonexistent")))))

(deftest get-cards-by-type-test
  (testing "Returns cards filtered by type"
    (let [player-cards (catalog/get-cards-by-type test-catalog :card-type/PLAYER_CARD)]
      (is (>= (count player-cards) 10))
      (is (every? #(= :card-type/PLAYER_CARD (:card-type %)) player-cards)))))
