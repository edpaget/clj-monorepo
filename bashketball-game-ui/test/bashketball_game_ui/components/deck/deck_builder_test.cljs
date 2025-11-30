(ns bashketball-game-ui.components.deck.deck-builder-test
  (:require
   [bashketball-game-ui.components.deck.deck-builder :refer [deck-builder]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-deck
  {:name "My Deck"
   :card-slugs ["player-1" "player-1" "action-1" "action-2"]
   :is-valid false
   :validation-errors ["Need at least 3 player cards"]})

(def sample-cards
  [{:slug "player-1" :name "Star Player" :card-type :card-type/PLAYER_CARD}
   {:slug "action-1" :name "Quick Pass" :card-type :card-type/COACHING_CARD}
   {:slug "action-2" :name "Fast Break" :card-type :card-type/STANDARD_ACTION_CARD}])

(t/deftest deck-builder-renders-title-test
  (uix-tlr/render ($ deck-builder {:deck sample-deck :cards sample-cards}))
  (t/is (some? (screen/get-by-text "Deck Contents"))))

(t/deftest deck-builder-renders-total-cards-test
  (uix-tlr/render ($ deck-builder {:deck sample-deck :cards sample-cards}))
  (t/is (some? (screen/get-by-text "4 cards"))))

(t/deftest deck-builder-renders-validation-status-test
  (uix-tlr/render ($ deck-builder {:deck sample-deck :cards sample-cards}))
  (t/is (some? (screen/get-by-text "Incomplete deck"))))

(t/deftest deck-builder-renders-valid-status-test
  (let [valid-deck (assoc sample-deck :is-valid true :validation-errors [])]
    (uix-tlr/render ($ deck-builder {:deck valid-deck :cards sample-cards}))
    (t/is (some? (screen/get-by-text "Valid deck")))))

(t/deftest deck-builder-renders-card-sections-test
  (uix-tlr/render ($ deck-builder {:deck sample-deck :cards sample-cards}))
  (t/is (some? (screen/get-by-text "Player Cards")))
  (t/is (some? (screen/get-by-text "Action Cards"))))

(t/deftest deck-builder-renders-card-names-test
  (uix-tlr/render ($ deck-builder {:deck sample-deck :cards sample-cards}))
  (t/is (some? (screen/get-by-text "Star Player")))
  (t/is (some? (screen/get-by-text "Quick Pass"))))

(t/deftest deck-builder-renders-validation-errors-test
  (uix-tlr/render ($ deck-builder {:deck sample-deck :cards sample-cards}))
  (t/is (some? (screen/get-by-text "Need at least 3 player cards"))))

(t/deftest deck-builder-remove-button-calls-handler-test
  (t/async done
           (let [removed (atom nil)
                 _       (uix-tlr/render ($ deck-builder {:deck sample-deck
                                                          :cards sample-cards
                                                          :on-remove-card #(reset! removed %)}))
                 usr     (user/setup)
                 btns    (screen/get-all-by-role "button" {:name "Remove one copy"})]
             (-> (user/click usr (first btns))
                 (.then (fn []
                          (t/is (some? @removed))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

;; Tests for card merging behavior (verifying fix for cards not appearing when added)

(t/deftest deck-builder-shows-card-count-even-without-card-data-test
  (t/testing "Total count reflects slugs even if cards list is incomplete"
    (let [deck-with-extra-slug {:name "Test Deck"
                                :card-slugs ["player-1" "missing-card"]
                                :is-valid false
                                :validation-errors []}
          ;; Only player-1 has card data, missing-card doesn't
          partial-cards        [{:slug "player-1" :name "Star Player" :card-type :card-type/PLAYER_CARD}]]
      (uix-tlr/render ($ deck-builder {:deck deck-with-extra-slug :cards partial-cards}))
      ;; Total should show 2 cards (from slugs count)
      (t/is (some? (screen/get-by-text "2 cards"))))))

(t/deftest deck-builder-renders-only-cards-with-data-test
  (t/testing "Cards without data in cards list are not rendered"
    (let [deck-with-extra-slug {:name "Test Deck"
                                :card-slugs ["player-1" "missing-card"]
                                :is-valid false
                                :validation-errors []}
          partial-cards        [{:slug "player-1" :name "Star Player" :card-type :card-type/PLAYER_CARD}]]
      (uix-tlr/render ($ deck-builder {:deck deck-with-extra-slug :cards partial-cards}))
      ;; Star Player should appear
      (t/is (some? (screen/get-by-text "Star Player")))
      ;; missing-card has no name to display
      (t/is (nil? (screen/query-by-text "missing-card"))))))

(t/deftest deck-builder-renders-merged-cards-correctly-test
  (t/testing "All cards render when cards list includes newly added cards"
    (let [deck         {:name "Test Deck"
                        :card-slugs ["player-1" "new-card"]
                        :is-valid false
                        :validation-errors []}
          ;; Simulates merged cards - both original and newly added
          merged-cards [{:slug "player-1" :name "Star Player" :card-type :card-type/PLAYER_CARD}
                        {:slug "new-card" :name "New Addition" :card-type :card-type/COACHING_CARD}]]
      (uix-tlr/render ($ deck-builder {:deck deck :cards merged-cards}))
      ;; Both cards should appear
      (t/is (some? (screen/get-by-text "Star Player")))
      (t/is (some? (screen/get-by-text "New Addition"))))))

(t/deftest deck-builder-renders-duplicate-cards-with-count-test
  (t/testing "Duplicate slugs show correct count when card is merged"
    (let [deck  {:name "Test Deck"
                 :card-slugs ["action-1" "action-1" "action-1"]
                 :is-valid false
                 :validation-errors []}
          cards [{:slug "action-1" :name "Quick Pass" :card-type :card-type/COACHING_CARD}]]
      (uix-tlr/render ($ deck-builder {:deck deck :cards cards}))
      ;; Should show 3× for the card
      (t/is (some? (screen/get-by-text "3×"))))))
