(ns bashketball-game-ui.game.handlers-test
  "Tests for game UI handler functions."
  (:require
   [bashketball-game-ui.game.handlers :as h]
   [cljs.test :as t :include-macros true]))

;; =============================================================================
;; selection-after-discard tests
;; =============================================================================

(t/deftest selection-after-discard-clears-when-card-discarded-test
  (let [selected  "card-1"
        discarded #{"card-1" "card-2"}
        result    (h/selection-after-discard selected discarded)]
    (t/is (nil? result)
          "Should clear selection when selected card is discarded")))

(t/deftest selection-after-discard-preserves-when-card-not-discarded-test
  (let [selected  "card-3"
        discarded #{"card-1" "card-2"}
        result    (h/selection-after-discard selected discarded)]
    (t/is (= "card-3" result)
          "Should preserve selection when selected card is not discarded")))

(t/deftest selection-after-discard-handles-nil-selection-test
  (let [selected  nil
        discarded #{"card-1" "card-2"}
        result    (h/selection-after-discard selected discarded)]
    (t/is (nil? result)
          "Should return nil when no card was selected")))

(t/deftest selection-after-discard-handles-empty-discard-test
  (let [selected  "card-1"
        discarded #{}
        result    (h/selection-after-discard selected discarded)]
    (t/is (= "card-1" result)
          "Should preserve selection when no cards discarded")))
