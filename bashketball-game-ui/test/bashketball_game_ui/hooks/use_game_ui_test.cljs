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
