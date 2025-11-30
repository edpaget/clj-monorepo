(ns bashketball-game-ui.schemas.deck-test
  (:require
   [bashketball-game-ui.schemas.deck :as deck]
   [cljs.test :as t :include-macros true]))

(t/deftest player-card-predicate-test
  (t/testing "player-card? returns true for player cards"
    (t/is (deck/player-card? {:cardType "PLAYER_CARD"}))
    (t/is (deck/player-card? {:card-type :card-type/PLAYER_CARD})))
  (t/testing "player-card? returns false for other card types"
    (t/is (not (deck/player-card? {:cardType "COACHING_CARD"})))
    (t/is (not (deck/player-card? {:cardType "STANDARD_ACTION_CARD"})))))

(t/deftest action-card-predicate-test
  (t/testing "action-card? returns true for non-player cards"
    (t/is (deck/action-card? {:cardType "COACHING_CARD"}))
    (t/is (deck/action-card? {:cardType "STANDARD_ACTION_CARD"}))
    (t/is (deck/action-card? {:cardType "SPLIT_PLAY_CARD"})))
  (t/testing "action-card? returns false for player cards"
    (t/is (not (deck/action-card? {:cardType "PLAYER_CARD"})))))

(t/deftest count-card-copies-test
  (t/testing "counts card copies correctly"
    (t/is (= {"card-a" 2 "card-b" 1}
             (deck/count-card-copies ["card-a" "card-b" "card-a"]))))
  (t/testing "handles empty list"
    (t/is (= {} (deck/count-card-copies [])))))

(t/deftest validate-deck-min-player-cards-test
  (t/testing "validates minimum player cards"
    (let [cards-by-slug {"player-1" {:cardType "PLAYER_CARD" :slug "player-1"}
                         "player-2" {:cardType "PLAYER_CARD" :slug "player-2"}}
          deck          {:cardSlugs ["player-1" "player-2"]}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (some #(re-find #"at least 3 player" %) errors)))))

(t/deftest validate-deck-max-player-cards-test
  (t/testing "validates maximum player cards"
    (let [cards-by-slug (into {}
                              (for [i (range 6)]
                                [(str "player-" i)
                                 {:cardType "PLAYER_CARD" :slug (str "player-" i)}]))
          deck          {:cardSlugs (mapv #(str "player-" %) (range 6))}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (some #(re-find #"Maximum 5 player" %) errors)))))

(t/deftest validate-deck-min-action-cards-test
  (t/testing "validates minimum action cards"
    (let [player-cards  (into {}
                              (for [i (range 3)]
                                [(str "player-" i)
                                 {:cardType "PLAYER_CARD" :slug (str "player-" i)}]))
          action-cards  (into {}
                              (for [i (range 10)]
                                [(str "action-" i)
                                 {:cardType "COACHING_CARD" :slug (str "action-" i)}]))
          cards-by-slug (merge player-cards action-cards)
          deck          {:cardSlugs (concat (keys player-cards) (keys action-cards))}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (some #(re-find #"at least 30 action" %) errors)))))

(t/deftest validate-deck-max-copies-test
  (t/testing "validates maximum copies per card"
    (let [cards-by-slug {"card-a" {:cardType "COACHING_CARD" :slug "card-a"}}
          deck          {:cardSlugs ["card-a" "card-a" "card-a" "card-a" "card-a"]}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (some #(re-find #"Maximum 4 copies" %) errors)))))

(t/deftest validate-deck-valid-deck-test
  (t/testing "returns empty errors for valid deck"
    (let [player-cards  (into {}
                              (for [i (range 4)]
                                [(str "player-" i)
                                 {:cardType "PLAYER_CARD" :slug (str "player-" i)}]))
          action-cards  (into {}
                              (for [i (range 35)]
                                [(str "action-" i)
                                 {:cardType "COACHING_CARD" :slug (str "action-" i)}]))
          cards-by-slug (merge player-cards action-cards)
          deck          {:cardSlugs (concat (keys player-cards) (keys action-cards))}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (empty? errors)))))

(t/deftest card-type-label-test
  (t/testing "returns human-readable labels"
    (t/is (= "Player" (deck/card-type-label "PLAYER_CARD")))
    (t/is (= "Coaching" (deck/card-type-label "COACHING_CARD")))
    (t/is (= "Action" (deck/card-type-label "STANDARD_ACTION_CARD"))))
  (t/testing "returns original value for unknown types"
    (t/is (= "UNKNOWN_TYPE" (deck/card-type-label "UNKNOWN_TYPE")))))
