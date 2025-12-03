(ns bashketball-game-ui.components.game.card-detail-modal-test
  (:require
   ["@apollo/client" :refer [InMemoryCache]]
   ["@apollo/client/testing/react" :refer [MockedProvider]]
   [bashketball-game-ui.components.game.card-detail-modal :refer [card-detail-modal]]
   [bashketball-game-ui.graphql.queries :as queries]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.screen :as screen]
   [cljs-tlr.uix :as uix-tlr]
   [cljs-tlr.user-event :as user]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-card
  #js {:__typename "StandardActionCard"
       :slug       "fast-break"
       :name       "Fast Break"
       :setSlug    "core-set"
       :cardType   "STANDARD_ACTION_CARD"
       :fate       3
       :offense    "Score bonus"
       :defense    nil})

(def game-card-possible-types
  #js {:GameCard #js ["PlayerCard" "AbilityCard" "PlayCard"
                      "StandardActionCard" "SplitPlayCard"
                      "CoachingCard" "TeamAssetCard"]})

(defn create-test-cache []
  (InMemoryCache. #js {:possibleTypes game-card-possible-types
                       :addTypename false}))

(def mock-card-query
  #js {:request #js {:query     queries/CARD_QUERY
                     :variables #js {:slug "fast-break"}}
       :result  #js {:data #js {:card sample-card}}})

(def mock-card-not-found-query
  #js {:request #js {:query     queries/CARD_QUERY
                     :variables #js {:slug "not-found"}}
       :result  #js {:data #js {:card nil}}})

(defn with-apollo [component mocks]
  ($ MockedProvider {:mocks (clj->js mocks)
                     :cache (create-test-cache)
                     :addTypename false}
     component))

(t/deftest card-detail-modal-renders-nothing-when-closed-test
  (uix-tlr/render (with-apollo
                   ($ card-detail-modal {:open?     false
                                          :card-slug "fast-break"
                                          :on-close  identity})
                   [mock-card-query]))
  (t/is (nil? (screen/query-by-text "×"))))

(t/deftest card-detail-modal-renders-close-button-when-open-test
  (uix-tlr/render (with-apollo
                   ($ card-detail-modal {:open?     true
                                          :card-slug nil
                                          :on-close  identity})
                   []))
  (t/is (some? (screen/get-by-text "×"))))

(t/deftest card-detail-modal-close-button-calls-handler-test
  (t/async done
           (let [closed (atom false)
                 _      (uix-tlr/render
                         (with-apollo
                          ($ card-detail-modal {:open?     true
                                                 :card-slug nil
                                                 :on-close  #(reset! closed true)})
                          []))
                 usr    (user/setup)
                 btn    (screen/get-by-text "×")]
             (-> (user/click usr btn)
                 (.then (fn []
                          (t/is @closed)
                          (done)))
                 (.catch (fn [e]
                           (t/is false (str e))
                           (done)))))))

(t/deftest card-detail-modal-shows-not-found-when-no-card-test
  (uix-tlr/render (with-apollo
                   ($ card-detail-modal {:open?     true
                                          :card-slug nil
                                          :on-close  identity})
                   []))
  (t/is (some? (screen/get-by-text "Card not found"))))
