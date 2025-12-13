(ns bashketball-game-ui.schemas.deck-test
  (:require
   [bashketball-game-ui.schemas.deck :as deck]
   [bashketball-schemas.core :as schemas]
   [cljs.test :as t :include-macros true]
   [malli.core :as m]))

(t/deftest player-card-predicate-test
  (t/testing "player-card? returns true for player cards"
    (t/is (deck/player-card? {:card-type :card-type/PLAYER_CARD})))
  (t/testing "player-card? returns false for other card types"
    (t/is (not (deck/player-card? {:card-type :card-type/COACHING_CARD})))
    (t/is (not (deck/player-card? {:card-type :card-type/STANDARD_ACTION_CARD})))))

(t/deftest action-card-predicate-test
  (t/testing "action-card? returns true for non-player cards"
    (t/is (deck/action-card? {:card-type :card-type/COACHING_CARD}))
    (t/is (deck/action-card? {:card-type :card-type/STANDARD_ACTION_CARD}))
    (t/is (deck/action-card? {:card-type :card-type/SPLIT_PLAY_CARD})))
  (t/testing "action-card? returns false for player cards"
    (t/is (not (deck/action-card? {:card-type :card-type/PLAYER_CARD})))))

(t/deftest count-card-copies-test
  (t/testing "counts card copies correctly"
    (t/is (= {"card-a" 2 "card-b" 1}
             (deck/count-card-copies ["card-a" "card-b" "card-a"]))))
  (t/testing "handles empty list"
    (t/is (= {} (deck/count-card-copies [])))))

(t/deftest validate-deck-min-player-cards-test
  (t/testing "validates minimum player cards"
    (let [cards-by-slug {"player-1" {:card-type :card-type/PLAYER_CARD :slug "player-1"}
                         "player-2" {:card-type :card-type/PLAYER_CARD :slug "player-2"}}
          deck          {:card-slugs ["player-1" "player-2"]}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (some #(re-find #"at least 3 player" %) errors)))))

(t/deftest validate-deck-max-player-cards-test
  (t/testing "validates maximum player cards"
    (let [cards-by-slug (into {}
                              (for [i (range 6)]
                                [(str "player-" i)
                                 {:card-type :card-type/PLAYER_CARD :slug (str "player-" i)}]))
          deck          {:card-slugs (mapv #(str "player-" %) (range 6))}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (some #(re-find #"Maximum 5 player" %) errors)))))

(t/deftest validate-deck-min-action-cards-test
  (t/testing "validates minimum action cards"
    (let [player-cards  (into {}
                              (for [i (range 3)]
                                [(str "player-" i)
                                 {:card-type :card-type/PLAYER_CARD :slug (str "player-" i)}]))
          action-cards  (into {}
                              (for [i (range 5)]
                                [(str "action-" i)
                                 {:card-type :card-type/COACHING_CARD :slug (str "action-" i)}]))
          cards-by-slug (merge player-cards action-cards)
          deck          {:card-slugs (concat (keys player-cards) (keys action-cards))}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (some #(re-find #"at least 10 action" %) errors)))))

(t/deftest validate-deck-max-copies-test
  (t/testing "validates maximum copies per card"
    (let [cards-by-slug {"card-a" {:card-type :card-type/COACHING_CARD :slug "card-a"}}
          deck          {:card-slugs ["card-a" "card-a" "card-a"]}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (some #(re-find #"Maximum 2 copies" %) errors)))))

(t/deftest validate-deck-valid-deck-test
  (t/testing "returns empty errors for valid deck"
    (let [player-cards  (into {}
                              (for [i (range 4)]
                                [(str "player-" i)
                                 {:card-type :card-type/PLAYER_CARD :slug (str "player-" i)}]))
          action-cards  (into {}
                              (for [i (range 35)]
                                [(str "action-" i)
                                 {:card-type :card-type/COACHING_CARD :slug (str "action-" i)}]))
          cards-by-slug (merge player-cards action-cards)
          deck          {:card-slugs (concat (keys player-cards) (keys action-cards))}
          errors        (deck/validate-deck-client deck cards-by-slug)]
      (t/is (empty? errors)))))

(t/deftest card-type-label-test
  (t/testing "returns human-readable labels for namespaced keywords"
    (t/is (= "Player" (deck/card-type-label :card-type/PLAYER_CARD)))
    (t/is (= "Coaching" (deck/card-type-label :card-type/COACHING_CARD)))
    (t/is (= "Action" (deck/card-type-label :card-type/STANDARD_ACTION_CARD))))
  (t/testing "returns name for unknown types"
    (t/is (= "UNKNOWN_TYPE" (deck/card-type-label :card-type/UNKNOWN_TYPE)))))

;; =============================================================================
;; DateTime schema tests
;; =============================================================================

(t/deftest datetime-validates-iso8601-strings-test
  (t/testing "accepts valid ISO8601 strings"
    (t/is (m/validate schemas/DateTime "2024-01-15T10:30:00Z"))
    (t/is (m/validate schemas/DateTime "2024-01-15T10:30:00.123Z"))
    (t/is (m/validate schemas/DateTime "2024-01-15T10:30:00+00:00"))))

(t/deftest datetime-validates-js-date-test
  (t/testing "accepts js/Date objects"
    (t/is (m/validate schemas/DateTime (js/Date.)))))

(t/deftest datetime-rejects-invalid-strings-test
  (t/testing "rejects invalid date strings"
    (t/is (not (m/validate schemas/DateTime "not-a-date")))
    (t/is (not (m/validate schemas/DateTime "2024-01-15")))
    (t/is (not (m/validate schemas/DateTime "")))))

(t/deftest datetime-rejects-other-types-test
  (t/testing "rejects non-date types"
    (t/is (not (m/validate schemas/DateTime 12345)))
    (t/is (not (m/validate schemas/DateTime nil)))
    (t/is (not (m/validate schemas/DateTime {:date "2024-01-15"})))))

;; =============================================================================
;; Deck schema tests
;; =============================================================================

(t/deftest deck-schema-accepts-valid-deck-test
  (t/testing "validates a complete deck"
    (let [deck {:id #uuid "550e8400-e29b-41d4-a716-446655440000"
                :name "My Deck"
                :card-slugs ["card-1" "card-2"]
                :is-valid true
                :validation-errors []}]
      (t/is (m/validate deck/Deck deck)))))

(t/deftest deck-schema-accepts-datetime-fields-test
  (t/testing "validates deck with ISO8601 datetime strings"
    (let [deck {:id #uuid "550e8400-e29b-41d4-a716-446655440000"
                :name "My Deck"
                :card-slugs []
                :is-valid false
                :created-at "2024-01-15T10:30:00Z"
                :updated-at "2024-01-15T11:00:00Z"}]
      (t/is (m/validate deck/Deck deck))))
  (t/testing "validates deck with js/Date objects"
    (let [deck {:id #uuid "550e8400-e29b-41d4-a716-446655440000"
                :name "My Deck"
                :card-slugs []
                :is-valid false
                :created-at (js/Date.)
                :updated-at (js/Date.)}]
      (t/is (m/validate deck/Deck deck)))))

(t/deftest deck-schema-accepts-nil-datetime-test
  (t/testing "validates deck with nil datetime fields"
    (let [deck {:id #uuid "550e8400-e29b-41d4-a716-446655440000"
                :name "My Deck"
                :card-slugs []
                :is-valid false
                :created-at nil
                :updated-at nil}]
      (t/is (m/validate deck/Deck deck)))))
