(ns bashketball-game-ui.components.deck.deck-card-test
  (:require
   [bashketball-game-ui.components.deck.deck-card :refer [deck-card deck-card-skeleton]]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def valid-deck
  {:id "deck-1"
   :name "Test Deck"
   :cardSlugs ["card-1" "card-2" "card-3"]
   :isValid true
   :validationErrors []})

(def invalid-deck
  {:id "deck-2"
   :name "Invalid Deck"
   :cardSlugs ["card-1"]
   :isValid false
   :validationErrors ["Need at least 3 player cards"]})

(t/deftest deck-card-renders-name-test
  (uix-tlr/render ($ deck-card {:deck valid-deck}))
  (t/is (some? (screen/get-by-text "Test Deck"))))

(t/deftest deck-card-renders-card-count-test
  (uix-tlr/render ($ deck-card {:deck valid-deck}))
  (t/is (some? (screen/get-by-text "3 cards"))))

(t/deftest deck-card-renders-valid-status-test
  (uix-tlr/render ($ deck-card {:deck valid-deck}))
  (t/is (some? (screen/get-by-text "Valid"))))

(t/deftest deck-card-renders-invalid-status-test
  (uix-tlr/render ($ deck-card {:deck invalid-deck}))
  (t/is (some? (screen/get-by-text "Need at least 3 player cards"))))

(t/deftest deck-card-edit-button-calls-handler-test
  (t/async done
           (let [edited (atom nil)
                 _      (uix-tlr/render ($ deck-card {:deck valid-deck
                                                      :on-edit #(reset! edited %)}))
                 usr    (user/setup)
                 btn    (screen/get-by-role "button" {:name "Edit deck"})]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is (= valid-deck @edited))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest deck-card-delete-button-calls-handler-test
  (t/async done
           (let [deleted (atom nil)
                 _       (uix-tlr/render ($ deck-card {:deck valid-deck
                                                       :on-delete #(reset! deleted %)}))
                 usr     (user/setup)
                 btn     (screen/get-by-role "button" {:name "Delete deck"})]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is (= valid-deck @deleted))
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest deck-card-skeleton-renders-test
  (uix-tlr/render ($ deck-card-skeleton))
  (t/is (some? (screen/query-by-text #".*" {:selector ".animate-pulse"}))))
