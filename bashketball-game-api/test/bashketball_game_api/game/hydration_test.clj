(ns bashketball-game-api.game.hydration-test
  "Tests for game state hydration utilities."
  (:require
   [bashketball-game-api.game.hydration :as hydration]
   [bashketball-game-api.services.catalog :as catalog]
   [clojure.test :refer [deftest is testing]]))

(def test-cards
  [{:slug "michael-jordan" :name "Michael Jordan" :card-type :card-type/PLAYER_CARD
    :set-slug "test-set" :speed 9 :sht 8 :pss 7 :def 6 :size :size/MD
    :abilities [] :player-subtypes [:player-subtype/HUMAN]}
   {:slug "shaq" :name "Shaquille O'Neal" :card-type :card-type/PLAYER_CARD
    :set-slug "test-set" :speed 5 :sht 6 :pss 4 :def 8 :size :size/LG
    :abilities [] :player-subtypes [:player-subtype/HUMAN]}
   {:slug "basic-shot" :name "Basic Shot" :card-type :card-type/PLAY_CARD
    :set-slug "test-set" :fate 3
    :play {:play/id "basic-shot"
           :play/effect {:effect/type :bashketball/shoot}}}])

(def test-catalog
  (catalog/create-catalog-from-data test-cards []))

(deftest collect-deck-slugs-test
  (testing "extracts slugs from all piles"
    (let [deck {:draw-pile [{:card-slug "a"} {:card-slug "b"}]
                :hand      [{:card-slug "c"}]
                :discard   [{:card-slug "d"}]
                :removed   [{:card-slug "e"}]}]
      (is (= #{"a" "b" "c" "d" "e"} (set (hydration/collect-deck-slugs deck))))))

  (testing "includes examined pile"
    (let [deck {:draw-pile [{:card-slug "a"}]
                :hand      []
                :discard   []
                :removed   []
                :examined  [{:card-slug "x"}]}]
      (is (= #{"a" "x"} (set (hydration/collect-deck-slugs deck))))))

  (testing "returns distinct slugs"
    (let [deck {:draw-pile [{:card-slug "a"} {:card-slug "a"}]
                :hand      [{:card-slug "a"}]
                :discard   []
                :removed   []}]
      (is (= ["a"] (vec (hydration/collect-deck-slugs deck))))))

  (testing "handles empty piles"
    (let [deck {:draw-pile []
                :hand      []
                :discard   []
                :removed   []}]
      (is (empty? (hydration/collect-deck-slugs deck)))))

  (testing "handles nil deck"
    (is (empty? (hydration/collect-deck-slugs nil)))))

(deftest collect-player-attachment-slugs-test
  (testing "extracts slugs from player attachments"
    (let [roster {:players {"player-1" {:attachments [{:card-slug "buff-1"}]}
                            "player-2" {:attachments [{:card-slug "buff-2"}
                                                      {:card-slug "buff-3"}]}}}]
      (is (= #{"buff-1" "buff-2" "buff-3"}
             (set (hydration/collect-player-attachment-slugs roster))))))

  (testing "handles empty attachments"
    (let [roster {:players {"player-1" {:attachments []}}}]
      (is (empty? (hydration/collect-player-attachment-slugs roster)))))

  (testing "handles nil roster"
    (is (empty? (hydration/collect-player-attachment-slugs nil)))))

(deftest collect-asset-slugs-test
  (testing "extracts slugs from assets"
    (let [assets [{:card-slug "asset-1"} {:card-slug "asset-2"}]]
      (is (= ["asset-1" "asset-2"] (vec (hydration/collect-asset-slugs assets))))))

  (testing "filters nil slugs"
    (let [assets [{:card-slug "asset-1"} {:other-key "value"}]]
      (is (= ["asset-1"] (vec (hydration/collect-asset-slugs assets))))))

  (testing "handles empty assets"
    (is (empty? (hydration/collect-asset-slugs [])))))

(deftest collect-extra-slugs-test
  (testing "collects from all extra locations"
    (let [game-state {:players {:team/HOME {:assets [{:card-slug "home-asset"}]
                                            :team {:players {"p1" {:attachments [{:card-slug "att-1"}]}}}}
                                :team/AWAY {:assets [{:card-slug "away-asset"}]
                                            :team {:players {"p2" {:attachments [{:card-slug "att-2"}]}}}}}
                      :play-area [{:card-slug "in-play"}]}]
      (is (= #{"home-asset" "away-asset" "att-1" "att-2" "in-play"}
             (set (hydration/collect-extra-slugs game-state))))))

  (testing "handles missing keys"
    (let [game-state {}]
      (is (empty? (hydration/collect-extra-slugs game-state))))))

(deftest hydrate-deck-test
  (testing "adds cards field with hydrated data"
    (let [deck   {:draw-pile [{:card-slug "michael-jordan"}]
                  :hand      [{:card-slug "basic-shot"}]
                  :discard   []
                  :removed   []}
          result (hydration/hydrate-deck deck test-catalog)]
      (is (= 2 (count (:cards result))))
      (is (some #(= "michael-jordan" (:slug %)) (:cards result)))
      (is (some #(= "basic-shot" (:slug %)) (:cards result)))))

  (testing "filters out missing cards"
    (let [deck   {:draw-pile [{:card-slug "unknown-card"}]
                  :hand      [{:card-slug "michael-jordan"}]
                  :discard   []
                  :removed   []}
          result (hydration/hydrate-deck deck test-catalog)]
      (is (= 1 (count (:cards result))))
      (is (= "michael-jordan" (:slug (first (:cards result)))))))

  (testing "returns empty cards when catalog is nil"
    (let [deck {:draw-pile [{:card-slug "a"}]
                :hand      []
                :discard   []
                :removed   []}]
      (is (= [] (:cards (hydration/hydrate-deck deck nil))))))

  (testing "handles empty deck"
    (let [deck   {:draw-pile []
                  :hand      []
                  :discard   []
                  :removed   []}
          result (hydration/hydrate-deck deck test-catalog)]
      (is (= [] (:cards result))))))

(deftest hydrate-game-state-test
  (testing "hydrates both HOME and AWAY decks"
    (let [game-state {:players
                      {:team/HOME {:deck {:draw-pile [{:card-slug "michael-jordan"}]
                                          :hand      []
                                          :discard   []
                                          :removed   []}}
                       :team/AWAY {:deck {:draw-pile [{:card-slug "shaq"}]
                                          :hand      []
                                          :discard   []
                                          :removed   []}}}}
          result     (hydration/hydrate-game-state game-state test-catalog)]
      (is (= 1 (count (get-in result [:players :team/HOME :deck :cards]))))
      (is (= "michael-jordan" (get-in result [:players :team/HOME :deck :cards 0 :slug])))
      (is (= 1 (count (get-in result [:players :team/AWAY :deck :cards]))))
      (is (= "shaq" (get-in result [:players :team/AWAY :deck :cards 0 :slug])))))

  (testing "includes extra cards from play area"
    (let [game-state {:players
                      {:team/HOME {:deck {:draw-pile [{:card-slug "michael-jordan"}]
                                          :hand      []
                                          :discard   []
                                          :removed   []}}
                       :team/AWAY {:deck {:draw-pile [{:card-slug "shaq"}]
                                          :hand      []
                                          :discard   []
                                          :removed   []}}}
                      :play-area [{:card-slug "basic-shot"}]}
          result     (hydration/hydrate-game-state game-state test-catalog)
          home-cards (get-in result [:players :team/HOME :deck :cards])
          away-cards (get-in result [:players :team/AWAY :deck :cards])]
      (is (some #(= "basic-shot" (:slug %)) home-cards))
      (is (some #(= "basic-shot" (:slug %)) away-cards))))

  (testing "returns nil for nil game state"
    (is (nil? (hydration/hydrate-game-state nil test-catalog))))

  (testing "handles nil catalog"
    (let [game-state {:players
                      {:team/HOME {:deck {:draw-pile [{:card-slug "a"}]
                                          :hand      []
                                          :discard   []
                                          :removed   []}}
                       :team/AWAY {:deck {:draw-pile [{:card-slug "b"}]
                                          :hand      []
                                          :discard   []
                                          :removed   []}}}}
          result     (hydration/hydrate-game-state game-state nil)]
      (is (= [] (get-in result [:players :team/HOME :deck :cards])))
      (is (= [] (get-in result [:players :team/AWAY :deck :cards]))))))
