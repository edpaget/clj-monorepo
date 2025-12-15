(ns bashketball-game-ui.hooks.use-game-ui-test
  "Tests for the game UI hooks."
  (:require
   ["@testing-library/react" :as rtl]
   [bashketball-game-ui.hooks.use-game-ui :as ui]
   [cljs-tlr.fixtures :as fixtures]
   [cljs-tlr.render :as render]
   [cljs.test :as t :include-macros true]))

(t/use-fixtures :each fixtures/cleanup-fixture)

(defn get-result
  "Gets the current hook result from render-hook output."
  [hook-result]
  (.-current (.-result hook-result)))

(defn act
  "Wrapper around RTL act."
  [f]
  (rtl/act f))

;; =============================================================================
;; use-side-panel-mode tests
;; =============================================================================

(t/deftest use-side-panel-mode-initial-state-test
  (let [hook-result (render/render-hook #(ui/use-side-panel-mode))
        result      (get-result hook-result)]
    (t/is (= :log (:mode result)) "Should start in log mode")))

(t/deftest use-side-panel-mode-returns-all-keys-test
  (let [hook-result (render/render-hook #(ui/use-side-panel-mode))
        result      (get-result hook-result)]
    (t/is (contains? result :mode) "Should have :mode key")
    (t/is (contains? result :show-log) "Should have :show-log key")
    (t/is (contains? result :show-players) "Should have :show-players key")
    (t/is (contains? result :toggle) "Should have :toggle key")))

(t/deftest use-side-panel-mode-show-players-changes-mode-test
  (let [hook-result (render/render-hook #(ui/use-side-panel-mode))]
    (act #((:show-players (get-result hook-result))))
    (t/is (= :players (:mode (get-result hook-result)))
          "Should be in players mode after show-players")))

(t/deftest use-side-panel-mode-show-log-changes-mode-test
  (let [hook-result (render/render-hook #(ui/use-side-panel-mode))]
    (act #((:show-players (get-result hook-result))))
    (act #((:show-log (get-result hook-result))))
    (t/is (= :log (:mode (get-result hook-result)))
          "Should be in log mode after show-log")))

(t/deftest use-side-panel-mode-toggle-switches-mode-test
  (let [hook-result (render/render-hook #(ui/use-side-panel-mode))]
    (t/is (= :log (:mode (get-result hook-result))) "Initial mode is log")
    (act #((:toggle (get-result hook-result))))
    (t/is (= :players (:mode (get-result hook-result))) "First toggle -> players")
    (act #((:toggle (get-result hook-result))))
    (t/is (= :log (:mode (get-result hook-result))) "Second toggle -> log")))

(t/deftest use-side-panel-mode-functions-are-callable-test
  (let [hook-result (render/render-hook #(ui/use-side-panel-mode))
        result      (get-result hook-result)]
    (t/is (fn? (:show-log result)) "show-log should be a function")
    (t/is (fn? (:show-players result)) "show-players should be a function")
    (t/is (fn? (:toggle result)) "toggle should be a function")))

;; =============================================================================
;; use-standard-action-mode tests
;; =============================================================================

(t/deftest use-standard-action-mode-initial-state-test
  (let [hook-result (render/render-hook #(ui/use-standard-action-mode))
        result      (get-result hook-result)]
    (t/is (false? (:active result)) "Should start inactive")
    (t/is (= :select-cards (:step result)) "Should start in select-cards step")
    (t/is (empty? (:cards result)) "Should start with empty cards set")
    (t/is (zero? (:count result)) "Count should be zero")))

(t/deftest use-standard-action-mode-returns-all-keys-test
  (let [hook-result (render/render-hook #(ui/use-standard-action-mode))
        result      (get-result hook-result)]
    (t/is (contains? result :active) "Should have :active key")
    (t/is (contains? result :step) "Should have :step key")
    (t/is (contains? result :cards) "Should have :cards key")
    (t/is (contains? result :count) "Should have :count key")
    (t/is (contains? result :toggle-card) "Should have :toggle-card key")
    (t/is (contains? result :enter) "Should have :enter key")
    (t/is (contains? result :proceed) "Should have :proceed key")
    (t/is (contains? result :cancel) "Should have :cancel key")
    (t/is (contains? result :get-cards-and-exit) "Should have :get-cards-and-exit key")))

(t/deftest use-standard-action-mode-enter-activates-test
  (let [hook-result (render/render-hook #(ui/use-standard-action-mode))]
    (act #((:enter (get-result hook-result))))
    (let [result (get-result hook-result)]
      (t/is (true? (:active result)) "Should be active after enter")
      (t/is (= :select-cards (:step result)) "Should be in select-cards step"))))

(t/deftest use-standard-action-mode-toggle-card-adds-card-test
  (let [hook-result (render/render-hook #(ui/use-standard-action-mode))]
    (act #((:enter (get-result hook-result))))
    (act #((:toggle-card (get-result hook-result)) "card-1"))
    (let [result (get-result hook-result)]
      (t/is (contains? (:cards result) "card-1") "Card should be in set")
      (t/is (= 1 (:count result)) "Count should be 1"))))

(t/deftest use-standard-action-mode-toggle-card-removes-card-test
  (let [hook-result (render/render-hook #(ui/use-standard-action-mode))]
    (act #((:enter (get-result hook-result))))
    (act #((:toggle-card (get-result hook-result)) "card-1"))
    (act #((:toggle-card (get-result hook-result)) "card-1"))
    (let [result (get-result hook-result)]
      (t/is (not (contains? (:cards result) "card-1")) "Card should be removed")
      (t/is (= 0 (:count result)) "Count should be 0"))))

(t/deftest use-standard-action-mode-proceed-requires-two-cards-test
  (let [hook-result (render/render-hook #(ui/use-standard-action-mode))]
    (act #((:enter (get-result hook-result))))
    (act #((:toggle-card (get-result hook-result)) "card-1"))
    (act #((:proceed (get-result hook-result))))
    (let [result (get-result hook-result)]
      (t/is (= :select-cards (:step result)) "Should still be in select-cards (only 1 card)"))))

(t/deftest use-standard-action-mode-proceed-changes-step-test
  (let [hook-result (render/render-hook #(ui/use-standard-action-mode))]
    (act #((:enter (get-result hook-result))))
    (act #((:toggle-card (get-result hook-result)) "card-1"))
    (act #((:toggle-card (get-result hook-result)) "card-2"))
    (act #((:proceed (get-result hook-result))))
    (let [result (get-result hook-result)]
      (t/is (= :select-action (:step result)) "Should be in select-action step"))))

(t/deftest use-standard-action-mode-cancel-resets-state-test
  (let [hook-result (render/render-hook #(ui/use-standard-action-mode))]
    (act #((:enter (get-result hook-result))))
    (act #((:toggle-card (get-result hook-result)) "card-1"))
    (act #((:toggle-card (get-result hook-result)) "card-2"))
    (act #((:cancel (get-result hook-result))))
    (let [result (get-result hook-result)]
      (t/is (false? (:active result)) "Should be inactive after cancel")
      (t/is (empty? (:cards result)) "Cards should be empty after cancel")
      (t/is (= :select-cards (:step result)) "Step should reset to select-cards"))))

(t/deftest use-standard-action-mode-get-cards-and-exit-test
  (let [hook-result (render/render-hook #(ui/use-standard-action-mode))]
    (act #((:enter (get-result hook-result))))
    (act #((:toggle-card (get-result hook-result)) "card-1"))
    (act #((:toggle-card (get-result hook-result)) "card-2"))
    (let [cards (atom nil)]
      (act #(reset! cards ((:get-cards-and-exit (get-result hook-result)))))
      (t/is (= #{"card-1" "card-2"} @cards) "Should return selected cards")
      (let [result (get-result hook-result)]
        (t/is (false? (:active result)) "Should be inactive after exit")
        (t/is (empty? (:cards result)) "Cards should be empty after exit")))))
