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
