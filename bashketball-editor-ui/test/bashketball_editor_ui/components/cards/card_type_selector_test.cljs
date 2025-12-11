(ns bashketball-editor-ui.components.cards.card-type-selector-test
  (:require
   ["react-router-dom" :as rr]
   [bashketball-editor-ui.components.cards.card-type-selector :refer [card-type-selector card-types]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(defn with-router
  [component {:keys [initial-entries]}]
  ($ rr/MemoryRouter {:initialEntries (clj->js initial-entries)}
     component))

(t/deftest card-type-selector-renders-test
  (t/testing "renders with placeholder when no type selected"
    (tlr/render (with-router ($ card-type-selector {}) {:initial-entries ["/"]}))
    (t/is (some? (tlr/get-by-role "combobox")))))

(t/deftest card-type-selector-shows-current-value-test
  (t/testing "shows current card type when provided"
    (tlr/render (with-router
                  ($ card-type-selector {:current-card-type "PLAYER_CARD"})
                  {:initial-entries ["/?type=PLAYER_CARD"]}))
    (t/is (some? (tlr/get-by-role "combobox")))))

(t/deftest card-types-has-all-card-types-test
  (t/testing "card-types includes all expected types"
    (let [type-values (set (map :value card-types))]
      (t/is (contains? type-values "PLAYER_CARD"))
      (t/is (contains? type-values "PLAY_CARD"))
      (t/is (contains? type-values "ABILITY_CARD")))))
