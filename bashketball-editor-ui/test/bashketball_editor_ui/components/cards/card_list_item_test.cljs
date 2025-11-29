(ns bashketball-editor-ui.components.cards.card-list-item-test
  (:require
   [bashketball-ui.cards.card-list-item :refer [card-list-item
                                                card-list-item-skeleton
                                                format-card-type
                                                format-relative-time]]
   [cljs-tlr.core :as tlr]
   [cljs-tlr.fixtures :as fixtures]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(def sample-card
  {:slug "lebron-james"
   :name "LeBron James"
   :card-type :card-type/PLAYER_CARD
   :updated-at "2024-01-15T10:00:00Z"})

;; -----------------------------------------------------------------------------
;; format-card-type tests
;; -----------------------------------------------------------------------------

(t/deftest format-card-type-removes-card-suffix-test
  (t/testing "removes _CARD suffix"
    (t/is (= "PLAYER" (format-card-type :card-type/PLAYER_CARD)))))

(t/deftest format-card-type-replaces-underscores-test
  (t/testing "replaces underscores with spaces"
    (t/is (= "SPLIT PLAY" (format-card-type :card-type/SPLIT_PLAY_CARD)))))

(t/deftest format-card-type-handles-nil-test
  (t/testing "returns nil for nil input"
    (t/is (nil? (format-card-type nil)))))

;; -----------------------------------------------------------------------------
;; format-relative-time tests
;; -----------------------------------------------------------------------------

(t/deftest format-relative-time-handles-nil-test
  (t/testing "returns nil for nil input"
    (t/is (nil? (format-relative-time nil)))))

(t/deftest format-relative-time-shows-just-now-test
  (t/testing "shows 'just now' for recent timestamps"
    (let [now (.toISOString (js/Date.))]
      (t/is (= "just now" (format-relative-time now))))))

;; -----------------------------------------------------------------------------
;; card-list-item tests
;; -----------------------------------------------------------------------------

(t/deftest card-list-item-renders-name-test
  (t/testing "renders card name"
    (tlr/render ($ card-list-item {:card sample-card}))
    (t/is (some? (tlr/get-by-text "LeBron James")))))

(t/deftest card-list-item-renders-type-badge-test
  (t/testing "renders card type badge"
    (tlr/render ($ card-list-item {:card sample-card}))
    (t/is (some? (tlr/get-by-text "PLAYER")))))

(t/deftest card-list-item-has-button-role-test
  (t/testing "has button role for accessibility"
    (tlr/render ($ card-list-item {:card sample-card}))
    (t/is (some? (tlr/get-by-role "button")))))

(t/deftest card-list-item-calls-on-click-test
  (t/async done
           (t/testing "calls on-click handler when clicked"
             (let [clicked (atom nil)
                   user    (tlr/setup)]
               (tlr/render ($ card-list-item {:card sample-card
                                              :on-click #(reset! clicked %)}))
               (-> (tlr/click user (tlr/get-by-role "button"))
                   (.then (fn []
                            (t/is (= sample-card @clicked))
                            (done)))
                   (.catch (fn [e]
                             (t/is false (str e))
                             (done))))))))

(t/deftest card-list-item-shows-selected-state-test
  (t/testing "applies selected styling when selected"
    (tlr/render ($ card-list-item {:card sample-card :selected? true}))
    (let [button (tlr/get-by-role "button")]
      (t/is (.includes (.-className button) "bg-blue-50")))))

(t/deftest card-list-item-player-card-has-blue-badge-test
  (t/testing "player card has blue badge styling"
    (tlr/render ($ card-list-item {:card sample-card}))
    (let [badge (tlr/get-by-text "PLAYER")]
      (t/is (.includes (.-className badge) "bg-blue-100")))))

(t/deftest card-list-item-ability-card-has-purple-badge-test
  (t/testing "ability card has purple badge styling"
    (tlr/render ($ card-list-item {:card (assoc sample-card :card-type :card-type/ABILITY_CARD)}))
    (let [badge (tlr/get-by-text "ABILITY")]
      (t/is (.includes (.-className badge) "bg-purple-100")))))

;; -----------------------------------------------------------------------------
;; card-list-item-skeleton tests
;; -----------------------------------------------------------------------------

(t/deftest skeleton-renders-test
  (t/testing "renders skeleton with pulse animation"
    (tlr/render ($ card-list-item-skeleton))
    (let [skeleton (js/document.querySelector ".animate-pulse")]
      (t/is (some? skeleton)))))
