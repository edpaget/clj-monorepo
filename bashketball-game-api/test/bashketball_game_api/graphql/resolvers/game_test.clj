(ns bashketball-game-api.graphql.resolvers.game-test
  "Unit tests for game resolver helper functions."
  (:require
   [bashketball-game-api.graphql.resolvers.game :as game-resolvers]
   [clojure.test :refer [deftest is testing]]))

;; Access private functions for testing
(def collect-deck-slugs #'game-resolvers/collect-deck-slugs)
(def hydrate-deck #'game-resolvers/hydrate-deck)
(def hydrate-game-state #'game-resolvers/hydrate-game-state)

(def test-catalog
  {"michael-jordan" {:slug "michael-jordan" :name "Michael Jordan" :speed 9}
   "shaq"           {:slug "shaq" :name "Shaquille O'Neal" :speed 5}
   "basic-shot"     {:slug "basic-shot" :name "Basic Shot" :fate 3}})

(deftest collect-deck-slugs-test
  (testing "extracts slugs from all piles"
    (let [deck {:draw-pile [{:card-slug "a"} {:card-slug "b"}]
                :hand      [{:card-slug "c"}]
                :discard   [{:card-slug "d"}]
                :removed   [{:card-slug "e"}]}]
      (is (= #{"a" "b" "c" "d" "e"} (set (collect-deck-slugs deck))))))

  (testing "returns distinct slugs"
    (let [deck {:draw-pile [{:card-slug "a"} {:card-slug "a"}]
                :hand      [{:card-slug "a"}]
                :discard   []
                :removed   []}]
      (is (= ["a"] (vec (collect-deck-slugs deck))))))

  (testing "handles empty piles"
    (let [deck {:draw-pile []
                :hand      []
                :discard   []
                :removed   []}]
      (is (empty? (collect-deck-slugs deck)))))

  (testing "handles nil deck"
    (is (empty? (collect-deck-slugs nil)))))

(deftest hydrate-deck-test
  (testing "adds cards field with hydrated data"
    (let [deck   {:draw-pile [{:card-slug "michael-jordan"}]
                  :hand      [{:card-slug "basic-shot"}]
                  :discard   []
                  :removed   []}
          result (hydrate-deck deck test-catalog)]
      (is (= 2 (count (:cards result))))
      (is (some #(= "michael-jordan" (:slug %)) (:cards result)))
      (is (some #(= "basic-shot" (:slug %)) (:cards result)))))

  (testing "filters out missing cards"
    (let [deck   {:draw-pile [{:card-slug "unknown-card"}]
                  :hand      [{:card-slug "michael-jordan"}]
                  :discard   []
                  :removed   []}
          result (hydrate-deck deck test-catalog)]
      (is (= 1 (count (:cards result))))
      (is (= "michael-jordan" (:slug (first (:cards result)))))))

  (testing "returns empty cards when catalog is nil"
    (let [deck {:draw-pile [{:card-slug "a"}]
                :hand      []
                :discard   []
                :removed   []}]
      (is (= [] (:cards (hydrate-deck deck nil))))))

  (testing "handles empty deck"
    (let [deck   {:draw-pile []
                  :hand      []
                  :discard   []
                  :removed   []}
          result (hydrate-deck deck test-catalog)]
      (is (= [] (:cards result))))))

(deftest hydrate-game-state-test
  (testing "hydrates both HOME and AWAY decks"
    (let [game-state {:players
                      {:HOME {:deck {:draw-pile [{:card-slug "michael-jordan"}]
                                     :hand      []
                                     :discard   []
                                     :removed   []}}
                       :AWAY {:deck {:draw-pile [{:card-slug "shaq"}]
                                     :hand      []
                                     :discard   []
                                     :removed   []}}}}
          result     (hydrate-game-state game-state test-catalog)]
      (is (= 1 (count (get-in result [:players :HOME :deck :cards]))))
      (is (= "michael-jordan" (get-in result [:players :HOME :deck :cards 0 :slug])))
      (is (= 1 (count (get-in result [:players :AWAY :deck :cards]))))
      (is (= "shaq" (get-in result [:players :AWAY :deck :cards 0 :slug])))))

  (testing "returns nil for nil game state"
    (is (nil? (hydrate-game-state nil test-catalog)))))
