(ns bashketball-game-ui.components.deck.deck-list-test
  (:require
   [bashketball-game-ui.components.deck.deck-list :refer [deck-list]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-decks
  [{:id "deck-1"
    :name "First Deck"
    :cardSlugs ["card-1" "card-2"]
    :isValid true
    :validationErrors []}
   {:id "deck-2"
    :name "Second Deck"
    :cardSlugs ["card-3"]
    :isValid false
    :validationErrors ["Not enough cards"]}])

(t/deftest deck-list-renders-decks-test
  (uix-tlr/render ($ deck-list {:decks sample-decks}))
  (t/is (some? (screen/get-by-text "First Deck")))
  (t/is (some? (screen/get-by-text "Second Deck"))))

(t/deftest deck-list-renders-empty-state-test
  (uix-tlr/render ($ deck-list {:decks []}))
  (t/is (some? (screen/get-by-text #"don't have any decks"))))

(t/deftest deck-list-renders-loading-state-test
  (uix-tlr/render ($ deck-list {:decks [] :loading true}))
  (t/is (nil? (screen/query-by-text #"don't have any decks"))))

(t/deftest deck-list-renders-nil-decks-as-empty-test
  (uix-tlr/render ($ deck-list {:decks nil}))
  (t/is (some? (screen/get-by-text #"don't have any decks"))))
